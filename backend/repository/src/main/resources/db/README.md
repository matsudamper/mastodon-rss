# スキーマの管理

`schema.sql` が、スキーマの唯一の定義。
テーブルの型（jOOQ の生成コード）もここから作られる。

マイグレーションの履歴管理はしていない。
開発用 DB を直接いじって形を決め、`schema.sql` に書き出して commit し、実 DB へは sqlite3def で手適用する。

## スキーマの変えかた

1. 開発用 DB（ローカルの SQLite ファイル。git には入れない）を sqlite3 などで直接いじって形を決める
2. `./gradlew :backend:repository:dumpSchema -PdevDb=/絶対パス/dev.db` で `schema.sql` に書き出す
3. diff を見て commit する

手で叩くのは `dumpSchema` だけで、`schema.sql` を手で編集することは無い。
書き出しの前に、読んだ CREATE 文を使い捨ての DB に適用し直して、`schema.sql` が単体で適用可能なことを確かめている。
出力はオブジェクト名でソートされるので、同じスキーマからは常に同じテキストが出る。

## 実 DB への適用のしかた

[sqlite3def](https://github.com/sqldef/sqldef) をローカルで手で叩く。
`schema.sql` と実 DB の差分を計算して適用してくれる。

```
sqlite3def --file backend/repository/src/main/resources/db/schema.sql /path/to/mastodon-rss.db
```

`--dry-run` を付けると、適用せずに差分の SQL だけ表示される。
適用の前に DB ファイルを cp でバックアップしておくとよい。

起動時の自動適用は無い。
空の DB で起動すると、起動時の書き込み確認（`verifyWritable`）が `no such table: health_check` で落ちる。
新しい環境では先に sqlite3def で適用すること。

スキーマが binary の期待より古いままでも起動は通るが、ずれたテーブルに触るクエリが実行時に SQL エラーで落ちる。
新しい binary を動かす前に適用すること。

## スキーマから対応するコードが生成されるまで

jOOQ の生成コードは、`schema.sql` からビルド時に作られる。
codegen を叩く操作は無い。

1. `buildJooqSchema` が使い捨ての SQLite (`build/jooq/schema.db`) を作り直し、`schema.sql` を適用する
2. `generateJooq` がその DB を読んで `build/generated/jooq` に Java を生成する
3. `compileKotlin` / `compileJava` が生成物を含めてコンパイルする

これらのタスクは `build-logic` の `DatabaseCodegenPlugin` が登録する。
native-image 向けのリフレクション設定も同じプラグインが作る。

`compileKotlin` は生成タスクに依存しているので、`schema.sql` を変えて普通にビルドすればテーブルの型は増えている。
生成コードは git に入れない。
`build/` の下なので clone した直後は存在せず、最初のビルドで作られる。
CI も同じで特別な手順は無い。

タスクの入力として宣言されているのは `schema.sql` だけなので、この README を直しても再生成は走らない。

実物の SQLite を経由するのは、生成される型を実行時の DB に合わせるため。
jOOQ には SQL を直接読む `DDLDatabase` もあるが、そちらは jOOQ 自身のパーサで DDL を解釈するので、SQLite の型親和性まで一致する保証が無い。

## テストでの使いかた

テストは `TestSchema.applyTo(dbPath)` で `schema.sql` を適用してから `createRepositories` する。
起動時の自動適用が無いのは実運用と同じで、適用を忘れると実運用と同じ形で落ちる。

## 現在のテーブル

| テーブル | 内容 |
| --- | --- |
| `accounts` | 管理画面から追加したアカウント。`username` は大文字小文字を区別せず一意 |
| `remote_actors` | 相手のサーバーのアクター。inbox と公開鍵 |
| `followers` | 成立しているフォロー。アカウントの名前と `remote_actors` の関連 |
| `notes` | 配信した投稿。相手がパーマリンクを引きに来るので残す |
| `health_check` | 起動時の書き込み確認用。行は常に 1 件 |

`followers` と `notes` がこちらのアカウントを名前で持ち、`accounts` への外部キーに
していないのは、`ACTOR_USERNAME` で決まる組み込みアカウントが `accounts` に
行を持たないため。引き当ての正は `ActorDirectory` にある。
