# Mastodon 仕様

ビルドや開発の進め方ではなく、サーバーが外部（Mastodon など）に対して
何をどう応答するかをまとめる。ビルドは [README.md](../README.md)、
横断的な設計は [architecture.md](architecture.md) を参照。

ここで出てくる `DOMAIN` と `ACTOR_USERNAME` の指定は
[README.md](../README.md) の「環境変数」を参照。

## エンドポイント

| パス | 内容 |
| --- | --- |
| `GET /.well-known/webfinger?resource=acct:<name>@<domain>` | アカウント発見の 1 ホップ目 (RFC 7033) |
| `GET /users/{name}` | Actor JSON。プロフィールと公開鍵 |
| `POST /users/{name}/inbox` | アクティビティの受け口。HTTP Signatures を検証する |
| `GET /.well-known/nodeinfo` | NodeInfo の discovery document |
| `GET /nodeinfo/2.1` | サーバーの実装と規模。調査用 |

`{name}` として応答するのは `ACTOR_USERNAME`（既定 `admin`）と、`test-` で始まる
任意の名前の 2 通り。後者は動作確認用で、下の「動作確認用のアカウント」を参照。

サーバーが持つパス以外は静的ファイルの配信に落ちる。どの画面が出るかは
[README.md](../README.md) の「画面のパス」を参照。

アカウント画面の `/@{name}` と Actor JSON の `/users/{name}` は別のパス。
1 つのパスで `Accept` を見て HTML と JSON を出し分けると、相手の綴りの揺れで
アカウントごと見つからなくなる。

inbox は署名が通れば 202、通らなければ 401 を返す。検証の内容は
[HttpSignatureVerifier.kt](../backend/src/main/kotlin/net/matsudamper/mastodon/rss/httpsignature/HttpSignatureVerifier.kt)
の KDoc にある。

届いたアクティビティのうち処理するのは `Follow` だけで、相手の inbox に `Accept` を
返してフォローを成立させる。フォロワーはまだ保存しないので、再起動すると
こちらには何も残らない（相手側にはフォローが残る）。それ以外の種類は
種類と送り主をログに出すだけ。

```sh
curl "http://localhost:8080/.well-known/webfinger?resource=acct:admin@example.com"
curl -H 'Accept: application/activity+json' http://localhost:8080/users/admin
```

外から見えるようにするには HTTPS が要る。開発中は Cloudflare Tunnel や ngrok で
`DOMAIN` に指定したホスト名に向ける。

## 動作確認用のアカウント

`test-` で始まる名前は、設定に関係なくすべてアクターとして応答する。
`@test-1@example.com` でも `@test-20260808@example.com` でも引ける。

```sh
curl "http://localhost:8080/.well-known/webfinger?resource=acct:test-1@example.com"
curl -H 'Accept: application/activity+json' http://localhost:8080/users/test-1
```

`admin` で試して失敗すると `admin` が使えなくなるので、検証はこちらを使い、
名前を変えながらやり直す。理由は `ActorUsername.kt` の KDoc にある。

- 中身は固定アクターと同じで、鍵も共有する
- `summary` が「動作確認用のアカウント」になるので、Mastodon 側の表示でも見分けられる
- 接頭辞は小文字ちょうど。`Test-1` は 404 になる

## アクターの鍵

鍵は消さずに持ち続ける必要がある。読み込み元は 2 つあり、同時には指定できない。
両方が設定されていると起動時に落とす。

| 指定 | 動き |
| --- | --- |
| `ACTOR_PRIVATE_KEY_PATH`（既定） | ファイルがあれば読む。無ければ生成して書き出す（所有者のみ読み書き可） |
| `ACTOR_PRIVATE_KEY_PEM` | PEM をそのまま使う。ファイルには書き出さない |

どちらから読んだかは起動ログに出る。生成した場合だけ警告になるので、
運用中に出ていたら以前の鍵を失っていることになる。

docker compose ではボリュームの中（`/data/actor-private-key.pem`）に置いている。
コンテナを作り直しても同じ鍵のままだが、ボリュームごと消すとアクターは別人になる。

保存するのは秘密鍵だけで、公開鍵は起動のたびに秘密鍵から導く。
鍵を持ち続ける理由とこの判断の理由は `ServerEnv.kt` の KDoc にある。
