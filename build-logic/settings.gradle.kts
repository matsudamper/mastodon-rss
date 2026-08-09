rootProject.name = "build-logic"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }

    // バージョンの定義場所を 1 つにする。ここで読まないと、build-logic だけ
    // 別のバージョンを書くことになり Renovate の追従から外れる
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
