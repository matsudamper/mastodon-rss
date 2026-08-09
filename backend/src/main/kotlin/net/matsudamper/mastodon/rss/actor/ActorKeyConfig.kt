package net.matsudamper.mastodon.rss.actor

import java.nio.file.Path

/**
 * アクターの秘密鍵をどこから読むか。どちらを使うかは
 * [net.matsudamper.mastodon.rss.AppConfig] が環境変数から決めて渡す。
 *
 * 鍵はアクターの同一性そのもので、変わると相手側の署名検証が通らなくなるため、
 * どちらから読んだのかが起動ログから分かるように取得元を型で分けている。
 */
sealed interface ActorKeyConfig {
    /**
     * PEM を直接渡す。Kubernetes の Secret や systemd の
     * `EnvironmentFile` から入れる場合はこちら。
     */
    data class Pem(
        val pem: String,
    ) : ActorKeyConfig

    /**
     * PEM をファイルから読む。ファイルが無ければ生成して書き出す。
     * docker compose のようにボリュームを持てる場合はこちら。
     */
    data class File(
        val path: Path,
    ) : ActorKeyConfig
}
