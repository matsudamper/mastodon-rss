package net.matsudamper.mastodon.rss.frontend.screen.account

/**
 * アカウント画面に出す内容。
 *
 * URL から決まるユーザー名とドメイン以外は、まだ本物の値ではない。
 * 管理 API (GraphQL) を作るのは Phase 8 で、フィードと記事を持つのは Phase 5 なので、
 * それまでは [placeholder] が組み立てた固定値を出している。
 * 画面の上に仮の値である旨を出しているのはこのため。
 *
 * API ができたら、この型を組み立てるところだけを差し替えれば画面は変わらない。
 */
data class AccountUiState(
    val username: String,
    val domain: String,
    val displayName: String,
    val summary: String,
    val followers: String,
    val deliveredCount: String,
    val lastDeliveredAt: String,
    val feed: FeedUiState,
    val delivery: DeliveryUiState,
    val articles: List<ArticleUiState>,
    /** 問い合わせ先になる運用者アカウントのユーザー名 */
    val operatorUsername: String,
    /** 実データを繋ぐ前かどうか。true の間は画面に断りを出す */
    val placeholder: Boolean,
) {
    /** Mastodon の検索窓に貼る形 */
    val acct: String get() = "@$username@$domain"

    /** ActivityPub の Actor JSON の URL。この画面と対になるもの */
    val actorUrl: String get() = "https://$domain/users/$username"

    /** アバターの代わりに出す 1 文字 */
    val initial: String get() = username.first().uppercase()

    /** 運用者アカウントの acct。同じサーバーの中なので、ドメインはこのアカウントと同じ */
    val operatorAcct: String get() = "@$operatorUsername@$domain"

    companion object {
        /**
         * 表示用の仮データを作る。
         *
         * ユーザー名とドメインだけは URL から取るので実際の値になる。
         * 残りはレイアウトを確認できる形にした固定値で、URL に `example.com` を
         * 使っているのは実在のフィードと見間違えないようにするため。
         *
         * @param domain 画面を開いているホスト。`DOMAIN` と一致している前提で、
         *   ずれている場合は Mastodon から引けない状態なので、そちらが設定の問題
         */
        fun placeholder(
            username: String,
            domain: String,
        ): AccountUiState {
            return AccountUiState(
                username = username,
                domain = domain,
                displayName = username,
                summary = "RSS/Atom フィードを ActivityPub で配信するアカウント",
                followers = "128",
                deliveredCount = "342",
                lastDeliveredAt = "2026-08-09 11:02",
                feed =
                FeedUiState(
                    title = "サンプルフィード",
                    feedUrl = "https://example.com/blog/feed.xml",
                    siteUrl = "https://example.com/blog",
                    format = "Atom 1.0",
                    interval = "15 分ごと",
                    lastFetchedAt = "2026-08-09 12:40",
                    nextFetchAt = "2026-08-09 12:55",
                    status = FetchStatus.Ok,
                ),
                delivery =
                DeliveryUiState(
                    queued = "0",
                    failed = "0",
                    lastError = null,
                ),
                articles =
                listOf(
                    ArticleUiState(
                        title = "フィードを ActivityPub で配信する",
                        publishedAt = "2026-08-09 11:02",
                        url = "https://example.com/blog/3",
                    ),
                    ArticleUiState(
                        title = "HTTP Signatures の検証でつまずいたところ",
                        publishedAt = "2026-08-08 20:15",
                        url = "https://example.com/blog/2",
                    ),
                    ArticleUiState(
                        title = "はじめての記事",
                        publishedAt = "2026-08-07 09:30",
                        url = "https://example.com/blog/1",
                    ),
                ),
                operatorUsername = OPERATOR_USERNAME,
                placeholder = true,
            )
        }

        /**
         * 運用者アカウントの既定のユーザー名。
         *
         * サーバー側は `ACTOR_USERNAME` で変えられるので、本当はこちらで決められない。
         * 仮データの間だけの表示で、API を繋いだらサーバーが返した名前に置き換える。
         */
        private const val OPERATOR_USERNAME = "admin"
    }
}

/**
 * 配信元のフィードの情報。RSS のアカウントであることが分かる部分。
 */
data class FeedUiState(
    val title: String,
    val feedUrl: String,
    val siteUrl: String?,
    val format: String,
    val interval: String,
    val lastFetchedAt: String,
    val nextFetchAt: String,
    val status: FetchStatus,
)

/** 直近の取得の結果 */
enum class FetchStatus(
    val label: String,
) {
    Ok("取得できている"),
    Failed("取得に失敗している"),
    Unknown("未取得"),
}

/** 配信キューの状況 */
data class DeliveryUiState(
    val queued: String,
    val failed: String,
    val lastError: String?,
)

/** 配信した記事 1 件 */
data class ArticleUiState(
    val title: String,
    val publishedAt: String,
    val url: String,
)
