# mastodon-rss 固有ルール

## 開発
- `TODO.md` を確認する
- 完了したら `TODO.md` のチェックを都度更新（部分実装はチェックせず補足を追記）

## 境界
- frontendはfrontendとsharedのみ知ってよい（スキーマは例外）
- backendはbackendとsharedのみ
- frontendがbackend事情を知るコード/コメントを書かない

## frontend
- UI表示に使わない値をUiStateに渡さない
- Ui/UiStateはバックエンド情報を知ってはいけない。何を表示したいかで書く
- UiにUi以外のロジックを書かない。ロジックはViewModel

## backend
- pagingはoffset禁止。cursorと最後のid等を使う

## コメント例外
- 基本は書かない。複雑な場合のみ
- mastodon / ActivityPub のコメントは詳細に記述してよい
- 書く前に「その情報が無ければ誤判断するか」を確認
- 制約は成立する最小スコープに書く。経緯はgit、実装はコードを正とする

## 禁止
- Global public関数（慣習のremember*等は可）

## ドキュメント
- KDocは `/** */` 一行にせず改行する
- 更新前に読み手と作業を決める。不要なら更新しない
- `README.md` / `docs/architecture.md` / `TODO.md` の役割分担に従う

## ビルド
```shell
./gradlew :compileAll
```

## その他
- テスト名も日本語
- マージコミットは既定文面。`-m` で書き直さない
