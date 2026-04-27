package com.moviecat.app.util

import com.moviecat.app.data.model.CatalogItem
import com.moviecat.app.data.model.Episode
import com.moviecat.app.data.model.ParsedPayload
import com.moviecat.app.data.model.SourceItem

fun SourceItem.debugSummary(): String {
    return buildString {
        append(label)
        append("(")
        append(kind.name)
        append(")")
        append(" url=")
        append(url)
        append(" spider=")
        append(settings.spiderMode.name)
        settings.spiderClass?.takeIf { it.isNotBlank() }?.let {
            append(" class=")
            append(it)
        }
    }
}

fun CatalogItem.debugSummary(): String {
    return buildString {
        append(title)
        detailToken?.takeIf { it.isNotBlank() }?.let {
            append(" detail=")
            append(it)
        }
        append(" playlists=")
        append(playlists.size)
        append(" episodes=")
        append(playlists.sumOf { it.episodes.size })
    }
}

fun Episode.debugSummary(): String {
    return buildString {
        append(name)
        flag?.takeIf { it.isNotBlank() }?.let {
            append(" flag=")
            append(it)
        }
        append(" parse=")
        append(parse ?: 0)
        append(" headers=")
        append(headers.keys.joinToString(prefix = "[", postfix = "]"))
        append(" url=")
        append(url.previewForLog())
    }
}

fun ParsedPayload.debugSummary(): String {
    return "items=${catalogItems.size}, featured=${featuredItems.size}, categories=${categories.size}, discovered=${discoveredSources.size}, warnings=${warnings.size}"
}

fun String.previewForLog(maxLength: Int = 180): String {
    val normalized = replace("\r", "\\r").replace("\n", "\\n")
    return if (normalized.length <= maxLength) normalized else normalized.take(maxLength) + "…"
}
