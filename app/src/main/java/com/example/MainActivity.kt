package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Downloading
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.DownloadTask
import com.example.ui.AppDestination
import com.example.ui.MainDownloaderViewModel
import com.example.ui.components.DeepScanSheet
import com.example.ui.components.ExtensionFilterSheet
import com.example.ui.screens.DetectedMediaScreen
import com.example.ui.screens.DownloadsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MidnightBg
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.MidnightSurfaceElevated
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.VividViolet

class MainActivity : ComponentActivity() {

    private val viewModel: MainDownloaderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingShareIntent(intent)

        setContent {
            MyApplicationTheme {
                PulseGrabApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingShareIntent(intent)
    }

    private fun handleIncomingShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
            if (!sharedText.isNullOrBlank()) {
                val extractedUrl = Regex("""https?://\S+""").find(sharedText)?.value ?: sharedText
                viewModel.onUrlInputChange(extractedUrl)
                viewModel.openUrlInBrowser(extractedUrl)
            }
        }
    }
}

@Composable
fun PulseGrabApp(viewModel: MainDownloaderViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Notification permission result handled gracefully */ }

    fun ensureNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            val displayName = uri.lastPathSegment?.substringAfterLast(':') ?: "Custom_Folder"
            viewModel.setCustomTreeUri(uri.toString(), displayName)
        }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    // Back navigation from secondary screens to Home
    if (uiState.currentDestination != AppDestination.HOME) {
        BackHandler {
            viewModel.selectDestination(AppDestination.HOME)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MidnightBg,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!isWideScreen) {
                    PulseGrabBottomBar(
                        currentDestination = uiState.currentDestination,
                        detectedCount = uiState.allDetectedCount,
                        activeDownloads = uiState.activeDownloadCount,
                        onSelectDestination = viewModel::selectDestination
                    )
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWideScreen) {
                    PulseGrabNavigationRail(
                        currentDestination = uiState.currentDestination,
                        detectedCount = uiState.allDetectedCount,
                        activeDownloads = uiState.activeDownloadCount,
                        onSelectDestination = viewModel::selectDestination
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (uiState.currentDestination) {
                        AppDestination.HOME -> {
                            HomeScreen(
                                uiState = uiState,
                                scannerEngine = viewModel.webMediaScannerEngine,
                                onUrlInputChange = viewModel::onUrlInputChange,
                                onOpenUrlInBrowser = viewModel::openUrlInBrowser,
                                onCloseBrowser = viewModel::closeBrowserToHomeHub,
                                onUpdateBrowserPageState = viewModel::updateBrowserPageState,
                                onOpenPortal = viewModel::openPortalShortcut,
                                onQuickAccessClick = viewModel::openDetectedWithFilter,
                                onOpenExtensionSheet = { viewModel.setExtensionSheetOpen(true) },
                                onOpenDeepScanSheet = { viewModel.setDeepScanSheetOpen(true) },
                                onOpenCrawlerSheet = { viewModel.setCrawlerSheetOpen(true) },
                                onOpenFolderSheet = { viewModel.setFolderSheetOpen(true) },
                                onOpenAlbumBatchSheet = { viewModel.setAlbumBatchSheetOpen(true) },
                                onToggleSkipDuplicates = viewModel::setSkipDuplicates,
                                onNavigateToDownloads = {
                                    viewModel.selectDestination(AppDestination.DOWNLOADS)
                                },
                                onOpenCompletedFile = { task ->
                                    openDownloadedFile(context, task, viewModel::showSnackbar)
                                },
                                onShareCompletedFile = { task ->
                                    shareDownloadedFile(context, task, viewModel::showSnackbar)
                                },
                                onPauseAllDownloads = viewModel::pauseAllActiveDownloads,
                                onResumeAllDownloads = viewModel::resumeAllPausedDownloads,
                                onSmartRedownloadFailed = viewModel::smartRedownloadAllFailedDownloads,
                                onCleanUnfinishedDownloads = viewModel::clearUnfinishedAndErrorDownloads,
                                onShowMessage = viewModel::showSnackbar
                            )
                        }

                        AppDestination.DETECTED -> {
                            DetectedMediaScreen(
                                uiState = uiState,
                                onSelectTab = viewModel::setMediaTabFilter,
                                onToggleExtensionChip = viewModel::toggleExtensionChip,
                                onApplyExtensionPreset = viewModel::applyExtensionPreset,
                                onClearExtensionChips = viewModel::clearExtensionChips,
                                onToggleSkipDuplicates = viewModel::setSkipDuplicates,
                                onSearchQueryChange = viewModel::onSearchQueryChange,
                                onToggleItemSelection = viewModel::toggleMediaSelection,
                                onSelectAll = viewModel::selectAllVisibleMedia,
                                onDeselectAll = viewModel::deselectAllMedia,
                                onBulkDownloadSelected = {
                                    ensureNotificationPermissionIfNeeded()
                                    viewModel.downloadSelectedMedia()
                                },
                                onDownloadSingleItem = { item ->
                                    ensureNotificationPermissionIfNeeded()
                                    viewModel.downloadSingleMediaItem(item)
                                },
                                onPreviewItem = viewModel::setPreviewMediaItem,
                                onOpenExtensionSheet = { viewModel.setExtensionSheetOpen(true) },
                                onOpenDeepScanSheet = { viewModel.setDeepScanSheetOpen(true) },
                                onOpenCrawlerSheet = { viewModel.setCrawlerSheetOpen(true) },
                                onOpenFolderSheet = { viewModel.setFolderSheetOpen(true) },
                                onClearDetected = viewModel::clearDetectedMedia
                            )
                        }

                        AppDestination.DOWNLOADS -> {
                            DownloadsScreen(
                                uiState = uiState,
                                onSelectStatusFilter = viewModel::setDownloadStatusFilter,
                                onPauseTask = viewModel::pauseDownload,
                                onResumeTask = viewModel::resumeDownload,
                                onRetryTask = viewModel::retryDownload,
                                onDeleteTask = viewModel::deleteDownload,
                                onOpenCompletedFile = { task ->
                                    openDownloadedFile(context, task, viewModel::showSnackbar)
                                },
                                onShareCompletedFile = { task ->
                                    shareDownloadedFile(context, task, viewModel::showSnackbar)
                                },
                                onClearCompleted = viewModel::clearCompletedDownloads,
                                onClearUnfinishedAndError = viewModel::clearUnfinishedAndErrorDownloads,
                                onClearFailedOnly = viewModel::clearFailedDownloads,
                                onClearAllDownloads = viewModel::clearAllDownloads,
                                onPauseAllActive = viewModel::pauseAllActiveDownloads,
                                onResumeAllPaused = viewModel::resumeAllPausedDownloads,
                                onRetryAllUnfinished = viewModel::retryAllFailedAndUnfinishedDownloads,
                                onSmartRedownloadFailed = viewModel::smartRedownloadAllFailedDownloads,
                                onMergeExistingFolders = viewModel::mergeAndUnifyExistingFolders,
                                onPauseAlbumFolder = viewModel::pauseAlbumFolder,
                                onResumeOrRetryAlbumFolder = viewModel::resumeOrRetryAlbumFolder,
                                onSmartRedownloadAlbumFolder = viewModel::smartRedownloadAlbumFolder,
                                onCleanAlbumFolderUnfinished = viewModel::cleanAlbumFolderUnfinished,
                                onToggleFolderCollapsed = viewModel::toggleFolderCollapsed,
                                onToggleCollapseAllFolders = viewModel::toggleCollapseAllFolders,
                                onDownloadSearchQueryChange = viewModel::onDownloadSearchQueryChange,
                                onOpenTelegramShareForFolder = viewModel::openTelegramShareSheet,
                                onRedownloadAlbumByFolderOrUrl = viewModel::redownloadAlbumByFolderOrUrl,
                                onCopyAlbumLink = { url ->
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Album Link", url))
                                    viewModel.showSnackbar("Copied album link: $url")
                                },
                                onOpenAlbumLinkInBrowser = { url ->
                                    viewModel.onUrlInputChange(url)
                                    viewModel.openUrlInBrowser(url)
                                    viewModel.selectDestination(AppDestination.HOME)
                                },
                                onSyncAllFoldersToGallery = viewModel::syncAllFoldersToGallery,
                                onOpenLogDialog = { filterUrl -> viewModel.openLogDialog(filterUrl) },
                                onExploreDetectedMedia = {
                                    viewModel.selectDestination(AppDestination.DETECTED)
                                },
                                onOpenFolderSheet = { viewModel.setFolderSheetOpen(true) },
                                onOpenBrowser = {
                                    viewModel.selectDestination(AppDestination.HOME)
                                },
                                onVerifyAndRedownloadMissing = viewModel::verifyAndRedownloadMissingFiles
                            )
                        }
                    }
                }
            }

            if (uiState.isExtensionSheetOpen) {
                ExtensionFilterSheet(
                    config = uiState.extensionConfig,
                    onToggleImageExt = viewModel::toggleTargetImageExtension,
                    onToggleVideoExt = viewModel::toggleTargetVideoExtension,
                    onSelectAllExtensions = viewModel::selectAllTargetExtensions,
                    onAddCustomExtension = viewModel::addCustomExtension,
                    onRemoveCustomExtension = viewModel::removeCustomExtension,
                    onSetMinResolution = viewModel::setMinResolutionFilter,
                    onDismiss = { viewModel.setExtensionSheetOpen(false) }
                )
            }

            if (uiState.isFolderSheetOpen) {
                com.example.ui.components.FolderSelectionDialog(
                    folderConfig = uiState.folderConfig,
                    onSelectMode = viewModel::setFolderMode,
                    onUpdateCustomSubfolder = viewModel::setCustomSubfolderName,
                    onLaunchFolderPicker = { folderPickerLauncher.launch(null) },
                    onDismiss = { viewModel.setFolderSheetOpen(false) }
                )
            }

            if (uiState.isCrawlerSheetOpen) {
                com.example.ui.components.DomainCrawlerDialog(
                    baseUrl = uiState.crawlerBaseUrl,
                    onBaseUrlChange = viewModel::onCrawlerBaseUrlChange,
                    isAnalyzing = uiState.isAnalyzingCrawlerPatterns,
                    discoveredPatterns = uiState.discoveredPatterns,
                    crawlerProgress = uiState.crawlerProgress,
                    maxDepth = uiState.crawlerDepth,
                    maxPages = uiState.crawlerMaxPages,
                    sameDomainOnly = uiState.crawlerSameDomain,
                    onMaxDepthChange = viewModel::onCrawlerDepthChange,
                    onMaxPagesChange = viewModel::onCrawlerMaxPagesChange,
                    onSameDomainChange = viewModel::onCrawlerSameDomainChange,
                    onAnalyzePatterns = viewModel::analyzeCrawlerPatterns,
                    onTogglePatternSelection = viewModel::toggleCrawlerPatternSelection,
                    onSelectAllPatterns = viewModel::selectAllCrawlerPatterns,
                    onStartCrawl = { viewModel.startDomainCrawlerService(context) },
                    onStopCrawl = { viewModel.stopDomainCrawlerService(context) },
                    onViewDiscoveredMedia = {
                        viewModel.setCrawlerSheetOpen(false)
                        viewModel.selectDestination(AppDestination.DETECTED)
                    },
                    onDismiss = { viewModel.setCrawlerSheetOpen(false) }
                )
            }

            if (uiState.isDeepScanSheetOpen) {
                DeepScanSheet(
                    config = uiState.deepScanConfig,
                    progress = uiState.deepScanProgress,
                    extensionConfig = uiState.extensionConfig,
                    expandedPatternPreview = uiState.expandedPatternPreview,
                    onUpdateConfig = viewModel::updateDeepScanConfig,
                    onOpenExtensionRules = {
                        viewModel.setExtensionSheetOpen(true)
                    },
                    onStartScan = viewModel::startDeepScan,
                    onTogglePauseScan = viewModel::togglePauseDeepScan,
                    onStopScan = viewModel::stopDeepScan,
                    onViewDetectedResults = {
                        viewModel.setDeepScanSheetOpen(false)
                        viewModel.selectDestination(AppDestination.DETECTED)
                    },
                    onDismiss = { viewModel.setDeepScanSheetOpen(false) }
                )
            }

            if (uiState.isAlbumBatchSheetOpen) {
                com.example.ui.components.AlbumBatchGrabberSheet(
                    albumFolderUrl = uiState.albumFolderUrlInput,
                    strictJpgMp4Only = uiState.strictJpgMp4Only,
                    skipAlreadyDownloadedAlbums = uiState.skipAlreadyDownloadedAlbums,
                    redownloadIfMissing = uiState.redownloadIfMissing,
                    batchProgress = uiState.albumBatchProgress,
                    downloadedAlbumsHistory = uiState.downloadedAlbumsHistory,
                    onAlbumFolderUrlChange = viewModel::onAlbumFolderUrlChange,
                    onToggleStrictJpgMp4 = viewModel::setStrictJpgMp4Only,
                    onToggleSkipDownloadedAlbums = viewModel::setSkipAlreadyDownloadedAlbums,
                    onToggleRedownloadIfMissing = viewModel::setRedownloadIfMissing,
                    onVerifyAndRedownloadMissingAlbums = viewModel::verifyAndRedownloadMissingAlbums,
                    onStartAlbumBatch = {
                        ensureNotificationPermissionIfNeeded()
                        viewModel.startAlbumFolderBatch()
                    },
                    onStopAlbumBatch = viewModel::stopAlbumFolderBatch,
                    onRemoveAlbumFromHistory = viewModel::removeAlbumFromHistory,
                    onRedownloadAlbum = { album ->
                        ensureNotificationPermissionIfNeeded()
                        viewModel.redownloadAlbumByFolderOrUrl(
                            folderName = album.folderName,
                            albumUrl = album.albumUrl,
                            albumTitle = album.albumTitle
                        )
                    },
                    onClearAlbumHistory = viewModel::clearAlbumHistory,
                    onOpenDownloads = {
                        viewModel.setAlbumBatchSheetOpen(false)
                        viewModel.selectDestination(AppDestination.DOWNLOADS)
                    },
                    onOpenUrlInBrowserForCookies = { targetUrl ->
                        viewModel.setAlbumBatchSheetOpen(false)
                        viewModel.onUrlInputChange(targetUrl)
                        viewModel.openUrlInBrowser(targetUrl)
                    },
                    onDismiss = { viewModel.setAlbumBatchSheetOpen(false) }
                )
            }

            uiState.telegramShareFolderName?.let { shareFolder ->
                val folderTasks = uiState.downloadTasks.filter {
                    (it.folderName.ifBlank { "Default" }) == shareFolder
                }
                val albumRecord = uiState.downloadedAlbumsHistory.firstOrNull {
                    it.folderName.equals(shareFolder, ignoreCase = true)
                }
                val sourceUrl = uiState.folderSourceLinks[shareFolder] ?: albumRecord?.albumUrl
                com.example.ui.components.TelegramFolderShareSheet(
                    folderName = shareFolder,
                    folderTasks = folderTasks,
                    sourceAlbumUrl = sourceUrl,
                    onShareFolderBatchToTelegram = { tasksToShare, caption, preferTelegramDirect ->
                        viewModel.shareFolderBatchToTelegram(
                            context = context,
                            folderName = shareFolder,
                            tasksToShare = tasksToShare,
                            caption = caption,
                            preferTelegramDirect = preferTelegramDirect
                        )
                    },
                    onSyncFolderToGallery = {
                        viewModel.syncFolderToGallery(shareFolder)
                    },
                    onCopyCaption = { caption ->
                        com.example.ui.components.TelegramMediaShareHelper.copyCaptionToClipboard(
                            context = context,
                            caption = caption
                        )
                        viewModel.showSnackbar("Copied #Hashtag & album caption to clipboard!")
                    },
                    onDismiss = viewModel::closeTelegramShareSheet
                )
            }

            if (uiState.isLogDialogOpen) {
                com.example.ui.components.DiagnosticLogDialog(
                    initialFilterUrl = uiState.logDialogFilterUrl,
                    onDismissRequest = viewModel::closeLogDialog
                )
            }
        }
    }
}

@Composable
private fun PulseGrabBottomBar(
    currentDestination: AppDestination,
    detectedCount: Int,
    activeDownloads: Int,
    onSelectDestination: (AppDestination) -> Unit
) {
    NavigationBar(
        containerColor = MidnightSurface,
        modifier = Modifier.testTag("bottom_navigation_bar")
    ) {
        NavigationBarItem(
            selected = currentDestination == AppDestination.HOME,
            onClick = { onSelectDestination(AppDestination.HOME) },
            icon = {
                Icon(
                    imageVector = if (currentDestination == AppDestination.HOME) Icons.Filled.Home else Icons.Outlined.Home,
                    contentDescription = "Home"
                )
            },
            label = { Text("Home") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = ElectricCyan,
                selectedTextColor = ElectricCyan,
                indicatorColor = MidnightSurfaceElevated
            ),
            modifier = Modifier.testTag("nav_item_home")
        )

        NavigationBarItem(
            selected = currentDestination == AppDestination.DETECTED,
            onClick = { onSelectDestination(AppDestination.DETECTED) },
            icon = {
                BadgedBox(
                    badge = {
                        if (detectedCount > 0) {
                            Badge(
                                containerColor = ElectricCyan,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ) {
                                Text(detectedCount.toString())
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (currentDestination == AppDestination.DETECTED) Icons.Filled.Collections else Icons.Outlined.Collections,
                        contentDescription = "Detected Media"
                    )
                }
            },
            label = { Text("Detected") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = ElectricCyan,
                selectedTextColor = ElectricCyan,
                indicatorColor = MidnightSurfaceElevated
            ),
            modifier = Modifier.testTag("nav_item_detected")
        )

        NavigationBarItem(
            selected = currentDestination == AppDestination.DOWNLOADS,
            onClick = { onSelectDestination(AppDestination.DOWNLOADS) },
            icon = {
                BadgedBox(
                    badge = {
                        if (activeDownloads > 0) {
                            Badge(
                                containerColor = VividViolet,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            ) {
                                Text(activeDownloads.toString())
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (currentDestination == AppDestination.DOWNLOADS) Icons.Filled.Downloading else Icons.Outlined.Downloading,
                        contentDescription = "Downloads"
                    )
                }
            },
            label = { Text("Downloads") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = ElectricCyan,
                selectedTextColor = ElectricCyan,
                indicatorColor = MidnightSurfaceElevated
            ),
            modifier = Modifier.testTag("nav_item_downloads")
        )
    }
}

@Composable
private fun PulseGrabNavigationRail(
    currentDestination: AppDestination,
    detectedCount: Int,
    activeDownloads: Int,
    onSelectDestination: (AppDestination) -> Unit
) {
    NavigationRail(
        containerColor = MidnightSurface,
        modifier = Modifier.testTag("side_navigation_rail")
    ) {
        NavigationRailItem(
            selected = currentDestination == AppDestination.HOME,
            onClick = { onSelectDestination(AppDestination.HOME) },
            icon = {
                Icon(
                    imageVector = if (currentDestination == AppDestination.HOME) Icons.Filled.Home else Icons.Outlined.Home,
                    contentDescription = "Home"
                )
            },
            label = { Text("Home") }
        )

        NavigationRailItem(
            selected = currentDestination == AppDestination.DETECTED,
            onClick = { onSelectDestination(AppDestination.DETECTED) },
            icon = {
                BadgedBox(
                    badge = {
                        if (detectedCount > 0) {
                            Badge(containerColor = ElectricCyan) {
                                Text(detectedCount.toString())
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (currentDestination == AppDestination.DETECTED) Icons.Filled.Collections else Icons.Outlined.Collections,
                        contentDescription = "Detected Media"
                    )
                }
            },
            label = { Text("Detected") }
        )

        NavigationRailItem(
            selected = currentDestination == AppDestination.DOWNLOADS,
            onClick = { onSelectDestination(AppDestination.DOWNLOADS) },
            icon = {
                BadgedBox(
                    badge = {
                        if (activeDownloads > 0) {
                            Badge(containerColor = VividViolet) {
                                Text(activeDownloads.toString())
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (currentDestination == AppDestination.DOWNLOADS) Icons.Filled.Downloading else Icons.Outlined.Downloading,
                        contentDescription = "Downloads"
                    )
                }
            },
            label = { Text("Downloads") }
        )
    }
}

private fun openDownloadedFile(
    context: Context,
    task: DownloadTask,
    onError: (String) -> Unit
) {
    val uriStr = task.localUri
    if (uriStr.isNullOrBlank()) {
        onError("File URI is not available")
        return
    }
    try {
        val uri = Uri.parse(uriStr)
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, task.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(viewIntent, "Open ${task.fileName}"))
    } catch (_: Exception) {
        onError("No app found to open ${task.fileName}")
    }
}

private fun shareDownloadedFile(
    context: Context,
    task: DownloadTask,
    onError: (String) -> Unit
) {
    val uriStr = task.localUri
    if (uriStr.isNullOrBlank()) {
        onError("File is not ready to share yet")
        return
    }
    try {
        val uri = Uri.parse(uriStr)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = task.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Downloaded via PulseGrab: ${task.fileName}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Share ${task.fileName}").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (_: Exception) {
        onError("Unable to share ${task.fileName}")
    }
}
