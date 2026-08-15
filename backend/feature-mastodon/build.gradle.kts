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

    // ルーティングの拡張関数（Route.actorRoutes など）と respondJson を公開するので api にする。
    // 使う側は Ktor のサーバーを立てることが前提になる
    api(libs.ktor.server.core)
    api(libs.kotlinx.serialization.json)

    // 相手のアクター文書を GET し、相手の inbox に POST する。
    // engine はサーバー側と揃えて CIO にする
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)

    // InboxService のように Ktor のルーティングから切り離したクラスは
    // Application.log を持たないので、SLF4J のロガーを直接引く
    implementation(libs.slf4j.api)

    // フェイクの鍵を作るのに使う。testFixtures は main の implementation を継がない
    testFixturesImplementation(project(":backend:crypto"))

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test)

    // 送信のテストだけは本物のサーバーを立てて往復させるので、engine が要る。
    // 実装側は engine を選ばないため main には入れない
    testImplementation(libs.ktor.server.cio)
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}
