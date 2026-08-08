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

include(":backend")
include(":crypto")
include(":repository")
include(":shared")
include(":frontend")
