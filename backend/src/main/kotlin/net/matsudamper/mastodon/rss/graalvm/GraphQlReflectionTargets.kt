package net.matsudamper.mastodon.rss.graalvm

import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * リフレクションの登録対象を数え上げる。
 *
 * [GraphQlReflectionFeature] から切り離してあるのは、GraalVM の API に触らずに
 * 走査だけをテストできるようにするため。Feature の側は native-image が
 * イメージのビルド中に読み込むので、JVM のテストからは動かせない。
 */
object GraphQlReflectionTargets {
    /** 生成されたモデルとリゾルバのインタフェース、そしてその実装 */
    val PACKAGES: List<String> =
        listOf(
            "net.matsudamper.mastodon.rss.graphql.model",
            "net.matsudamper.mastodon.rss.graphql.resolver",
        )

    private const val CLASS_EXTENSION = ".class"

    /** [packageName] の下にあるクラスの名前。入れ子のクラスも含む */
    fun classNamesIn(packageName: String): List<String> {
        val packagePath = packageName.replace('.', '/')
        val classLoader =
            Thread.currentThread().contextClassLoader
                ?: GraphQlReflectionTargets::class.java.classLoader

        return classLoader
            .getResources(packagePath)
            .asSequence()
            .flatMap { resource ->
                val uri = resource.toURI()
                when (uri.scheme) {
                    // 展開済みのクラスファイル。:backend 自身の出力がこちら
                    "file" -> classNamesInDirectory(Paths.get(uri), packageName)

                    // jar の中。依存として入る :backend:graphql の生成物がこちら
                    "jar" ->
                        FileSystems.newFileSystem(uri, emptyMap<String, Any>()).use { fileSystem ->
                            classNamesInDirectory(fileSystem.getPath(packagePath), packageName)
                        }

                    else -> emptySequence()
                }
            }.distinct()
            .sorted()
            .toList()
    }

    /**
     * ディレクトリの下の `.class` を、パッケージ名を前に付けたクラス名にする。
     *
     * 入れ子のクラス（`Foo$Bar`）も 1 つのファイルとして出てくるので、
     * ファイル名をそのまま使えばよい。
     */
    private fun classNamesInDirectory(
        directory: Path,
        packageName: String,
    ): Sequence<String> {
        if (!Files.isDirectory(directory)) return emptySequence()

        // 一度リストにしてから返す。遅延したままだと jar の FileSystem を
        // 閉じた後に辿ることになる
        return Files.walk(directory).use { paths ->
            paths
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(CLASS_EXTENSION) }
                .map { path ->
                    val relative =
                        directory
                            .relativize(path)
                            .toString()
                            .removeSuffix(CLASS_EXTENSION)
                            .replace(File.separatorChar, '.')
                            .replace('/', '.')

                    "$packageName.$relative"
                }.toList()
                .asSequence()
        }
    }
}
