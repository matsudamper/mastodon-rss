plugins {
    alias(libs.plugins.kotlin.jvm)
}

// GraphQL のスキーマだけを持つ。
//
// スキーマは :backend（実行時にリソースとして読む）と :frontend（Apollo の
// コード生成の入力）の両方が見る唯一の定義で、写しを作ると片方にだけ
// フィールドがある状態になる。backend/ にも frontend/ にも入れずに root へ
// 置いているのは、どちらかの下に置くと相手のビルドがそのディレクトリを
// 見ることになるため。
//
// 成果物を使うのは :backend だけなので JVM のモジュールにしてある。
// :frontend はビルド時にファイルを読むだけで、依存はしない。
kotlin {
    jvmToolchain(25)
}
