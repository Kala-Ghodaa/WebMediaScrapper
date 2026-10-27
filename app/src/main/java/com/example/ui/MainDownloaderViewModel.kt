package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.download.DownloadRepository
import com.example.data.local.PulseGrabDatabase
import com.example.data.scanner.AlbumBatchScraperEngine
import com.example.data.scanner.DeepScanEngine
import com.example.data.scanner.DomainCrawlerController
import com.example.data.scanner.WebMediaScannerEngine
import com.example.domain.model.AlbumBatchProgress
import com.example.domain.model.DeepScanConfig
import com.example.domain.model.DeepScanMode
import com.example.domain.model.DeepScanProgress
import com.example.domain.model.DetectedMediaItem
import com.example.domain.model.DiscoveredPathPattern
import com.example.domain.model.DownloadFolderConfig
import com.example.domain.model.DownloadFolderMode
import com.example.domain.model.DownloadStatus
import com.example.domain.model.DownloadTask
import com.example.domain.model.DownloadedAlbumRecord
import com.example.domain.model.ExtensionFilterConfig
import com.example.domain.model.MediaTabFilter
import com.example.domain.model.MediaType
import com.example.domain.model.PortalShortcut
import com.example.domain.usecase.ExpandUrlPatternUseCase
import com.example.domain.usecase.FilterAndDeduplicateMediaUseCase
import com.example.domain.usecase.UrlMediaUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

enum class AppDestination {
    HOME,
    DETECTED,
    DOWNLOADS
}

enum class ExtensionChipPreset {
    ALL,
    PHOTOS,
    VECTORS,
    VIDEOS
}

data class InternalControlState(
    val currentDestination: AppDestination = AppDestination.HOME,
    val urlInput: String = "",
    val activeBrowserUrl: String = "pulsegrab://showcase",
    val isBrowserOpen: Boolean = false,
    val isPageLoading: Boolean = false,
    val pageProgress: Int = 0,
    val rawDetectedItems: List<DetectedMediaItem> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val tabFilter: MediaTabFilter = MediaTabFilter.ALL,
    val selectedExtensionChips: Set<String> = emptySet(),
    val extensionConfig: ExtensionFilterConfig = ExtensionFilterConfig(),
    val folderConfig: DownloadFolderConfig = DownloadFolderConfig(),
    val skipDuplicates: Boolean = true,
    val searchQuery: String = "",
    val previewMediaItem: DetectedMediaItem? = null,
    val isExtensionSheetOpen: Boolean = false,
    val isDeepScanSheetOpen: Boolean = false,
    val isFolderSheetOpen: Boolean = false,
    val isCrawlerSheetOpen: Boolean = false,
    val isAlbumBatchSheetOpen: Boolean = false,
    val albumFolderUrlInput: String = "",
    val strictJpgMp4Only: Boolean = true,
    val skipAlreadyDownloadedAlbums: Boolean = true,
    val redownloadIfMissing: Boolean = true,
    val crawlerBaseUrl: String = "https://commons.wikimedia.org/wiki/Commons:Featured_pictures",
    val crawlerDepth: Int = 2,
    val crawlerMaxPages: Int = 30,
    val crawlerSameDomain: Boolean = true,
    val deepScanConfig: DeepScanConfig = DeepScanConfig(),
    val downloadFilterStatus: DownloadStatus? = null,
    val downloadSearchQuery: String = "",
    val telegramShareFolderName: String? = null,
    val isPreparingTelegramShare: Boolean = false,
    val telegramShareMessage: String? = null,
    val isLogDialogOpen: Boolean = false,
    val logDialogFilterUrl: String? = null,
    val collapsedFolderNames: Set<String> = emptySet(),
    val snackbarMessage: String? = null
)

data class MainUiState(
    val currentDestination: AppDestination = AppDestination.HOME,
    val urlInput: String = "",
    val activeBrowserUrl: String = "",
    val isBrowserOpen: Boolean = false,
    val isPageLoading: Boolean = false,
    val pageProgress: Int = 0,
    val allDetectedCount: Int = 0,
    val detectedImageCount: Int = 0,
    val detectedVideoCount: Int = 0,
    val filteredMediaItems: List<DetectedMediaItem> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val tabFilter: MediaTabFilter = MediaTabFilter.ALL,
    val selectedExtensionChips: Set<String> = emptySet(),
    val extensionCounts: Map<String, Int> = emptyMap(),
    val extensionConfig: ExtensionFilterConfig = ExtensionFilterConfig(),
    val folderConfig: DownloadFolderConfig = DownloadFolderConfig(),
    val skipDuplicates: Boolean = true,
    val searchQuery: String = "",
    val previewMediaItem: DetectedMediaItem? = null,
    val isExtensionSheetOpen: Boolean = false,
    val isDeepScanSheetOpen: Boolean = false,
    val isFolderSheetOpen: Boolean = false,
    val isCrawlerSheetOpen: Boolean = false,
    val isAlbumBatchSheetOpen: Boolean = false,
    val albumFolderUrlInput: String = "",
    val strictJpgMp4Only: Boolean = true,
    val skipAlreadyDownloadedAlbums: Boolean = true,
    val redownloadIfMissing: Boolean = true,
    val albumBatchProgress: AlbumBatchProgress = AlbumBatchProgress(),
    val downloadedAlbumsHistory: List<DownloadedAlbumRecord> = emptyList(),
    val folderSourceLinks: Map<String, String> = emptyMap(),
    val crawlerBaseUrl: String = "",
    val crawlerDepth: Int = 2,
    val crawlerMaxPages: Int = 30,
    val crawlerSameDomain: Boolean = true,
    val isAnalyzingCrawlerPatterns: Boolean = false,
    val discoveredPatterns: List<DiscoveredPathPattern> = emptyList(),
    val crawlerProgress: DeepScanProgress = DeepScanProgress(),
    val deepScanConfig: DeepScanConfig = DeepScanConfig(),
    val deepScanProgress: DeepScanProgress = DeepScanProgress(),
    val expandedPatternPreview: List<String> = emptyList(),
    val downloadTasks: List<DownloadTask> = emptyList(),
    val filteredDownloadTasks: List<DownloadTask> = emptyList(),
    val downloadFilterStatus: DownloadStatus? = null,
    val downloadSearchQuery: String = "",
    val telegramShareFolderName: String? = null,
    val isPreparingTelegramShare: Boolean = false,
    val telegramShareMessage: String? = null,
    val activeDownloadCount: Int = 0,
    val totalDownloadSpeed: Long = 0L,
    val isLogDialogOpen: Boolean = false,
    val logDialogFilterUrl: String? = null,
    val collapsedFolderNames: Set<String> = emptySet(),
    val portals: List<PortalShortcut> = defaultPortals,
    val snackbarMessage: String? = null
)

private val defaultPortals = listOf(
    PortalShortcut(
        id = "showcase",
        title = "Demo gallery",
        subtitle = "Instant test gallery with JPG, PNG, WEBP, SVG, MP4 & multi-page links",
        url = "pulsegrab://showcase",
        badge = "BUILT-IN",
        defaultPatternHint = "https://showcase.pulsegrab.local/gallery/page-[1-3]"
    ),
    PortalShortcut(
        id = "wikimedia_featured",
        title = "Wikimedia Featured Pictures",
        subtitle = "High-resolution curated photography & archival images",
        url = "https://commons.wikimedia.org/wiki/Commons:Featured_pictures",
        badge = "IMAGES",
        defaultPatternHint = "*commons.wikimedia.org/wiki/*"
    ),
    PortalShortcut(
        id = "wikimedia_videos",
        title = "Wikimedia Open Media Videos",
        subtitle = "Public domain & CC-licensed WEBM / OGV / MP4 clips",
        url = "https://commons.wikimedia.org/wiki/Commons:Video",
        badge = "VIDEOS",
        defaultPatternHint = "*commons.wikimedia.org*"
    ),
    PortalShortcut(
        id = "nasa_gallery",
        title = "NASA Image & Video Library",
        subtitle = "Public domain space photography and mission footage",
        url = "https://images.nasa.gov",
        badge = "SPACE",
        defaultPatternHint = "https://images.nasa.gov/*"
    )
)

class MainDownloaderViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = com.example.data.local.DownloaderPreferences(application)
    private val database = PulseGrabDatabase.getInstance(application)
    private val downloadRepository = DownloadRepository(application, database.downloadDao())
    private val filterMediaUseCase = FilterAndDeduplicateMediaUseCase()
    private val expandUrlPatternUseCase = ExpandUrlPatternUseCase()

    private val controlState = MutableStateFlow(InternalControlState(
        folderConfig = preferences.folderConfig(), extensionConfig = preferences.extensions(),
        skipDuplicates = preferences.skipDuplicates
    ))

    val webMediaScannerEngine = WebMediaScannerEngine { newItems ->
        appendDiscoveredMedia(newItems)
    }

    private val deepScanEngine = DeepScanEngine(
        scope = viewModelScope,
        expandUrlPatternUseCase = expandUrlPatternUseCase,
        onMediaDiscovered = { newItems ->
            appendDiscoveredMedia(newItems)
        }
    )

    private val albumBatchEngine = AlbumBatchScraperEngine(
        scope = viewModelScope,
        downloadDao = database.downloadDao(),
        downloadRepository = downloadRepository,
        expandUrlPatternUseCase = expandUrlPatternUseCase,
        onMediaDiscovered = { newItems ->
            appendDiscoveredMedia(newItems)
        }
    )

    init {
        downloadRepository.activeFolderConfig = controlState.value.folderConfig
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            controlState.map { Triple(it.folderConfig, it.extensionConfig, it.skipDuplicates) }
                .distinctUntilChanged().collect { (folder, extensions, skip) ->
                    preferences.saveFolder(folder)
                    preferences.saveExtensions(extensions)
                    preferences.skipDuplicates = skip
                    downloadRepository.activeFolderConfig = folder
                }
        }
        // Connect DomainCrawlerController callback to stream discovered media
        DomainCrawlerController.onMediaDiscoveredCallback = { newItems ->
            appendDiscoveredMedia(newItems)
        }


    }

    private data class CoreCombined(
        val ctrl: InternalControlState,
        val downloads: List<DownloadTask>,
        val scanProgress: DeepScanProgress,
        val albumBatchProgress: AlbumBatchProgress,
        val downloadedAlbums: List<DownloadedAlbumRecord>
    )

    val uiState: StateFlow<MainUiState> = combine(
        combine(
            controlState,
            downloadRepository.allDownloadTasks,
            deepScanEngine.progress,
            albumBatchEngine.progress,
            database.downloadDao().observeDownloadedAlbums()
        ) { ctrl, downloads, scanProgress, albumBatch, albumEntities ->
            CoreCombined(
                ctrl = ctrl,
                downloads = downloads,
                scanProgress = scanProgress,
                albumBatchProgress = albumBatch,
                downloadedAlbums = albumEntities.map { it.toDomain() }
            )
        },
        DomainCrawlerController.crawlerProgress,
        DomainCrawlerController.discoveredPatterns,
        DomainCrawlerController.isAnalyzingPatterns
    ) { core, crawlerProgress, patterns, isAnalyzingPatterns ->
        val ctrl = core.ctrl
        val downloads = core.downloads
        val scanProgress = core.scanProgress
        val downloadedUrls = downloads
            .filter { it.status == DownloadStatus.COMPLETED || it.status == DownloadStatus.DOWNLOADING }
            .map { it.normalizedUrl }
            .toSet()

        val baseAllowedItems = filterMediaUseCase(
            items = ctrl.rawDetectedItems,
            tabFilter = MediaTabFilter.ALL,
            selectedExtensionChips = emptySet(),
            extensionConfig = ctrl.extensionConfig,
            skipDuplicates = ctrl.skipDuplicates,
            searchQuery = "",
            downloadedNormalizedUrls = downloadedUrls
        )

        val imgCount = baseAllowedItems.count { it.mediaType == MediaType.IMAGE }
        val vidCount = baseAllowedItems.count { it.mediaType == MediaType.VIDEO }

        val tabScopedItems = when (ctrl.tabFilter) {
            MediaTabFilter.ALL -> baseAllowedItems
            MediaTabFilter.IMAGES -> baseAllowedItems.filter { it.mediaType == MediaType.IMAGE }
            MediaTabFilter.VIDEOS -> baseAllowedItems.filter { it.mediaType == MediaType.VIDEO }
        }

        val extCounts = LinkedHashMap<String, Int>()
        for (item in tabScopedItems) {
            val extKey = if (item.extension == "jpeg") "jpg" else item.extension.lowercase(Locale.US)
            if (extKey.isNotEmpty()) {
                extCounts[extKey] = (extCounts[extKey] ?: 0) + 1
            }
        }

        val visibleDetected = filterMediaUseCase(
            items = ctrl.rawDetectedItems,
            tabFilter = ctrl.tabFilter,
            selectedExtensionChips = ctrl.selectedExtensionChips,
            extensionConfig = ctrl.extensionConfig,
            skipDuplicates = ctrl.skipDuplicates,
            searchQuery = ctrl.searchQuery,
            downloadedNormalizedUrls = downloadedUrls
        )

        val visibleIds = visibleDetected.map { it.id }.toSet()
        val validSelectedIds = ctrl.selectedIds.intersect(visibleIds)

        // Map each folderName to its source album link (from downloadedAlbums + tasks refererUrl)
        val folderLinksMap = LinkedHashMap<String, String>()
        for (album in core.downloadedAlbums) {
            if (album.folderName.isNotBlank() && album.albumUrl.isNotBlank()) {
                folderLinksMap[album.folderName] = album.albumUrl
                val safe = DownloadFolderConfig.sanitizeFolderName(album.folderName)
                if (safe.isNotBlank()) folderLinksMap[safe] = album.albumUrl
            }
        }
        for (task in downloads) {
            val fName = task.folderName.ifBlank { "PulseGrab" }
            if (task.refererUrl.isNotBlank() && !folderLinksMap.containsKey(fName)) {
                folderLinksMap[fName] = task.refererUrl
            }
        }

        val statusFiltered = if (ctrl.downloadFilterStatus == null) {
            downloads
        } else {
            downloads.filter { it.status == ctrl.downloadFilterStatus }
        }

        val rawQuery = ctrl.downloadSearchQuery.trim()
        val cleanQuery = rawQuery.removePrefix("#").lowercase(Locale.US)
        val filteredDownloads = if (cleanQuery.isEmpty()) {
            statusFiltered
        } else {
            statusFiltered.filter { task ->
                val fName = task.folderName.ifBlank { "PulseGrab" }
                val sourceLink = folderLinksMap[fName] ?: task.refererUrl
                fName.lowercase(Locale.US).contains(cleanQuery) ||
                    fName.replace('_', ' ').lowercase(Locale.US).contains(cleanQuery) ||
                    task.fileName.lowercase(Locale.US).contains(cleanQuery) ||
                    task.url.lowercase(Locale.US).contains(cleanQuery) ||
                    sourceLink.lowercase(Locale.US).contains(cleanQuery)
            }
        }

        val activeDownloads = downloads.count {
            it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED
        }
        val totalSpeed = downloads
            .filter { it.status == DownloadStatus.DOWNLOADING }
            .sumOf { it.speedBytesPerSec }

        val patternPreview = if (ctrl.deepScanConfig.urlPattern.isNotBlank()) {
            expandUrlPatternUseCase.expandPattern(ctrl.deepScanConfig.urlPattern, maxCount = 5)
        } else {
            emptyList()
        }

        MainUiState(
            currentDestination = ctrl.currentDestination,
            urlInput = ctrl.urlInput,
            activeBrowserUrl = ctrl.activeBrowserUrl,
            isBrowserOpen = ctrl.isBrowserOpen,
            isPageLoading = ctrl.isPageLoading,
            pageProgress = ctrl.pageProgress,
            allDetectedCount = baseAllowedItems.size,
            detectedImageCount = imgCount,
            detectedVideoCount = vidCount,
            filteredMediaItems = visibleDetected,
            selectedIds = validSelectedIds,
            tabFilter = ctrl.tabFilter,
            selectedExtensionChips = ctrl.selectedExtensionChips,
            extensionCounts = extCounts,
            extensionConfig = ctrl.extensionConfig,
            folderConfig = ctrl.folderConfig,
            skipDuplicates = ctrl.skipDuplicates,
            searchQuery = ctrl.searchQuery,
            previewMediaItem = ctrl.previewMediaItem,
            isExtensionSheetOpen = ctrl.isExtensionSheetOpen,
            isDeepScanSheetOpen = ctrl.isDeepScanSheetOpen,
            isFolderSheetOpen = ctrl.isFolderSheetOpen,
            isCrawlerSheetOpen = ctrl.isCrawlerSheetOpen,
            isAlbumBatchSheetOpen = ctrl.isAlbumBatchSheetOpen,
            albumFolderUrlInput = ctrl.albumFolderUrlInput,
            strictJpgMp4Only = ctrl.strictJpgMp4Only,
            skipAlreadyDownloadedAlbums = ctrl.skipAlreadyDownloadedAlbums,
            redownloadIfMissing = ctrl.redownloadIfMissing,
            albumBatchProgress = core.albumBatchProgress,
            downloadedAlbumsHistory = core.downloadedAlbums,
            folderSourceLinks = folderLinksMap,
            crawlerBaseUrl = ctrl.crawlerBaseUrl,
            crawlerDepth = ctrl.crawlerDepth,
            crawlerMaxPages = ctrl.crawlerMaxPages,
            crawlerSameDomain = ctrl.crawlerSameDomain,
            isAnalyzingCrawlerPatterns = isAnalyzingPatterns,
            discoveredPatterns = patterns,
            crawlerProgress = crawlerProgress,
            deepScanConfig = ctrl.deepScanConfig,
            deepScanProgress = scanProgress,
            expandedPatternPreview = patternPreview,
            downloadTasks = downloads,
            filteredDownloadTasks = filteredDownloads,
            downloadFilterStatus = ctrl.downloadFilterStatus,
            downloadSearchQuery = ctrl.downloadSearchQuery,
            telegramShareFolderName = ctrl.telegramShareFolderName,
            isPreparingTelegramShare = ctrl.isPreparingTelegramShare,
            telegramShareMessage = ctrl.telegramShareMessage,
            activeDownloadCount = activeDownloads,
            totalDownloadSpeed = totalSpeed,
            isLogDialogOpen = ctrl.isLogDialogOpen,
            logDialogFilterUrl = ctrl.logDialogFilterUrl,
            collapsedFolderNames = ctrl.collapsedFolderNames,
            portals = defaultPortals,
            snackbarMessage = ctrl.snackbarMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState()
    )

    fun openLogDialog(filterUrl: String? = null) {
        controlState.update { it.copy(isLogDialogOpen = true, logDialogFilterUrl = filterUrl) }
    }

    fun closeLogDialog() {
        controlState.update { it.copy(isLogDialogOpen = false, logDialogFilterUrl = null) }
    }

    fun onDownloadSearchQueryChange(query: String) {
        controlState.update { it.copy(downloadSearchQuery = query) }
    }

    fun openTelegramShareSheet(folderName: String) {
        controlState.update { it.copy(telegramShareFolderName = folderName, telegramShareMessage = null) }
    }

    fun closeTelegramShareSheet() {
        controlState.update { it.copy(telegramShareFolderName = null, telegramShareMessage = null) }
    }

    fun toggleFolderCollapsed(folderName: String) {
        controlState.update { state ->
            val updated = if (state.collapsedFolderNames.contains(folderName)) {
                state.collapsedFolderNames - folderName
            } else {
                state.collapsedFolderNames + folderName
            }
            state.copy(collapsedFolderNames = updated)
        }
    }

    fun toggleCollapseAllFolders() {
        val allFolders = uiState.value.filteredDownloadTasks
            .map { it.folderName.ifBlank { "PulseGrab" } }
            .toSet()
        if (allFolders.isEmpty()) return
        controlState.update { state ->
            val allCurrentlyCollapsed = allFolders.all { state.collapsedFolderNames.contains(it) }
            val updated = if (allCurrentlyCollapsed) {
                state.collapsedFolderNames - allFolders
            } else {
                state.collapsedFolderNames + allFolders
            }
            state.copy(collapsedFolderNames = updated)
        }
    }

    fun selectDestination(destination: AppDestination) {
        controlState.update { it.copy(currentDestination = destination) }
    }

    fun onUrlInputChange(newUrl: String) {
        controlState.update { it.copy(urlInput = newUrl) }
    }

    fun openUrlInBrowser(rawUrl: String = controlState.value.urlInput) {
        val target = rawUrl.trim()
        if (target.isEmpty()) {
            showSnackbar("Please enter or paste a webpage URL")
            return
        }

        if (expandUrlPatternUseCase.hasRangePattern(target)) {
            val normalizedPattern = UrlMediaUtils.normalizeInputUrl(target)
            controlState.update {
                it.copy(
                    urlInput = target,
                    isDeepScanSheetOpen = true,
                    deepScanConfig = it.deepScanConfig.copy(
                        seedUrl = normalizedPattern,
                        urlPattern = normalizedPattern,
                        mode = DeepScanMode.URL_PATTERN
                    )
                )
            }
            return
        }

        val normalized = UrlMediaUtils.normalizeInputUrl(target)
        val domainTitle = runCatching { java.net.URI(normalized).host?.removePrefix("www.") }.getOrNull() ?: "Web_Media"
        controlState.update {
            it.copy(
                currentDestination = AppDestination.HOME,
                urlInput = if (normalized.startsWith("pulsegrab://")) "pulsegrab://showcase" else normalized,
                activeBrowserUrl = normalized,
                crawlerBaseUrl = normalized,
                isBrowserOpen = true,
                deepScanConfig = it.deepScanConfig.copy(seedUrl = normalized),
                folderConfig = it.folderConfig.copy(currentPageTitle = domainTitle)
            )
        }
    }

    fun openPortalShortcut(portal: PortalShortcut) {
        val domainTitle = runCatching { java.net.URI(portal.url).host?.removePrefix("www.") }.getOrNull() ?: portal.title
        controlState.update {
            it.copy(
                currentDestination = AppDestination.HOME,
                urlInput = portal.url,
                activeBrowserUrl = portal.url,
                crawlerBaseUrl = portal.url,
                isBrowserOpen = true,
                deepScanConfig = it.deepScanConfig.copy(
                    seedUrl = portal.url,
                    urlPattern = portal.defaultPatternHint.ifBlank { it.deepScanConfig.urlPattern }
                ),
                folderConfig = it.folderConfig.copy(currentPageTitle = domainTitle)
            )
        }
        if (portal.url == "pulsegrab://showcase") {
            val items = DeepScanEngine.extractMediaFromHtml(
                html = WebMediaScannerEngine.buildShowcaseHtml(1),
                baseUrl = "https://showcase.pulsegrab.local/gallery/page-1",
                crawlDepth = 0,
                extensionFilterConfig = controlState.value.extensionConfig
            )
            appendDiscoveredMedia(items)
        }
    }

    fun closeBrowserToHomeHub() {
        controlState.update { it.copy(isBrowserOpen = false) }
    }

    fun updateBrowserPageState(url: String?, isLoading: Boolean, progress: Int, pageTitle: String? = null) {
        controlState.update { current ->
            val updatedUrl = if (!url.isNullOrBlank() && !url.startsWith("about:")) url else current.activeBrowserUrl
            val safeTitle = if (!pageTitle.isNullOrBlank() && !pageTitle.startsWith("http")) {
                pageTitle
            } else {
                runCatching { java.net.URI(updatedUrl).host?.removePrefix("www.") }.getOrNull() ?: current.folderConfig.currentPageTitle
            }
            current.copy(
                activeBrowserUrl = updatedUrl,
                isPageLoading = isLoading,
                pageProgress = progress,
                folderConfig = current.folderConfig.copy(currentPageTitle = safeTitle)
            )
        }
    }

    fun openDetectedWithFilter(tabFilter: MediaTabFilter, extensionChip: String? = null) {
        controlState.update {
            it.copy(
                currentDestination = AppDestination.DETECTED,
                tabFilter = tabFilter,
                selectedExtensionChips = if (extensionChip != null) setOf(extensionChip.lowercase(Locale.US)) else emptySet()
            )
        }
    }

    fun setMediaTabFilter(tabFilter: MediaTabFilter) {
        controlState.update {
            it.copy(
                tabFilter = tabFilter,
                selectedExtensionChips = emptySet()
            )
        }
    }

    // Multi-Select Extension Chips
    fun toggleExtensionChip(extension: String) {
        val clean = extension.lowercase(Locale.US).removePrefix(".")
        controlState.update { current ->
            val nextSet = current.selectedExtensionChips.toMutableSet()
            if (!nextSet.add(clean)) {
                nextSet.remove(clean)
            }
            current.copy(selectedExtensionChips = nextSet)
        }
    }

    fun applyExtensionPreset(preset: ExtensionChipPreset) {
        controlState.update { current ->
            val newSelection = when (preset) {
                ExtensionChipPreset.ALL -> emptySet()
                ExtensionChipPreset.PHOTOS -> setOf("jpg", "jpeg", "png", "webp")
                ExtensionChipPreset.VECTORS -> setOf("png", "svg", "gif")
                ExtensionChipPreset.VIDEOS -> setOf("mp4", "webm", "mkv", "mov")
            }
            current.copy(selectedExtensionChips = newSelection)
        }
    }

    fun clearExtensionChips() {
        controlState.update { it.copy(selectedExtensionChips = emptySet()) }
    }

    fun setSkipDuplicates(skip: Boolean) {
        controlState.update { it.copy(skipDuplicates = skip) }
    }

    fun onSearchQueryChange(query: String) {
        controlState.update { it.copy(searchQuery = query) }
    }

    fun toggleMediaSelection(itemId: String) {
        controlState.update { current ->
            val next = current.selectedIds.toMutableSet()
            if (!next.add(itemId)) {
                next.remove(itemId)
            }
            current.copy(selectedIds = next)
        }
    }

    fun selectAllVisibleMedia() {
        val visibleIds = uiState.value.filteredMediaItems.map { it.id }.toSet()
        controlState.update { it.copy(selectedIds = visibleIds) }
    }

    fun deselectAllMedia() {
        controlState.update { it.copy(selectedIds = emptySet()) }
    }

    fun clearDetectedMedia() {
        controlState.update {
            it.copy(
                rawDetectedItems = emptyList(),
                selectedIds = emptySet()
            )
        }
        showSnackbar("Cleared detected media list")
    }

    fun setPreviewMediaItem(item: DetectedMediaItem?) {
        controlState.update { it.copy(previewMediaItem = item) }
    }

    // Folder Management
    fun setFolderSheetOpen(open: Boolean) {
        controlState.update { it.copy(isFolderSheetOpen = open) }
    }

    fun setFolderMode(mode: DownloadFolderMode) {
        controlState.update {
            it.copy(folderConfig = it.folderConfig.copy(folderMode = mode))
        }
    }

    fun setCustomSubfolderName(name: String) {
        controlState.update {
            it.copy(folderConfig = it.folderConfig.copy(customSubfolderName = name))
        }
    }

    fun setCustomTreeUri(uri: String, displayName: String) {
        controlState.update {
            it.copy(
                folderConfig = it.folderConfig.copy(
                    folderMode = DownloadFolderMode.SAF_CUSTOM_TREE,
                    customTreeUri = uri,
                    customTreeDisplayName = displayName
                )
            )
        }
        showSnackbar("Destination set to $displayName")
    }

    // Domain Crawler Service
    fun setCrawlerSheetOpen(open: Boolean) {
        controlState.update { current ->
            val target = current.crawlerBaseUrl.ifBlank { current.urlInput.ifBlank { current.activeBrowserUrl } }
            current.copy(
                isCrawlerSheetOpen = open,
                crawlerBaseUrl = target
            )
        }
    }

    fun onCrawlerBaseUrlChange(url: String) {
        controlState.update { it.copy(crawlerBaseUrl = url) }
    }

    fun onCrawlerDepthChange(depth: Int) {
        controlState.update { it.copy(crawlerDepth = depth) }
    }

    fun onCrawlerMaxPagesChange(pages: Int) {
        controlState.update { it.copy(crawlerMaxPages = pages) }
    }

    fun onCrawlerSameDomainChange(same: Boolean) {
        controlState.update { it.copy(crawlerSameDomain = same) }
    }

    fun analyzeCrawlerPatterns() {
        val target = controlState.value.crawlerBaseUrl.ifBlank { controlState.value.activeBrowserUrl }
        DomainCrawlerController.analyzeBaseUrlPatterns(target)
    }

    fun toggleCrawlerPatternSelection(patternId: String) {
        DomainCrawlerController.togglePatternSelection(patternId)
    }

    fun selectAllCrawlerPatterns(select: Boolean) {
        DomainCrawlerController.selectAllPatterns(select)
    }

    fun startDomainCrawlerService(context: Context) {
        val ctrl = controlState.value
        val selectedPatterns = DomainCrawlerController.discoveredPatterns.value
            .filter { it.isSelected }
            .map { it.pattern }

        val config = DeepScanConfig(
            seedUrl = ctrl.crawlerBaseUrl,
            mode = DeepScanMode.DOMAIN_BFS,
            maxDepth = ctrl.crawlerDepth,
            maxPages = ctrl.crawlerMaxPages,
            sameDomainOnly = ctrl.crawlerSameDomain
        )

        DomainCrawlerController.startCrawl(
            context = context,
            config = config,
            selectedPatternStrings = selectedPatterns,
            extensionConfig = ctrl.extensionConfig
        )
    }

    fun stopDomainCrawlerService(context: Context) {
        DomainCrawlerController.stopCrawl(context)
    }

    // Extension Filter Config Sheet actions
    fun setExtensionSheetOpen(open: Boolean) {
        controlState.update { it.copy(isExtensionSheetOpen = open) }
    }

    fun toggleTargetImageExtension(ext: String) {
        val clean = ext.lowercase(Locale.US).removePrefix(".")
        controlState.update { current ->
            val nextSet = current.extensionConfig.enabledImageExtensions.toMutableSet()
            if (!nextSet.add(clean)) {
                nextSet.remove(clean)
            }
            current.copy(
                extensionConfig = current.extensionConfig.copy(enabledImageExtensions = nextSet)
            )
        }
    }

    fun toggleTargetVideoExtension(ext: String) {
        val clean = ext.lowercase(Locale.US).removePrefix(".")
        controlState.update { current ->
            val nextSet = current.extensionConfig.enabledVideoExtensions.toMutableSet()
            if (!nextSet.add(clean)) {
                nextSet.remove(clean)
            }
            current.copy(
                extensionConfig = current.extensionConfig.copy(enabledVideoExtensions = nextSet)
            )
        }
    }

    fun selectAllTargetExtensions(enableAll: Boolean) {
        controlState.update { current ->
            val nextCfg = if (enableAll) {
                current.extensionConfig.copy(
                    enabledImageExtensions = ExtensionFilterConfig.DEFAULT_IMAGE_EXTENSIONS,
                    enabledVideoExtensions = ExtensionFilterConfig.DEFAULT_VIDEO_EXTENSIONS
                )
            } else {
                current.extensionConfig.copy(
                    enabledImageExtensions = emptySet(),
                    enabledVideoExtensions = emptySet(),
                    customExtensions = emptySet()
                )
            }
            current.copy(extensionConfig = nextCfg)
        }
    }

    fun addCustomExtension(rawExt: String) {
        val clean = rawExt.lowercase(Locale.US).trim().removePrefix(".")
            .replace(Regex("[^a-z0-9]"), "")
        if (clean.length !in 2..6) {
            showSnackbar("Enter a valid extension (e.g. flac, avif, zip)")
            return
        }
        controlState.update { current ->
            val nextCustom = current.extensionConfig.customExtensions + clean
            current.copy(
                extensionConfig = current.extensionConfig.copy(customExtensions = nextCustom)
            )
        }
    }

    fun removeCustomExtension(ext: String) {
        controlState.update { current ->
            val nextCustom = current.extensionConfig.customExtensions - ext.lowercase(Locale.US)
            current.copy(
                extensionConfig = current.extensionConfig.copy(customExtensions = nextCustom)
            )
        }
    }

    fun setMinResolutionFilter(minWidth: Int, minHeight: Int) {
        controlState.update { current ->
            current.copy(
                extensionConfig = current.extensionConfig.copy(
                    minWidthPx = minWidth,
                    minHeightPx = minHeight
                )
            )
        }
    }

    // Deep Scan actions
    fun setDeepScanSheetOpen(open: Boolean) {
        controlState.update { current ->
            val activeSeed = current.urlInput.ifBlank { current.activeBrowserUrl }
            val updatedCfg = if (open && current.deepScanConfig.seedUrl.isBlank()) {
                current.deepScanConfig.copy(seedUrl = activeSeed)
            } else {
                current.deepScanConfig
            }
            current.copy(
                isDeepScanSheetOpen = open,
                deepScanConfig = updatedCfg
            )
        }
    }

    fun updateDeepScanConfig(transform: (DeepScanConfig) -> DeepScanConfig) {
        controlState.update { current ->
            current.copy(deepScanConfig = transform(current.deepScanConfig))
        }
    }

    fun startDeepScan() {
        val cfg = controlState.value.deepScanConfig
        val extCfg = controlState.value.extensionConfig
        deepScanEngine.startDeepScan(cfg, extCfg)
    }

    fun togglePauseDeepScan() {
        deepScanEngine.togglePause()
    }

    fun stopDeepScan() {
        deepScanEngine.stopDeepScan()
    }

    // Album Folder Batch Grabber actions
    fun setAlbumBatchSheetOpen(open: Boolean) {
        controlState.update { current ->
            val candidateUrl = current.urlInput.ifBlank { current.activeBrowserUrl }
            val updatedFolderUrl = if (open && (current.albumFolderUrlInput.isBlank() || current.albumFolderUrlInput.contains("showcase.pulsegrab.local"))) {
                candidateUrl.ifBlank { current.albumFolderUrlInput }
            } else {
                current.albumFolderUrlInput
            }
            current.copy(
                isAlbumBatchSheetOpen = open,
                albumFolderUrlInput = updatedFolderUrl
            )
        }
    }

    fun onAlbumFolderUrlChange(url: String) {
        controlState.update { it.copy(albumFolderUrlInput = url) }
    }

    fun setStrictJpgMp4Only(strict: Boolean) {
        controlState.update { it.copy(strictJpgMp4Only = strict) }
    }

    fun setSkipAlreadyDownloadedAlbums(skip: Boolean) {
        controlState.update { it.copy(skipAlreadyDownloadedAlbums = skip) }
    }

    fun setRedownloadIfMissing(enabled: Boolean) {
        controlState.update { it.copy(redownloadIfMissing = enabled) }
    }

    fun startAlbumFolderBatch() {
        val ctrl = controlState.value
        val targetUrl = ctrl.albumFolderUrlInput.ifBlank { ctrl.urlInput }
        albumBatchEngine.startAlbumFolderBatch(
            rawFolderUrl = targetUrl,
            strictJpgMp4Only = ctrl.strictJpgMp4Only,
            skipAlreadyDownloadedAlbums = ctrl.skipAlreadyDownloadedAlbums,
            redownloadIfMissing = ctrl.redownloadIfMissing
        )
    }

    fun stopAlbumFolderBatch() {
        albumBatchEngine.stopAlbumBatch()
    }

    fun removeAlbumFromHistory(normalizedAlbumUrl: String) {
        viewModelScope.launch {
            database.downloadDao().deleteDownloadedAlbum(normalizedAlbumUrl)
            showSnackbar("Removed album from history — can be downloaded again")
        }
    }

    fun clearAlbumHistory() {
        viewModelScope.launch {
            database.downloadDao().clearAllDownloadedAlbums()
            showSnackbar("Cleared downloaded albums history")
        }
    }

    fun verifyAndRedownloadMissingFiles() {
        downloadRepository.verifyAndRedownloadMissingTasks { count ->
            showSnackbar(
                if (count > 0) {
                    "Verified disk: $count missing file(s) re-queued from saved album links"
                } else {
                    "All completed files & album folders verified to exist on disk"
                }
            )
        }
    }

    fun verifyAndRedownloadMissingAlbums() {
        downloadRepository.verifyAndRedownloadMissingAlbums { count ->
            showSnackbar(
                if (count > 0) {
                    "Verified disk (history preserved): re-queued $count missing file(s) from saved album links"
                } else {
                    "All downloaded album folders and files exist on disk (history preserved)"
                }
            )
        }
    }

    fun redownloadAlbumByFolderOrUrl(
        folderName: String,
        albumUrl: String?,
        albumTitle: String = folderName.replace('_', ' ')
    ) {
        val resolvedUrl = albumUrl?.takeIf { it.isNotBlank() }
            ?: uiState.value.folderSourceLinks[folderName]
            ?: ""
        downloadRepository.redownloadAlbumFromUrl(
            albumUrl = resolvedUrl,
            folderName = folderName,
            albumTitle = albumTitle
        ) { count ->
            showSnackbar(
                if (count > 0) {
                    "Re-downloading $count file(s) for '$folderName' from source album"
                } else {
                    "All files in '$folderName' already exist on disk"
                }
            )
        }
    }

    fun syncFolderToGallery(folderName: String) {
        viewModelScope.launch {
            val uris = downloadRepository.prepareGalleryMediaUrisForFolder(folderName)
            showSnackbar(
                if (uris.isNotEmpty()) {
                    "Verified & indexed ${uris.size} media file(s) in 'Download/PulseGrab/$folderName'"
                } else {
                    "No completed media files found in 'Download/PulseGrab/$folderName' yet"
                }
            )
        }
    }

    fun syncAllFoldersToGallery() {
        downloadRepository.syncAllFoldersToGalleryBuckets { count ->
            showSnackbar(
                if (count > 0) {
                    "Consolidated & indexed $count media file(s) in Download/PulseGrab/<Album>"
                } else {
                    "All completed folders are unified in Download/PulseGrab"
                }
            )
        }
    }

    fun shareFolderBatchToTelegram(
        context: Context,
        folderName: String,
        tasksToShare: List<DownloadTask>,
        caption: String,
        preferTelegramDirect: Boolean = true
    ) {
        if (controlState.value.isPreparingTelegramShare) return
        val completed = tasksToShare.filter { it.status == DownloadStatus.COMPLETED }.distinctBy { it.id }
        if (completed.isEmpty()) {
            controlState.update { it.copy(telegramShareMessage = "No completed files selected. Finish or retry downloads first.") }
            return
        }
        controlState.update { it.copy(isPreparingTelegramShare = true, telegramShareMessage = null) }
        viewModelScope.launch {
            try {
                val uris = downloadRepository.prepareGalleryMediaUrisForFolder(
                    folderName = folderName,
                    specificTaskIds = completed.map { it.id }.toSet()
                )
                // Do not substitute an entire folder for a missing part or silently send fewer files.
                if (uris.size != completed.size) {
                    controlState.update { it.copy(telegramShareMessage =
                        "Found ${uris.size} of ${completed.size} files. Re-download missing files before sharing this part.") }
                    return@launch
                }
                com.example.ui.components.TelegramMediaShareHelper.shareMediaUrisToTelegram(
                    context = context, uris = uris,
                    hasImages = completed.any { it.mediaType == MediaType.IMAGE },
                    hasVideos = completed.any { it.mediaType == MediaType.VIDEO },
                    caption = caption, preferTelegramDirect = preferTelegramDirect,
                    onMessage = { message -> controlState.update { it.copy(telegramShareMessage = message) } }
                )
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                controlState.update { it.copy(telegramShareMessage = "Could not prepare files: ${error.localizedMessage ?: "storage unavailable"}") }
            } finally {
                controlState.update { it.copy(isPreparingTelegramShare = false) }
            }
        }
    }

    // Download actions
    fun downloadSelectedMedia() {
        val state = uiState.value
        val selectedItems = state.filteredMediaItems.filter { it.id in state.selectedIds }
        if (selectedItems.isEmpty()) {
            showSnackbar("Select at least one image or video to download")
            return
        }
        viewModelScope.launch {
            val count = downloadRepository.enqueueMediaItems(
                items = selectedItems,
                skipDuplicates = state.skipDuplicates,
                folderConfig = state.folderConfig
            )
            controlState.update {
                it.copy(
                    selectedIds = emptySet(),
                    snackbarMessage = if (count > 0) {
                        "Queued $count file${if (count > 1) "s" else ""} in '${state.folderConfig.activeSubfolderName}'"
                    } else {
                        "Selected files are already downloaded or in queue"
                    }
                )
            }
        }
    }

    fun downloadSingleMediaItem(item: DetectedMediaItem) {
        if (com.example.domain.usecase.UrlMediaUtils.isThumbnailOrHptUrl(item.url) ||
            com.example.domain.usecase.UrlMediaUtils.isThumbnailOrHptUrl(item.normalizedUrl)
        ) {
            showSnackbar("Skipping protected SEO thumbnail / HPT asset")
            return
        }
        viewModelScope.launch {
            val count = downloadRepository.enqueueMediaItems(
                items = listOf(item),
                skipDuplicates = false,
                folderConfig = controlState.value.folderConfig
            )
            if (count > 0) {
                showSnackbar("Downloading ${item.title} to '${controlState.value.folderConfig.activeSubfolderName}'")
            }
        }
    }

    fun setDownloadStatusFilter(status: DownloadStatus?) {
        controlState.update { it.copy(downloadFilterStatus = status) }
    }

    fun pauseDownload(taskId: String) = downloadRepository.pauseDownload(taskId)
    fun resumeDownload(taskId: String) = downloadRepository.resumeDownload(taskId)
    fun retryDownload(taskId: String) = downloadRepository.retryDownload(taskId)
    fun deleteDownload(taskId: String) = downloadRepository.deleteDownloadTask(taskId)
    fun clearCompletedDownloads() {
        downloadRepository.clearCompleted()
        showSnackbar("Cleared completed downloads")
    }

    fun clearUnfinishedAndErrorDownloads() {
        downloadRepository.clearAllUnfinishedAndError()
        showSnackbar("Cleaned all unfinished, hung, and failed downloads")
    }

    fun clearFailedDownloads() {
        downloadRepository.clearAllFailed()
        showSnackbar("Cleaned all failed downloads")
    }

    fun clearAllDownloads() {
        downloadRepository.clearAllDownloads()
        showSnackbar("Wiped all download tasks")
    }

    fun pauseAllActiveDownloads() {
        downloadRepository.pauseAllActive()
        showSnackbar("Paused all active transfers & unified album folders")
    }

    fun resumeAllPausedDownloads() {
        downloadRepository.resumeAllPaused()
        showSnackbar("Resumed all paused & unfinished transfers into unified album folders")
    }

    fun retryAllFailedAndUnfinishedDownloads() {
        downloadRepository.retryAllFailedAndUnfinished()
        showSnackbar("Smart Re-Download: Refreshing album pages & cookies for failed/paused transfers…")
    }

    fun smartRedownloadAllFailedDownloads() {
        downloadRepository.smartRedownloadAllFailed(includePaused = false) { count ->
            showSnackbar(
                if (count > 0) {
                    "Smart Re-Download: Refreshed album cookies & links for $count failed file(s)"
                } else {
                    "No failed downloads found to re-download"
                }
            )
        }
    }

    fun mergeAndUnifyExistingFolders() {
        downloadRepository.mergeAndUnifyAllExistingAndActiveFolders { count ->
            showSnackbar(
                if (count > 0) {
                    "Merged & unified $count existing/unfinished picture & video files into single album folders"
                } else {
                    "All existing & active pictures and videos are already unified in single album folders"
                }
            )
        }
    }

    fun pauseAlbumFolder(folderName: String) {
        downloadRepository.pauseAlbumFolder(folderName)
        showSnackbar("Paused album folder '$folderName'")
    }

    fun resumeOrRetryAlbumFolder(folderName: String) {
        downloadRepository.resumeOrRetryAlbumFolder(folderName)
        showSnackbar("Resuming & refreshing album folder '$folderName'")
    }

    fun smartRedownloadAlbumFolder(folderName: String) {
        downloadRepository.smartRedownloadAlbumFolder(folderName) { count ->
            showSnackbar(
                if (count > 0) {
                    "Smart Re-Download: Refreshed album '$folderName' & re-queued $count file(s)"
                } else {
                    "All files in '$folderName' are already completed"
                }
            )
        }
    }

    fun cleanAlbumFolderUnfinished(folderName: String) {
        downloadRepository.cleanAlbumFolderUnfinished(folderName)
        showSnackbar("Cleaned unfinished/failed files in '$folderName'")
    }

    fun showSnackbar(message: String) {
        controlState.update { it.copy(snackbarMessage = message) }
    }

    fun clearSnackbar() {
        controlState.update { it.copy(snackbarMessage = null) }
    }

    private fun appendDiscoveredMedia(newItems: List<DetectedMediaItem>) {
        if (newItems.isEmpty()) return
        val filtered = newItems.filterNot {
            UrlMediaUtils.isThumbnailOrHptUrl(it.url) || UrlMediaUtils.isThumbnailOrHptUrl(it.normalizedUrl)
        }
        if (filtered.isEmpty()) return
        controlState.update { current ->
            val map = LinkedHashMap<String, DetectedMediaItem>()
            for (existing in current.rawDetectedItems) {
                map[existing.normalizedUrl] = existing
            }
            for (incoming in filtered) {
                val prev = map[incoming.normalizedUrl]
                if (prev == null) {
                    map[incoming.normalizedUrl] = incoming
                } else {
                    val mergedWidth = maxOf(prev.width, incoming.width)
                    val mergedHeight = maxOf(prev.height, incoming.height)
                    map[incoming.normalizedUrl] = prev.copy(
                        width = mergedWidth,
                        height = mergedHeight
                    )
                }
            }
            current.copy(rawDetectedItems = map.values.toList())
        }
    }
    override fun onCleared() {
        downloadRepository.close()
        DomainCrawlerController.onMediaDiscoveredCallback = null
        super.onCleared()
    }

}
