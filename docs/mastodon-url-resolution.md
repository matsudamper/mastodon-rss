# Mastodon の URL 解決の調査

プロフィール・投稿 URL の応答を変更する開発者向けの根拠。2026-11-01 に
mastodon/mastodon の main、コミット
`147bfc1f1784b173a1ce157830c480787dd13fe0` のソースを確認した。
以下の正常系はローカルの存在する公開アカウント・通常の公開投稿が対象。
削除・非公開・リブログ・リモートのパーマリンク等の分岐とは区別する。

## ルートと MIME 型

[config/routes.rb L5-L15、L91-L119、L160-L175](https://github.com/mastodon/mastodon/blob/147bfc1f1784b173a1ce157830c480787dd13fe0/config/routes.rb#L91)
では `/@username` と `/users/username` は AccountsController#show、
`/@username/:id` と `/users/username/statuses/:id` は StatusesController#show に入る。
ただし前段に `req.format.nil? || req.format.html?` の制約がある。
この場合の `/users/...` は `redirect_with_vary` が返す 301 で短い画面 URL に転送する。
RedirectWithVary#build_response が `Vary: Origin, Accept` を設定し、Link は設定しない。

[config/initializers/mime_types.rb L3](https://github.com/mastodon/mastodon/blob/147bfc1f1784b173a1ce157830c480787dd13fe0/config/initializers/mime_types.rb#L3)
は `application/activity+json` と `application/ld+json` をともに `:json` の別名に登録している。
これらの Accept は HTML 用リダイレクトの条件を満たさない。

## コントローラーの正常応答

[AccountsController#show L18-L42](https://github.com/mastodon/mastodon/blob/147bfc1f1784b173a1ce157830c480787dd13fe0/app/controllers/accounts_controller.rb#L18)
は `respond_to` の `format.json` で ActorSerializer を使い、
`content_type: 'application/activity+json'` で render する。明示の別ステータス指定はなく 200。
`/@username` も `/users/username` も JSON 要求なら直接 JSON を返し、転送しない。
`format.html` は短い URL で HTML を表示する。

[StatusesController#show L25-L38](https://github.com/mastodon/mastodon/blob/147bfc1f1784b173a1ce157830c480787dd13fe0/app/controllers/statuses_controller.rb#L25)
も同じ分岐で NoteSerializer により 200 application/activity+json を返す。
したがって `/@username/:id` も JSON 要求には直接 JSON を返す。
HTML の `/users/username/statuses/:id` は前述のルートで 301 になる。

| パス | HTML | activity+json / ld+json の Accept | Link |
| --- | --- | --- | --- |
| `/@username` | 200 HTML | 200 application/activity+json | HTML のみ lrdd と alternate |
| `/users/username` | 301 → `/@username` | 200 application/activity+json | 無し |
| `/@username/:id` | 200 HTML | 200 application/activity+json | HTML・JSON に alternate |
| `/users/username/statuses/:id` | 301 → `/@username/:id` | 200 application/activity+json | JSON に alternate、301 には無し |

プロフィールの Link は
[AccountControllerConcern L11-L45](https://github.com/mastodon/mastodon/blob/147bfc1f1784b173a1ce157830c480787dd13fe0/app/controllers/concerns/account_controller_concern.rb#L11)
の `after_action :set_link_headers` に HTML の条件が付いており、WebFinger の lrdd と
Actor の application/activity+json alternate を設定する。
投稿は [StatusesController L16、L60-L64](https://github.com/mastodon/mastodon/blob/147bfc1f1784b173a1ce157830c480787dd13fe0/app/controllers/statuses_controller.rb#L60)
の after_action に形式の条件がなく、JSON にも alternate が付く。

Vary は [AccountsController L11](https://github.com/mastodon/mastodon/blob/147bfc1f1784b173a1ce157830c480787dd13fe0/app/controllers/accounts_controller.rb#L11)
と [StatusesController L9](https://github.com/mastodon/mastodon/blob/147bfc1f1784b173a1ce157830c480787dd13fe0/app/controllers/statuses_controller.rb#L9)
で `Accept, Accept-Language, Cookie`、署名が必要な設定ではさらに Signature。
[CacheConcern#vary_by L7-L11](https://github.com/mastodon/mastodon/blob/147bfc1f1784b173a1ce157830c480787dd13fe0/app/controllers/concerns/cache_concern.rb#L7)
が実際に応答ヘッダーへ書く。ルートの 301 はこのコントローラー処理を通らない。

HTML にも [accounts/show.html.haml L8-L9](https://github.com/mastodon/mastodon/blob/147bfc1f1784b173a1ce157830c480787dd13fe0/app/views/accounts/show.html.haml#L8)
と [statuses/show.html.haml L11-L12](https://github.com/mastodon/mastodon/blob/147bfc1f1784b173a1ce157830c480787dd13fe0/app/views/statuses/show.html.haml#L11)
で両 MIME 型の alternate link を埋めている。

## ワイルドカードと明示的な HTML 拒否

上記 Mastodon の [Gemfile.lock](https://github.com/mastodon/mastodon/blob/147bfc1f1784b173a1ce157830c480787dd13fe0/Gemfile.lock)
が固定する Rails は 8.1.4。
`Accept: */*;q=0.9, text/html;q=0, application/activity+json;q=0.8` は本家でも HTML を選ぶ。
この入力について、最も具体的な媒体範囲の quality を使って JSON を選ぶ変更は行わない。

- [Mime::Type の AcceptItem / AcceptList / parse](https://github.com/rails/rails/blob/v8.1.4/actionpack/lib/action_dispatch/http/mime_type.rb#L92-L155)
  は quality の降順、同値なら記載順で並べる。この入力の順序は `*/*`、JSON、HTML になる。
- [MimeNegotiation#formats / #negotiate_mime](https://github.com/rails/rails/blob/v8.1.4/actionpack/lib/action_dispatch/http/mime_negotiation.rb#L72-L91)
  は `*/*` を保持し、先頭の `Mime::ALL` に対してコントローラーの最初の形式を返す。
  この入力の `*/*` の直後には `;q=0.9` があるため、同ファイルの
  `BROWSER_LIKE_ACCEPTS` による HTML へのフォールバックではなく、通常のネゴシエーションを通る。
- 前述の AccountsController / StatusesController は `format.html` を最初に定義しているため、HTML が選ばれる。

これはこの入力での形式選択の互換性であり、Rails の Accept 処理全般を完全再現しているという意味ではない。

## リモート URL の検索

[ResolveURLService L13-L16、L24-L27、L65-L70](https://github.com/mastodon/mastodon/blob/147bfc1f1784b173a1ce157830c480787dd13fe0/app/services/resolve_url_service.rb#L65)
はリモート URL を FetchResourceService で取得し、型に応じて FetchRemoteActorService 等へ渡す。

[FetchResourceService L6、L29-L44](https://github.com/mastodon/mastodon/blob/147bfc1f1784b173a1ce157830c480787dd13fe0/app/services/fetch_resource_service.rb#L6)
の ACCEPT_HEADER は次の値で、perform_request が実際にリクエストへ設定する。

```http
Accept: application/ld+json; profile="https://www.w3.org/ns/activitystreams", application/activity+json, text/html;q=0.1
```

開発環境以外では代表アカウントで署名する。
[process_response L46-L65](https://github.com/mastodon/mastodon/blob/147bfc1f1784b173a1ce157830c480787dd13fe0/app/services/fetch_resource_service.rb#L46)
は 200 以外を採用せず、ActivityPub の Content-Type なら JSON の context・type を検証する。
JSON の id が要求 URL や最終 URL と違えば、id を terminal モードで取り直す。
そのため短い画面 URL で JSON を返しても、正規 Actor ID `/users/...` で取得を確認できる。

JSON でなければ `process_link_headers(response) || process_html(response)` の順。
[process_link_headers L81-L91](https://github.com/mastodon/mastodon/blob/147bfc1f1784b173a1ce157830c480787dd13fe0/app/services/fetch_resource_service.rb#L81)
は Link の alternate と両 ActivityStreams MIME 型を探す。
[process_html L72-L79](https://github.com/mastodon/mastodon/blob/147bfc1f1784b173a1ce157830c480787dd13fe0/app/services/fetch_resource_service.rb#L72)
は text/html の場合だけ HTML をパースして `<link rel="alternate">` と type を調べる。
いずれもリンク先を terminal モードで取得する。SPA の JavaScript は実行しない。

## 実サーバーでの補助確認

2026-11-01 UTC に curl でリダイレクトを追わず確認した。

- `mastodon.social/users/Mastodon`、Accept: text/html → 301、Location は
  `https://mastodon.social/@Mastodon`、Vary: Origin, Accept、Link 無し。
- `mastodon.social/@Mastodon`、Accept: application/activity+json → 401、
  Vary: Accept, Accept-Language, Cookie, Signature, Origin、Link 無し。
  これは署名なし取得の結果であり、正常系が 401 という意味ではない。
  AccountsController の `require_account_signature!` 分岐と整合する。

実サーバーの設定・稼働版と調査した main は同一とは限らない。正常応答の仕様判断は上のソースを根拠にする。

## mastodon-rss への適用

画面 URL からも直接 JSON を返す方式と、JSON URL の HTML 用 301 を採用する。
正規の Actor ID `/users/{name}` と投稿 ID `/notes/{id}` は変更しない。
投稿 ID を本家の `/users/{name}/statuses/{id}` に変えると既存の配信済み投稿が別 ID になるため、
パスの綴りは保持して同じ内容交渉を適用する。
言語・Cookie・署名で応答を変えないため通常の Vary は Accept のみ。
HTML 発見は HTTP Link で提供し、静的 SPA に個別の alternate 要素は埋め込まない。
URL 検索は直接 JSON を取得でき、HTML を取得したクライアントも Link で発見できる。
