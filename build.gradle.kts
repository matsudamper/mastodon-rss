plugins {
    kotlin("jvm") version "2.2.21"
    application
    id("org.graalvm.buildtools.native") version "1.1.7"
}

group = "dev.matsudamper"
version = "0.1.0"

repositories {
    mavenCentral()
}

val ktorVersion = "3.5.2"

dependencies {
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-cio:$ktorVersion")

    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("dev.matsudamper.mastodonrss.ApplicationKt")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

graalvmNative {
    binaries {
        named("main") {
            imageName.set("mastodon-rss")
            mainClass.set("dev.matsudamper.mastodonrss.ApplicationKt")
            buildArgs.add("--no-fallback")
        }
    }
}
