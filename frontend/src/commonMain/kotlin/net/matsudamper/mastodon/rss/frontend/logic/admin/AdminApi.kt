package net.matsudamper.mastodon.rss.frontend.logic.admin // pragma: allowlist secret

import kotlin.time.Instant
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.Optional
import net.matsudamper.mastodon.rss.frontend.graphql.AdminAccountQuery // pragma: allowlist secret // pragma: allowlist secret
import net.matsudamper.mastodon.rss.frontend.graphql.AdminAccountsQuery // pragma: allowlist secret // pragma: allowlist secret
import net.matsudamper.mastodon.rss.frontend.graphql.AdminAddAccountMutation // pragma: allowlist secret // pragma: allowlist secret
import net.matsudamper.mastodon.rss.frontend.graphql.AdminLoginMutation // pragma: allowlist secret // pragma: allowlist secret
import net.matsudamper.mastodon.rss.frontend.graphql.AdminLogoutMutation // pragma: allowlist secret // pragma: allowlist secret
import net.matsudamper.mastodon.rss.frontend.graphql.AdminNotesQuery // pragma: allowlist secret // pragma: allowlist secret
import net.matsudamper.mastodon.rss.frontend.graphql.AdminPostNoteMutation // pragma: allowlist secret // pragma: allowlist secret
import net.matsudamper.mastodon.rss.frontend.graphql.AdminPreviewFeedMutation // pragma: allowlist secret // pragma: allowlist secret
import net.matsudamper.mastodon.rss.frontend.graphql.AdminSaveFeedMutation // pragma: allowlist secret // pragma: allowlist secret
import net.matsudamper.mastodon.rss.frontend.graphql.AdminSessionQuery // pragma: allowlist secret // pragma: allowlist secret
import net.matsudamper.mastodon.rss.frontend.graphql.fragment.AdminAccountFields // pragma: allowlist secret // pragma: allowlist secret
import net.matsudamper.mastodon.rss.frontend.graphql.fragment.AdminNoteFields // pragma: allowlist secret // pragma: allowlist secret
import net.matsudamper.mastodon.rss.frontend.graphql.fragment.AdminSessionFields // pragma: allowlist secret // pragma: allowlist secret
import net.matsudamper.mastodon.rss.frontend.graphql.type.AdminFeedPreviewFailure // pragma: allowlist secret // pragma: allowlist secret
import net.matsudamper.mastodon.rss.frontend.graphql.type.AdminLoginFailure // pragma: allowlist secret // pragma: allowlist secret
import net.matsudamper.mastodon.rss.frontend.graphql.type.AdminSaveFeedFailure // pragma: allowlist secret // pragma: allowlist secret
import net.matsudamper.mastodon.rss.frontend.logic.GraphQlClient // pragma: allowlist secret // pragma: allowlist secret
import net.matsudamper.mastodon.rss.frontend.logic.account.Account // pragma: allowlist secret // pragma: allowlist secret

class AdminApi(
    private val client: ApolloClient = GraphQlClient.apollo,
) {
    suspend fun session(): AdminSessionResult {
        return client
            .query(AdminSessionQuery())
            .execute()
            .toSessionResult { it.admin.session.adminSessionFields }
    }

    suspend fun login(password: String): AdminLoginResult {
        val response = client.mutation(AdminLoginMutation(password)).execute()
        val login = response.data?.admin?.login ?: return AdminLoginResult.Failure(response.failureMessage())

        return when (login.failure) {
            null -> AdminLoginResult.Success
            AdminLoginFailure.WRONG_PASSWORD -> AdminLoginResult.WrongPassword
            AdminLoginFailure.NOT_CONFIGURED -> AdminLoginResult.NotConfigured
            AdminLoginFailure.UNKNOWN__ -> AdminLoginResult.Failure("Unknown")
        }
    }

    suspend fun logout(): AdminSessionResult {
        return client
            .mutation(AdminLogoutMutation())
            .execute()
            .toSessionResult { it.admin.logout.adminSessionFields }
    }

    suspend fun accounts(): AdminAccountsResult {
        val response = client.query(AdminAccountsQuery()).execute()
        val data = response.data ?: return AdminAccountsResult.Failure(response.failureMessage())

        return AdminAccountsResult.Success(
            data.admin.adminAccounts.map { it.adminAccountFields.toAdminAccount() },
        )
    }

    suspend fun account(username: String): AdminAccountResult {
        val response = client.query(AdminAccountQuery(username)).execute()

        // 失敗を先に見る。null は「そのアカウントが無い」の意味なので、
        // エラーで返ってきた null と混ぜると、繋がらないだけの状態を
        // アカウントが無いと表示してしまう
        if (response.exception != null || response.errors.orEmpty().isNotEmpty()) {
            return AdminAccountResult.Failure(response.failureMessage())
        }

        val data = response.data ?: return AdminAccountResult.Failure(response.failureMessage())

        return AdminAccountResult.Success(data.admin.adminAccount?.adminAccountFields?.toAdminAccount())
    }

    suspend fun addAccount(username: String): AdminAddAccountResult {
        val response = client.mutation(AdminAddAccountMutation(username)).execute()
        val added = response.data?.admin?.addAccount ?: return AdminAddAccountResult.Failure(response.failureMessage())

        val failure = added.failure
            ?: return AdminAddAccountResult.Success(
                added.adminAccount?.account?.acct ?: return AdminAddAccountResult.Failure("追加できたが内容が返ってこない"),
            )

        return AdminAddAccountResult.Rejected(
            unusableCharacters = failure.unusableCharacters.orEmpty(),
            maxLength = failure.maxLength,
            minLength = failure.minLength,
            isDuplicated = failure.isDuplicated,
        )
    }

    /**
     * @param cursor 直前のページの続きから取る。null なら先頭から
     * @param limit 要求する件数。上限はサーバー側で決まる
     */
    suspend fun notes(
        username: String,
        cursor: String? = null,
        limit: Int,
    ): AdminNotesResult {
        val response = client
            .query(
                AdminNotesQuery(
                    username = username,
                    cursor = Optional.presentIfNotNull(cursor),
                    limit = limit,
                ),
            ).execute()

        if (response.exception != null || response.errors.orEmpty().isNotEmpty()) {
            return AdminNotesResult.Failure(response.failureMessage())
        }

        val data = response.data ?: return AdminNotesResult.Failure(response.failureMessage())

        return AdminNotesResult.Success(
            notes = data.admin.notes.nodes.map { it.adminNoteFields.toAdminNote() },
            cursor = data.admin.notes.pageInfo.nextCursor,
        )
    }

    suspend fun previewFeed(url: String): AdminFeedPreviewResult {
        val response = client.mutation(AdminPreviewFeedMutation(url)).execute()
        val result = response.data?.admin?.previewFeed
            ?: return AdminFeedPreviewResult.Failure(AdminFeedPreviewResult.PreviewFailure.UNKNOWN, response.failureMessage())

        val preview = result.preview
        if (preview != null) {
            return AdminFeedPreviewResult.Success(
                AdminFeedPreview(
                    title = preview.title,
                    siteUrl = preview.siteUrl,
                    format = preview.format,
                    description = preview.description,
                    itemCount = preview.itemCount,
                    sampleItems = preview.sampleItems.map { item ->
                        AdminFeedPreviewItem(
                            title = item.title,
                            link = item.link,
                            publishedAt = item.publishedAt,
                        )
                    },
                ),
            )
        }

        return AdminFeedPreviewResult.Failure(
            reason = result.failure?.toPreviewFailure() ?: AdminFeedPreviewResult.PreviewFailure.UNKNOWN,
            message = previewFailureMessage(result.failure),
        )
    }

    suspend fun saveFeed(
        accountId: String,
        url: String,
    ): AdminSaveFeedResult {
        val response = client.mutation(AdminSaveFeedMutation(accountId = accountId, url = url)).execute()
        val result = response.data?.admin?.saveFeed
            ?: return AdminSaveFeedResult.Failure(AdminSaveFeedResult.SaveFailure.UNKNOWN, response.failureMessage())

        val feed = result.feed
        if (feed != null) {
            return AdminSaveFeedResult.Success(feed.adminFeedFields.toAdminFeed())
        }

        return AdminSaveFeedResult.Failure(
            reason = result.failure?.toSaveFailure() ?: AdminSaveFeedResult.SaveFailure.UNKNOWN,
            message = saveFailureMessage(result.failure),
        )
    }

    suspend fun postNote(
        username: String,
        body: String,
    ): AdminPostNoteResult {
        val response = client.mutation(AdminPostNoteMutation(username = username, body = body)).execute()

        if (response.exception != null || response.errors.orEmpty().isNotEmpty()) {
            return AdminPostNoteResult.Failure(response.failureMessage())
        }

        val posted = response.data?.admin?.postNote ?: return AdminPostNoteResult.Failure(response.failureMessage())

        val failure = posted.failure
        if (failure != null) {
            return AdminPostNoteResult.Rejected(
                unknownAccount = failure.unknownAccount,
                isEmpty = failure.isEmpty,
                maxLength = failure.maxLength,
            )
        }

        val note = posted.note ?: return AdminPostNoteResult.Failure("投稿できたが内容が返ってこない")

        return AdminPostNoteResult.Success(
            note = note.adminNoteFields.toAdminNote(),
            deliveryTargets = posted.deliveryTargets ?: 0,
            delivered = posted.delivered ?: 0,
        )
    }

    private fun AdminAccountFields.toAdminAccount(): AdminAccount = AdminAccount(
        account = Account(
            id = account.id,
            username = account.username,
            acct = account.acct,
            actorUrl = account.actorUrl,
        ),
        createdAt = createdAt,
        followerCount = followerCount,
        feed = feed?.adminFeedFields?.toAdminFeed(),
    )

    private fun net.matsudamper.mastodon.rss.frontend.graphql.fragment.AdminFeedFields.toAdminFeed(): AdminFeed = AdminFeed( // pragma: allowlist secret
        id = id,
        url = url,
        title = title,
        siteUrl = siteUrl,
        format = format,
    )

    private fun AdminFeedPreviewFailure.toPreviewFailure(): AdminFeedPreviewResult.PreviewFailure =
        when (this) {
            AdminFeedPreviewFailure.INVALID_URL -> AdminFeedPreviewResult.PreviewFailure.INVALID_URL
            AdminFeedPreviewFailure.FETCH_FAILED -> AdminFeedPreviewResult.PreviewFailure.FETCH_FAILED
            AdminFeedPreviewFailure.PARSE_FAILED -> AdminFeedPreviewResult.PreviewFailure.PARSE_FAILED
            AdminFeedPreviewFailure.UNKNOWN__ -> AdminFeedPreviewResult.PreviewFailure.UNKNOWN
        }

    private fun AdminSaveFeedFailure.toSaveFailure(): AdminSaveFeedResult.SaveFailure =
        when (this) {
            AdminSaveFeedFailure.UNKNOWN_ACCOUNT -> AdminSaveFeedResult.SaveFailure.UNKNOWN_ACCOUNT
            AdminSaveFeedFailure.DUPLICATE_URL -> AdminSaveFeedResult.SaveFailure.DUPLICATE_URL
            AdminSaveFeedFailure.ALREADY_HAS_FEED -> AdminSaveFeedResult.SaveFailure.ALREADY_HAS_FEED
            AdminSaveFeedFailure.INVALID_URL -> AdminSaveFeedResult.SaveFailure.INVALID_URL
            AdminSaveFeedFailure.FETCH_FAILED -> AdminSaveFeedResult.SaveFailure.FETCH_FAILED
            AdminSaveFeedFailure.PARSE_FAILED -> AdminSaveFeedResult.SaveFailure.PARSE_FAILED
            AdminSaveFeedFailure.UNKNOWN__ -> AdminSaveFeedResult.SaveFailure.UNKNOWN
        }

    private fun previewFailureMessage(failure: AdminFeedPreviewFailure?): String =
        when (failure) {
            AdminFeedPreviewFailure.INVALID_URL -> "URL の形式が正しくない"
            AdminFeedPreviewFailure.FETCH_FAILED -> "フィードを取得できなかった"
            AdminFeedPreviewFailure.PARSE_FAILED -> "フィードを読み取れなかった"
            AdminFeedPreviewFailure.UNKNOWN__, null -> "プレビューできなかった"
        }

    private fun saveFailureMessage(failure: AdminSaveFeedFailure?): String =
        when (failure) {
            AdminSaveFeedFailure.UNKNOWN_ACCOUNT -> "このアカウントには登録できない"
            AdminSaveFeedFailure.DUPLICATE_URL -> "同じ URL は既に登録されている"
            AdminSaveFeedFailure.ALREADY_HAS_FEED -> "このアカウントには既にフィードがある"
            AdminSaveFeedFailure.INVALID_URL -> "URL の形式が正しくない"
            AdminSaveFeedFailure.FETCH_FAILED -> "フィードを取得できなかった"
            AdminSaveFeedFailure.PARSE_FAILED -> "フィードを読み取れなかった"
            AdminSaveFeedFailure.UNKNOWN__, null -> "保存できなかった"
        }

    private fun AdminNoteFields.toAdminNote(): AdminNote = AdminNote(
        url = url,
        contentHtml = contentHtml,
        publishedAt = Instant.fromEpochSeconds(publishedAt),
    )

    /**
     * `data` が無いのは失敗。ログインしていない状態と混ぜない
     */
    private fun <D : Operation.Data> ApolloResponse<D>.toSessionResult(
        select: (D) -> AdminSessionFields,
    ): AdminSessionResult {
        val data = data ?: return AdminSessionResult.Failure(failureMessage())
        val session = select(data)

        return AdminSessionResult.Success(
            loggedIn = session.loggedIn,
            passwordConfigured = session.passwordConfigured,
        )
    }

    private fun ApolloResponse<*>.failureMessage(): String {
        return exception?.message
            ?: errors?.joinToString("\n") { it.message }?.takeIf { it.isNotEmpty() }
            ?: "ネットワークエラー"
    }
}
