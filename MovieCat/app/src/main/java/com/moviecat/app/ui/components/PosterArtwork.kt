package com.moviecat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun PosterArtwork(
    title: String,
    coverUrl: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val normalizedCoverUrl = remember(coverUrl) { coverUrl.normalizedCoverUrl() }
    val imageRequest = remember(context, normalizedCoverUrl) {
        normalizedCoverUrl?.let { url ->
            ImageRequest.Builder(context)
                .data(url)
                .crossfade(true)
                .setHeader("User-Agent", CoverUserAgent)
                .setHeader("Referer", url.coverReferer())
                .build()
        }
    }
    var imageLoaded by remember(normalizedCoverUrl) { mutableStateOf(false) }
    var imageFailed by remember(normalizedCoverUrl) { mutableStateOf(false) }
    val showDefaultCover = normalizedCoverUrl == null || imageFailed || !imageLoaded

    Box(modifier = modifier) {
        if (showDefaultCover) {
            DefaultPosterCover(
                title = title,
                isLoading = normalizedCoverUrl != null && !imageFailed && !imageLoaded,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (imageRequest != null && !imageFailed) {
            AsyncImage(
                model = imageRequest,
                contentDescription = title,
                contentScale = contentScale,
                onSuccess = { imageLoaded = true },
                onError = { imageFailed = true },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun DefaultPosterCover(
    title: String,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = remember(title) {
        DefaultCoverPalette[(title.hashCode() and Int.MAX_VALUE) % DefaultCoverPalette.size]
    }
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF102331),
                        accent.copy(alpha = 0.55f),
                        Color(0xFF070A0D)
                    )
                )
            )
            .padding(16.dp)
    ) {
        Text(
            text = "MovieCat",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.72f),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.TopStart)
        )
        Text(
            text = if (isLoading) "加载封面" else "默认封面",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.64f),
            modifier = Modifier.align(Alignment.TopEnd)
        )
        Text(
            text = title.ifBlank { "暂无封面" },
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.Center)
        )
        Text(
            text = if (isLoading) "正在逐步加载图片" else "封面暂不可用",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.62f),
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

private fun String?.normalizedCoverUrl(): String? {
    val raw = this
        ?.trim()
        ?.replace("\\/", "/")
        ?.replace("\\u002F", "/")
        .orEmpty()
    if (raw.isBlank() || raw.equals("null", ignoreCase = true) || raw.equals("undefined", ignoreCase = true)) {
        return null
    }
    return when {
        raw.startsWith("//") -> "https:$raw"
        raw.startsWith("http://img", ignoreCase = true) -> raw.replaceFirst("http://", "https://", ignoreCase = true)
        else -> raw
    }
}

private fun String.coverReferer(): String {
    return when {
        contains("doubanio.com", ignoreCase = true) -> "https://movie.douban.com/"
        else -> "https://m.douban.com/"
    }
}

private const val CoverUserAgent =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36"

private val DefaultCoverPalette = listOf(
    Color(0xFF12B7D8),
    Color(0xFF8BC34A),
    Color(0xFFFFB74D),
    Color(0xFF7E57C2),
    Color(0xFFFF6E6E),
    Color(0xFF26A69A)
)
