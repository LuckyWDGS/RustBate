@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.moviecat.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.moviecat.app.data.model.CatalogItem
import com.moviecat.app.data.model.CategoryParam
import com.moviecat.app.data.model.Episode
import com.moviecat.app.data.model.LibraryEntry
import com.moviecat.app.data.model.PlaylistGroup
import com.moviecat.app.data.model.SourceItem
import com.moviecat.app.ui.components.PosterArtwork
import com.moviecat.app.viewmodel.DeviceNetworkStatus
import com.moviecat.app.viewmodel.MovieCatUiState
import com.moviecat.app.viewmodel.WeatherStatus
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Accent = Color(0xFF19E6F4)
private val Lime = Color(0xFF9FEA3A)
private val Gold = Color(0xFFFFC642)
private val Panel = Color(0xB811171B)
private val PanelStrong = Color(0xDD10161B)
private val Stroke = Color.White.copy(alpha = 0.16f)
private val Muted = Color(0xFFAAB2BA)

private data class MovieCatLayout(
    val compact: Boolean,
    val tablet: Boolean,
    val tvLike: Boolean,
    val screenPadding: Dp,
    val topPadding: Dp,
    val sectionGap: Dp,
    val rowGap: Dp,
    val backdropHeight: Dp,
    val mosaicTileWidth: Dp,
    val mosaicTileHeight: Dp,
    val navPillHeight: Dp,
    val navPillPadding: Dp,
    val actionWidth: Dp,
    val actionHeight: Dp,
    val heroWidth: Dp,
    val heroHeight: Dp,
    val wideWidth: Dp,
    val wideHeight: Dp,
    val recommendationColumns: Int,
    val recommendationWidth: Dp,
    val recommendationHeight: Dp,
    val searchPadding: Dp,
    val searchGap: Dp,
    val searchSideWidth: Dp,
    val searchRankWidth: Dp,
    val searchTopOffset: Dp,
    val searchInputHeight: Dp,
    val searchTitleSize: Int,
    val keyboardGap: Dp,
    val keyboardKeyWidth: Dp,
    val keyboardKeyHeight: Dp,
    val keyboardWideKeyWidth: Dp,
    val resultSideWidth: Dp,
    val resultGap: Dp,
    val resultColumns: Int,
    val resultCardHeight: Dp,
    val resultPosterHeight: Dp,
    val rankingPosterWidth: Dp,
    val rankingPosterHeight: Dp
)

private fun movieCatLayout(maxWidth: Dp, maxHeight: Dp, isTv: Boolean): MovieCatLayout {
    val width = maxWidth.value
    val height = maxHeight.value
    val landscape = width >= height
    val compact = width < 700f && !landscape
    val handheldLandscape = landscape && width < 840f && !isTv
    val tablet = (width in 700f..1199f && !isTv) || handheldLandscape
    val tvLike = isTv || width >= 1200f
    val scale = (width / 1536f).coerceIn(if (compact || handheldLandscape) 0.66f else 0.82f, if (tvLike) 1.22f else 1.02f)

    fun scaled(base: Float, min: Float, max: Float): Dp = (base * scale).coerceIn(min, max).dp
    fun percent(value: Float, min: Float, max: Float): Dp = (width * value).coerceIn(min, max).dp

    val screenPadding = percent(if (compact) 0.044f else 0.031f, if (compact) 14f else 24f, if (tvLike) 58f else 34f)
    val rowGap = scaled(10f, 8f, 16f)
    val searchPadding = percent(if (compact) 0.04f else 0.022f, if (compact) 14f else 24f, if (tvLike) 42f else 28f)
    val searchGap = percent(if (compact) 0.028f else 0.020f, 12f, 34f)
    val searchSideWidth = percent(0.248f, if (handheldLandscape) 178f else 260f, if (tvLike) 430f else 330f)
    val searchRankWidth = percent(0.319f, if (handheldLandscape) 214f else 320f, if (tvLike) 560f else 420f)
    val resultSideWidth = percent(0.182f, if (handheldLandscape) 160f else 210f, if (tvLike) 330f else 260f)
    val resultContentWidth = if (compact) {
        width - searchPadding.value * 2f
    } else {
        width - searchPadding.value * 2f - resultSideWidth.value - searchGap.value
    }.coerceAtLeast(260f)
    val resultColumns = when {
        compact && width < 430f -> 1
        compact -> 2
        tablet -> (resultContentWidth / 190f).toInt().coerceIn(3, 4)
        else -> (resultContentWidth / 214f).toInt().coerceIn(4, 6)
    }
    val resultGap = percent(if (compact) 0.024f else 0.010f, 10f, 18f)
    val resultCardWidth = ((resultContentWidth - resultGap.value * (resultColumns - 1)) / resultColumns).coerceAtLeast(150f)
    val resultPosterHeight = (resultCardWidth * 1.34f).coerceIn(if (compact) 190f else 250f, if (tvLike) 330f else 280f).dp

    val keyboardGap = scaled(8f, 6f, 10f)
    val keyboardKeyWidth = when {
        compact -> ((width - searchPadding.value * 2f - keyboardGap.value * 6f) / 7f).coerceIn(38f, 58f).dp
        tablet -> scaled(56f, 50f, 62f)
        else -> scaled(66f, 58f, 76f)
    }
    val keyboardKeyHeight = when {
        compact -> scaled(46f, 42f, 52f)
        else -> scaled(58f, 50f, 66f)
    }
    val homeContentWidth = (width - screenPadding.value * 2f).coerceAtLeast(280f)
    val recommendationColumns = when {
        compact && width < 430f -> 2
        compact -> 3
        tablet -> 4
        tvLike -> 5
        else -> 5
    }
    val rawRecommendationWidth = (homeContentWidth - rowGap.value * (recommendationColumns - 1)) / recommendationColumns
    val recommendationWidth = rawRecommendationWidth
        .coerceAtLeast(if (compact) 132f else 150f)
        .dp

    return MovieCatLayout(
        compact = compact,
        tablet = tablet,
        tvLike = tvLike,
        screenPadding = screenPadding,
        topPadding = percent(if (compact) 0.026f else 0.021f, 14f, 36f),
        sectionGap = scaled(14f, 10f, 18f),
        rowGap = rowGap,
        backdropHeight = (height * 0.33f).coerceIn(210f, if (tvLike) 360f else 290f).dp,
        mosaicTileWidth = percent(0.104f, 112f, 210f),
        mosaicTileHeight = percent(0.156f, 168f, 315f),
        navPillHeight = scaled(38f, 32f, 44f),
        navPillPadding = scaled(18f, 12f, 24f),
        actionWidth = percent(0.085f, if (handheldLandscape) 88f else 104f, if (tvLike) 150f else 132f),
        actionHeight = scaled(56f, 46f, 68f),
        heroWidth = percent(if (compact) 0.43f else 0.162f, if (compact) 146f else 160f, if (tvLike) 292f else 210f),
        heroHeight = percent(if (compact) 0.54f else 0.202f, if (compact) 184f else 200f, if (tvLike) 354f else 262f),
        wideWidth = percent(if (compact) 0.46f else 0.170f, 152f, if (tvLike) 296f else 230f),
        wideHeight = percent(if (compact) 0.27f else 0.096f, 92f, if (tvLike) 168f else 126f),
        recommendationColumns = recommendationColumns,
        recommendationWidth = recommendationWidth,
        recommendationHeight = (recommendationWidth.value * 0.58f).coerceIn(if (compact) 86f else 104f, if (tvLike) 178f else 146f).dp,
        searchPadding = searchPadding,
        searchGap = searchGap,
        searchSideWidth = searchSideWidth,
        searchRankWidth = searchRankWidth,
        searchTopOffset = if (compact) 0.dp else scaled(82f, 58f, 96f),
        searchInputHeight = scaled(78f, 58f, 86f),
        searchTitleSize = when {
            compact -> 28
            tablet -> 32
            else -> 38
        },
        keyboardGap = keyboardGap,
        keyboardKeyWidth = keyboardKeyWidth,
        keyboardKeyHeight = keyboardKeyHeight,
        keyboardWideKeyWidth = (keyboardKeyWidth.value * 2f + keyboardGap.value).dp,
        resultSideWidth = resultSideWidth,
        resultGap = resultGap,
        resultColumns = resultColumns,
        resultCardHeight = (resultPosterHeight.value + scaled(110f, 90f, 126f).value).dp,
        resultPosterHeight = resultPosterHeight,
        rankingPosterWidth = scaled(78f, 64f, 92f),
        rankingPosterHeight = scaled(118f, 96f, 138f)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    state: MovieCatUiState,
    isTv: Boolean,
    onRefresh: () -> Unit,
    onHomeVisible: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClearSearch: () -> Unit,
    onBrowsePrimaryCategory: (String) -> Unit,
    onBrowseCategory: (CategoryParam?) -> Unit,
    onSelectSource: (SourceItem) -> Unit,
    onAddSource: (String, String) -> Unit,
    onImportSource: (SourceItem) -> Unit,
    onOpenDetails: (CatalogItem) -> Unit,
    onUpdateDetailSelection: (Int, Int) -> Unit,
    onCloseDetails: () -> Unit,
    onPlaySelection: (CatalogItem, Int, Int) -> Unit,
    onToggleFavorite: (CatalogItem) -> Unit,
    onDismissPlayer: (Long) -> Unit,
    onSelectEpisode: (Int, Int) -> Unit
) {
    var selectedPrimary by rememberSaveable { mutableStateOf(BrowsePrimaryCategory.Home.key) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var reopenSearchAfterDetail by rememberSaveable { mutableStateOf(false) }
    var sourceDialogOpen by rememberSaveable { mutableStateOf(false) }
    val favoriteKeys = remember(state.favorites) { state.favorites.map { it.entryKey }.toSet() }
    val playableSourceIds = remember(state.sources, state.discoveredSources) {
        (state.sources + state.discoveredSources).map { it.id }.toSet()
    }

    fun favoriteKeyFor(item: CatalogItem): String {
        val sourceId = item.sourceId
            ?.takeIf { it in playableSourceIds }
            ?: state.selectedSourceId
            ?: item.sourceId
            ?: ""
        return item.stableEntryKey(sourceId)
    }

    LaunchedEffect(selectedPrimary) {
        if (selectedPrimary == BrowsePrimaryCategory.Home.key) {
            onHomeVisible()
        }
    }

    Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val layout = remember(maxWidth, maxHeight, isTv) { movieCatLayout(maxWidth, maxHeight, isTv) }

            AppBackdrop(items = state.featuredItems + state.catalogItems, layout = layout)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = layout.screenPadding,
                    end = layout.screenPadding,
                    top = layout.topPadding,
                    bottom = layout.screenPadding
                ),
                verticalArrangement = Arrangement.spacedBy(layout.sectionGap)
            ) {
                item {
                    HomeTopBar(
                        selectedPrimary = selectedPrimary,
                        isTv = isTv,
                        layout = layout,
                        isLoading = state.isLoading,
                        networkStatus = state.networkStatus,
                        weatherStatus = state.weatherStatus,
                        lanServerRunning = state.lanServerRunning,
                        lanServerUrls = state.lanServerUrls,
                        lanServerMessage = state.lanServerMessage,
                        onNetworkClick = onRefresh,
                        onPrimaryClick = { primary ->
                            selectedPrimary = primary.key
                            onBrowsePrimaryCategory(primary.key)
                        }
                    )
                }

                item {
                    HomeActionRail(
                        isTv = isTv,
                        layout = layout,
                        onHistory = { selectedPrimary = BrowsePrimaryCategory.Home.key },
                        onLive = { onBrowsePrimaryCategory(BrowsePrimaryCategory.Series.key) },
                        onSearch = { searchOpen = true },
                        onConfig = { sourceDialogOpen = true },
                        onSwitchSource = { sourceDialogOpen = true },
                        onCloudDrive = { sourceDialogOpen = true },
                        onFavorites = { selectedPrimary = BrowsePrimaryCategory.Home.key },
                        onPush = { sourceDialogOpen = true },
                        onSettings = { sourceDialogOpen = true }
                    )
                }

                val playableHistory = state.history.filter { it.playUrl.isNotBlank() }
                if (playableHistory.isNotEmpty()) {
                    item {
                        ContinueWatchingSection(
                            isTv = isTv,
                            layout = layout,
                            history = playableHistory,
                            onOpenDetails = onOpenDetails
                        )
                    }
                }

                item {
                    RecommendationStrip(
                        title = "今日推荐",
                        isTv = isTv,
                        layout = layout,
                        items = state.featuredItems.ifEmpty { state.catalogItems },
                        onOpenDetails = onOpenDetails
                    )
                }

            }

            if (searchOpen) {
                SearchExperience(
                    state = state,
                    isTv = isTv,
                    layout = layout,
                    favoriteKeys = favoriteKeys,
                    favoriteKeyFor = ::favoriteKeyFor,
                    onDismiss = { searchOpen = false },
                    onQueryChange = onSearchQueryChange,
                    onSearch = onSearch,
                    onClear = onClearSearch,
                    onSelectSource = onSelectSource,
                    onOpenDetails = { item ->
                        searchOpen = false
                        reopenSearchAfterDetail = true
                        onOpenDetails(item)
                    },
                    onToggleFavorite = onToggleFavorite
                )
            }

            if (sourceDialogOpen) {
                SourceCenterDialog(
                    state = state,
                    layout = layout,
                    onDismiss = { sourceDialogOpen = false },
                    onSelectSource = onSelectSource,
                    onImportSource = onImportSource,
                    onAddSource = onAddSource
                )
            }

            if (state.isDetailLoading || state.detailItem != null) {
                DetailDialog(
                    isTv = isTv,
                    layout = layout,
                    item = state.detailItem,
                    source = state.selectedSource,
                    isLoading = state.isDetailLoading,
                    selectedGroupIndex = state.detailSelectedGroupIndex,
                    selectedEpisodeIndex = state.detailSelectedEpisodeIndex,
                    isFavorite = state.detailItem?.let { favoriteKeys.contains(favoriteKeyFor(it)) } ?: false,
                    onGroupSelect = { groupIndex -> onUpdateDetailSelection(groupIndex, 0) },
                    onEpisodeSelect = { groupIndex, episodeIndex ->
                        state.detailItem?.let { detailItem ->
                            onUpdateDetailSelection(groupIndex, episodeIndex)
                            onCloseDetails()
                            reopenSearchAfterDetail = false
                            onPlaySelection(detailItem, groupIndex, episodeIndex)
                        }
                    },
                    onDismiss = {
                        onCloseDetails()
                        if (reopenSearchAfterDetail) {
                            searchOpen = true
                        }
                        reopenSearchAfterDetail = false
                    },
                    onPlaySelection = { item, groupIndex, episodeIndex ->
                        onCloseDetails()
                        reopenSearchAfterDetail = false
                        onPlaySelection(item, groupIndex, episodeIndex)
                    },
                    onToggleFavorite = { state.detailItem?.let(onToggleFavorite) }
                )
            }

            state.playerSession?.let { session ->
                BackHandler { onDismissPlayer(0L) }
                PlayerDialog(
                    isTv = isTv,
                    session = session,
                    onDismiss = onDismissPlayer,
                    onSelectEpisode = onSelectEpisode
                )
            }
        }
    }
}

@Composable
private fun AppBackdrop(items: List<CatalogItem>, layout: MovieCatLayout) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        PosterMosaic(
            items = items,
            layout = layout,
            modifier = Modifier
                .fillMaxWidth()
                .height(layout.backdropHeight)
                .align(Alignment.TopCenter)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0x6618F4FF),
                            Color(0x22000A0D),
                            Color.Black
                        ),
                        radius = 1200f
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.28f),
                            Color(0xEF02070A),
                            Color.Black
                        )
                    )
                )
        )
    }
}

@Composable
private fun PosterMosaic(items: List<CatalogItem>, layout: MovieCatLayout, modifier: Modifier = Modifier) {
    val covers = remember(items) { items.filter { !it.coverUrl.isNullOrBlank() }.take(14) }
    Row(
        modifier = modifier.graphicsLayer { alpha = 0.22f },
        horizontalArrangement = Arrangement.spacedBy(layout.rowGap)
    ) {
        if (covers.isEmpty()) {
            repeat(10) { index ->
                MosaicTile(label = "MovieCat", index = index, layout = layout)
            }
        } else {
            covers.forEachIndexed { index, item ->
                Box(
                    modifier = Modifier
                        .width(layout.mosaicTileWidth)
                        .height(layout.mosaicTileHeight)
                        .graphicsLayer {
                            rotationZ = if (index % 2 == 0) -8f else 7f
                            translationY = ((index % 3) * 18).toFloat()
                        }
                        .clip(RoundedCornerShape(14.dp))
                ) {
                    PosterArtwork(
                        title = item.title,
                        coverUrl = item.coverUrl,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun MosaicTile(label: String, index: Int, layout: MovieCatLayout) {
    Box(
        modifier = Modifier
            .width(layout.mosaicTileWidth)
            .height(layout.mosaicTileHeight)
            .graphicsLayer {
                rotationZ = if (index % 2 == 0) -8f else 7f
                translationY = ((index % 3) * 18).toFloat()
            }
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF26333C), Color(0xFF081014))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White.copy(alpha = 0.42f), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HomeTopBar(
    selectedPrimary: String,
    isTv: Boolean,
    layout: MovieCatLayout,
    isLoading: Boolean,
    networkStatus: DeviceNetworkStatus,
    weatherStatus: WeatherStatus,
    lanServerRunning: Boolean,
    lanServerUrls: List<String>,
    lanServerMessage: String?,
    onNetworkClick: () -> Unit,
    onPrimaryClick: (BrowsePrimaryCategory) -> Unit
) {
    var time by remember { mutableStateOf(currentClockText()) }
    LaunchedEffect(Unit) {
        while (true) {
            time = currentClockText()
            delay(30_000)
        }
    }

    @Composable
    fun BrandLogo() {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "MovieCat",
                color = Accent,
                fontSize = if (isTv) 22.sp else 22.sp,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = " 电影猫",
                color = Color.White.copy(alpha = 0.88f),
                fontSize = if (isTv) 13.sp else 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 3.dp)
            )
        }
    }

    @Composable
    fun PrimaryNav(modifier: Modifier = Modifier) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(if (layout.tvLike) 8.dp else layout.rowGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrowsePrimaryCategory.entries.forEach { item ->
                TvNavPill(
                    label = item.label,
                    selected = selectedPrimary == item.key,
                    isTv = isTv,
                    layout = layout,
                    onClick = { onPrimaryClick(item) }
                )
            }
        }
    }

    @Composable
    fun StatusCluster() {
        val lanStatus = lanServerStatus(lanServerRunning, lanServerUrls, lanServerMessage)
        Row(
            horizontalArrangement = Arrangement.spacedBy(if (layout.tvLike) 14.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            }
            TopInlineStatus(
                icon = Icons.Outlined.Wifi,
                accent = if (networkStatus.connected) Accent else Muted,
                onClick = onNetworkClick
            )
            TopInlineStatus(
                icon = Icons.Outlined.CloudQueue,
                accent = lanStatus.accent
            )
            Text(
                weatherStatus.temperatureLabel(),
                color = Color.White.copy(alpha = if (weatherStatus.temperatureC != null) 0.90f else 0.55f),
                fontSize = if (layout.tvLike) 15.sp else 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Text(time, color = Color.White.copy(alpha = 0.72f), fontSize = if (layout.tvLike) 16.sp else 14.sp)
        }
    }

    if (layout.compact) {
        Column(verticalArrangement = Arrangement.spacedBy(layout.rowGap)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BrandLogo()
                StatusCluster()
            }
            PrimaryNav(modifier = Modifier.fillMaxWidth())
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrandLogo()
            Spacer(Modifier.width(if (layout.tvLike) 34.dp else 18.dp))
            PrimaryNav(modifier = Modifier.weight(1f))
            Spacer(Modifier.width(if (layout.tvLike) 22.dp else 12.dp))
            StatusCluster()
        }
    }
}

@Composable
private fun TopInlineStatus(
    icon: ImageVector,
    accent: Color,
    onClick: (() -> Unit)? = null
) {
    val modifier = Modifier.size(24.dp)
    if (onClick == null) {
        Icon(icon, contentDescription = null, tint = accent, modifier = modifier)
    } else {
        Icon(
            icon,
            contentDescription = null,
            tint = accent,
            modifier = modifier
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .padding(1.dp)
        )
    }
}

@Composable
private fun TopStatusChip(
    icon: ImageVector,
    label: String,
    isTv: Boolean,
    layout: MovieCatLayout,
    onClick: (() -> Unit)? = null,
    subLabel: String? = null,
    accent: Color = Accent
) {
    val width = when {
        subLabel != null && layout.tvLike -> 122.dp
        subLabel != null -> 98.dp
        layout.tvLike -> 92.dp
        else -> 72.dp
    }
    @Composable
    fun Content() {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = 0.07f))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(if (layout.tvLike) 19.dp else 17.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    label,
                    color = Color.White.copy(alpha = 0.90f),
                    fontSize = if (layout.tvLike) 12.sp else 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subLabel != null && layout.tvLike) {
                    Text(
                        subLabel,
                        color = Muted,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    val modifier = Modifier.width(width).height(if (layout.tvLike) 38.dp else 34.dp)
    if (onClick == null) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .border(1.dp, accent.copy(alpha = 0.36f), RoundedCornerShape(18.dp))
        ) {
            Content()
        }
    } else {
        FocusContainer(
            isTv = isTv,
            onClick = onClick,
            shape = RoundedCornerShape(18.dp),
            focusedBorder = accent,
            modifier = modifier
        ) {
            Content()
        }
    }
}

@Composable
private fun TvNavPill(label: String, selected: Boolean, isTv: Boolean, layout: MovieCatLayout, onClick: () -> Unit) {
    val pillWidth = when {
        selected && label.length >= 3 -> if (layout.tvLike) 74.dp else 68.dp
        selected -> if (layout.tvLike) 66.dp else 60.dp
        label.length >= 3 -> if (layout.tvLike) 60.dp else 56.dp
        else -> if (layout.tvLike) 48.dp else 44.dp
    }
    val horizontalPadding = when {
        selected -> if (layout.tvLike) 12.dp else 10.dp
        else -> if (layout.tvLike) 6.dp else 5.dp
    }
    FocusContainer(
        isTv = isTv,
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        focusedBorder = Accent,
        selected = selected,
        showIdleBorder = selected,
        modifier = Modifier
            .width(pillWidth)
            .height(layout.navPillHeight)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (selected) Color(0x3319E6F4) else Color.Transparent)
                .padding(horizontal = horizontalPadding, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = if (selected) Accent else Color.White.copy(alpha = 0.65f),
                fontSize = if (layout.tvLike) 13.sp else 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HomeActionRail(
    isTv: Boolean,
    layout: MovieCatLayout,
    onHistory: () -> Unit,
    onLive: () -> Unit,
    onSearch: () -> Unit,
    onConfig: () -> Unit,
    onSwitchSource: () -> Unit,
    onCloudDrive: () -> Unit,
    onFavorites: () -> Unit,
    onPush: () -> Unit,
    onSettings: () -> Unit
) {
    val actions = listOf(
        HomeAction("历史记录", "历史", Icons.Outlined.History, onHistory),
        HomeAction("电视直播", "直播", Icons.Outlined.LiveTv, onLive),
        HomeAction("搜索", "搜索", Icons.Outlined.Search, onSearch),
        HomeAction("配置中心", "配置", Icons.Outlined.Settings, onConfig),
        HomeAction("线路切换", "线路", Icons.Outlined.SwapHoriz, onSwitchSource),
        HomeAction("网盘播放", "网盘", Icons.Outlined.CloudQueue, onCloudDrive),
        HomeAction("我的收藏", "收藏", Icons.Outlined.StarBorder, onFavorites),
        HomeAction("推送", "推送", Icons.Outlined.Send, onPush),
        HomeAction("设置", "设置", Icons.Outlined.Tune, onSettings)
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(if (layout.tvLike) 8.dp else 6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        actions.forEach { action ->
            FocusGlowButton(
                label = if (layout.tvLike) action.label else action.shortLabel,
                icon = action.icon,
                isTv = isTv,
                layout = layout,
                modifier = Modifier.weight(1f),
                useFixedWidth = false,
                onClick = action.onClick
            )
        }
    }
}

@Composable
private fun ContinueWatchingSection(
    isTv: Boolean,
    layout: MovieCatLayout,
    history: List<LibraryEntry>,
    onOpenDetails: (CatalogItem) -> Unit
) {
    val historyCards = remember(history) { history.take(12).map { it.toCatalogItem() } }
    SectionHeader("继续观看")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(layout.rowGap), modifier = Modifier.fillMaxWidth()) {
        items(historyCards.size, key = { index -> "continue-${history[index].entryKey}" }) { index ->
            val item = historyCards[index]
            HeroPosterCard(
                item = item,
                isTv = isTv,
                layout = layout,
                selected = index == 0,
                progressText = history[index].resumeHint(),
                onClick = { onOpenDetails(item) }
            )
        }
    }
}

@Composable
private fun RecommendationStrip(
    title: String,
    isTv: Boolean,
    layout: MovieCatLayout,
    items: List<CatalogItem>,
    onOpenDetails: (CatalogItem) -> Unit
) {
    SectionHeader(title)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(layout.rowGap),
        verticalArrangement = Arrangement.spacedBy(layout.rowGap)
    ) {
        if (items.isEmpty()) {
            repeat(layout.recommendationColumns * 2) { index ->
                RecommendationSkeletonCard(index = index, layout = layout)
            }
        } else {
            items.take(layout.recommendationColumns * 3).forEachIndexed { index, item ->
                WidePosterCard(
                    item = item,
                    isTv = isTv,
                    layout = layout,
                    selected = index == 0,
                    onClick = { onOpenDetails(item) }
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = Lime,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
    )
}

@Composable
private fun HeroPosterCard(
    item: CatalogItem,
    isTv: Boolean,
    layout: MovieCatLayout,
    selected: Boolean,
    progressText: String,
    onClick: () -> Unit
) {
    FocusContainer(
        isTv = isTv,
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        selected = selected,
        modifier = Modifier.width(layout.heroWidth).height(layout.heroHeight)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(PanelStrong)) {
            PosterArtwork(
                title = item.title,
                coverUrl = item.coverUrl,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            PosterGradient()
            MediaBadges(item = item)
            Column(
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(item.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(progressText, color = Color.White.copy(alpha = 0.78f), fontSize = 11.sp)
                ProgressRail(item = item, layout = layout)
            }
        }
    }
}

@Composable
private fun WidePosterCard(
    item: CatalogItem,
    isTv: Boolean,
    layout: MovieCatLayout,
    selected: Boolean,
    onClick: () -> Unit
) {
    FocusContainer(
        isTv = isTv,
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        selected = selected,
        modifier = Modifier.width(layout.recommendationWidth).height(layout.recommendationHeight)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(PanelStrong)) {
            PosterArtwork(title = item.title, coverUrl = item.coverUrl, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            PosterGradient()
            MediaBadges(item = item, compact = true)
            Column(
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    item.title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RecommendationSkeletonCard(index: Int, layout: MovieCatLayout) {
    Box(
        modifier = Modifier
            .width(layout.recommendationWidth)
            .height(layout.recommendationHeight)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.04f))
                )
            )
            .border(1.dp, Stroke, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.10f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .width((layout.recommendationWidth.value * 0.46f + index * 4f).dp)
                .height(8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.12f))
        )
    }
}

@Composable
private fun BoxScope.MediaBadges(
    item: CatalogItem,
    compact: Boolean = false
) {
    Badge(
        text = ratingLabel(item) ?: "0.0",
        color = Color(0xC600B9C8),
        modifier = Modifier.align(Alignment.TopStart).padding(if (compact) 5.dp else 8.dp)
    )
    Badge(
        text = item.cardSourceLabel(),
        color = Color(0xB4895800),
        textColor = Gold,
        modifier = Modifier.align(Alignment.TopEnd).padding(if (compact) 5.dp else 8.dp)
    )
}

@Composable
private fun FavoriteStarIndicator(isFavorite: Boolean, compact: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(if (compact) 24.dp else 32.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = if (isFavorite) 0.58f else 0.34f))
            .border(1.dp, if (isFavorite) Gold.copy(alpha = 0.82f) else Color.White.copy(alpha = 0.22f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
            contentDescription = null,
            tint = if (isFavorite) Gold else Color.White.copy(alpha = 0.72f),
            modifier = Modifier.size(if (compact) 15.dp else 19.dp)
        )
    }
}

@Composable
private fun Badge(text: String, color: Color, textColor: Color = Color.White, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .widthIn(min = 48.dp)
            .heightIn(min = 24.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(color)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = textColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PosterGradient() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.82f)
                    )
                )
            )
    )
}

@Composable
private fun ProgressRail(item: CatalogItem, layout: MovieCatLayout) {
    val value = remember(item.id) { ((item.title.hashCode() and Int.MAX_VALUE) % 70 + 10) / 100f }
    Box(modifier = Modifier.width(layout.heroWidth * 0.56f).height(3.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.22f))) {
        Box(modifier = Modifier.fillMaxWidth(value).fillMaxHeight().background(Accent))
    }
}

@Composable
private fun PosterSkeleton(index: Int, large: Boolean, layout: MovieCatLayout) {
    Box(
        modifier = Modifier
            .width(if (large) layout.heroWidth else layout.wideWidth)
            .height(if (large) layout.heroHeight else layout.wideHeight)
            .clip(RoundedCornerShape(if (large) 18.dp else 14.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.04f))
                )
            )
            .border(1.dp, Stroke, RoundedCornerShape(if (large) 18.dp else 14.dp))
            .padding(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(if (large) 52.dp else 38.dp)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.10f))
        )
        Box(modifier = Modifier.align(Alignment.BottomStart).width(76.dp + (index * 5).dp).height(8.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.12f)))
    }
}

@Composable
private fun SearchExperience(
    state: MovieCatUiState,
    isTv: Boolean,
    layout: MovieCatLayout,
    favoriteKeys: Set<String>,
    favoriteKeyFor: (CatalogItem) -> String,
    onDismiss: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onSelectSource: (SourceItem) -> Unit,
    onOpenDetails: (CatalogItem) -> Unit,
    onToggleFavorite: (CatalogItem) -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            AppBackdrop(items = state.featuredItems + state.catalogItems + state.searchResults, layout = layout)
            if (state.searchResults.isNotEmpty() && !state.isSearching) {
                SearchResultsSurface(
                    state = state,
                    isTv = isTv,
                    layout = layout,
                    favoriteKeys = favoriteKeys,
                    favoriteKeyFor = favoriteKeyFor,
                    onDismiss = onDismiss,
                    onOpenDetails = onOpenDetails,
                    onToggleFavorite = onToggleFavorite
                )
            } else {
                SearchInputSurface(
                    state = state,
                    isTv = isTv,
                    layout = layout,
                    onDismiss = onDismiss,
                    onQueryChange = onQueryChange,
                    onSearch = onSearch,
                    onClear = onClear,
                    onSelectSource = onSelectSource
                )
            }
        }
    }
}

@Composable
private fun SearchInputSurface(
    state: MovieCatUiState,
    isTv: Boolean,
    layout: MovieCatLayout,
    onDismiss: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onSelectSource: (SourceItem) -> Unit
) {
    val suggestions = remember(state.history, state.featuredItems, state.catalogItems) {
        (state.history.map { it.title } + state.featuredItems.map { it.title } + state.catalogItems.map { it.title })
            .filter { it.isNotBlank() }
            .distinct()
            .take(8)
    }
    @Composable
    fun SearchCenterContent(modifier: Modifier = Modifier) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(layout.searchGap)
        ) {
            Text(
                "全网搜索 一搜即看",
                color = Accent,
                fontSize = layout.searchTitleSize.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.shadow(18.dp)
            )
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = { Text("搜索电影、电视剧、综艺、动漫", color = Color.White.copy(alpha = 0.36f)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.82f)) },
                modifier = Modifier
                    .fillMaxWidth(if (layout.compact) 1f else 0.88f)
                    .height(layout.searchInputHeight)
                    .border(1.5.dp, Accent.copy(alpha = 0.9f), RoundedCornerShape(16.dp))
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Mic, contentDescription = null, tint = Accent)
                Text("支持语音搜索，按遥控器麦克风键说话", color = Color.White.copy(alpha = 0.72f))
            }
            TvKeyboard(
                query = state.searchQuery,
                isTv = isTv,
                layout = layout,
                isSearching = state.isSearching,
                onQueryChange = onQueryChange,
                onClear = onClear,
                onSearch = onSearch
            )
            Text("提示：支持首字母搜索，如“庆余年”输入 QYN", color = Color.White.copy(alpha = 0.58f))
        }
    }

    if (layout.compact) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(layout.searchPadding),
            verticalArrangement = Arrangement.spacedBy(layout.searchGap),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SearchLogo(onDismiss = onDismiss, layout = layout)
            SearchCenterContent(modifier = Modifier.fillMaxWidth())
            GlassPanel(modifier = Modifier.fillMaxWidth()) {
                SearchHistoryPanel(suggestions = suggestions, layout = layout, onPick = onQueryChange, onClear = onClear)
                Spacer(modifier = Modifier.height(layout.searchGap))
                SearchSourcePanel(state = state, layout = layout, onSelectSource = onSelectSource)
            }
            RankingPanel(
                modifier = Modifier.fillMaxWidth(),
                items = state.featuredItems.ifEmpty { state.catalogItems },
                layout = layout
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxSize().padding(layout.searchPadding),
            horizontalArrangement = Arrangement.spacedBy(layout.searchGap)
        ) {
            Column(modifier = Modifier.width(layout.searchSideWidth).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(layout.searchGap)) {
                SearchLogo(onDismiss = onDismiss, layout = layout)
                GlassPanel(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    SearchHistoryPanel(suggestions = suggestions, layout = layout, onPick = onQueryChange, onClear = onClear)
                    Spacer(modifier = Modifier.height(layout.searchGap))
                    SearchSourcePanel(state = state, layout = layout, onSelectSource = onSelectSource)
                }
            }

            SearchCenterContent(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(top = layout.searchTopOffset)
            )

            RankingPanel(
                modifier = Modifier.width(layout.searchRankWidth).fillMaxHeight().padding(top = layout.searchTopOffset),
                items = state.featuredItems.ifEmpty { state.catalogItems },
                layout = layout
            )
        }
    }
}

@Composable
private fun SearchLogo(onDismiss: () -> Unit, layout: MovieCatLayout) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Movie", color = Color.White, fontSize = if (layout.compact) 30.sp else 34.sp, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic)
            Text("Cat", color = Accent, fontSize = if (layout.compact) 30.sp else 34.sp, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic)
        }
        FocusContainer(isTv = true, onClick = onDismiss, shape = CircleShape, modifier = Modifier.size(if (layout.compact) 42.dp else 46.dp)) {
            Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = null, tint = Color.White)
            }
        }
    }
}

@Composable
private fun SearchHistoryPanel(suggestions: List<String>, layout: MovieCatLayout, onPick: (String) -> Unit, onClear: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.History, contentDescription = null, tint = Lime)
            Text("搜索历史", color = Lime, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        OutlinedButton(onClick = onClear, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("清除")
        }
    }
    Spacer(Modifier.height(layout.searchGap))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(layout.rowGap), verticalArrangement = Arrangement.spacedBy(layout.rowGap)) {
        if (suggestions.isEmpty()) {
            Text("暂无搜索记录，先试试热门内容。", color = Muted)
        } else {
            suggestions.forEachIndexed { index, title ->
                SearchChip(title = title, selected = index == 0, layout = layout, onClick = { onPick(title) })
            }
        }
    }
}

@Composable
private fun SearchChip(title: String, selected: Boolean, layout: MovieCatLayout, onClick: () -> Unit) {
    FocusContainer(
        isTv = true,
        onClick = onClick,
        shape = RoundedCornerShape(26.dp),
        selected = selected,
        modifier = Modifier.height(if (layout.compact) 40.dp else 44.dp)
    ) {
        Box(
            modifier = Modifier
                .background(if (selected) Color(0x5521F2EF) else Color.White.copy(alpha = 0.10f))
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SearchSourcePanel(state: MovieCatUiState, layout: MovieCatLayout, onSelectSource: (SourceItem) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Layers, contentDescription = null, tint = Lime)
            Text("搜索源", color = Lime, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        OutlinedButton(onClick = {}, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
            Icon(Icons.Outlined.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("切换源")
        }
    }
    Spacer(Modifier.height(layout.rowGap))
    SourceRow(label = "聚合全网", subLabel = "解析出的 Box 站点全部查询", selected = true, accent = Accent, layout = layout, onClick = {})
    (state.discoveredSources.ifEmpty { state.sources }).take(6).forEachIndexed { index, source ->
        SourceRow(
            label = source.label,
            subLabel = source.kind.name,
            selected = source.id == state.selectedSourceId,
            accent = SourceAccent[index % SourceAccent.size],
            layout = layout,
            onClick = { onSelectSource(source) }
        )
    }
}

@Composable
private fun SourceRow(label: String, subLabel: String, selected: Boolean, accent: Color, layout: MovieCatLayout, onClick: () -> Unit) {
    FocusContainer(
        isTv = true,
        onClick = onClick,
        shape = RoundedCornerShape(13.dp),
        selected = selected,
        modifier = Modifier.fillMaxWidth().height(if (layout.compact) 54.dp else 58.dp).padding(vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().background(if (selected) Color(0x4321F2EF) else Color.Transparent).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                Text(label.take(1), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(label, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subLabel, color = Muted, fontSize = 12.sp, maxLines = 1)
            }
            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = if (selected) Accent else Color.White.copy(alpha = 0.35f))
        }
    }
}

@Composable
private fun TvKeyboard(
    query: String,
    isTv: Boolean,
    layout: MovieCatLayout,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit
) {
    val rows = listOf(
        listOf("A", "B", "C", "D", "E", "F", "G"),
        listOf("H", "I", "J", "K", "L", "M", "N"),
        listOf("O", "P", "Q", "R", "S", "T", "U"),
        listOf("V", "W", "X", "Y", "Z", "1", "2"),
        listOf("3", "4", "5", "6", "7", "8", "9")
    )
    Column(verticalArrangement = Arrangement.spacedBy(layout.keyboardGap), horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEachIndexed { rowIndex, keys ->
            Row(horizontalArrangement = Arrangement.spacedBy(layout.keyboardGap)) {
                keys.forEachIndexed { keyIndex, key ->
                    KeyboardKey(
                        label = key,
                        selected = rowIndex == 0 && keyIndex == 0,
                        isTv = isTv,
                        layout = layout,
                        onClick = { onQueryChange(query + key) }
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(layout.keyboardGap)) {
            KeyboardKey(label = "清空", wide = true, icon = Icons.Outlined.DeleteOutline, isTv = isTv, layout = layout, onClick = onClear)
            KeyboardKey(label = "0", isTv = isTv, layout = layout, onClick = { onQueryChange(query + "0") })
            KeyboardKey(label = "退格", wide = true, icon = Icons.Outlined.Backspace, isTv = isTv, layout = layout, onClick = { onQueryChange(query.dropLast(1)) })
            KeyboardKey(label = if (isSearching) "搜索中" else "搜索", wide = true, accent = true, isTv = isTv, layout = layout, onClick = onSearch)
        }
    }
}

@Composable
private fun KeyboardKey(
    label: String,
    isTv: Boolean,
    layout: MovieCatLayout,
    onClick: () -> Unit,
    selected: Boolean = false,
    wide: Boolean = false,
    accent: Boolean = false,
    icon: ImageVector? = null
) {
    FocusContainer(
        isTv = isTv,
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        selected = selected || accent,
        modifier = Modifier.width(if (wide) layout.keyboardWideKeyWidth else layout.keyboardKeyWidth).height(layout.keyboardKeyHeight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(if (accent) Color(0x4421F2EF) else Color.White.copy(alpha = 0.10f)),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.let {
                Icon(it, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(label, color = if (accent) Accent else Color.White, fontSize = if (wide || layout.compact) 20.sp else 24.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun RankingPanel(modifier: Modifier, items: List<CatalogItem>, layout: MovieCatLayout) {
    GlassPanel(modifier = modifier) {
        RankingBlock(title = "热搜榜", items = items.take(5), layout = layout)
        Spacer(Modifier.height(layout.searchGap))
        RankingBlock(title = "电影榜", items = items.drop(5).take(5).ifEmpty { items.take(5) }, layout = layout)
        Spacer(Modifier.height(layout.searchGap))
        RankingBlock(title = "综艺榜", items = items.drop(10).take(5).ifEmpty { items.take(5) }, layout = layout)
    }
}

@Composable
private fun RankingBlock(title: String, items: List<CatalogItem>, layout: MovieCatLayout) {
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Lime, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("更多", color = Color.White)
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Color.White)
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(layout.rowGap)) {
            if (items.isEmpty()) {
                items(5) { PosterSkeleton(index = it, large = false, layout = layout) }
            } else {
                items(items.take(5), key = { "rank-${title}-${it.id}" }) { item ->
                    Box(modifier = Modifier.width(layout.rankingPosterWidth)) {
                        PosterArtwork(
                            title = item.title,
                            coverUrl = item.coverUrl,
                            modifier = Modifier.width(layout.rankingPosterWidth).height(layout.rankingPosterHeight).clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Text(item.title, color = Color.White, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = layout.rankingPosterHeight + 6.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchResultsSurface(
    state: MovieCatUiState,
    isTv: Boolean,
    layout: MovieCatLayout,
    favoriteKeys: Set<String>,
    favoriteKeyFor: (CatalogItem) -> String,
    onDismiss: () -> Unit,
    onOpenDetails: (CatalogItem) -> Unit,
    onToggleFavorite: (CatalogItem) -> Unit
) {
    var selectedSource by rememberSaveable(state.searchResults) { mutableStateOf("全部") }
    val sourceCounts = remember(state.searchResults) {
        state.searchResults
            .groupBy { it.sourceLabel ?: "未知来源" }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
    }
    val visibleItems = remember(state.searchResults, selectedSource) {
        if (selectedSource == "全部") state.searchResults else state.searchResults.filter { (it.sourceLabel ?: "未知来源") == selectedSource }
    }

    val resultContent: @Composable (Modifier) -> Unit = { contentModifier ->
        Column(modifier = contentModifier) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "关键词 “${state.searchQuery.ifBlank { "全部" }}”",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = if (layout.compact) 17.sp else 20.sp
                )
            }
            Text("${visibleItems.size} 个结果", color = Accent, fontSize = if (layout.compact) 21.sp else 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = layout.searchGap))

            LazyVerticalGrid(
                columns = GridCells.Fixed(layout.resultColumns),
                horizontalArrangement = Arrangement.spacedBy(layout.resultGap),
                verticalArrangement = Arrangement.spacedBy(layout.resultGap),
                modifier = Modifier.fillMaxSize()
            ) {
                if (visibleItems.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyResultCard()
                    }
                } else {
                    items(
                        visibleItems.size,
                        key = { index -> "result-${visibleItems[index].sourceId}-${visibleItems[index].id}" }
                    ) { index ->
                        val item = visibleItems[index]
                        ResultPosterCard(
                            item = item,
                            isTv = isTv,
                            layout = layout,
                            isFavorite = favoriteKeys.contains(favoriteKeyFor(item)),
                            selected = index == 0,
                            onClick = { onOpenDetails(item) },
                            onToggleFavorite = { onToggleFavorite(item) }
                        )
                    }
                }
            }
        }
    }

    val filters: @Composable (Modifier) -> Unit = { filtersModifier ->
        Column(modifier = filtersModifier, verticalArrangement = Arrangement.spacedBy(layout.searchGap)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FocusContainer(isTv = isTv, onClick = onDismiss, shape = CircleShape, modifier = Modifier.size(if (layout.compact) 50.dp else 66.dp)) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Search, contentDescription = null, tint = Color.White, modifier = Modifier.size(if (layout.compact) 28.dp else 36.dp))
                    }
                }
                Text("搜索结果", color = Color.White, fontSize = if (layout.compact) 28.sp else 34.sp, fontWeight = FontWeight.ExtraBold)
            }
            Text("全部来源", color = Lime, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = if (layout.compact) 4.dp else 28.dp))
            SourceFilterCard(label = "全部", count = state.searchResults.size, selected = selectedSource == "全部", layout = layout, onClick = { selectedSource = "全部" })
            sourceCounts.take(8).forEach { (label, count) ->
                SourceFilterCard(label = label, count = count, selected = selectedSource == label, layout = layout, onClick = { selectedSource = label })
            }
        }
    }

    if (layout.compact) {
        Column(
            modifier = Modifier.fillMaxSize().padding(layout.searchPadding),
            verticalArrangement = Arrangement.spacedBy(layout.searchGap)
        ) {
            filters(Modifier.fillMaxWidth().heightIn(max = 310.dp).verticalScroll(rememberScrollState()))
            resultContent(Modifier.fillMaxWidth().weight(1f))
        }
    } else {
        Row(modifier = Modifier.fillMaxSize().padding(layout.searchPadding), horizontalArrangement = Arrangement.spacedBy(layout.searchGap)) {
            filters(Modifier.width(layout.resultSideWidth).fillMaxHeight())

            resultContent(Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun SourceFilterCard(label: String, count: Int, selected: Boolean, layout: MovieCatLayout, onClick: () -> Unit) {
    FocusContainer(
        isTv = true,
        onClick = onClick,
        selected = selected,
        shape = RoundedCornerShape(15.dp),
        modifier = Modifier.fillMaxWidth().height(if (layout.compact) 58.dp else 72.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().background(if (selected) Color(0x6600DCEB) else Color.White.copy(alpha = 0.07f)).padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = Color.White, fontSize = 19.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(count.toString(), color = Color.White.copy(alpha = 0.80f), fontSize = 19.sp)
        }
    }
}

@Composable
private fun ResultPosterCard(
    item: CatalogItem,
    isTv: Boolean,
    layout: MovieCatLayout,
    isFavorite: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    FocusContainer(
        isTv = isTv,
        onClick = onClick,
        onLongClick = onToggleFavorite,
        shape = RoundedCornerShape(18.dp),
        selected = selected,
        modifier = Modifier.height(layout.resultCardHeight)
    ) {
        Column(modifier = Modifier.fillMaxSize().background(PanelStrong)) {
            Box(modifier = Modifier.fillMaxWidth().height(layout.resultPosterHeight)) {
                PosterArtwork(title = item.title, coverUrl = item.coverUrl, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                PosterGradient()
                Row(modifier = Modifier.align(Alignment.TopStart).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Badge(item.mediaKindLabel(), color = Color(0xCC006C70), textColor = Accent)
                    Badge(ratingLabel(item) ?: "--", color = Color(0xB7895800), textColor = Gold)
                }
                FavoriteStarIndicator(isFavorite = isFavorite, compact = false, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp))
            }
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(item.year, item.area).joinToString(" · ").ifBlank { "-- · --" }, color = Muted, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun EmptyResultCard() {
    GlassPanel(modifier = Modifier.fillMaxWidth().height(180.dp)) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("没有找到匹配结果", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("可以换一个关键词，或回到搜索页重新选择来源。", color = Muted, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun DetailDialog(
    isTv: Boolean,
    layout: MovieCatLayout,
    item: CatalogItem?,
    source: SourceItem?,
    isLoading: Boolean,
    selectedGroupIndex: Int,
    selectedEpisodeIndex: Int,
    isFavorite: Boolean,
    onGroupSelect: (Int) -> Unit,
    onEpisodeSelect: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
    onPlaySelection: (CatalogItem, Int, Int) -> Unit,
    onToggleFavorite: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Color.Black.copy(alpha = 0.94f), modifier = Modifier.fillMaxSize()) {
            AppBackdrop(items = listOfNotNull(item), layout = layout)
            Box(modifier = Modifier.fillMaxSize().padding(if (layout.compact) 18.dp else layout.searchPadding)) {
                if (isLoading || item == null) {
                    GlassPanel(modifier = Modifier.align(Alignment.Center).width(420.dp).height(220.dp)) {
                        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Accent)
                            Text("正在解析详情", color = Color.White, modifier = Modifier.padding(top = 18.dp))
                        }
                    }
                    return@Box
                }

                Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(layout.searchGap)) {
                    FocusContainer(isTv = isTv, onClick = {}, shape = RoundedCornerShape(24.dp), selected = true, modifier = Modifier.width(if (layout.tvLike) layout.heroWidth * 1.35f else 300.dp).fillMaxHeight(0.88f)) {
                        Box(modifier = Modifier.fillMaxSize().background(PanelStrong)) {
                            PosterArtwork(title = item.title, coverUrl = item.coverUrl, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            PosterGradient()
                            Row(modifier = Modifier.align(Alignment.TopStart).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Badge(item.mediaKindLabel(), color = Color(0xCC006C70), textColor = Accent)
                                Badge(ratingLabel(item) ?: "--", color = Color(0xB7895800), textColor = Gold)
                            }
                        }
                    }

                    GlassPanel(modifier = Modifier.weight(1f).fillMaxHeight(0.88f)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOfNotNull(item.year, item.area, item.tags, item.sourceLabel ?: source?.label).joinToString(" · "),
                                    color = Accent,
                                    fontSize = 18.sp,
                                    modifier = Modifier.padding(top = 10.dp)
                                )
                            }
                            FocusGlowButton(
                                label = if (isFavorite) "已收藏" else "收藏",
                                icon = Icons.Outlined.StarBorder,
                                isTv = isTv,
                                layout = layout,
                                accent = if (isFavorite) Gold else Accent,
                                onClick = onToggleFavorite
                            )
                        }

                        Text(
                            text = item.description ?: "暂无简介。当前内容来自真实解析源，详情和播放地址会按所选来源继续解析。",
                            color = Color.White.copy(alpha = 0.78f),
                            fontSize = 18.sp,
                            lineHeight = 28.sp,
                            modifier = Modifier.padding(top = 26.dp)
                        )

                        Spacer(Modifier.height(28.dp))
                        Text("线路", color = Lime, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp)) {
                            if (item.playlists.isEmpty()) {
                                Badge("暂无播放线路", color = Color.White.copy(alpha = 0.10f), textColor = Muted)
                            } else {
                                item.playlists.forEachIndexed { index, group ->
                                    SearchChip(title = group.name, selected = selectedGroupIndex == index, layout = layout, onClick = { onGroupSelect(index) })
                                }
                            }
                        }

                        Spacer(Modifier.height(24.dp))
                        Text("选集", color = Lime, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp).heightIn(max = 220.dp)) {
                            item.playlists.getOrNull(selectedGroupIndex)?.episodes?.forEachIndexed { index, episode ->
                                SearchChip(
                                    title = episode.name,
                                    selected = selectedEpisodeIndex == index,
                                    layout = layout,
                                    onClick = { onEpisodeSelect(selectedGroupIndex, index) }
                                )
                            }
                        }

                        Spacer(Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            FocusGlowButton(
                                label = "立即播放",
                                icon = Icons.Outlined.PlayArrow,
                                isTv = isTv,
                                layout = layout,
                                accent = Accent,
                                onClick = {
                                    onPlaySelection(item, selectedGroupIndex, selectedEpisodeIndex)
                                }
                            )
                            OutlinedButton(onClick = onDismiss, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                                Text("返回")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceCenterDialog(
    state: MovieCatUiState,
    layout: MovieCatLayout,
    onDismiss: () -> Unit,
    onSelectSource: (SourceItem) -> Unit,
    onImportSource: (SourceItem) -> Unit,
    onAddSource: (String, String) -> Unit
) {
    var label by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }

    @Composable
    fun SavedSources(modifier: Modifier = Modifier) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(layout.rowGap)) {
            Text("已保存接口", color = Lime, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            state.sources.forEach { source ->
                SourceRow(
                    label = source.label,
                    subLabel = source.url,
                    selected = source.id == state.selectedSourceId,
                    accent = Accent,
                    layout = layout,
                    onClick = {
                        onSelectSource(source)
                        onDismiss()
                    }
                )
            }
        }
    }

    @Composable
    fun DiscoveredSources(modifier: Modifier = Modifier) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(layout.rowGap)) {
            Text("从配置发现的站点", color = Lime, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            state.discoveredSources.take(10).forEach { source ->
                SourceRow(
                    label = source.label,
                    subLabel = source.kind.name,
                    selected = false,
                    accent = Gold,
                    layout = layout,
                    onClick = { onImportSource(source) }
                )
            }
        }
    }

    @Composable
    fun ManualSourceForm(modifier: Modifier = Modifier) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(layout.rowGap)) {
            Text("手动添加", color = Lime, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("名称") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("接口地址") }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    onAddSource(label, url)
                    label = ""
                    url = ""
                },
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black)
            ) {
                Text("保存并加载")
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Color.Black.copy(alpha = 0.94f), modifier = Modifier.fillMaxSize()) {
            AppBackdrop(items = state.featuredItems + state.catalogItems, layout = layout)
            GlassPanel(modifier = Modifier.fillMaxSize().padding(if (layout.compact) 18.dp else layout.searchPadding)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("配置中心", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                    OutlinedButton(onClick = onDismiss, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                        Text("关闭")
                    }
                }
                Spacer(Modifier.height(22.dp))
                if (layout.compact) {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(layout.searchGap)
                    ) {
                        SavedSources()
                        DiscoveredSources()
                        ManualSourceForm()
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(layout.searchGap), modifier = Modifier.fillMaxSize()) {
                        SavedSources(modifier = Modifier.weight(1f))
                        DiscoveredSources(modifier = Modifier.weight(1f))
                        ManualSourceForm(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Panel)
            .border(1.dp, Stroke, RoundedCornerShape(18.dp))
            .padding(18.dp),
        content = content
    )
}

@Composable
private fun FocusGlowButton(
    label: String,
    icon: ImageVector,
    isTv: Boolean,
    layout: MovieCatLayout,
    modifier: Modifier = Modifier,
    useFixedWidth: Boolean = true,
    onClick: () -> Unit,
    compact: Boolean = false,
    accent: Color = Accent
) {
    val sizeModifier = if (useFixedWidth) {
        Modifier
            .width(if (compact) layout.actionWidth * 0.72f else layout.actionWidth)
            .height(if (compact) 32.dp else layout.actionHeight)
    } else {
        Modifier
            .fillMaxWidth()
            .height(if (compact) 32.dp else layout.actionHeight)
    }
    FocusContainer(
        isTv = isTv,
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        focusedBorder = accent,
        modifier = modifier.then(sizeModifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = 0.08f))
                .padding(horizontal = if (useFixedWidth) 14.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (useFixedWidth) 8.dp else 4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(if (compact) 16.dp else if (useFixedWidth) 20.dp else 18.dp))
            Text(
                label,
                modifier = if (useFixedWidth) Modifier else Modifier.weight(1f, fill = false),
                color = if (compact) Gold else Color.White,
                fontSize = if (compact) 12.sp else if (useFixedWidth) 13.sp else 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FocusContainer(
    isTv: Boolean,
    onClick: () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    focusedBorder: Color = Accent,
    selected: Boolean = false,
    showIdleBorder: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var longClickJob by remember { mutableStateOf<Job?>(null) }
    var longClickConsumed by remember { mutableStateOf(false) }
    val active = focused || selected
    val focusAmount by animateFloatAsState(
        targetValue = when {
            focused -> 1f
            selected -> 0.72f
            else -> 0f
        },
        label = "focus_glow"
    )
    val scale by animateFloatAsState(
        targetValue = when {
            isTv && focused -> 1.055f
            selected -> 1.015f
            else -> 1f
        },
        label = "focus_scale"
    )
    val borderColor = when {
        focused -> focusedBorder
        selected -> focusedBorder.copy(alpha = 0.88f)
        !showIdleBorder -> Color.Transparent
        else -> Stroke
    }
    val longPressModifier = if (onLongClick == null) {
        Modifier
    } else {
        Modifier.onPreviewKeyEvent { event ->
            val isSelectKey = event.key == Key.DirectionCenter || event.key == Key.Enter
            if (!isSelectKey) {
                false
            } else {
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        if (longClickJob == null) {
                            longClickConsumed = false
                            longClickJob = scope.launch {
                                delay(1_200)
                                longClickConsumed = true
                                longClickJob = null
                                onLongClick()
                            }
                        }
                        false
                    }
                    KeyEventType.KeyUp -> {
                        val consumed = longClickConsumed
                        longClickJob?.cancel()
                        longClickJob = null
                        longClickConsumed = false
                        consumed
                    }
                    else -> false
                }
            }
        }
    }
    DisposableEffect(onLongClick) {
        onDispose {
            longClickJob?.cancel()
            longClickJob = null
        }
    }
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = if (active) 28f * focusAmount else 0f
            }
            .then(longPressModifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled = isTv)
            .clickable { onClick() }
    ) {
        if (focusAmount > 0.01f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        alpha = focusAmount
                        scaleX = if (focused) 1.12f else 1.07f
                        scaleY = if (focused) 1.14f else 1.08f
                    }
                    .clip(shape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                focusedBorder.copy(alpha = 0.46f),
                                focusedBorder.copy(alpha = 0.16f),
                                Color.Transparent
                            ),
                            radius = 430f
                        )
                    )
            )
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape),
            content = content
        )

        if (focusAmount > 0.01f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(Color(0xFF003D45).copy(alpha = 0.20f * focusAmount))
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(2.dp)
                    .clip(shape)
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.22f * focusAmount)), shape)
            )
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .border(BorderStroke(if (active) 2.dp else 1.dp, borderColor), shape)
        )
    }
}

private enum class BrowsePrimaryCategory(val key: String, val label: String) {
    Home("home", "首页"),
    Movie("movie", "电影"),
    Series("series", "剧集"),
    Variety("variety", "综艺"),
    Animation("animation", "动漫"),
    Documentary("documentary", "纪录片"),
    Sports("sports", "体育"),
    Kids("kids", "少儿")
}

private data class HomeAction(
    val label: String,
    val shortLabel: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

private data class LanStatusDisplay(
    val label: String,
    val subLabel: String?,
    val accent: Color
)

private val SourceAccent = listOf(
    Color(0xFF4FC3F7),
    Color(0xFF26A69A),
    Color(0xFF7E57C2),
    Color(0xFFFFB74D),
    Color(0xFFFF7043)
)

private fun LibraryEntry.toCatalogItem(): CatalogItem {
    return CatalogItem(
        id = entryKey,
        title = title,
        coverUrl = coverUrl,
        description = note ?: "从上次观看的位置继续播放。",
        year = null,
        area = null,
        tags = note,
        detailToken = playUrl,
        playlists = listOf(
            PlaylistGroup(
                name = "继续观看",
                episodes = listOf(Episode(name = note ?: "继续播放", url = playUrl))
            )
        ),
        sourceId = sourceId,
        sourceLabel = null
    )
}

private fun LibraryEntry.resumeHint(): String {
    if (lastPositionMs <= 0L) {
        return note ?: "继续播放"
    }
    return "观看至 ${lastPositionMs.asClockText()}"
}

private fun Long.asClockText(): String {
    val totalSeconds = (this / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    fun two(value: Long): String = value.toString().padStart(2, '0')
    return if (hours > 0L) {
        "$hours:${two(minutes)}:${two(seconds)}"
    } else {
        "${two(minutes)}:${two(seconds)}"
    }
}

private fun ratingLabel(item: CatalogItem): String? {
    val text = listOfNotNull(item.tags, item.description).joinToString(" ")
    val match = Regex("""评分\s*([0-9]+(?:\.[0-9]+)?)""").find(text)
    return match?.groupValues?.getOrNull(1)
}

private fun CatalogItem.mediaKindLabel(): String {
    val text = listOfNotNull(title, tags, description).joinToString(" ")
    return when {
        text.contains("综艺", ignoreCase = true) -> "综艺"
        text.contains("剧", ignoreCase = true) || text.contains("tv", ignoreCase = true) -> "电视剧"
        else -> "电影"
    }
}

private fun CatalogItem.watchHint(): String {
    val episodeCount = playlists.sumOf { it.episodes.size }
    return when {
        episodeCount > 1 -> "共 $episodeCount 集"
        !sourceLabel.isNullOrBlank() -> sourceLabel
        !year.isNullOrBlank() -> year
        else -> "查看详情"
    }.orEmpty()
}

private fun String.sourceBadgeLabel(): String {
    return when {
        contains("豆瓣", ignoreCase = true) -> "豆瓣"
        length <= 4 -> this
        else -> take(4)
    }
}

private fun CatalogItem.cardSourceLabel(): String {
    return sourceLabel
        ?.takeIf { it.isNotBlank() }
        ?.sourceBadgeLabel()
        ?: mediaKindLabel()
}

private fun DeviceNetworkStatus.displayLabel(): String {
    return when {
        connected -> transportLabel
        hasInternet -> "待验证"
        else -> "离线"
    }
}

private fun WeatherStatus.displayLabel(): String {
    return when {
        isLoading -> "定位中"
        temperatureC != null -> "$temperatureC°C${condition?.let { " $it" }.orEmpty()}"
        errorMessage != null -> "天气 --"
        else -> "天气 --"
    }
}

private fun WeatherStatus.districtLabel(): String? {
    return district?.takeIf { it.isNotBlank() }
}

private fun WeatherStatus.temperatureLabel(): String {
    return when {
        isLoading -> "定位中"
        temperatureC != null -> "$temperatureC°C"
        else -> "--°C"
    }
}

private fun lanServerStatus(running: Boolean, urls: List<String>, message: String?): LanStatusDisplay {
    val isError = !running || urls.isEmpty() || message?.contains("失败") == true
    return if (isError) {
        LanStatusDisplay(label = "管理异常", subLabel = null, accent = Color(0xFFFF7A59))
    } else {
        LanStatusDisplay(
            label = "管理就绪",
            subLabel = urls.firstOrNull()?.substringAfter("//")?.substringBefore(":"),
            accent = Accent
        )
    }
}

private fun currentClockText(): String {
    return LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
}
