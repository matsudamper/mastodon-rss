package net.matsudamper.mastodon.rss

import net.matsudamper.mastodon.rss.actor.ActorUsername

/**
 * サーバーの設定。値は [AppConfig] が環境変数から組み立てて渡す。
 *
 * @param host バインドするアドレス
 * @param port 待ち受けポート
 * @param domain 外部に公開するドメイン。WebFinger の `acct:` と Actor の `id` に使う。
 *   Mastodon はリモートアクターを永続キャッシュするので、間違えると相手側からは直せない
 * @param actorUsername 固定アクターのユーザー名。`acct:<name>@<domain>` と
 *   `/users/<name>` の両方に入る。Phase 6 で複数アクターにするまでは 1 つだけ
 */
data class ServerConfig(
    val host: String,
    val port: Int,
    val domain: String,
    val actorUsername: String,
) {
    init {
        require(ActorUsername.isValid(actorUsername)) {
            "${AppConfig.ENV_ACTOR_USERNAME} が使えない形式: $actorUsername。" +
                "英数字と _ . - のみ、先頭と末尾は英数字か _ にすること"
        }
    }
}
