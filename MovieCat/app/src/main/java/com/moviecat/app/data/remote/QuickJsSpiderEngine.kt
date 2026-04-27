package com.moviecat.app.data.remote

import android.util.Log
import com.moviecat.app.data.model.CatalogItem
import com.moviecat.app.data.model.CategoryParam
import com.moviecat.app.data.model.Episode
import com.moviecat.app.data.model.ParsedPayload
import com.moviecat.app.data.model.SourceItem
import com.moviecat.app.util.debugSummary
import com.moviecat.app.util.previewForLog
import java.util.concurrent.ConcurrentHashMap

class QuickJsSpiderEngine(
    private val textLoader: suspend (String, SourceItem) -> String
) {
    private val tag = "MovieCatQuickJS"
    private val runtimeFactory = ReflectionQuickJsRuntimeFactory()
    private val scriptCache = ConcurrentHashMap<String, String>()

    suspend fun fetchHome(source: SourceItem): ParsedPayload {
        Log.d(tag, "fetchHome start ${source.debugSummary()}")
        return executeWithRuntime(source) { runtime ->
            runtime.init(source)
            runtime.callString("homeContent", listOf(true))
                ?: runtime.callString("homeContent", listOf(false))
                ?: runtime.callString("home")
        }?.let(SourcePayloadParser::parseSpiderJson)
            ?.also { Log.d(tag, "fetchHome done ${source.label}: ${it.debugSummary()}") }
            ?: ParsedPayload(
                warnings = listOf("QuickJS 已接入，但当前脚本没有返回可识别的 homeContent。")
            )
    }

    suspend fun fetchDetail(source: SourceItem, item: CatalogItem): CatalogItem? {
        val detailId = item.detailToken ?: return null
        Log.d(tag, "fetchDetail start ${source.label}: ${item.debugSummary()}")
        return executeWithRuntime(source) { runtime ->
            runtime.init(source)
            runtime.callString("detailContent", listOf(listOf(detailId)))
                ?: runtime.callString("detailContent", listOf(detailId))
                ?: runtime.callString("detail", listOf(detailId))
        }?.let(SourcePayloadParser::parseDetailItem)
            ?.also { Log.d(tag, "fetchDetail done ${source.label}: ${it.debugSummary()}") }
    }

    suspend fun search(source: SourceItem, query: String): ParsedPayload {
        Log.d(tag, "search start ${source.label}: query=${query.previewForLog(80)}")
        return executeWithRuntime(source) { runtime ->
            runtime.init(source)
            runtime.callString("searchContent", listOf(query, false))
                ?: runtime.callString("searchContent", listOf(query))
                ?: runtime.callString("search", listOf(query))
        }?.let(SourcePayloadParser::parseSpiderJson)
            ?.also { Log.d(tag, "search done ${source.label}: ${it.debugSummary()}") }
            ?: ParsedPayload(
                warnings = listOf("QuickJS 已接入，但当前脚本没有返回可识别的 searchContent。")
            )
    }

    suspend fun fetchCategory(source: SourceItem, category: CategoryParam, page: Int): ParsedPayload {
        val categoryId = category.value.ifBlank { category.name }
        Log.d(tag, "fetchCategory start ${source.label}: category=$categoryId, page=$page")
        return executeWithRuntime(source) { runtime ->
            runtime.init(source)
            runtime.callString(
                "categoryContent",
                listOf(categoryId, page.toString(), false, category.extra.associateBy { it })
            )
                ?: runtime.callString("categoryContent", listOf(categoryId, page.toString(), false))
                ?: runtime.callString("categoryContent", listOf(categoryId, page.toString()))
                ?: runtime.callString("category", listOf(categoryId, page.toString()))
        }?.let(SourcePayloadParser::parseSpiderJson)
            ?.also { Log.d(tag, "fetchCategory done ${source.label}: ${it.debugSummary()}") }
            ?: ParsedPayload(
                warnings = listOf("QuickJS 已接入，但当前脚本没有返回可识别的 categoryContent。")
            )
    }

    suspend fun resolveEpisode(source: SourceItem, episode: Episode): Episode {
        if (episode.url.startsWith("http", ignoreCase = true) && episode.parse != 1) {
            Log.d(tag, "resolveEpisode skip ${source.label}: already direct ${episode.debugSummary()}")
            return episode
        }
        Log.d(tag, "resolveEpisode start ${source.label}: ${episode.debugSummary()}")
        return executeWithRuntime(source) { runtime ->
            runtime.init(source)
            runtime.callString(
                "playerContent",
                listOf(episode.flag.orEmpty(), episode.url, emptyList<String>())
            )
                ?: runtime.callString("playerContent", listOf(episode.flag.orEmpty(), episode.url))
                ?: runtime.callString("player", listOf(episode.flag.orEmpty(), episode.url))
        }?.let { SourcePayloadParser.parsePlayerEpisode(it, episode.name, episode.flag) }
            ?.also { Log.d(tag, "resolveEpisode done ${source.label}: ${it.debugSummary()}") }
            ?: episode
    }

    private suspend fun executeWithRuntime(
        source: SourceItem,
        block: (JsRuntimeAdapter) -> String?
    ): String? {
        val runtime = runtimeFactory.createOrNull()
        if (runtime == null) {
            Log.e(tag, "QuickJS runtime unavailable for ${source.debugSummary()}")
            return null
        }
        return try {
            val script = loadScript(source) ?: return null
            Log.d(tag, "bootstrap ${source.label}: scriptSize=${script.length}")
            runtime.bootstrap(script)
            block(runtime)
                ?.also { Log.d(tag, "executeWithRuntime result ${source.label}: ${it.previewForLog()}") }
        } finally {
            runtime.close()
        }
    }

    private suspend fun loadScript(source: SourceItem): String? {
        val cacheKey = buildString {
            append(source.id)
            append("|")
            append(source.settings.spiderScriptUrl.orEmpty())
            append("|")
            append(source.settings.spiderExt.orEmpty())
            append("|")
            append(source.settings.spiderScriptCode.orEmpty().hashCode())
        }
        scriptCache[cacheKey]?.let { return it }

        val inlineScript = source.settings.spiderScriptCode?.trim().orEmpty()
        val scriptUrl = source.settings.spiderScriptUrl
            ?: source.settings.spiderExt?.takeIf { it.startsWith("http") }
            ?: source.url.takeIf { it.endsWith(".js", ignoreCase = true) }
        val extScriptUrl = source.settings.spiderExt
            ?.takeIf { it.startsWith("http") && it.endsWith(".js", ignoreCase = true) }
            ?.takeIf { it != scriptUrl }

        val script = when {
            inlineScript.isNotBlank() -> inlineScript.also {
                Log.d(tag, "loadScript inline ${source.label}: size=${it.length}")
            }
            !scriptUrl.isNullOrBlank() -> {
                val mainScript = textLoader(scriptUrl, source)
                val extScript = extScriptUrl?.let { textLoader(it, source) }.orEmpty()
                Log.d(
                    tag,
                    "loadScript remote ${source.label}: main=$scriptUrl(${mainScript.length}) ext=${extScriptUrl ?: "-"}(${extScript.length})"
                )
                if (extScript.isBlank()) {
                    mainScript
                } else {
                    buildString {
                        append(mainScript)
                        append("\n\n")
                        append(extScript)
                    }
                }
            }
            else -> null
        } ?: return null

        scriptCache[cacheKey] = script
        return script
    }
}

private interface JsRuntimeAdapter : AutoCloseable {
    fun bootstrap(script: String)

    fun init(source: SourceItem)

    fun callString(functionName: String, args: List<Any?> = emptyList()): String?
}

private class ReflectionQuickJsRuntimeFactory {
    fun createOrNull(): JsRuntimeAdapter? {
        val quickJsClass = runCatching { Class.forName("app.cash.quickjs.QuickJs") }.getOrNull()
            ?: return null
        return CashAppQuickJsAdapter(quickJsClass)
    }
}

private class CashAppQuickJsAdapter(
    quickJsClass: Class<*>
) : JsRuntimeAdapter {
    private val runtime: Any = quickJsClass.getMethod("create").invoke(null)
        ?: error("QuickJs.create() returned null")
    private val evaluateMethod = quickJsClass.methods.firstOrNull {
        it.name == "evaluate" && it.parameterTypes.size == 1 && it.parameterTypes[0] == String::class.java
    } ?: throw IllegalStateException("QuickJs evaluate(String) method not found.")
    private val closeMethod = quickJsClass.methods.firstOrNull { it.name == "close" && it.parameterTypes.isEmpty() }

    override fun bootstrap(script: String) {
        val wrappedScript = buildString {
            append("var global = globalThis;")
            append("var window = globalThis;")
            append("var module = { exports: {} };")
            append("var exports = module.exports;")
            append(script)
            append(
                """
                ;
                globalThis.__mcExports = module.exports;
                globalThis.__mcSpider = globalThis.spider
                  || globalThis.Spider
                  || (typeof globalThis.__JS_SPIDER__ !== 'undefined' ? globalThis.__JS_SPIDER__ : null)
                  || (module.exports && Object.keys(module.exports).length ? module.exports : null);

                globalThis.__mcResolveTarget = function(name) {
                  if (typeof globalThis[name] === 'function') return globalThis;
                  if (globalThis.__mcSpider && typeof globalThis.__mcSpider[name] === 'function') return globalThis.__mcSpider;
                  if (globalThis.__mcExports && typeof globalThis.__mcExports[name] === 'function') return globalThis.__mcExports;
                  if (typeof globalThis.Spider === 'function') {
                    try {
                      if (!globalThis.__mcSpiderInstance) globalThis.__mcSpiderInstance = new globalThis.Spider();
                      if (typeof globalThis.__mcSpiderInstance[name] === 'function') return globalThis.__mcSpiderInstance;
                    } catch (e) {}
                  }
                  return null;
                };

                globalThis.__mcCall = function(name, args) {
                  var target = globalThis.__mcResolveTarget(name);
                  if (!target) return null;
                  var fn = target[name];
                  return fn.apply(target, args || []);
                };
                """.trimIndent()
            )
        }
        evaluate(wrappedScript)
    }

    override fun init(source: SourceItem) {
        callVoid("init", listOf(source.url, source.settings.spiderExt.orEmpty()))
        callVoid("init", listOf(source.settings.spiderExt.orEmpty()))
        callVoid("init")
    }

    override fun callString(functionName: String, args: List<Any?>): String? {
        val js = buildString {
            append("(function(){")
            append("const __ret = globalThis.__mcCall(")
            append("\"")
            append(escapeForJs(functionName))
            append("\"")
            append(", ")
            append(args.toJsLiteral())
            append(");")
            append("if (__ret == null) return null;")
            append("return typeof __ret === 'string' ? __ret : JSON.stringify(__ret);")
            append("})();")
        }
        return evaluate(js)?.takeIf { it != "null" }
    }

    override fun close() {
        closeMethod?.invoke(runtime)
    }

    private fun callVoid(functionName: String, args: List<Any?> = emptyList()) {
        val js = buildString {
            append("(function(){")
            append("try { globalThis.__mcCall(")
            append("\"")
            append(escapeForJs(functionName))
            append("\"")
            append(", ")
            append(args.toJsLiteral())
            append("); } catch (e) {}")
            append("})();")
        }
        evaluate(js)
    }

    private fun evaluate(script: String): String? {
        return runCatching { evaluateMethod.invoke(runtime, script)?.toString() }.getOrElse {
            Log.e("MovieCatQuickJS", "evaluate failed script=${script.previewForLog(220)}", it)
            null
        }
    }
}

private fun Any?.toJsLiteral(): String {
    return when (this) {
        null -> "null"
        is String -> "\"" + escapeForJs(this) + "\""
        is Boolean, is Number -> toString()
        is List<*> -> joinToString(prefix = "[", postfix = "]") { it.toJsLiteral() }
        is Map<*, *> -> entries.joinToString(prefix = "{", postfix = "}") { entry ->
            "\"${escapeForJs(entry.key?.toString().orEmpty())}\":${entry.value.toJsLiteral()}"
        }
        else -> "\"" + escapeForJs(toString()) + "\""
    }
}

private fun escapeForJs(value: String): String {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
}
