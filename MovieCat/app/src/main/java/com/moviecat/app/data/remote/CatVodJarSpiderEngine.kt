package com.moviecat.app.data.remote

import android.content.Context
import android.util.Log
import dalvik.system.DexClassLoader
import android.app.Application
import com.moviecat.app.data.model.CatalogItem
import com.moviecat.app.data.model.CategoryParam
import com.moviecat.app.data.model.Episode
import com.moviecat.app.data.model.ParsedPayload
import com.moviecat.app.data.model.SourceItem
import com.moviecat.app.util.debugSummary
import com.moviecat.app.util.previewForLog
import java.io.File
import java.lang.reflect.Method
import java.net.URL
import java.util.HashMap
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CatVodJarSpiderEngine(
    private val context: Context,
    private val binaryLoader: suspend (String, SourceItem) -> ByteArray
) {
    private val tag = "MovieCatCatVod"
    private val instanceCache = ConcurrentHashMap<String, Any>()
    private val methodCache = ConcurrentHashMap<String, Method>()

    suspend fun fetchHome(source: SourceItem): ParsedPayload = withContext(Dispatchers.IO) {
        Log.d(tag, "fetchHome start ${source.debugSummary()}")
        val spider = loadSpider(source) ?: return@withContext ParsedPayload(
            warnings = listOf("没有成功加载 CatVod Spider：请检查 jar、类名和 ext。")
        )
        val raw = invokeHome(spider)
            ?: return@withContext ParsedPayload(
                warnings = listOf("Spider 已加载，但没有拿到 homeContent 返回值。")
            )
        SourcePayloadParser.parseSpiderJson(raw).also {
            Log.d(tag, "fetchHome done ${source.label}: ${it.debugSummary()}")
        }
    }

    suspend fun fetchDetail(source: SourceItem, item: CatalogItem): CatalogItem? = withContext(Dispatchers.IO) {
        Log.d(tag, "fetchDetail start ${source.label}: ${item.debugSummary()}")
        val spider = loadSpider(source) ?: return@withContext null
        val detailId = item.detailToken ?: return@withContext null
        val raw = invokeDetail(spider, detailId) ?: return@withContext null
        SourcePayloadParser.parseDetailItem(raw)?.also {
            Log.d(tag, "fetchDetail done ${source.label}: ${it.debugSummary()}")
        }
    }

    suspend fun search(source: SourceItem, query: String): ParsedPayload = withContext(Dispatchers.IO) {
        Log.d(tag, "search start ${source.label}: query=${query.previewForLog(80)}")
        val spider = loadSpider(source) ?: return@withContext ParsedPayload(
            warnings = listOf("没有成功加载 CatVod Spider：请检查 jar、类名和 ext。")
        )
        val raw = invokeSearch(spider, query)
            ?: return@withContext ParsedPayload(
                warnings = listOf("Spider 已加载，但没有拿到 searchContent 返回值。")
            )
        SourcePayloadParser.parseSpiderJson(raw).also {
            Log.d(tag, "search done ${source.label}: ${it.debugSummary()}")
        }
    }

    suspend fun fetchCategory(
        source: SourceItem,
        category: CategoryParam,
        page: Int = 1
    ): ParsedPayload = withContext(Dispatchers.IO) {
        Log.d(tag, "fetchCategory start ${source.label}: category=${category.name}/${category.value}, page=$page")
        val spider = loadSpider(source) ?: return@withContext ParsedPayload(
            warnings = listOf("没有成功加载 CatVod Spider：请检查 jar、类名和 ext。")
        )
        val raw = invokeCategory(spider, category, page)
            ?: return@withContext ParsedPayload(
                warnings = listOf("Spider 已加载，但没有拿到 categoryContent 返回值。")
            )
        SourcePayloadParser.parseSpiderJson(raw).also {
            Log.d(tag, "fetchCategory done ${source.label}: ${it.debugSummary()}")
        }
    }

    suspend fun resolveEpisode(source: SourceItem, episode: Episode): Episode = withContext(Dispatchers.IO) {
        if (episode.url.startsWith("http", ignoreCase = true) && episode.parse != 1) {
            Log.d(tag, "resolveEpisode skip ${source.label}: already direct ${episode.debugSummary()}")
            return@withContext episode
        }
        Log.d(tag, "resolveEpisode start ${source.label}: ${episode.debugSummary()}")
        val spider = loadSpider(source) ?: return@withContext episode
        val raw = invokePlayer(spider, episode.flag.orEmpty(), episode.url)
            ?: return@withContext episode
        SourcePayloadParser.parsePlayerEpisode(raw, episode.name, episode.flag)?.also {
            Log.d(tag, "resolveEpisode done ${source.label}: ${it.debugSummary()}")
        } ?: episode
    }

    private suspend fun loadSpider(source: SourceItem): Any? = withContext(Dispatchers.IO) {
        val jarUrl = source.settings.spiderJarUrl ?: return@withContext null
        val jarFile = ensureJarFile(jarUrl, source)
        Log.d(tag, "loadSpider jarFile=${jarFile.absolutePath}, size=${jarFile.length()}, class=${source.settings.spiderClass}")
        val optimizedDir = File(context.codeCacheDir, "moviecat_spider_odex").apply { mkdirs() }
        val loader = DexClassLoader(
            jarFile.absolutePath,
            optimizedDir.absolutePath,
            null,
            context.classLoader
        )

        val classNames = buildList {
            source.settings.spiderClass?.let { spiderClass ->
                add(spiderClass)
                if (!spiderClass.contains(".")) {
                    add("com.github.catvod.spider.$spiderClass")
                    add("com.github.catvod.jar.$spiderClass")
                    add("com.github.catvod.demo.$spiderClass")
                }
            }
        }
        Log.d(tag, "candidate classes=${classNames.joinToString()}")
        val cacheKey = "${jarFile.absolutePath}|${classNames.joinToString(",")}|${source.settings.spiderExt.orEmpty()}"
        instanceCache[cacheKey]?.let {
            Log.d(tag, "loadSpider hit cache ${source.label}: ${it.javaClass.name}")
            return@withContext it
        }

        prepareStaticInit(loader, source)

        val spider = withThreadContextClassLoader(loader) {
            classNames.asSequence()
                .mapNotNull { className ->
                    runCatching {
                        val clazz = loader.loadClass(className)
                        Log.d(tag, "loaded class=$className")
                        clazz.getDeclaredConstructor().newInstance()
                    }.onFailure {
                        Log.e(tag, "load spider class failed: $className", it)
                    }.getOrNull()
                }
                .firstOrNull()
        }
            ?: return@withContext null

        Log.d(tag, "spider instance=${spider.javaClass.name}")
        initSpider(spider, source)

        instanceCache[cacheKey] = spider
        spider
    }

    private suspend fun ensureJarFile(jarUrl: String, source: SourceItem): File = withContext(Dispatchers.IO) {
        val jarDir = File(context.codeCacheDir, "moviecat_spider_jars").apply { mkdirs() }
        val target = File(jarDir, "${jarUrl.hashCode()}.jar")
        if (target.exists() && target.length() > 0) {
            enforceReadOnlyJar(target)
            Log.d(tag, "ensureJarFile cache hit ${source.label}: ${target.absolutePath} size=${target.length()}")
            return@withContext target
        }

        val temp = File(jarDir, "${jarUrl.hashCode()}.tmp").apply {
            parentFile?.mkdirs()
            if (exists()) delete()
            createNewFile()
        }

        if (jarUrl.startsWith("http")) {
            temp.writeBytes(binaryLoader(jarUrl, source))
            Log.d(tag, "ensureJarFile downloaded ${source.label}: $jarUrl size=${temp.length()}")
        } else {
            val input = URL(jarUrl).openStream()
            temp.outputStream().use { output ->
                input.use { it.copyTo(output) }
            }
            Log.d(tag, "ensureJarFile copied local ${source.label}: $jarUrl size=${temp.length()}")
        }

        if (target.exists()) {
            target.delete()
        }
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        enforceReadOnlyJar(target)
        target
    }

    private fun invoke(spider: Any, methodName: String, argument: Any): String? {
        val cacheKey = "${spider.javaClass.name}#$methodName#1"
        val method = methodCache[cacheKey]
            ?: spider.javaClass.methods.firstOrNull {
                it.name == methodName && it.parameterTypes.size == 1
            }?.also {
                methodCache[cacheKey] = it
                Log.d(tag, "method cached ${spider.javaClass.name}#$methodName(${it.parameterTypes.joinToString { type -> type.simpleName }})")
            }
            ?: return null
        return runCatching {
            method.invoke(spider, argument)?.toString()
        }.onFailure {
            Log.e(tag, "invoke failed ${spider.javaClass.name}#$methodName arg=${argument.toString().previewForLog(120)}", it)
        }.getOrNull()?.also {
            Log.d(tag, "invoke ok ${spider.javaClass.name}#$methodName -> ${it.previewForLog()}")
        }
    }

    private fun invokeHome(spider: Any): String? {
        return invoke(spider, "homeContent", true)
            ?: invoke(spider, "homeContent", false)
            ?: invokeVariant(spider, "homeContent(true, map)") {
                spider.javaClass.methods.firstOrNull {
                    it.name == "homeContent" && it.parameterTypes.size == 2
                }?.invoke(spider, true, HashMap<String, String>())?.toString()
            }
            ?: invokeVariant(spider, "homeContent(false, map)") {
                spider.javaClass.methods.firstOrNull {
                    it.name == "homeContent" && it.parameterTypes.size == 2
                }?.invoke(spider, false, HashMap<String, String>())?.toString()
            }
            ?: invokeVariant(spider, "homeContent()") {
                spider.javaClass.methods.firstOrNull {
                    it.name == "homeContent" && it.parameterTypes.isEmpty()
                }?.invoke(spider)?.toString()
            }
            ?: invokeVariant(spider, "homeVideoContent()") {
                spider.javaClass.methods.firstOrNull {
                    it.name == "homeVideoContent" && it.parameterTypes.isEmpty()
                }?.invoke(spider)?.toString()
            }
    }

    private fun invokeDetail(spider: Any, detailId: String): String? {
        return invoke(spider, "detailContent", listOf(detailId))
            ?: invokeVariant(spider, "detailContent(arrayOf)") {
                spider.javaClass.methods.firstOrNull {
                    it.name == "detailContent" &&
                        it.parameterTypes.size == 1 &&
                        it.parameterTypes[0].isArray
                }?.invoke(spider, arrayOf(detailId))?.toString()
            }
            ?: invokeVariant(spider, "detailContent(list, map)") {
                spider.javaClass.methods.firstOrNull {
                    it.name == "detailContent" && it.parameterTypes.size == 2
                }?.invoke(spider, listOf(detailId), HashMap<String, String>())?.toString()
            }
    }

    private fun invokeSearch(spider: Any, query: String): String? {
        return invokeVariant(spider, "searchContent(query,false)") {
            spider.javaClass.methods.firstOrNull {
                it.name == "searchContent" && it.parameterTypes.size == 2
            }?.invoke(spider, query, false)?.toString()
        }
            ?: invokeVariant(spider, "searchContent(query,true)") {
                spider.javaClass.methods.firstOrNull {
                    it.name == "searchContent" && it.parameterTypes.size == 2
                }?.invoke(spider, query, true)?.toString()
            }
            ?: invokeVariant(spider, "searchContent(query,false,pageHints)") {
                spider.javaClass.methods.firstOrNull {
                    it.name == "searchContent" && it.parameterTypes.size == 3
                }?.invoke(spider, query, false, pageHints())?.toString()
            }
            ?: invokeVariant(spider, "searchContent(query)") {
                spider.javaClass.methods.firstOrNull {
                    it.name == "searchContent" && it.parameterTypes.size == 1
                }?.invoke(spider, query)?.toString()
            }
    }

    private fun invokePlayer(spider: Any, flag: String, id: String): String? {
        return invokeVariant(spider, "playerContent(flag,id,list)") {
            spider.javaClass.methods.firstOrNull {
                it.name == "playerContent" && it.parameterTypes.size == 3
            }?.invoke(spider, flag, id, emptyList<String>())?.toString()
        }
            ?: invokeVariant(spider, "playerContent(flag,id,list,map)") {
            spider.javaClass.methods.firstOrNull {
                it.name == "playerContent" && it.parameterTypes.size == 4
            }?.invoke(spider, flag, id, emptyList<String>(), HashMap<String, String>())?.toString()
        }
            ?: invokeVariant(spider, "playerContent(flag,id,list,map,map)") {
                spider.javaClass.methods.firstOrNull {
                    it.name == "playerContent" && it.parameterTypes.size == 5
                }?.invoke(spider, flag, id, emptyList<String>(), HashMap<String, String>(), HashMap<String, String>())?.toString()
            }
            ?: invokeVariant(spider, "playerContent(flag,id)") {
                spider.javaClass.methods.firstOrNull {
                    it.name == "playerContent" && it.parameterTypes.size == 2
                }?.invoke(spider, flag, id)?.toString()
            }
    }

    private fun invokeCategory(spider: Any, category: CategoryParam, page: Int): String? {
        val categoryId = category.value.ifBlank { category.name }
        val extendMap = HashMap<String, String>().apply {
            category.extra.forEachIndexed { index, value ->
                put("extra_$index", value)
            }
        }

        return invokeVariant(spider, "categoryContent(id,page,false,extendMap)") {
            spider.javaClass.methods.firstOrNull {
                it.name == "categoryContent" && it.parameterTypes.size == 4
            }?.invoke(spider, categoryId, page.toString(), false, extendMap)?.toString()
        }
            ?: invokeVariant(spider, "categoryContent(id,page,true,extendMap)") {
                spider.javaClass.methods.firstOrNull {
                    it.name == "categoryContent" && it.parameterTypes.size == 4
                }?.invoke(spider, categoryId, page.toString(), true, extendMap)?.toString()
            }
            ?: invokeVariant(spider, "categoryContent(id,page,false,extendMap,map)") {
                spider.javaClass.methods.firstOrNull {
                    it.name == "categoryContent" && it.parameterTypes.size == 5
                }?.invoke(spider, categoryId, page.toString(), false, extendMap, HashMap<String, String>())?.toString()
            }
            ?: invokeVariant(spider, "categoryContent(id,page,true,extendMap,map)") {
                spider.javaClass.methods.firstOrNull {
                    it.name == "categoryContent" && it.parameterTypes.size == 5
                }?.invoke(spider, categoryId, page.toString(), true, extendMap, HashMap<String, String>())?.toString()
            }
            ?: invokeVariant(spider, "categoryContent(id,pageInt,false,extendMap)") {
                spider.javaClass.methods.firstOrNull {
                    it.name == "categoryContent" && it.parameterTypes.size == 4
                }?.invoke(spider, categoryId, page, false, extendMap)?.toString()
            }
            ?: invokeVariant(spider, "categoryContent(id,page,false)") {
                spider.javaClass.methods.firstOrNull {
                    it.name == "categoryContent" && it.parameterTypes.size == 3
                }?.invoke(spider, categoryId, page.toString(), false)?.toString()
            }
            ?: invokeVariant(spider, "categoryContent(id,page)") {
                spider.javaClass.methods.firstOrNull {
                    it.name == "categoryContent" && it.parameterTypes.size == 2
                }?.invoke(spider, categoryId, page.toString())?.toString()
            }
            ?: invokeVariant(spider, "categoryContent(id)") {
                spider.javaClass.methods.firstOrNull {
                    it.name == "categoryContent" && it.parameterTypes.size == 1
            }?.invoke(spider, categoryId)?.toString()
            }
    }

    private fun initSpider(spider: Any, source: SourceItem) {
        val ext = source.settings.spiderExt.orEmpty()
        runCatching {
            spider.javaClass.methods.firstOrNull {
                it.name == "init" && it.parameterTypes.size == 3
            }?.invoke(spider, context, source.url, ext)
        }.onFailure {
            Log.w(tag, "instance init(context, url, ext) failed", it)
        }
        runCatching {
            spider.javaClass.methods.firstOrNull {
                it.name == "init" && it.parameterTypes.size == 2
            }?.invoke(spider, context, ext)
        }.onFailure {
            Log.w(tag, "instance init(context, ext) failed", it)
        }
        runCatching {
            spider.javaClass.methods.firstOrNull {
                it.name == "init" && it.parameterTypes.size == 1
            }?.invoke(spider, context)
        }.onFailure {
            Log.w(tag, "instance init(context) failed", it)
        }
        runCatching {
            spider.javaClass.methods.firstOrNull {
                it.name == "init" && it.parameterTypes.isEmpty()
            }?.invoke(spider)
        }.onFailure {
            Log.w(tag, "instance init() failed", it)
        }
    }

    private fun pageHints(): Map<String, String> {
        return hashMapOf(
            "pg" to "1",
            "page" to "1"
        )
    }

    private fun prepareStaticInit(loader: DexClassLoader, source: SourceItem) {
        val ext = source.settings.spiderExt.orEmpty()
        val initClass = withThreadContextClassLoader(loader) {
            runCatching {
                loader.loadClass("com.github.catvod.spider.Init")
            }.onFailure {
                Log.w(tag, "load static Init class failed", it)
            }.getOrNull()
        } ?: return

        withThreadContextClassLoader(loader) {
            runCatching {
                initClass.methods.firstOrNull {
                    it.name == "init" && it.parameterTypes.size == 3
                }?.invoke(null, context, source.url, ext)
            }.onFailure {
                Log.w(tag, "static Init.init(context, url, ext) failed", it)
            }
            runCatching {
                initClass.methods.firstOrNull {
                    it.name == "init" && it.parameterTypes.size == 2
                }?.invoke(null, context, ext)
            }.onFailure {
                Log.w(tag, "static Init.init(context, ext) failed", it)
            }
            runCatching {
                initClass.methods.firstOrNull {
                    it.name == "init" && it.parameterTypes.size == 1
                }?.invoke(null, context)
            }.onFailure {
                Log.w(tag, "static Init.init(context) failed", it)
            }
            seedInitSingleton(initClass, loader)
        }
    }

    private fun invokeVariant(spider: Any, label: String, block: () -> String?): String? {
        return runCatching { block() }
            .onFailure { Log.e(tag, "invoke variant failed ${spider.javaClass.name}::$label", it) }
            .getOrNull()
            ?.also { Log.d(tag, "invoke variant ok ${spider.javaClass.name}::$label -> ${it.previewForLog()}") }
    }

    private fun enforceReadOnlyJar(file: File) {
        runCatching {
            file.setReadable(true, true)
            file.setWritable(false, false)
            file.setWritable(false, true)
            file.setReadOnly()
        }.onFailure {
            Log.w(tag, "enforceReadOnlyJar failed ${file.absolutePath}", it)
        }
        Log.d(tag, "jar perms ${file.absolutePath}: exists=${file.exists()} canWrite=${file.canWrite()} canRead=${file.canRead()}")
    }

    private fun <T> withThreadContextClassLoader(
        loader: ClassLoader,
        block: () -> T
    ): T {
        val thread = Thread.currentThread()
        val previous = thread.contextClassLoader
        if (previous !== loader) {
            thread.contextClassLoader = loader
            Log.d(tag, "contextClassLoader switched: ${previous?.javaClass?.name ?: "null"} -> ${loader.javaClass.name}")
        }
        return try {
            block()
        } finally {
            if (previous !== loader) {
                thread.contextClassLoader = previous
                Log.d(tag, "contextClassLoader restored: ${previous?.javaClass?.name ?: "null"}")
            }
        }
    }

    private fun seedInitSingleton(
        initClass: Class<*>,
        loader: DexClassLoader
    ) {
        val application = context.applicationContext as? Application
        val singleton = runCatching {
            initClass.methods.firstOrNull {
                it.name == "get" && it.parameterTypes.isEmpty()
            }?.invoke(null)
        }.onFailure {
            Log.w(tag, "Init.get() failed", it)
        }.getOrNull() ?: return

        val fields = initClass.declaredFields.associateBy { it.type.name }
        fields["java.lang.ClassLoader"]?.let { field ->
            runCatching {
                field.isAccessible = true
                field.set(singleton, loader)
                Log.d(tag, "seedInitSingleton classLoader=${loader.javaClass.name}")
            }.onFailure {
                Log.w(tag, "seed Init.classLoader failed", it)
            }
        }
        fields["dalvik.system.DexClassLoader"]?.let { field ->
            runCatching {
                field.isAccessible = true
                field.set(singleton, loader)
                Log.d(tag, "seedInitSingleton dexLoader=${loader.javaClass.name}")
            }.onFailure {
                Log.w(tag, "seed Init.dexLoader failed", it)
            }
        }
        fields["android.app.Application"]?.let { field ->
            runCatching {
                field.isAccessible = true
                if (application != null) {
                    field.set(singleton, application)
                    Log.d(tag, "seedInitSingleton application=${application.javaClass.name}")
                } else {
                    Log.w(tag, "seedInitSingleton skipped application because context.applicationContext is not Application")
                }
            }.onFailure {
                Log.w(tag, "seed Init.application failed", it)
            }
        }

        runCatching {
            val loaderGetter = initClass.methods.firstOrNull {
                it.name == "loader" && it.parameterTypes.isEmpty()
            }
            val classLoaderGetter = initClass.methods.firstOrNull {
                it.name == "classLoader" && it.parameterTypes.isEmpty()
            }
            val contextGetter = initClass.methods.firstOrNull {
                it.name == "context" && it.parameterTypes.isEmpty()
            }
            Log.d(
                tag,
                "seedInitSingleton verify loader=${loaderGetter?.invoke(null)?.javaClass?.name ?: "null"} classLoader=${classLoaderGetter?.invoke(null)?.javaClass?.name ?: "null"} context=${contextGetter?.invoke(null)?.javaClass?.name ?: "null"}"
            )
        }.onFailure {
            Log.w(tag, "seed Init verify failed", it)
        }
    }
}
