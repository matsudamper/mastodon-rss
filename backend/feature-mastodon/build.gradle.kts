plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)

    // テスト用のフェイク（相手のアクター、送信先）を :backend のテストからも使う。
    // テストのソースセットは他モジュールから参照できないので、成果物として出す
    `java-test-fixtures`
}

dependencies {
    // 鍵の生成と署名。ActivityPub は HTTP Signature と RSA 鍵が前提なので、
    // このモジュールを切り出す際も一緒に付いてくる
    implementation(project(":backend:crypto"))

    // HTTP のサーバーとクライアントには依存しない。エンドポイントは EndpointResponse を返し、
    // 外向きの通信は ActivityPubHttpClient を受け取る。どちらの実装も使う側が用意する
    api(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    // アクター文書の取得を span で囲み、その中の GET をぶら下げる。
    // suspend の間も span を current に保つのに要る
    implementation(libs.opentelemetry.api)
    implementation(libs.opentelemetry.extension.kotlin)

    // Ktor の Application.log には頼れないので、SLF4J のロガーを直接引く
    implementation(libs.slf4j.api)

    // フェイクの鍵を作るのに使う。testFixtures は main の implementation を継がない
    testFixturesImplementation(project(":backend:crypto"))

    testImplementation(libs.kotlin.test)
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}
