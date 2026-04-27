package com.moviecat.app.viewmodel

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.CancellationSignal
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moviecat.app.data.model.CatalogItem
import com.moviecat.app.data.model.CategoryParam
import com.moviecat.app.data.model.Episode
import com.moviecat.app.data.model.LibraryEntry
import com.moviecat.app.data.model.PlayerSession
import com.moviecat.app.data.model.SourceItem
import com.moviecat.app.data.model.SourceKind
import com.moviecat.app.data.model.SpiderMode
import com.moviecat.app.data.remote.DoubanSection
import com.moviecat.app.data.remote.LanControlServer
import com.moviecat.app.data.remote.LanControlSnapshot
import com.moviecat.app.data.remote.WeatherLocation
import com.moviecat.app.data.remote.WeatherRemoteDataSource
import com.moviecat.app.data.repository.CatalogRepository
import com.moviecat.app.data.repository.SourceDraft
import com.moviecat.app.util.debugSummary
import com.moviecat.app.util.previewForLog
import java.util.Locale
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers

data class MovieCatUiState(
    val isLoading: Boolean = false,
    val sources: List<SourceItem> = emptyList(),
    val selectedSourceId: String? = null,
    val discoveredSources: List<SourceItem> = emptyList(),
    val catalogItems: List<CatalogItem> = emptyList(),
    val featuredItems: List<CatalogItem> = emptyList(),
    val categories: List<CategoryParam> = emptyList(),
    val activeCategory: CategoryParam? = null,
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val searchResults: List<CatalogItem> = emptyList(),
    val isDetailLoading: Boolean = false,
    val detailItem: CatalogItem? = null,
    val detailSelectedGroupIndex: Int = 0,
    val detailSelectedEpisodeIndex: Int = 0,
    val favorites: List<LibraryEntry> = emptyList(),
    val history: List<LibraryEntry> = emptyList(),
    val warnings: List<String> = emptyList(),
    val errorMessage: String? = null,
    val lanServerRunning: Boolean = false,
    val lanServerUrls: List<String> = emptyList(),
    val lanServerMessage: String? = null,
    val networkStatus: DeviceNetworkStatus = DeviceNetworkStatus(),
    val weatherStatus: WeatherStatus = WeatherStatus(),
    val playerSession: PlayerSession? = null
) {
    val selectedSource: SourceItem?
        get() = sources.firstOrNull { it.id == selectedSourceId }
}

data class DeviceNetworkStatus(
    val connected: Boolean = false,
    val transportLabel: String = "离线",
    val hasInternet: Boolean = false
)

data class WeatherStatus(
    val district: String? = null,
    val temperatureC: Int? = null,
    val condition: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val updatedAt: Long = 0L
)

private data class SearchSourceResult(
    val source: SourceItem,
    val items: List<CatalogItem>,
    val warnings: List<String>,
    val errorMessage: String? = null
)

class MovieCatViewModel(application: Application) : AndroidViewModel(application) {
    private val tag = "MovieCatVM"
    private val app = application
    private val repository = CatalogRepository.create(application)
    private val weatherDataSource = WeatherRemoteDataSource()
    private val locationManager = application.getSystemService(LocationManager::class.java)
    private val connectivityManager = application.getSystemService(ConnectivityManager::class.java)
    private val _uiState = MutableStateFlow(MovieCatUiState())
    val uiState = _uiState.asStateFlow()

    private val lanControlServer = LanControlServer(
        scope = viewModelScope,
        snapshotProvider = {
            LanControlSnapshot(
                sources = _uiState.value.sources,
                selectedSourceId = _uiState.value.selectedSourceId,
                warnings = _uiState.value.warnings,
                lastError = _uiState.value.errorMessage,
                catalogCount = _uiState.value.catalogItems.size,
                featuredCount = _uiState.value.featuredItems.size,
                categories = _uiState.value.categories
            )
        },
        onUpsertSource = { draft -> upsertSourceInternal(draft) },
        onDeleteSource = { sourceId -> removeSourceInternal(sourceId) },
        onSelectSource = { sourceId -> selectSourceInternal(sourceId) }
    )

    private var autoSelected = false
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    init {
        observeNetworkStatus()
        observeSources()
        observeFavorites()
        observeHistory()
        viewModelScope.launch {
            repository.seedDefaultSourcesIfNeeded()
            startLanServer()
        }
        refreshHomeWeather()
    }

    fun refresh() {
        val source = _uiState.value.selectedSource ?: return
        val category = _uiState.value.activeCategory
        if (category != null) {
            browseCategory(category)
        } else {
            loadSource(source)
        }
    }

    fun refreshHomeWeather() {
        val current = _uiState.value
        if (current.weatherStatus.isLoading) {
            return
        }
        if (!current.networkStatus.connected) {
            _uiState.update {
                it.copy(weatherStatus = it.weatherStatus.copy(isLoading = false, errorMessage = "当前网络不可用。"))
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(weatherStatus = it.weatherStatus.copy(isLoading = true, errorMessage = null)) }
            runCatching {
                val location = currentFineWeatherLocation()
                weatherDataSource.fetchCurrentWeather(location)
            }.onSuccess { snapshot ->
                _uiState.update {
                    it.copy(
                        weatherStatus = WeatherStatus(
                            district = snapshot.district,
                            temperatureC = snapshot.temperatureC,
                            condition = snapshot.condition,
                            isLoading = false,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }.onFailure { throwable ->
            Log.w(tag, "refreshWeather failed", throwable)
                _uiState.update {
                    it.copy(
                        weatherStatus = it.weatherStatus.copy(
                            isLoading = false,
                            errorMessage = throwable.message ?: "天气刷新失败"
                        )
                    )
                }
            }
        }
    }

    fun hasFineLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    fun onFineLocationPermissionResult(granted: Boolean) {
        if (granted) {
            refreshHomeWeather()
        } else {
            _uiState.update {
                it.copy(
                    weatherStatus = it.weatherStatus.copy(
                        isLoading = false,
                        errorMessage = "精确定位权限未开启。"
                    )
                )
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun clearSearch() {
        _uiState.update { it.copy(searchQuery = "", searchResults = emptyList(), isSearching = false) }
    }

    fun browseCategory(category: CategoryParam?) {
        val source = _uiState.value.selectedSource ?: return
        if (category == null) {
            Log.d(tag, "browseCategory reset ${source.label}")
            _uiState.update { it.copy(activeCategory = null) }
            loadSource(source)
            return
        }
        viewModelScope.launch {
            Log.d(tag, "browseCategory start ${source.label}: ${category.name}/${category.value}")
            _uiState.update {
                it.copy(
                    isLoading = true,
                    activeCategory = category,
                    errorMessage = null,
                    detailItem = null
                )
            }
            try {
                val payload = repository.fetchCategory(source, category)
                Log.d(tag, "browseCategory done ${source.label}: ${payload.debugSummary()}")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        catalogItems = payload.catalogItems,
                        featuredItems = payload.featuredItems,
                        categories = if (payload.categories.isNotEmpty()) payload.categories else it.categories,
                        searchResults = emptyList(),
                        warnings = payload.warnings,
                        errorMessage = if (payload.catalogItems.isEmpty()) "当前分类没有内容。" else null
                    )
                }
            } catch (throwable: Throwable) {
                Log.e(tag, "browseCategory failed ${source.label}: ${category.name}/${category.value}", throwable)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = throwable.message ?: "分类加载失败"
                    )
                }
            }
        }
    }

    fun browsePrimaryCategory(key: String) {
        val source = _uiState.value.selectedSource
        if (key == "home") {
            if (source != null) {
                loadSource(source)
            }
            return
        }

        val state = _uiState.value
        val sourceCategory = state.categories.firstOrNull { it.matchesPrimaryKey(key) }
        if (sourceCategory != null) {
            browseCategory(sourceCategory)
            return
        }

        val doubanSection = key.toDoubanSection() ?: return
        viewModelScope.launch {
            Log.d(tag, "browsePrimaryCategory douban key=$key section=$doubanSection")
            _uiState.update {
                it.copy(
                    isLoading = true,
                    activeCategory = CategoryParam(name = key.primaryLabel(), value = "douban:$key"),
                    errorMessage = null,
                    detailItem = null
                )
            }
            try {
                val payload = repository.fetchDoubanSection(doubanSection)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        catalogItems = payload.catalogItems,
                        featuredItems = emptyList(),
                        searchResults = emptyList(),
                        warnings = payload.warnings,
                        errorMessage = if (payload.catalogItems.isEmpty()) "豆瓣接口暂时没有返回内容。" else null
                    )
                }
            } catch (throwable: Throwable) {
                Log.e(tag, "browsePrimaryCategory douban failed key=$key", throwable)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = throwable.message ?: "豆瓣数据加载失败"
                    )
                }
            }
        }
    }

    fun search() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请输入搜索关键词。") }
            return
        }
        viewModelScope.launch {
            val searchSources = searchableSources()
            if (searchSources.isEmpty()) {
                _uiState.update { it.copy(errorMessage = "当前没有可搜索的解析站点。") }
                return@launch
            }
            Log.d(tag, "search all start sources=${searchSources.size}: ${query.previewForLog(80)}")
            _uiState.update { it.copy(isSearching = true, errorMessage = null) }
            val results = searchAcrossSources(searchSources, query)
            val items = results.flatMap { it.items }.distinctBy { "${it.sourceId}|${it.detailToken ?: it.title}" }
            val failed = results.filter { it.errorMessage != null }
            val warnings = buildList {
                add("已搜索 ${searchSources.size} 个解析站点，返回 ${items.size} 条结果。")
                if (failed.isNotEmpty()) {
                    add("其中 ${failed.size} 个站点暂时失败：${failed.take(5).joinToString("、") { it.source.label }}")
                }
                results.flatMap { it.warnings }.distinct().take(6).forEach(::add)
            }
            _uiState.update {
                it.copy(
                    isSearching = false,
                    searchResults = items,
                    detailItem = null,
                    warnings = warnings,
                    errorMessage = if (items.isEmpty()) "所有解析站点都没有搜到结果。" else null
                )
            }
        }
    }

    fun selectSource(source: SourceItem) {
        Log.d(tag, "selectSource ${source.debugSummary()}")
        _uiState.update { it.copy(selectedSourceId = source.id, detailItem = null, activeCategory = null) }
        loadSource(source)
    }

    fun openDetails(item: CatalogItem) {
        val source = sourceForItem(item)
        viewModelScope.launch {
            Log.d(tag, "openDetails start ${source?.label ?: item.sourceLabel.orEmpty()}: ${item.debugSummary()}")
            _uiState.update { it.copy(isDetailLoading = true, errorMessage = null) }
            val resolvedItem = if (source == null) {
                item
            } else {
                runCatching {
                    if (item.playlists.isEmpty()) repository.resolveDetail(source, item) else item
                }.onFailure {
                    Log.e(tag, "openDetails failed ${source.label}: ${item.debugSummary()}", it)
                }.getOrElse { item }
            }
            Log.d(tag, "openDetails done ${source?.label ?: item.sourceLabel.orEmpty()}: ${resolvedItem.debugSummary()}")
            _uiState.update {
                it.copy(
                    isDetailLoading = false,
                    detailItem = resolvedItem,
                    detailSelectedGroupIndex = 0,
                    detailSelectedEpisodeIndex = 0
                )
            }
        }
    }

    fun updateDetailSelection(groupIndex: Int, episodeIndex: Int) {
        _uiState.update {
            it.copy(
                detailSelectedGroupIndex = groupIndex,
                detailSelectedEpisodeIndex = episodeIndex
            )
        }
    }

    fun closeDetails() {
        _uiState.update { it.copy(detailItem = null, isDetailLoading = false) }
    }

    fun addSource(label: String, url: String) {
        if (url.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请先输入源地址。") }
            return
        }

        viewModelScope.launch {
            upsertSourceInternal(
                SourceDraft(
                    label = label,
                    url = url,
                    kind = if (url.endsWith(".js", ignoreCase = true) || url.contains("/tv")) {
                        SourceKind.TVBOX_CONFIG
                    } else {
                        SourceKind.DIRECT
                    }
                )
            )
        }
    }

    fun importDiscoveredSource(source: SourceItem) {
        viewModelScope.launch {
            upsertSourceInternal(
                SourceDraft(
                    label = source.label,
                    url = source.url,
                    kind = source.kind,
                    settings = source.settings
                )
            )
        }
    }

    fun removeSource(sourceId: String) {
        viewModelScope.launch {
            removeSourceInternal(sourceId)
        }
    }

    fun startLanServer() {
        viewModelScope.launch {
            try {
                val urls = lanControlServer.start()
                Log.d(tag, "startLanServer done urls=${urls.joinToString()}")
                _uiState.update {
                    it.copy(
                        lanServerRunning = true,
                        lanServerUrls = urls,
                        lanServerMessage = if (urls.isEmpty()) {
                            "服务已启动，但暂时没拿到局域网地址。"
                        } else {
                            "后台管理页已就绪，可在同一局域网访问。"
                        }
                    )
                }
            } catch (error: Throwable) {
                Log.e(tag, "startLanServer failed", error)
                _uiState.update {
                    it.copy(
                        lanServerRunning = false,
                        lanServerUrls = emptyList(),
                        lanServerMessage = "管理页启动失败：${error.message}"
                    )
                }
            }
        }
    }

    fun stopLanServer() {
        lanControlServer.stop()
        Log.d(tag, "stopLanServer")
        _uiState.update {
            it.copy(
                lanServerRunning = false,
                lanServerUrls = emptyList(),
                lanServerMessage = "管理页已停止。"
            )
        }
    }

    fun play(item: CatalogItem) {
        Log.d(tag, "play default ${item.debugSummary()}")
        playSelection(item, 0, 0)
    }

    fun playSelection(item: CatalogItem, groupIndex: Int, episodeIndex: Int) {
        val source = sourceForItem(item) ?: run {
            _uiState.update { it.copy(errorMessage = "这个条目来自外部榜单，没有可直接播放的解析站点。") }
            return
        }
        viewModelScope.launch {
            Log.d(tag, "playSelection start ${source.label}: ${item.debugSummary()}, group=$groupIndex, episode=$episodeIndex")
            val resolvedItem = if (item.playlists.isEmpty()) {
                repository.resolveDetail(source, item)
            } else {
                item
            }
            if (resolvedItem.playlists.isEmpty()) {
                _uiState.update { it.copy(errorMessage = "当前条目还没有可直接播放的地址。") }
                return@launch
            }
            val hydratedItem = resolveEpisodeForItem(source, resolvedItem, groupIndex, episodeIndex)
            val targetUrl = hydratedItem.playlists.getOrNull(groupIndex)
                ?.episodes?.getOrNull(episodeIndex)?.url
            if (targetUrl.isNullOrBlank()) {
                Log.w(tag, "playSelection missing url ${source.label}: ${hydratedItem.debugSummary()}, group=$groupIndex, episode=$episodeIndex")
                _uiState.update { it.copy(errorMessage = "播放地址解析失败。") }
                return@launch
            }
            Log.d(tag, "playSelection ready ${source.label}: url=${targetUrl.previewForLog()} group=$groupIndex episode=$episodeIndex")
            _uiState.update {
                it.copy(
                    playerSession = PlayerSession(
                        source = source,
                        item = hydratedItem,
                        selectedGroupIndex = groupIndex,
                        selectedEpisodeIndex = episodeIndex
                    ),
                    errorMessage = null
                )
            }
        }
    }

    fun updateEpisode(groupIndex: Int, episodeIndex: Int) {
        val session = _uiState.value.playerSession ?: return
        viewModelScope.launch {
            Log.d(tag, "updateEpisode start ${session.source.label}: group=$groupIndex, episode=$episodeIndex")
            val hydratedItem = resolveEpisodeForItem(session.source, session.item, groupIndex, episodeIndex)
            Log.d(
                tag,
                "updateEpisode done ${session.source.label}: ${hydratedItem.playlists.getOrNull(groupIndex)?.episodes?.getOrNull(episodeIndex)?.debugSummary()}"
            )
            _uiState.update { state ->
                val activeSession = state.playerSession ?: return@update state
                state.copy(
                    playerSession = activeSession.copy(
                        item = hydratedItem,
                        selectedGroupIndex = groupIndex,
                        selectedEpisodeIndex = episodeIndex
                    ),
                    errorMessage = null
                )
            }
        }
    }

    fun closePlayer(lastPositionMs: Long = 0L) {
        val session = _uiState.value.playerSession
        if (session != null) {
            val playUrl = session.currentEpisode?.url
            Log.d(
                tag,
                "closePlayer ${session.source.label}: pos=$lastPositionMs url=${playUrl?.previewForLog()}"
            )
            if (!playUrl.isNullOrBlank()) {
                viewModelScope.launch {
                    repository.saveHistory(
                        source = session.source,
                        item = session.item,
                        playUrl = playUrl,
                        positionMs = lastPositionMs
                    )
                }
            }
        }
        _uiState.update { it.copy(playerSession = null) }
    }

    fun toggleFavorite(item: CatalogItem) {
        val source = sourceForItem(item) ?: run {
            _uiState.update { it.copy(errorMessage = "这个条目来自外部榜单，没有可收藏的播放地址。") }
            return
        }
        viewModelScope.launch {
            repository.toggleFavorite(source, item)
        }
    }

    override fun onCleared() {
        networkCallback?.let { callback ->
            runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        }
        networkCallback = null
        lanControlServer.stop()
        super.onCleared()
    }

    private fun observeNetworkStatus() {
        _uiState.update { it.copy(networkStatus = currentNetworkStatus()) }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                updateNetworkStatus()
            }

            override fun onLost(network: Network) {
                updateNetworkStatus()
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                updateNetworkStatus()
            }
        }
        networkCallback = callback
        runCatching {
            connectivityManager.registerDefaultNetworkCallback(callback)
        }.onFailure { error ->
            Log.w(tag, "network callback unavailable", error)
        }
    }

    private fun updateNetworkStatus() {
        val status = currentNetworkStatus()
        _uiState.update {
            it.copy(
                networkStatus = status,
                weatherStatus = if (status.connected) {
                    it.weatherStatus
                } else {
                    it.weatherStatus.copy(isLoading = false, errorMessage = "当前网络不可用。")
                }
            )
        }
    }

    private fun currentNetworkStatus(): DeviceNetworkStatus {
        val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            ?: return DeviceNetworkStatus()
        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val connected = hasInternet && validated
        val label = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "有线"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "移动"
            hasInternet -> "网络"
            else -> "离线"
        }
        return DeviceNetworkStatus(
            connected = connected,
            transportLabel = if (connected) label else "离线",
            hasInternet = hasInternet
        )
    }

    private suspend fun currentFineWeatherLocation(): WeatherLocation {
        if (!hasFineLocationPermission()) {
            error("精确定位权限未开启。")
        }
        val location = currentFineLocation() ?: error("暂时无法获取精确定位。")
        return WeatherLocation(
            latitude = location.latitude,
            longitude = location.longitude,
            district = districtLabelFor(location)
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentFineLocation(): Location? {
        val fresh = requestFreshLocation()
        if (fresh != null) {
            return fresh
        }
        return bestLastKnownLocation()
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestFreshLocation(): Location? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return null
        }
        val provider = preferredLocationProviders().firstOrNull { provider ->
            runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
        } ?: return null
        val deferred = CompletableDeferred<Location?>()
        val cancellationSignal = CancellationSignal()
        runCatching {
            locationManager.getCurrentLocation(
                provider,
                cancellationSignal,
                app.mainExecutor
            ) { location ->
                deferred.complete(location)
            }
        }.onFailure {
            deferred.complete(null)
        }
        return try {
            withTimeoutOrNull(8_000L) { deferred.await() }
        } finally {
            cancellationSignal.cancel()
        }
    }

    @SuppressLint("MissingPermission")
    private fun bestLastKnownLocation(): Location? {
        return preferredLocationProviders()
            .mapNotNull { provider ->
                runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxWithOrNull(
                compareBy<Location> { it.time }
                    .thenBy { it.accuracy }
            )
    }

    private fun preferredLocationProviders(): List<String> {
        return listOf(
            FusedLocationProvider,
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
    }

    private suspend fun districtLabelFor(location: Location): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) {
            return@withContext null
        }
        runCatching {
            @Suppress("DEPRECATION")
            Geocoder(app, Locale.CHINA)
                .getFromLocation(location.latitude, location.longitude, 1)
                ?.firstOrNull()
                ?.let { address ->
                    listOf(
                        address.subLocality,
                        address.subAdminArea,
                        address.locality,
                        address.adminArea
                    ).firstOrNull { !it.isNullOrBlank() }
                }
        }.getOrNull()
    }

    private fun observeSources() {
        viewModelScope.launch {
            repository.observeSources().collect { sources ->
                val previousSelected = _uiState.value.selectedSourceId
                _uiState.update { state ->
                    val selected = when {
                        state.selectedSourceId != null && sources.any { it.id == state.selectedSourceId } -> state.selectedSourceId
                        sources.isNotEmpty() -> pickPreferredSource(sources)?.id
                        else -> null
                    }
                    state.copy(sources = sources, selectedSourceId = selected)
                }
                if (!autoSelected && sources.isNotEmpty()) {
                    autoSelected = true
                    pickPreferredSource(sources)?.let(::loadSource)
                } else if (previousSelected != _uiState.value.selectedSourceId) {
                    _uiState.value.selectedSource?.let(::loadSource)
                }
            }
        }
    }

    private fun observeFavorites() {
        viewModelScope.launch {
            repository.observeFavorites().collect { favorites ->
                _uiState.update { it.copy(favorites = favorites) }
            }
        }
    }

    private fun observeHistory() {
        viewModelScope.launch {
            repository.observeHistory().collect { history ->
                _uiState.update { it.copy(history = history) }
            }
        }
    }

    private fun loadSource(source: SourceItem) {
        viewModelScope.launch {
            loadSourceInternal(source)
        }
    }

    private suspend fun upsertSourceInternal(draft: SourceDraft) {
        val source = repository.addOrUpdateSource(draft)
        Log.d(tag, "upsertSource ${source.debugSummary()}")
        _uiState.update { it.copy(selectedSourceId = source.id, errorMessage = null) }
        if (source.kind == SourceKind.UNSUPPORTED) {
            _uiState.update {
                it.copy(errorMessage = "这个站点当前还没有识别出可执行的 HTTP / Spider 协议。")
            }
            return
        }
        loadSourceInternal(source)
    }

    private suspend fun removeSourceInternal(sourceId: String) {
        Log.d(tag, "removeSource $sourceId")
        repository.removeSource(sourceId)
        if (_uiState.value.selectedSourceId == sourceId) {
            _uiState.update {
                it.copy(
                    selectedSourceId = null,
                    catalogItems = emptyList(),
                    featuredItems = emptyList(),
                    categories = emptyList(),
                    activeCategory = null,
                    detailItem = null
                )
            }
        }
    }

    private suspend fun selectSourceInternal(sourceId: String) {
        val source = _uiState.value.sources.firstOrNull { it.id == sourceId } ?: return
        Log.d(tag, "selectSourceInternal ${source.debugSummary()}")
        _uiState.update { it.copy(selectedSourceId = source.id, errorMessage = null) }
        loadSourceInternal(source)
    }

    private suspend fun loadSourceInternal(source: SourceItem) {
        if (source.kind == SourceKind.UNSUPPORTED) {
            Log.w(tag, "loadSource unsupported ${source.debugSummary()}")
            _uiState.update {
                it.copy(
                    selectedSourceId = source.id,
                    catalogItems = emptyList(),
                    featuredItems = emptyList(),
                    categories = emptyList(),
                    activeCategory = null,
                    searchResults = emptyList(),
                    detailItem = null,
                    detailSelectedGroupIndex = 0,
                    detailSelectedEpisodeIndex = 0,
                    discoveredSources = emptyList(),
                    warnings = emptyList(),
                    errorMessage = "这个站点需要额外的 Spider/脚本兼容支持，当前版本还没识别到可运行入口。"
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isLoading = true,
                selectedSourceId = source.id,
                warnings = emptyList(),
                errorMessage = null
            )
        }

        try {
            Log.d(tag, "loadSource start ${source.debugSummary()}")
            val payload = repository.fetchPayload(source)
            val todayRecommendations = loadDoubanHomeRecommendations()
                .ifEmpty { payload.featuredItems }
            Log.d(tag, "loadSource done ${source.label}: ${payload.debugSummary()}")
            _uiState.update {
                it.copy(
                    isLoading = false,
                    catalogItems = payload.catalogItems,
                    featuredItems = todayRecommendations,
                    categories = payload.categories,
                    activeCategory = null,
                    searchResults = emptyList(),
                    detailItem = null,
                    detailSelectedGroupIndex = 0,
                    detailSelectedEpisodeIndex = 0,
                    discoveredSources = payload.discoveredSources,
                    warnings = payload.warnings,
                    errorMessage = if (
                        payload.catalogItems.isEmpty() &&
                        payload.discoveredSources.isEmpty() &&
                        todayRecommendations.isEmpty()
                    ) {
                        "当前源已连接，但还没有识别到可展示内容。"
                    } else {
                        null
                    }
                )
            }
        } catch (throwable: Throwable) {
            Log.e(tag, "loadSource failed ${source.debugSummary()}", throwable)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    catalogItems = emptyList(),
                    featuredItems = emptyList(),
                    categories = emptyList(),
                    activeCategory = null,
                    searchResults = emptyList(),
                    detailItem = null,
                    detailSelectedGroupIndex = 0,
                    detailSelectedEpisodeIndex = 0,
                    discoveredSources = emptyList(),
                    errorMessage = throwable.message ?: "加载失败"
                )
            }
        }
    }

    private suspend fun resolveEpisodeForItem(
        source: SourceItem,
        item: CatalogItem,
        groupIndex: Int,
        episodeIndex: Int
    ): CatalogItem {
        val targetGroup = item.playlists.getOrNull(groupIndex) ?: return item
        val targetEpisode = targetGroup.episodes.getOrNull(episodeIndex) ?: return item
        Log.d(tag, "resolveEpisodeForItem start ${source.label}: ${targetEpisode.debugSummary()}")
        val resolvedEpisode = repository.resolveEpisode(source, targetEpisode)
        if (resolvedEpisode == targetEpisode) {
            Log.d(tag, "resolveEpisodeForItem unchanged ${source.label}: ${targetEpisode.debugSummary()}")
            return item
        }
        Log.d(tag, "resolveEpisodeForItem updated ${source.label}: ${resolvedEpisode.debugSummary()}")
        val updatedGroups = item.playlists.mapIndexed { currentGroupIndex, group ->
            if (currentGroupIndex != groupIndex) {
                group
            } else {
                group.copy(
                    episodes = group.episodes.mapIndexed { currentEpisodeIndex, episode ->
                        if (currentEpisodeIndex == episodeIndex) resolvedEpisode else episode
                    }
                )
            }
        }
        return item.copy(playlists = updatedGroups)
    }

    private suspend fun loadDoubanHomeRecommendations(): List<CatalogItem> {
        return runCatching {
            Log.d(tag, "todayRecommendations start douban")
            repository.fetchDoubanHomeRecommendations().catalogItems
        }.onSuccess {
            Log.d(tag, "todayRecommendations done douban, items=${it.size}")
        }.onFailure {
            Log.w(tag, "todayRecommendations failed douban", it)
        }.getOrDefault(emptyList())
    }

    private suspend fun searchAcrossSources(sources: List<SourceItem>, query: String): List<SearchSourceResult> {
        val semaphore = Semaphore(6)
        return supervisorScope {
            sources.map { source ->
                async {
                    semaphore.withPermit {
                        runCatching {
                            val payload = repository.searchCatalog(source, query)
                            SearchSourceResult(
                                source = source,
                                items = payload.catalogItems,
                                warnings = payload.warnings
                            )
                        }.getOrElse { throwable ->
                            Log.w(tag, "search source failed ${source.label}: ${query.previewForLog(80)}", throwable)
                            SearchSourceResult(
                                source = source,
                                items = emptyList(),
                                warnings = emptyList(),
                                errorMessage = throwable.message ?: "搜索失败"
                            )
                        }
                    }
                }
            }.awaitAll()
        }
    }

    private fun searchableSources(): List<SourceItem> {
        val state = _uiState.value
        val parsedSources = state.discoveredSources.filter(SourceItem::isSearchable)
        val candidates = if (parsedSources.isNotEmpty()) {
            parsedSources
        } else {
            state.sources.filter(SourceItem::isSearchable)
        }
        return candidates.distinctBy { it.id }
    }

    private fun sourceForItem(item: CatalogItem): SourceItem? {
        val state = _uiState.value
        val allSources = state.sources + state.discoveredSources
        val explicitSourceId = item.sourceId
        if (explicitSourceId != null) {
            return allSources.firstOrNull { it.id == explicitSourceId } ?: state.selectedSource
        }
        return state.selectedSource
    }

    private fun containsAny(text: String, keywords: List<String>): Boolean {
        return keywords.any { keyword -> text.contains(keyword, ignoreCase = true) }
    }

    private fun pickPreferredSource(sources: List<SourceItem>): SourceItem? {
        return sources.firstOrNull {
            it.label.contains("饭太硬")
        } ?: sources.firstOrNull {
            it.kind == SourceKind.DIRECT
        } ?: sources.firstOrNull()
    }
}

private fun SourceItem.isSearchable(): Boolean {
    if (kind == SourceKind.UNSUPPORTED) {
        return false
    }
    if (settings.spiderMode != SpiderMode.NONE) {
        return true
    }
    if (!settings.searchPath.isNullOrBlank()) {
        return true
    }
    return kind == SourceKind.DIRECT ||
        kind == SourceKind.DISCOVERED_SITE ||
        kind == SourceKind.SPIDER
}

private fun CategoryParam.matchesPrimaryKey(key: String): Boolean {
    val text = "$name $value"
    return when (key) {
        "movie", "movie_filter" -> text.containsAny(
            "电影",
            "影片",
            "院线",
            "movie",
            "dianying"
        )
        "series", "tv_filter" -> text.containsAny(
            "电视剧",
            "剧集",
            "连续剧",
            "国产剧",
            "美剧",
            "韩剧",
            "tv",
            "series",
            "dianshiju"
        )
        "variety" -> text.containsAny("综艺", "真人秀", "show", "variety", "zongyi")
        else -> false
    }
}

private fun String.toDoubanSection(): DoubanSection? {
    return when (this) {
        "movie", "movie_filter" -> DoubanSection.HotMovie
        "series", "tv_filter" -> DoubanSection.HotTv
        "variety" -> DoubanSection.HotVariety
        "movie_ranking" -> DoubanSection.MovieRanking
        "tv_ranking" -> DoubanSection.TvRanking
        else -> null
    }
}

private fun String.primaryLabel(): String {
    return when (this) {
        "movie" -> "热播电影"
        "series" -> "热播剧集"
        "variety" -> "热播综艺"
        "movie_filter" -> "电影筛选"
        "tv_filter" -> "电视筛选"
        "movie_ranking" -> "电影榜单"
        "tv_ranking" -> "电视剧榜单"
        else -> this
    }
}

    private fun String.containsAny(vararg keywords: String): Boolean {
    return keywords.any { keyword -> contains(keyword, ignoreCase = true) }
}

private const val FusedLocationProvider = "fused"
