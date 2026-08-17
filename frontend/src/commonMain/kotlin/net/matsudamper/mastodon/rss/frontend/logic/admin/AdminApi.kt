package net.matsudamper.mastodon.rss.frontend.logic.admin

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.Optional
import net.matsudamper.mastodon.rss.frontend.graphql.AdminAccountQuery
import net.matsudamper.mastodon.rss.frontend.graphql.AdminAccountsQuery
import net.matsudamper.mastodon.rss.frontend.graphql.AdminAddAccountMutation
import net.matsudamper.mastodon.rss.frontend.graphql.AdminLoginMutation
import net.matsudamper.mastodon.rss.frontend.graphql.AdminLogoutMutation
import net.matsudamper.mastodon.rss.frontend.graphql.AdminNotesQuery
import net.matsudamper.mastodon.rss.frontend.graphql.AdminPostNoteMutation
import net.matsudamper.mastodon.rss.frontend.graphql.AdminSessionQuery
import net.matsudamper.mastodon.rss.frontend.graphql.fragment.AdminAccountFields
import net.matsudamper.mastodon.rss.frontend.graphql.fragment.AdminNoteFields
import net.matsudamper.mastodon.rss.frontend.graphql.fragment.AdminSessionFields
import net.matsudamper.mastodon.rss.frontend.graphql.type.AdminLoginFailure
import net.matsudamper.mastodon.rss.frontend.logic.GraphQlClient

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
            data.admin.accounts.map { it.adminAccountFields.toAdminAccount() },
        )
    }

    suspend fun account(username: String): AdminAccountResult {
        val response = client.query(AdminAccountQuery(username)).execute()
        val data = response.data ?: return AdminAccountResult.Failure(response.failureMessage())

        return AdminAccountResult.Success(data.admin.account?.adminAccountFields?.toAdminAccount())
    }

    suspend fun addAccount(username: String): AdminAddAccountResult {
        val response = client.mutation(AdminAddAccountMutation(username)).execute()
        val added = response.data?.admin?.addAccount ?: return AdminAddAccountResult.Failure(response.failureMessage())

        val failure = added.failure
            ?: return AdminAddAccountResult.Success(
                added.account?.acct ?: return AdminAddAccountResult.Failure("追加できたが内容が返ってこない"),
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
        limit: Int? = null,
    ): AdminNotesResult {
        val response = client
            .query(
                AdminNotesQuery(
                    username = username,
                    cursor = Optional.presentIfNotNull(cursor),
                    limit = Optional.presentIfNotNull(limit),
                ),
            ).execute()
        val data = response.data ?: return AdminNotesResult.Failure(response.failureMessage())

        return AdminNotesResult.Success(
            notes = data.admin.notes.items.map { it.adminNoteFields.toAdminNote() },
            cursor = data.admin.notes.cursor,
        )
    }

    suspend fun postNote(
        username: String,
        body: String,
    ): AdminPostNoteResult {
        val response = client.mutation(AdminPostNoteMutation(username = username, body = body)).execute()
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
        username = username,
        acct = acct,
        actorUrl = actorUrl,
        createdAt = createdAt,
        followerCount = followerCount,
    )

    private fun AdminNoteFields.toAdminNote(): AdminNote = AdminNote(
        url = url,
        contentHtml = contentHtml,
        publishedAt = publishedAt,
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
