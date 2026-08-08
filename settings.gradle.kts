rootProject.name = "mastodon-rss"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

// :crypto と :repository は :backend からしか使われない（JCA も JDBC も JVM 専用で、
// Kotlin/Wasm の :frontend からは参照できない）ので、backend の下にネストする
include(":backend")
include(":backend:crypto")
include(":backend:repository")

// :shared は :backend と :frontend の両方から使うので、どちらの下にも置かずに root に残す
include(":shared")
include(":frontend")
