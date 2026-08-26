package net.matsudamper.mastodon.rss.graalvm

import java.io.File
import java.lang.reflect.GenericArrayType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * リフレクションの登録対象を数え上げる。[GraphQlReflectionFeature] から切り離してあるのは、
 * GraalVM の API に触らずに走査だけをテストできるようにするため。
 */
object GraphQlReflectionTargets {
    const val MODEL_PACKAGE = "net.matsudamper.mastodon.rss.graphql.model"
    const val RESOLVER_PACKAGE = "net.matsudamper.mastodon.rss.graphql.resolver"
    const val SHARED_PACKAGE = "net.matsudamper.mastodon.rss.shared"

    val PACKAGES: List<String> =
        listOf(
            MODEL_PACKAGE,
            RESOLVER_PACKAGE,
        )

    private const val CLASS_EXTENSION = ".class"

    /**
     * 入れ子のクラスも含む
     */
    fun classNamesIn(packageName: String): List<String> {
        val packagePath = packageName.replace('.', '/')
        val classLoader = classLoader()

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

    /**
     * 生成モデルがフィールドやコンストラクタに持つ `:shared` の型。
     * input object の中のスカラーは kickstart が Jackson で組み立てるので、
     * native-image ではコンストラクタを登録しないと `AccountId` を作れない。
     */
    fun sharedTypeNamesReferencedByGraphqlModels(): List<String> {
        val classLoader = classLoader()
        return classNamesIn(MODEL_PACKAGE)
            .asSequence()
            .flatMap { className ->
                referencedClasses(Class.forName(className, false, classLoader))
            }.map { it.name }
            .filter { it.startsWith("$SHARED_PACKAGE.") }
            .distinct()
            .sorted()
            .toList()
    }

    private fun referencedClasses(clazz: Class<*>): Sequence<Class<*>> {
        val types = mutableSetOf<Type>()
        clazz.genericSuperclass?.let(types::add)
        types += clazz.genericInterfaces
        clazz.declaredFields.forEach { types += it.genericType }
        clazz.declaredConstructors.forEach { types += it.genericParameterTypes }
        clazz.declaredMethods.forEach { method ->
            types += method.genericReturnType
            types += method.genericParameterTypes
        }
        return types.asSequence().flatMap(::classesIn)
    }

    private fun classesIn(type: Type): Sequence<Class<*>> =
        when (type) {
            is Class<*> ->
                if (type.isArray) {
                    classesIn(type.componentType)
                } else {
                    sequenceOf(type)
                }

            is ParameterizedType ->
                classesIn(type.rawType) + type.actualTypeArguments.asSequence().flatMap(::classesIn)

            is GenericArrayType -> classesIn(type.genericComponentType)

            is WildcardType ->
                type.upperBounds.asSequence().flatMap(::classesIn) +
                    type.lowerBounds.asSequence().flatMap(::classesIn)

            is TypeVariable<*> -> type.bounds.asSequence().flatMap(::classesIn)

            else -> emptySequence()
        }

    private fun classLoader(): ClassLoader =
        Thread.currentThread().contextClassLoader
            ?: GraphQlReflectionTargets::class.java.classLoader

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
