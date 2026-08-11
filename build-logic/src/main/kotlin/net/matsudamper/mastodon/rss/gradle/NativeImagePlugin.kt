package net.matsudamper.mastodon.rss.gradle

import org.graalvm.buildtools.gradle.NativeImagePlugin as GraalVmNativeImagePlugin
import org.graalvm.buildtools.gradle.dsl.GraalVMExtension
import org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

/**
 * native-image を使うモジュールの共通設定。
 *
 * GraalVM は自分では探さず、Gradle のツールチェインから受け取る。手元に無ければ
 * settings.gradle.kts の foojay-resolver が取ってくるので、各自で入れる必要が無い。
 *
 * 何を作るか（`imageName` や `buildArgs`）はモジュールごとに違うので、ここには置かない。
 * 決めているのは「どの GraalVM で作るか」だけ。
 */
class NativeImagePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply(GraalVmNativeImagePlugin::class.java)

        val javaToolchains = target.extensions.getByType<JavaToolchainService>()

        // Java の版はモジュールの jvmToolchain(...) に合わせる。ここに書くと
        // モジュール側と 2 か所で揃えることになる
        val toolchain = target.extensions.getByType<JavaPluginExtension>().toolchain

        val graalVmLauncher =
            javaToolchains.launcherFor {
                languageVersion.set(toolchain.languageVersion)
                vendor.set(JvmVendorSpec.GRAAL_VM)
            }

        val repairLauncher =
            target.tasks.register<RepairNativeImageLauncherTask>("repairNativeImageLauncher") {
                description = "Gradle が用意した GraalVM の native-image を使える状態に直す"
                javaLauncher.set(graalVmLauncher)
            }

        target.extensions.configure<GraalVMExtension> {
            // 切ってはいけない。切ると native-image を探すときに
            // 下で渡す launcher が読まれなくなり、JAVA_HOME を見に行って
            // 「native-image が無い」と言って落ちる
            toolchainDetection.set(true)

            // configureEach ではなく all にする。nativeTest の実行タスクは
            // container 経由でなく直接この設定オブジェクトを掴むので、
            // 遅延させると launcher を渡す前に読まれる
            binaries.all {
                javaLauncher.set(graalVmLauncher)
            }
        }

        // nativeCompile も nativeTestCompile もこのタスクの型なので、まとめて繋がる
        target.tasks.withType<BuildNativeImageTask>().configureEach {
            dependsOn(repairLauncher)
        }
    }
}
