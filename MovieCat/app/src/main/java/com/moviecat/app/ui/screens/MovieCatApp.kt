package com.moviecat.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moviecat.app.viewmodel.MovieCatViewModel

@Composable
fun MovieCatApp(viewModel: MovieCatViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isTv = rememberIsTelevision()

    HomeScreen(
        state = state,
        isTv = isTv,
        onRefresh = viewModel::refresh,
        onSearchQueryChange = viewModel::updateSearchQuery,
        onSearch = viewModel::search,
        onClearSearch = viewModel::clearSearch,
        onBrowsePrimaryCategory = viewModel::browsePrimaryCategory,
        onBrowseCategory = viewModel::browseCategory,
        onSelectSource = viewModel::selectSource,
        onAddSource = viewModel::addSource,
        onImportSource = viewModel::importDiscoveredSource,
        onStartLanServer = viewModel::startLanServer,
        onStopLanServer = viewModel::stopLanServer,
        onOpenDetails = viewModel::openDetails,
        onUpdateDetailSelection = viewModel::updateDetailSelection,
        onCloseDetails = viewModel::closeDetails,
        onPlaySelection = viewModel::playSelection,
        onToggleFavorite = viewModel::toggleFavorite,
        onDismissPlayer = viewModel::closePlayer,
        onSelectEpisode = viewModel::updateEpisode
    )
}

@Composable
private fun rememberIsTelevision(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK) ==
            android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    }
}
