package net.matsudamper.mastodon.rss.gradle

import java.io.File
import java.security.MessageDigest
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Sync
import org.gradle.kotlin.dsl.withType

class WebpackBundleHashPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.tasks.withType<Sync>().configureEach {
            if (name !in DISTRIBUTION_TASK_NAMES) return@configureEach

            doLast {
                renameBundle(destinationDir)
            }
        }
    }

    private fun renameBundle(distDir: File) {
        val bundle = distDir.resolve(BUNDLE_FILE_NAME)
        check(bundle.isFile) { "$bundle が無い" }

        val hashedName = "${bundle.nameWithoutExtension}.${contentHashOf(bundle)}.${bundle.extension}"
        check(bundle.renameTo(distDir.resolve(hashedName))) { "$bundle の名前を $hashedName に変えられない" }

        // 読み込みは root 絶対。相対にすると深いパスから引けなくなる。
        // 名前だけで探すと同じ名前を書いた説明文まで書き換わるので、読み込みの形ごと見る
        val reference = "src=\"/$BUNDLE_FILE_NAME\""
        val index = distDir.resolve(INDEX_FILE_NAME)
        val html = index.readText()
        check(html.contains(reference)) { "$index に $reference が無い" }
        index.writeText(html.replace(reference, "src=\"/$hashedName\""))
    }

    private fun contentHashOf(file: File): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(file.readBytes())
            .joinToString(separator = "") { "%02x".format(it) }
            .take(HASH_LENGTH)

    companion object {
        const val BUNDLE_FILE_NAME: String = "frontend.js"

        private const val INDEX_FILE_NAME = "index.html"

        private const val HASH_LENGTH = 16

        private val DISTRIBUTION_TASK_NAMES =
            setOf(
                "wasmJsBrowserDistribution",
                "wasmJsBrowserDevelopmentExecutableDistribution",
            )
    }
}
