package com.moviecat.app.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.moviecat.app.data.model.PlayerSession
import com.moviecat.app.util.debugSummary
import com.moviecat.app.util.previewForLog

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerDialog(
    isTv: Boolean,
    session: PlayerSession,
    onDismiss: (Long) -> Unit,
    onSelectEpisode: (Int, Int) -> Unit
) {
    val tag = "MovieCatPlayer"
    val context = LocalContext.current
    val currentEpisode = session.currentEpisode
    val currentUrl = currentEpisode?.url.orEmpty()
    val currentHeaders = currentEpisode?.headers.orEmpty()
    val adBlockEnabled = session.source.settings.blockVideoAds
    val adSkipSeconds = session.source.settings.normalizedAdSkipSeconds()
    var isPlaying by remember(currentUrl, currentHeaders) { mutableStateOf(true) }
    var currentPosition by remember(currentUrl, currentHeaders) { mutableLongStateOf(0L) }
    var duration by remember(currentUrl, currentHeaders) { mutableLongStateOf(0L) }
    var showAdvanced by remember { mutableStateOf(false) }
    var autoAdSkipped by remember(currentUrl, currentHeaders) { mutableStateOf(false) }
    val previousTarget = remember(session.item, session.selectedGroupIndex, session.selectedEpisodeIndex) {
        findAdjacentEpisodeTarget(
            session = session,
            direction = -1
        )
    }
    val nextTarget = remember(session.item, session.selectedGroupIndex, session.selectedEpisodeIndex) {
        findAdjacentEpisodeTarget(
            session = session,
            direction = 1
        )
    }

    val player = remember(currentUrl, currentHeaders) {
        Log.d(
            tag,
            "create player source=${session.source.label} episode=${currentEpisode?.debugSummary()} adBlock=$adBlockEnabled skip=$adSkipSeconds"
        )
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(currentHeaders)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(currentUrl))
                prepare()
                playWhenReady = true
            }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                Log.d(tag, "onIsPlayingChanged playing=$playing url=${currentUrl.previewForLog()}")
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                Log.d(
                    tag,
                    "onPlaybackStateChanged state=${playbackStateLabel(playbackState)} pos=${player.currentPosition} duration=${player.duration}"
                )
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e(
                    tag,
                    "onPlayerError source=${session.source.label} url=${currentUrl.previewForLog()} headers=${currentHeaders.keys.joinToString()}",
                    error
                )
            }

            override fun onEvents(player: Player, events: Player.Events) {
                currentPosition = player.currentPosition
                duration = player.duration.coerceAtLeast(0L)
                if (
                    adBlockEnabled &&
                    !autoAdSkipped &&
                    player.currentPosition <= 2_000L &&
                    player.playbackState == Player.STATE_READY
                ) {
                    val seekTarget = (adSkipSeconds * 1000L).coerceAtMost(player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE)
                    if (seekTarget > 5_000L) {
                        Log.d(tag, "autoAdSkip target=$seekTarget source=${session.source.label} url=${currentUrl.previewForLog()}")
                        player.seekTo(seekTarget)
                        autoAdSkipped = true
                    }
                }
                if (player.playbackState == Player.STATE_ENDED) {
                    Log.d(tag, "playback ended source=${session.source.label} next=${nextTarget != null}")
                    nextTarget?.let { (groupIndex, episodeIndex) ->
                        onSelectEpisode(groupIndex, episodeIndex)
                    }
                }
            }
        }
        player.addListener(listener)
        onDispose {
            Log.d(tag, "release player source=${session.source.label} pos=${player.currentPosition} url=${currentUrl.previewForLog()}")
            player.removeListener(listener)
            player.release()
        }
    }

    Dialog(
        onDismissRequest = { onDismiss(player.currentPosition) },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.96f))
        ) {
            VideoSurface(player = player)

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(if (isTv) 24.dp else 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CompactControlBar(
                    title = session.item.title,
                    groupName = session.currentGroup?.name ?: "默认线路",
                    episodeName = session.currentEpisode?.name ?: "未选择",
                    isPlaying = isPlaying,
                    currentPosition = currentPosition,
                    duration = duration,
                    hasPrevious = previousTarget != null,
                    hasNext = nextTarget != null,
                    onPlayPause = {
                        if (player.isPlaying) player.pause() else player.play()
                    },
                    onSeekBack = {
                        player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                    },
                    onSeekForward = {
                        player.seekTo((player.currentPosition + 30_000L).coerceAtMost(player.duration.coerceAtLeast(0L)))
                    },
                    onPreviousEpisode = {
                        previousTarget?.let { (groupIndex, episodeIndex) ->
                            onSelectEpisode(groupIndex, episodeIndex)
                        }
                    },
                    onNextEpisode = {
                        nextTarget?.let { (groupIndex, episodeIndex) ->
                            onSelectEpisode(groupIndex, episodeIndex)
                        }
                    },
                    onToggleAdvanced = { showAdvanced = !showAdvanced },
                    onDismiss = { onDismiss(player.currentPosition) }
                )

                if (showAdvanced) {
                    AdvancedPlayerPanel(
                        session = session,
                        adBlockEnabled = adBlockEnabled,
                        adSkipSeconds = adSkipSeconds,
                        onSelectEpisode = onSelectEpisode
                    )
                }
            }
        }
    }
}

private fun playbackStateLabel(state: Int): String {
    return when (state) {
        Player.STATE_IDLE -> "IDLE"
        Player.STATE_BUFFERING -> "BUFFERING"
        Player.STATE_READY -> "READY"
        Player.STATE_ENDED -> "ENDED"
        else -> state.toString()
    }
}

@Composable
private fun VideoSurface(player: ExoPlayer) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                this.player = player
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun CompactControlBar(
    title: String,
    groupName: String,
    episodeName: String,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onPreviousEpisode: () -> Unit,
    onNextEpisode: () -> Unit,
    onToggleAdvanced: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xD9151820)),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "$groupName / $episodeName",
                        color = Color.White.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    text = "${formatMillis(currentPosition)} / ${formatMillis(duration)}",
                    color = Color.White.copy(alpha = 0.86f),
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onPreviousEpisode, enabled = hasPrevious) { Text("上一集") }
                Button(onClick = onPlayPause) { Text(if (isPlaying) "暂停" else "播放") }
                OutlinedButton(onClick = onNextEpisode, enabled = hasNext) { Text("下一集") }
                OutlinedButton(onClick = onSeekBack) { Text("后退 10 秒") }
                OutlinedButton(onClick = onSeekForward) { Text("前进 30 秒") }
                OutlinedButton(onClick = onToggleAdvanced) { Text("高级控制") }
                OutlinedButton(onClick = onDismiss) { Text("退出") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvancedPlayerPanel(
    session: PlayerSession,
    adBlockEnabled: Boolean,
    adSkipSeconds: Int,
    onSelectEpisode: (Int, Int) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xE3191D24)),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("高级控制", color = Color.White, style = MaterialTheme.typography.titleLarge)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AssistChip(
                    onClick = {},
                    label = { Text("播放器 Exo") },
                    colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF1E242E), labelColor = Color.White)
                )
                AssistChip(
                    onClick = {},
                    label = { Text("请求头 ${if (session.currentEpisode?.headers?.isNotEmpty() == true) "已带入" else "默认"}") },
                    colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF1E242E), labelColor = Color.White)
                )
                AssistChip(
                    onClick = {},
                    label = { Text(if (adBlockEnabled) "广告拦截 已开($adSkipSeconds 秒)" else "广告拦截 已关") },
                    colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF1E242E), labelColor = Color.White)
                )
            }

            Text("线路", color = Color.White, style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                session.item.playlists.forEachIndexed { index, group ->
                    AssistChip(
                        onClick = { onSelectEpisode(index, 0) },
                        label = { Text(group.name) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (session.selectedGroupIndex == index) Color(0xFF1C8CCF) else Color(0xFF1E242E),
                            labelColor = Color.White
                        )
                    )
                }
            }

            Text("选集", color = Color.White, style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                session.currentGroup?.episodes?.forEachIndexed { index, episode ->
                    AssistChip(
                        onClick = { onSelectEpisode(session.selectedGroupIndex, index) },
                        label = { Text(episode.name) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (session.selectedEpisodeIndex == index) Color(0xFF1C8CCF) else Color(0xFF1E242E),
                            labelColor = Color.White
                        )
                    )
                }
            }
        }
    }
}

private fun formatMillis(value: Long): String {
    if (value <= 0L) return "00:00"
    val totalSeconds = value / 1000
    val minutes = (totalSeconds / 60) % 60
    val seconds = totalSeconds % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private fun findAdjacentEpisodeTarget(
    session: PlayerSession,
    direction: Int
): Pair<Int, Int>? {
    if (direction == 0) return null
    val groups = session.item.playlists
    if (groups.isEmpty()) return null

    var groupIndex = session.selectedGroupIndex
    var episodeIndex = session.selectedEpisodeIndex + direction

    while (groupIndex in groups.indices) {
        val episodes = groups[groupIndex].episodes
        if (episodeIndex in episodes.indices) {
            return groupIndex to episodeIndex
        }
        groupIndex += direction
        if (groupIndex !in groups.indices) {
            return null
        }
        episodeIndex = if (direction > 0) 0 else groups[groupIndex].episodes.lastIndex
    }
    return null
}
