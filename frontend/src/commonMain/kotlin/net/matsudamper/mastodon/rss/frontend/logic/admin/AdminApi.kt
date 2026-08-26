package net.matsudamper.mastodon.rss.frontend.logic.admin

import kotlin.time.Instant
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.Optional
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.fetchPolicy
import net.matsudamper.mastodon.rss.frontend.graphql.AdminAccountQuery
import net.matsudamper.mastodon.rss.frontend.graphql.AdminAccountsQuery
import net.matsudamper.mastodon.rss.frontend.graphql.AdminAddAccountMutation
import net.matsudamper.mastodon.rss.frontend.graphql.AdminLoginMutation
import net.matsudamper.mastodon.rss.frontend.graphql.AdminLogoutMutation
import net.matsudamper.mastodon.rss.frontend.graphql.AdminNotesQuery
import net.matsudamper.mastodon.rss.frontend.graphql.AdminPostFeedItemsMutation
import net.matsudamper.mastodon.rss.frontend.graphql.AdminPostNoteMutation
import net.matsudamper.mastodon.rss.frontend.graphql.AdminPreviewFeedQuery
import net.matsudamper.mastodon.rss.frontend.graphql.AdminSaveFeedMutation
import net.matsudamper.mastodon.rss.frontend.graphql.AdminSessionQuery
import net.matsudamper.mastodon.rss.frontend.graphql.AdminUnpublishedFeedItemsQuery
import net.matsudamper.mastodon.rss.frontend.graphql.fragment.AdminAccountFields
import net.matsudamper.mastodon.rss.frontend.graphql.fragment.AdminNoteFields
import net.matsudamper.mastodon.rss.frontend.graphql.fragment.AdminSessionFields
import net.matsudamper.mastodon.rss.frontend.graphql.type.AdminFeedPreviewFailureReason
import net.matsudamper.mastodon.rss.frontend.graphql.type.AdminLoginFailure
import net.matsudamper.mastodon.rss.frontend.graphql.type.AdminPostFeedItemsFailureReason
import net.matsudamper.mastodon.rss.frontend.graphql.type.AdminSaveFeedFailureReason
import net.matsudamper.mastodon.rss.frontend.graphql.type.AdminUnpublishedFeedItemsFailureReason
import net.matsudamper.mastodon.rss.frontend.graphql.type.PostFeedItemsQuery
import net.matsudamper.mastodon.rss.frontend.graphql.type.SaveFeedQuery
import net.matsudamper.mastodon.rss.frontend.graphql.type.UnpublishedFeedItemsQuery
import net.matsudamper.mastodon.rss.frontend.logic.GraphQlClient
import net.matsudamper.mastodon.rss.frontend.logic.account.Account

class AdminApi(
    private val client: ApolloClient = GraphQlClient.apollo,
) {
    suspend fun session(): AdminSessionResult {
        return client
            .query(AdminSessionQuery())
            .fetchPolicy(FetchPolicy.NetworkOnly)
            .execute()
            .toSessionResult { it.admin.session.adminSessionFields }
    }

    suspend fun login(password: String): AdminLoginResult {
        val response =
            client
                .mutation(AdminLoginMutation(password))
                .fetchPolicy(FetchPolicy.NetworkOnly)
                .execute()
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
            .fetchPolicy(FetchPolicy.NetworkOnly)
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
        val response = client.query(AdminPreviewFeedQuery(url)).execute()
        val result = response.data?.admin?.previewFeed
            ?: return AdminFeedPreviewResult.Failure(response.failureMessage())

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

        return AdminFeedPreviewResult.Rejected(
            reason = result.failure?.reason?.toPreviewFailure() ?: AdminFeedPreviewResult.PreviewFailure.UNKNOWN,
        )
    }

    suspend fun saveFeed(
        accountId: Long,
        url: String,
    ): AdminSaveFeedResult {
        val response = client.mutation(
            AdminSaveFeedMutation(
                saveFeedQuery = SaveFeedQuery(
                    accountId = accountId,
                    url = url,
                ),
            ),
        ).execute()
        val result = response.data?.admin?.saveFeed
            ?: return AdminSaveFeedResult.Failure(response.failureMessage())

        val feed = result.feed
        if (feed != null) {
            return AdminSaveFeedResult.Success(
                feed = AdminFeed(
                    id = feed.id,
                    url = feed.url,
                    title = feed.title,
                    siteUrl = feed.siteUrl,
                    format = feed.format,
                ),
            )
        }

        return AdminSaveFeedResult.Rejected(
            reason = result.failure?.reason?.toSaveFailure() ?: AdminSaveFeedResult.SaveFailure.UNKNOWN,
        )
    }

    suspend fun unpublishedFeedItems(accountId: Long): AdminUnpublishedFeedItemsResult {
        val response = client.query(
            AdminUnpublishedFeedItemsQuery(
                query = UnpublishedFeedItemsQuery(accountId = accountId),
            ),
        ).execute()
        val result = response.data?.admin?.unpublishedFeedItems
            ?: return AdminUnpublishedFeedItemsResult.Failure(response.failureMessage())
        val items = result.items
        if (items != null) {
            return AdminUnpublishedFeedItemsResult.Success(
                items = items.map { item ->
                    AdminUnpublishedFeedItem(
                        title = item.title,
                        link = item.link,
                        publishedAt = item.publishedAt,
                    )
                },
            )
        }
        return AdminUnpublishedFeedItemsResult.Rejected(
            reason = result.failure?.reason?.toUnpublishedFailure()
                ?: AdminUnpublishedFeedItemsResult.FailureReason.UNKNOWN,
        )
    }

    suspend fun postFeedItems(accountId: Long): AdminPostFeedItemsResult {
        val response = client.mutation(
            AdminPostFeedItemsMutation(
                query = PostFeedItemsQuery(accountId = accountId),
            ),
        ).execute()
        val result = response.data?.admin?.postFeedItems
            ?: return AdminPostFeedItemsResult.Failure(response.failureMessage())
        val items = result.items
        if (items != null) {
            return AdminPostFeedItemsResult.Success(
                items = items.map { item ->
                    AdminUnpublishedFeedItem(
                        title = item.title,
                        link = item.link,
                        publishedAt = item.publishedAt,
                    )
                },
            )
        }
        return AdminPostFeedItemsResult.Rejected(
            reason = result.failure?.reason?.toPostFeedItemsFailure()
                ?: AdminPostFeedItemsResult.FailureReason.UNKNOWN,
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
        feed = feed?.let {
            AdminFeed(
                id = it.id,
                url = it.url,
                title = it.title,
                siteUrl = it.siteUrl,
                format = it.format,
            )
        },
    )

    private fun AdminFeedPreviewFailureReason.toPreviewFailure(): AdminFeedPreviewResult.PreviewFailure =
        when (this) {
            AdminFeedPreviewFailureReason.INVALID_URL -> AdminFeedPreviewResult.PreviewFailure.INVALID_URL
            AdminFeedPreviewFailureReason.FETCH_FAILED -> AdminFeedPreviewResult.PreviewFailure.FETCH_FAILED
            AdminFeedPreviewFailureReason.PARSE_FAILED -> AdminFeedPreviewResult.PreviewFailure.PARSE_FAILED
            AdminFeedPreviewFailureReason.UNKNOWN__ -> AdminFeedPreviewResult.PreviewFailure.UNKNOWN
        }

    private fun AdminSaveFeedFailureReason.toSaveFailure(): AdminSaveFeedResult.SaveFailure =
        when (this) {
            AdminSaveFeedFailureReason.UNKNOWN_ACCOUNT -> AdminSaveFeedResult.SaveFailure.UNKNOWN_ACCOUNT
            AdminSaveFeedFailureReason.DUPLICATE_URL -> AdminSaveFeedResult.SaveFailure.DUPLICATE_URL
            AdminSaveFeedFailureReason.ALREADY_HAS_FEED -> AdminSaveFeedResult.SaveFailure.ALREADY_HAS_FEED
            AdminSaveFeedFailureReason.INVALID_URL -> AdminSaveFeedResult.SaveFailure.INVALID_URL
            AdminSaveFeedFailureReason.FETCH_FAILED -> AdminSaveFeedResult.SaveFailure.FETCH_FAILED
            AdminSaveFeedFailureReason.PARSE_FAILED -> AdminSaveFeedResult.SaveFailure.PARSE_FAILED
            AdminSaveFeedFailureReason.UNKNOWN__ -> AdminSaveFeedResult.SaveFailure.UNKNOWN
        }

    private fun AdminUnpublishedFeedItemsFailureReason.toUnpublishedFailure(): AdminUnpublishedFeedItemsResult.FailureReason =
        when (this) {
            AdminUnpublishedFeedItemsFailureReason.UNKNOWN_ACCOUNT ->
                AdminUnpublishedFeedItemsResult.FailureReason.UNKNOWN_ACCOUNT

            AdminUnpublishedFeedItemsFailureReason.NO_FEED ->
                AdminUnpublishedFeedItemsResult.FailureReason.NO_FEED

            AdminUnpublishedFeedItemsFailureReason.UNKNOWN__ ->
                AdminUnpublishedFeedItemsResult.FailureReason.UNKNOWN
        }

    private fun AdminPostFeedItemsFailureReason.toPostFeedItemsFailure(): AdminPostFeedItemsResult.FailureReason =
        when (this) {
            AdminPostFeedItemsFailureReason.UNKNOWN_ACCOUNT ->
                AdminPostFeedItemsResult.FailureReason.UNKNOWN_ACCOUNT

            AdminPostFeedItemsFailureReason.NO_FEED ->
                AdminPostFeedItemsResult.FailureReason.NO_FEED

            AdminPostFeedItemsFailureReason.UNKNOWN__ ->
                AdminPostFeedItemsResult.FailureReason.UNKNOWN
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
