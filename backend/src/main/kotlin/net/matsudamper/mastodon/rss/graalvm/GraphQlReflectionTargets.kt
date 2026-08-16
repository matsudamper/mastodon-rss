package net.matsudamper.mastodon.rss.graalvm

import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * リフレクションの登録対象を数え上げる。[GraphQlReflectionFeature] から切り離してあるのは、
 * GraalVM の API に触らずに走査だけをテストできるようにするため。
 */
object GraphQlReflectionTargets {
    val PACKAGES: List<String> =
        listOf(
            "net.matsudamper.mastodon.rss.graphql.model",
            "net.matsudamper.mastodon.rss.graphql.resolver",
        )

    private const val CLASS_EXTENSION = ".class"

    /**
     * 入れ子のクラスも含む
     */
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
                    // :backend 自身の出力
                    "file" -> classNamesInDirectory(Paths.get(uri), packageName)

                    // 依存として入る :backend:graphql の生成物
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

    private fun classNamesInDirectory(
        directory: Path,
        packageName: String,
    ): Sequence<String> {
        if (!Files.isDirectory(directory)) return emptySequence()

        // 遅延したままだと jar の FileSystem を閉じた後に辿ることになる
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
