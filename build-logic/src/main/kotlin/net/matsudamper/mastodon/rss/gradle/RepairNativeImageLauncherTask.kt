package net.matsudamper.mastodon.rss.gradle

import java.nio.file.LinkOption
import java.nio.file.Path
import kotlin.io.path.createLinkPointingTo
import kotlin.io.path.deleteIfExists
import kotlin.io.path.fileSize
import kotlin.io.path.isRegularFile
import kotlin.io.path.moveTo
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.toolchain.JavaLauncher

/**
 * Gradle が用意した GraalVM の `bin/native-image` を使える状態に直す。
 *
 * GraalVM の配布物では `bin/native-image` が `../lib/svm/bin/native-image` への
 * シンボリックリンクになっている。Gradle はツールチェインを取ってきたあと自前の
 * コピー処理で配置するが、そこでリンクが辿られず 0 バイトのファイルになる。
 * 結果、`native-image` は在るのに起動できない。
 *
 *     A problem occurred starting process 'command
 *     '~/.gradle/jdks/graalvm_community-25-amd64-linux/bin/native-image''
 *
 * Gradle 側の問題は https://github.com/gradle/gradle/issues/28583 で、
 * 直るまでは実体へのハードリンクを張り直して回避する。
 *
 * 自分で入れた GraalVM を使う場合（CI の setup-graalvm や手動インストール）は
 * リンクが壊れていないので、このタスクは何もしない。
 *
 * 直すのは `native-image` だけにしてある。同じディレクトリの `native-image-configure` と
 * `native-image-utils` も同じ理由で 0 バイトになっているが、こちらは呼んでいない。
 * 使うようになったら一緒に直すこと。
 *
 * up-to-date 判定は付けない。触るのはビルドの出力ではなく共有された JDK の中身で、
 * ツールチェインが取り直されれば同じパスのまま壊れた状態に戻る。
 * 見るのはファイルの種類と大きさだけなので、毎回走らせても差は出ない。
 */
abstract class RepairNativeImageLauncherTask : DefaultTask() {
    @get:Internal
    abstract val javaLauncher: Property<JavaLauncher>

    /**
     * 途中のファイルに付ける名前。モジュールごとに違う値にする。
     *
     * 直す先は共有された JDK なので、native-image を使うモジュールの分だけ
     * 同じディレクトリに対してこのタスクが走る。`org.gradle.parallel` が有効なら
     * それが同時に動くため、途中のファイルの名前が衝突しないようにする
     */
    @get:Input
    abstract val temporaryNameSuffix: Property<String>

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun repair() {
        val binDirectory =
            javaLauncher
                .get()
                .executablePath
                .asFile
                .toPath()
                .parent

        val target = binDirectory.resolve(NATIVE_IMAGE)
        val real = binDirectory.resolve("../lib/svm/bin/$NATIVE_IMAGE").normalize()

        if (!real.isRegularFile(LinkOption.NOFOLLOW_LINKS)) {
            logger.info("実体が見つからないので何もしない: {}", real)
            return
        }

        if (!target.isBroken()) {
            logger.info("壊れていないので何もしない: {}", target)
            return
        }

        logger.lifecycle("Gradle が配置した {} が空なので、{} へのリンクを張り直す", target, real)

        // 消してから作ると、その間に他のモジュールの分が同じ場所を見て
        // NoSuchFileException で落ちる。別名で作って置き換えれば、
        // 途中で見られても壊れていない側が見える
        //
        //   java.nio.file.NoSuchFileException:
        //   ~/.gradle/jdks/graalvm_community-25-amd64-linux/bin/native-image
        val temporary = binDirectory.resolve("$NATIVE_IMAGE.${temporaryNameSuffix.get()}")

        try {
            temporary.deleteIfExists()
            temporary.createLinkPointingTo(real)
            temporary.moveTo(target, overwrite = true)
        } finally {
            // 同じ実体を指すもの同士の rename は「何もせず成功」になる（POSIX）。
            // 先に他のモジュールの分が張り直していると move が空振りして、
            // 別名のまま JDK の bin に残るので必ず消す
            temporary.deleteIfExists()
        }
    }

    /** 0 バイトの普通のファイルなら、リンクがコピーで潰れた状態 */
    private fun Path.isBroken(): Boolean {
        return isRegularFile(LinkOption.NOFOLLOW_LINKS) && fileSize() == 0L
    }

    private companion object {
        const val NATIVE_IMAGE = "native-image"
    }
}
