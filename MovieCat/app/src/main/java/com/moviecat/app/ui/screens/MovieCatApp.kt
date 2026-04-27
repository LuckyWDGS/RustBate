package com.moviecat.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moviecat.app.viewmodel.MovieCatViewModel

@Composable
fun MovieCatApp(viewModel: MovieCatViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isTv = rememberIsTelevision()
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onFineLocationPermissionResult(granted)
    }

    LaunchedEffect(context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            viewModel.onFineLocationPermissionResult(true)
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    HomeScreen(
        state = state,
        isTv = isTv,
        onRefresh = viewModel::refresh,
        onHomeVisible = viewModel::refreshHomeWeather,
        onSearchQueryChange = viewModel::updateSearchQuery,
        onSearch = viewModel::search,
        onClearSearch = viewModel::clearSearch,
        onBrowsePrimaryCategory = viewModel::browsePrimaryCategory,
        onBrowseCategory = viewModel::browseCategory,
        onSelectSource = viewModel::selectSource,
        onAddSource = viewModel::addSource,
        onImportSource = viewModel::importDiscoveredSource,
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
