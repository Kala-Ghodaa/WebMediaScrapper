package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.scanner.WebMediaScannerEngine
import com.example.domain.model.DownloadStatus
import com.example.domain.model.DownloadTask
import com.example.domain.model.FormatUtils
import com.example.domain.model.MediaTabFilter
import com.example.domain.model.MediaType
import com.example.domain.model.PortalShortcut
import com.example.ui.MainUiState
import com.example.ui.components.GlassBackground
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassHeaderSurface
import com.example.ui.components.GlassPill
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.GlassBorderLight
import com.example.ui.theme.GlassBorderSubtle
import com.example.ui.theme.GlassSurfaceWhite
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VividViolet

@Composable
fun HomeScreen(
    uiState: MainUiState,
    scannerEngine: WebMediaScannerEngine,
    onUrlInputChange: (String) -> Unit,
    onOpenUrlInBrowser: (String) -> Unit,
    onCloseBrowser: () -> Unit,
    onUpdateBrowserPageState: (String?, Boolean, Int, String?) -> Unit,
    onOpenPortal: (PortalShortcut) -> Unit,
    onQuickAccessClick: (MediaTabFilter, String?) -> Unit,
    onOpenExtensionSheet: () -> Unit,
    onOpenDeepScanSheet: () -> Unit,
    onOpenCrawlerSheet: () -> Unit = {},
    onOpenAlbumBatchSheet: () -> Unit = {},
    onOpenFolderSheet: () -> Unit = {},
    onToggleSkipDuplicates: (Boolean) -> Unit = {},
    onNavigateToDownloads: () -> Unit,
    onOpenCompletedFile: (DownloadTask) -> Unit,
    onShareCompletedFile: (DownloadTask) -> Unit,
    onPauseAllDownloads: () -> Unit = {},
    onResumeAllDownloads: () -> Unit = {},
    onSmartRedownloadFailed: () -> Unit = {},
    onCleanUnfinishedDownloads: () -> Unit = {},
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    if (uiState.isBrowserOpen) {
        BackHandler {
            onCloseBrowser()
        }
        InAppBrowserInspector(
            uiState = uiState,
            scannerEngine = scannerEngine,
            onUrlInputChange = onUrlInputChange,
            onOpenUrl = {
                focusManager.clearFocus()
                onOpenUrlInBrowser(uiState.urlInput)
            },
            onCloseBrowser = onCloseBrowser,
            onUpdateBrowserPageState = onUpdateBrowserPageState,
            onOpenExtensionSheet = onOpenExtensionSheet,
            onOpenDeepScanSheet = onOpenDeepScanSheet,
            onOpenCrawlerSheet = onOpenCrawlerSheet,
            onOpenAlbumBatchSheet = onOpenAlbumBatchSheet,
            onOpenFolderSheet = onOpenFolderSheet,
            onToggleSkipDuplicates = onToggleSkipDuplicates,
            onNavigateToDownloads = onNavigateToDownloads,
            onViewDetectedMedia = { onQuickAccessClick(MediaTabFilter.ALL, null) }
        )
        return
    }

    GlassBackground(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1DM-Inspired Minimalist Glass Browser Omnibox Bar
            GlassBrowserTopBar(
                urlInput = uiState.urlInput,
                detectedCount = uiState.allDetectedCount,
                activeDownloadCount = uiState.activeDownloadCount,
                enabledFormatsCount = uiState.extensionConfig.allEnabledExtensions.size,
                skipDuplicates = uiState.skipDuplicates,
                activeFolderName = uiState.folderConfig.activeSubfolderName,
                isCrawlerRunning = uiState.crawlerProgress.isScanning,
                isDeepScanning = uiState.deepScanProgress.isScanning,
                showBackButton = false,
                onBackClick = {},
                onUrlInputChange = onUrlInputChange,
                onSubmitUrl = {
                    focusManager.clearFocus()
                    onOpenUrlInBrowser(uiState.urlInput)
                },
                onPasteClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val pasted = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
                    if (!pasted.isNullOrBlank()) {
                        onUrlInputChange(pasted)
                        onShowMessage("Pasted URL from clipboard")
                    } else {
                        val sampleUrl = "https://commons.wikimedia.org/wiki/Commons:Featured_pictures"
                        onUrlInputChange(sampleUrl)
                        onShowMessage("Clipboard empty — inserted sample media URL")
                    }
                },
                onGrabberBadgeClick = { onQuickAccessClick(MediaTabFilter.ALL, null) },
                onDownloadsClick = onNavigateToDownloads,
                onOpenExtensionSheet = onOpenExtensionSheet,
                onOpenDeepScanSheet = onOpenDeepScanSheet,
                onOpenCrawlerSheet = onOpenCrawlerSheet,
                onOpenAlbumBatchSheet = onOpenAlbumBatchSheet,
                onOpenFolderSheet = onOpenFolderSheet,
                onToggleSkipDuplicates = onToggleSkipDuplicates
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("home_screen_list"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Clean Glass Command Strip (Launch Browser + Album Batch + Pattern Scan + Folder)
                item {
                    GlassCommandHeroCard(
                        urlInput = uiState.urlInput,
                        enabledExtensionsCount = uiState.extensionConfig.allEnabledExtensions.size,
                        activeFolderName = uiState.folderConfig.activeSubfolderName,
                        isDeepScanning = uiState.deepScanProgress.isScanning,
                        isAlbumBatchRunning = uiState.albumBatchProgress.isScanning,
                        onOpenBrowserClick = {
                            focusManager.clearFocus()
                            onOpenUrlInBrowser(uiState.urlInput)
                        },
                        onOpenDeepScanClick = onOpenDeepScanSheet,
                        onOpenAlbumBatchClick = onOpenAlbumBatchSheet,
                        onOpenExtensionSheetClick = onOpenExtensionSheet,
                        onOpenFolderSheetClick = onOpenFolderSheet
                    )
                }

                // 3-Column Frosted Glass Detected Media Summary
                item {
                    QuickAccessMediaSection(
                        allCount = uiState.allDetectedCount,
                        imageCount = uiState.detectedImageCount,
                        videoCount = uiState.detectedVideoCount,
                        onSelectFilter = { filter -> onQuickAccessClick(filter, null) }
                    )
                }

                // Compact Glass Format Filter Pills
                item {
                    ExtensionGrabberQuickStrip(
                        enabledImageExts = uiState.extensionConfig.enabledImageExtensions,
                        enabledVideoExts = uiState.extensionConfig.enabledVideoExtensions,
                        customExts = uiState.extensionConfig.customExtensions,
                        onConfigureClick = onOpenExtensionSheet,
                        onChipClick = { ext ->
                            onQuickAccessClick(MediaTabFilter.ALL, ext)
                        }
                    )
                }

                // 2x2 Speed-Dial Glass Portals (Decluttered)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Speed Dial & Media Hubs",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextSecondary
                            )
                            Text(
                                text = "Tap to launch",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan
                            )
                        }

                        val chunkedPortals = uiState.portals.chunked(2)
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            chunkedPortals.forEach { rowPortals ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    rowPortals.forEach { portal ->
                                        PortalSpeedDialGlassTile(
                                            portal = portal,
                                            modifier = Modifier.weight(1f),
                                            onOpenPortal = { onOpenPortal(portal) },
                                            onDeepScanPortal = {
                                                onOpenPortal(portal)
                                                onOpenDeepScanSheet()
                                            }
                                        )
                                    }
                                    if (rowPortals.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }

                // Recent Transfers (1DM-Style Glass Rows with Dedicated Quick-Action Strip)
                item {
                    val unfinishedCount = uiState.downloadTasks.count { it.status != DownloadStatus.COMPLETED }
                    val activeCount = uiState.downloadTasks.count {
                        it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED
                    }
                    val pausedCount = uiState.downloadTasks.count { it.status == DownloadStatus.PAUSED }
                    val failedCount = uiState.downloadTasks.count {
                        it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELLED
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Recent Transfers",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextSecondary
                            )
                            if (uiState.downloadTasks.isNotEmpty()) {
                                TextButton(
                                    onClick = onNavigateToDownloads,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("view_all_downloads_button")
                                ) {
                                    Text(
                                        text = "All (${uiState.downloadTasks.size})",
                                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                        color = ElectricCyan
                                    )
                                }
                            }
                        }

                        if (failedCount > 0 || pausedCount > 0 || activeCount > 0 || unfinishedCount > 0) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (failedCount > 0) {
                                    GlassPill(
                                        selected = true,
                                        accentColor = ElectricCyan,
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        onClick = onSmartRedownloadFailed,
                                        modifier = Modifier.testTag("home_smart_redownload_failed_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            tint = ElectricCyan,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Re-Download Failed ($failedCount)",
                                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                            fontWeight = FontWeight.SemiBold,
                                            color = ElectricCyan
                                        )
                                    }
                                }
                                if (pausedCount > 0) {
                                    GlassPill(
                                        selected = true,
                                        accentColor = EmeraldSuccess,
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        onClick = onResumeAllDownloads,
                                        modifier = Modifier.testTag("home_resume_all_button")
                                    ) {
                                        Text(
                                            text = "Resume All ($pausedCount)",
                                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                            fontWeight = FontWeight.SemiBold,
                                            color = EmeraldSuccess
                                        )
                                    }
                                }
                                if (activeCount > 0) {
                                    GlassPill(
                                        selected = true,
                                        accentColor = AmberWarning,
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        onClick = onPauseAllDownloads,
                                        modifier = Modifier.testTag("home_pause_all_button")
                                    ) {
                                        Text(
                                            text = "Pause All ($activeCount)",
                                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                            fontWeight = FontWeight.SemiBold,
                                            color = AmberWarning
                                        )
                                    }
                                }
                                if (unfinishedCount > 0) {
                                    GlassPill(
                                        selected = false,
                                        accentColor = CoralError,
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        onClick = onCleanUnfinishedDownloads,
                                        modifier = Modifier.testTag("home_clean_unfinished_button")
                                    ) {
                                        Text(
                                            text = "Clean ($unfinishedCount)",
                                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                            color = CoralError
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (uiState.downloadTasks.isEmpty()) {
                    item {
                        GlassCard(
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(ElectricCyan.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Downloading,
                                        contentDescription = null,
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "No transfers yet",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "Browse any site or tap Detected Media to grab images & videos.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                } else {
                    items(
                        items = uiState.downloadTasks.take(4),
                        key = { it.id }
                    ) { task ->
                        RecentDownloadGlassRow(
                            task = task,
                            onOpen = { onOpenCompletedFile(task) },
                            onShare = { onShareCompletedFile(task) },
                            onClickCard = onNavigateToDownloads
                        )
                    }
                }
            }
        }
    }
}

/**
 * 1DM-Inspired Minimalist Glass Browser Omnibox Header (`image_aa133889.png` & `image_7785fc29.png`).
 * Consolidates URL input, Paste/Refresh, Tab counter, Live Grabber badge, and Glass Popover Menu.
 */
@Composable
private fun GlassBrowserTopBar(
    urlInput: String,
    detectedCount: Int,
    activeDownloadCount: Int,
    enabledFormatsCount: Int,
    skipDuplicates: Boolean,
    activeFolderName: String,
    isCrawlerRunning: Boolean,
    isDeepScanning: Boolean,
    showBackButton: Boolean,
    onBackClick: () -> Unit,
    onUrlInputChange: (String) -> Unit,
    onSubmitUrl: () -> Unit,
    onPasteClick: () -> Unit,
    onGrabberBadgeClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onOpenExtensionSheet: () -> Unit,
    onOpenDeepScanSheet: () -> Unit,
    onOpenCrawlerSheet: () -> Unit,
    onOpenAlbumBatchSheet: () -> Unit = {},
    onOpenFolderSheet: () -> Unit,
    onToggleSkipDuplicates: (Boolean) -> Unit,
    onReloadWebView: (() -> Unit)? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }

    GlassHeaderSurface(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (showBackButton) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("browser_back_home_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Home",
                        tint = TextPrimary
                    )
                }
            } else {
                // 1DM-style Glass Tab Counter Box `[1]`
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(GlassSurfaceWhite)
                        .border(1.dp, GlassBorderLight, RoundedCornerShape(9.dp))
                        .clickable { onSubmitUrl() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "1",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan
                    )
                }
            }

            // Pill-Shaped Frosted Glass Omnibox (`Search or Type URL`)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(
                        width = 1.dp,
                        brush = Brush.horizontalGradient(
                            listOf(GlassBorderLight, ElectricCyan.copy(alpha = 0.35f))
                        ),
                        shape = RoundedCornerShape(50)
                    )
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = EmeraldSuccess,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (urlInput.isEmpty()) {
                        Text(
                            text = "Search or type URL…",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            maxLines = 1
                        )
                    }
                    BasicTextField(
                        value = urlInput,
                        onValueChange = onUrlInputChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
                        cursorBrush = SolidColor(ElectricCyan),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(onGo = { onSubmitUrl() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(if (showBackButton) "browser_address_bar" else "url_input_field")
                    )
                }

                if (onReloadWebView != null) {
                    IconButton(
                        onClick = onReloadWebView,
                        modifier = Modifier
                            .size(30.dp)
                            .testTag("browser_rescan_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reload and Rescan",
                            tint = TextSecondary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = onPasteClick,
                        modifier = Modifier
                            .size(30.dp)
                            .testTag("paste_url_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Paste URL",
                            tint = ElectricCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Live Grabber Badge Button (1DM-style media capture indicator)
            BadgedBox(
                badge = {
                    if (detectedCount > 0) {
                        Badge(
                            containerColor = ElectricCyan,
                            contentColor = Color.Black
                        ) {
                            Text(detectedCount.toString())
                        }
                    }
                }
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (detectedCount > 0) ElectricCyan.copy(alpha = 0.18f)
                            else GlassSurfaceWhite
                        )
                        .border(
                            1.dp,
                            if (detectedCount > 0) ElectricCyan.copy(alpha = 0.6f) else GlassBorderSubtle,
                            CircleShape
                        )
                        .clickable(onClick = onGrabberBadgeClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Detected Media Grabber",
                        tint = if (detectedCount > 0) ElectricCyan else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 1DM-Style Glass Overflow Menu (`⋮`)
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("browser_glass_menu_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Browser & Grabber Options",
                        tint = TextPrimary
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    containerColor = MidnightSurface.copy(alpha = 0.96f),
                    border = BorderStroke(1.dp, GlassBorderLight),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("Capture Audio / Video / Images", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "$enabledFormatsCount extensions active",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricCyan
                                )
                            }
                        },
                        leadingIcon = {
                            Icon(Icons.Default.FilterList, contentDescription = null, tint = ElectricCyan)
                        },
                        trailingIcon = {
                            Icon(Icons.Default.CheckBox, contentDescription = null, tint = ElectricCyan)
                        },
                        onClick = {
                            menuExpanded = false
                            onOpenExtensionSheet()
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Skip Duplicate URLs", style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = {
                            Icon(Icons.Default.Collections, contentDescription = null, tint = EmeraldSuccess)
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = if (skipDuplicates) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                contentDescription = null,
                                tint = if (skipDuplicates) EmeraldSuccess else TextSecondary
                            )
                        },
                        onClick = {
                            onToggleSkipDuplicates(!skipDuplicates)
                        }
                    )

                    HorizontalDivider(color = GlassBorderSubtle)

                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("Album Folder Auto-Grabber", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "Skip old albums & save each in new <Title> folder",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldSuccess
                                )
                            }
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = EmeraldSuccess)
                        },
                        onClick = {
                            menuExpanded = false
                            onOpenAlbumBatchSheet()
                        }
                    )

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (isCrawlerRunning) "Domain Crawler (Running…)" else "Domain Crawler (Sub-pages)",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Explore,
                                contentDescription = null,
                                tint = if (isCrawlerRunning) AmberWarning else VividViolet
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onOpenCrawlerSheet()
                        }
                    )

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (isDeepScanning) "Pattern Scan (Scanning…)" else "Deep Pattern Scan [1-N]",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Radar, contentDescription = null, tint = ElectricCyan)
                        },
                        onClick = {
                            menuExpanded = false
                            onOpenDeepScanSheet()
                        }
                    )

                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("Download Folder", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    activeFolderName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldSuccess
                                )
                            }
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = EmeraldSuccess)
                        },
                        onClick = {
                            menuExpanded = false
                            onOpenFolderSheet()
                        }
                    )

                    DropdownMenuItem(
                        text = {
                            Text(
                                "Downloads Manager ($activeDownloadCount active)",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Downloading, contentDescription = null, tint = ElectricCyan)
                        },
                        onClick = {
                            menuExpanded = false
                            onDownloadsClick()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassCommandHeroCard(
    urlInput: String,
    enabledExtensionsCount: Int,
    activeFolderName: String,
    isDeepScanning: Boolean,
    isAlbumBatchRunning: Boolean,
    onOpenBrowserClick: () -> Unit,
    onOpenDeepScanClick: () -> Unit,
    onOpenAlbumBatchClick: () -> Unit,
    onOpenExtensionSheetClick: () -> Unit,
    onOpenFolderSheetClick: () -> Unit
) {
    GlassCard(
        shape = RoundedCornerShape(22.dp),
        accentColor = ElectricCyan,
        elevated = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hero_command_card")
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "PULSEGRAB GLASS ENGINE",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan
                    )
                    Text(
                        text = "Web Media & Album Grabber",
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                // Quick Folder Pill (No arbitrary character truncation)
                GlassPill(
                    selected = true,
                    accentColor = EmeraldSuccess,
                    onClick = onOpenFolderSheetClick,
                    modifier = Modifier.widthIn(max = 170.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Folder",
                        tint = EmeraldSuccess,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = activeFolderName,
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldSuccess,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Balanced 2x2 Action Grid: Gives every label generous horizontal room at any font scale
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Primary Glowing Glass Button: Open Browser
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(ElectricCyan, Color(0xFF00B4D8))
                                )
                            )
                            .clickable(onClick = onOpenBrowserClick)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("open_browser_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Browse URL",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.Black,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Glass Pill: Album Folder Batch Grabber
                    GlassPill(
                        selected = isAlbumBatchRunning,
                        accentColor = if (isAlbumBatchRunning) AmberWarning else EmeraldSuccess,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 11.dp),
                        onClick = onOpenAlbumBatchClick,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 46.dp)
                            .testTag("open_album_batch_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = if (isAlbumBatchRunning) AmberWarning else EmeraldSuccess,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isAlbumBatchRunning) "Batching…" else "Album Batch",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Glass Pill: Deep / Pattern Scan
                    GlassPill(
                        selected = isDeepScanning,
                        accentColor = if (isDeepScanning) AmberWarning else VividViolet,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        onClick = onOpenDeepScanClick,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .testTag("open_deep_scan_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Radar,
                            contentDescription = null,
                            tint = if (isDeepScanning) AmberWarning else VividViolet,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isDeepScanning) "Scanning…" else "Pattern Scan",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Glass Pill: Extension Rules
                    GlassPill(
                        selected = false,
                        accentColor = ElectricCyan,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        onClick = onOpenExtensionSheetClick,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .testTag("hero_extension_rules_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Extension rules",
                            tint = ElectricCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Formats ($enabledExtensionsCount)",
                            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                            color = ElectricCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtensionGrabberQuickStrip(
    enabledImageExts: Set<String>,
    enabledVideoExts: Set<String>,
    customExts: Set<String>,
    onConfigureClick: () -> Unit,
    onChipClick: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassPill(
            selected = true,
            accentColor = VividViolet,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
            onClick = onConfigureClick
        ) {
            Icon(
                imageVector = Icons.Default.FilterList,
                contentDescription = null,
                tint = VividViolet,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Formats",
                style = MaterialTheme.typography.labelSmall,
                color = VividViolet
            )
        }

        val allActive = (enabledImageExts.map { it to false } +
            enabledVideoExts.map { it to true } +
            customExts.map { it to false })

        allActive.forEach { (ext, isVid) ->
            val accent = if (isVid) VividViolet else ElectricCyan
            GlassPill(
                selected = false,
                accentColor = accent,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                onClick = { onChipClick(ext) }
            ) {
                Text(
                    text = ".${ext.uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent
                )
            }
        }
    }
}

@Composable
private fun QuickAccessMediaSection(
    allCount: Int,
    imageCount: Int,
    videoCount: Int,
    onSelectFilter: (MediaTabFilter) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        QuickAccessGlassCard(
            title = "All Media",
            count = allCount,
            accentColor = ElectricCyan,
            icon = Icons.Default.Collections,
            testTag = "quick_access_all",
            modifier = Modifier.weight(1f),
            onClick = { onSelectFilter(MediaTabFilter.ALL) }
        )
        QuickAccessGlassCard(
            title = "Images",
            count = imageCount,
            accentColor = EmeraldSuccess,
            icon = Icons.Default.Image,
            testTag = "quick_access_images",
            modifier = Modifier.weight(1f),
            onClick = { onSelectFilter(MediaTabFilter.IMAGES) }
        )
        QuickAccessGlassCard(
            title = "Videos",
            count = videoCount,
            accentColor = VividViolet,
            icon = Icons.Default.Videocam,
            testTag = "quick_access_videos",
            modifier = Modifier.weight(1f),
            onClick = { onSelectFilter(MediaTabFilter.VIDEOS) }
        )
    }
}

@Composable
private fun QuickAccessGlassCard(
    title: String,
    count: Int,
    accentColor: Color,
    icon: ImageVector,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    GlassCard(
        shape = RoundedCornerShape(18.dp),
        accentColor = accentColor,
        onClick = onClick,
        modifier = modifier.testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PortalSpeedDialGlassTile(
    portal: PortalShortcut,
    modifier: Modifier = Modifier,
    onOpenPortal: () -> Unit,
    onDeepScanPortal: () -> Unit
) {
    GlassCard(
        shape = RoundedCornerShape(16.dp),
        onClick = onOpenPortal,
        modifier = modifier.testTag("portal_card_${portal.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = ElectricCyan.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = portal.badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                IconButton(
                    onClick = onDeepScanPortal,
                    modifier = Modifier
                        .size(26.dp)
                        .testTag("portal_deepscan_${portal.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Radar,
                        contentDescription = "Deep scan ${portal.title}",
                        tint = VividViolet,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Text(
                text = portal.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = portal.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RecentDownloadGlassRow(
    task: DownloadTask,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onClickCard: () -> Unit
) {
    val accent = if (task.status == DownloadStatus.COMPLETED) EmeraldSuccess else ElectricCyan

    GlassCard(
        shape = RoundedCornerShape(14.dp),
        onClick = onClickCard,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("recent_download_${task.id}")
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Subtle full-row glass progress fill for active/paused transfers
            if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.PAUSED) {
                Box(modifier = Modifier.matchParentSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(task.progressFraction.coerceIn(0.04f, 1f))
                            .background(ElectricCyan.copy(alpha = 0.10f))
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (task.mediaType == MediaType.VIDEO) VividViolet.copy(alpha = 0.18f)
                            else ElectricCyan.copy(alpha = 0.16f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (task.mediaType == MediaType.VIDEO) Icons.Default.Videocam else Icons.Default.Image,
                        contentDescription = null,
                        tint = if (task.mediaType == MediaType.VIDEO) VividViolet else ElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val statusLabel = when (task.status) {
                        DownloadStatus.COMPLETED -> "Completed • ${FormatUtils.formatBytes(task.downloadedBytes)}"
                        DownloadStatus.DOWNLOADING -> "${task.progressPercent}% • ${FormatUtils.formatSpeed(task.speedBytesPerSec)}"
                        DownloadStatus.PAUSED -> "Paused • ${FormatUtils.formatBytes(task.downloadedBytes)}"
                        DownloadStatus.FAILED -> "Failed • Tap to view or retry"
                        else -> "Queued"
                    }
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = accent
                    )
                }

                if (task.status == DownloadStatus.COMPLETED) {
                    IconButton(onClick = onOpen, modifier = Modifier.size(38.dp)) {
                        Icon(
                            imageVector = Icons.Default.DownloadDone,
                            contentDescription = "Open ${task.fileName}",
                            tint = EmeraldSuccess,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onShare, modifier = Modifier.size(38.dp)) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share ${task.fileName}",
                            tint = ElectricCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun InAppBrowserInspector(
    uiState: MainUiState,
    scannerEngine: WebMediaScannerEngine,
    onUrlInputChange: (String) -> Unit,
    onOpenUrl: () -> Unit,
    onCloseBrowser: () -> Unit,
    onUpdateBrowserPageState: (String?, Boolean, Int, String?) -> Unit,
    onOpenExtensionSheet: () -> Unit,
    onOpenDeepScanSheet: () -> Unit,
    onOpenCrawlerSheet: () -> Unit,
    onOpenFolderSheet: () -> Unit,
    onOpenAlbumBatchSheet: () -> Unit = {},
    onToggleSkipDuplicates: (Boolean) -> Unit,
    onNavigateToDownloads: () -> Unit,
    onViewDetectedMedia: () -> Unit
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var webViewGeneration by remember { mutableIntStateOf(0) }

    GlassBackground(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1DM-Style Minimalist Glass Browser Omnibox Bar
            GlassBrowserTopBar(
                urlInput = uiState.urlInput,
                detectedCount = uiState.allDetectedCount,
                activeDownloadCount = uiState.activeDownloadCount,
                enabledFormatsCount = uiState.extensionConfig.allEnabledExtensions.size,
                skipDuplicates = uiState.skipDuplicates,
                activeFolderName = uiState.folderConfig.activeSubfolderName,
                isCrawlerRunning = uiState.crawlerProgress.isScanning,
                isDeepScanning = uiState.deepScanProgress.isScanning,
                showBackButton = true,
                onBackClick = onCloseBrowser,
                onUrlInputChange = onUrlInputChange,
                onSubmitUrl = onOpenUrl,
                onPasteClick = {},
                onGrabberBadgeClick = onViewDetectedMedia,
                onDownloadsClick = onNavigateToDownloads,
                onOpenExtensionSheet = onOpenExtensionSheet,
                onOpenDeepScanSheet = onOpenDeepScanSheet,
                onOpenCrawlerSheet = onOpenCrawlerSheet,
                onOpenFolderSheet = onOpenFolderSheet,
                onOpenAlbumBatchSheet = onOpenAlbumBatchSheet,
                onToggleSkipDuplicates = onToggleSkipDuplicates,
                onReloadWebView = {
                    webViewRef?.evaluateJavascript(WebMediaScannerEngine.DOM_SCANNER_JS, null)
                    webViewRef?.reload()
                }
            )

            // Hidden-capable compact accessibility test hooks for Deep Scan & Extension Rules
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0A101E))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📁 ${uiState.folderConfig.activeSubfolderName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldSuccess,
                    modifier = Modifier.clickable { onOpenFolderSheet() }
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Albums",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldSuccess,
                        modifier = Modifier
                            .clickable { onOpenAlbumBatchSheet() }
                            .testTag("browser_album_batch_button")
                    )
                    Text(
                        text = "Crawler",
                        style = MaterialTheme.typography.labelSmall,
                        color = VividViolet,
                        modifier = Modifier.clickable { onOpenCrawlerSheet() }
                    )
                    Text(
                        text = "Pattern Scan",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan,
                        modifier = Modifier
                            .clickable { onOpenDeepScanSheet() }
                            .testTag("browser_deep_scan_button")
                    )
                    Text(
                        text = "Formats (${uiState.extensionConfig.allEnabledExtensions.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan,
                        modifier = Modifier
                            .clickable { onOpenExtensionSheet() }
                            .testTag("browser_extension_rules_button")
                    )
                }
            }

            if (uiState.isPageLoading) {
                LinearProgressIndicator(
                    progress = { (uiState.pageProgress / 100f).coerceIn(0.05f, 1f) },
                    color = ElectricCyan,
                    trackColor = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                )
            }

            // WebView Content with automatic renderer-crash isolation & recovery
            key(webViewGeneration) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("in_app_webview"),
                    factory = { ctx ->
                        com.example.data.scanner.BrowserHttpBridge.purgeCorruptWebViewVariationsSeeds(ctx)
                        WebView(ctx).apply {
                            webViewRef = this
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true)
                            }
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadsImagesAutomatically = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            com.example.data.scanner.BrowserHttpBridge.syncWebViewSettings(this)

                            addJavascriptInterface(scannerEngine, "PulseGrabBridge")

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    onUpdateBrowserPageState(url, true, 15, null)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    runCatching { android.webkit.CookieManager.getInstance().flush() }
                                    onUpdateBrowserPageState(url, false, 100, view?.title)
                                    view?.evaluateJavascript(WebMediaScannerEngine.DOM_SCANNER_JS, null)
                                }

                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): WebResourceResponse? {
                                    val reqUrl = request?.url?.toString()
                                    if (!reqUrl.isNullOrBlank()) {
                                        scannerEngine.inspectNetworkRequest(
                                            requestUrl = reqUrl,
                                            pageUrl = uiState.activeBrowserUrl
                                        )
                                    }
                                    return super.shouldInterceptRequest(view, request)
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val target = request?.url?.toString().orEmpty()
                                    if (target.contains("showcase.pulsegrab.local")) {
                                        val pageNum = Regex("""page-(\d+)""")
                                            .find(target)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
                                        view?.loadDataWithBaseURL(
                                            target,
                                            WebMediaScannerEngine.buildShowcaseHtml(pageNum),
                                            "text/html",
                                            "UTF-8",
                                            null
                                        )
                                        return true
                                    }
                                    return false
                                }

                                override fun onRenderProcessGone(
                                    view: WebView?,
                                    detail: RenderProcessGoneDetail?
                                ): Boolean {
                                    runCatching {
                                        if (webViewRef === view) {
                                            webViewRef = null
                                        }
                                        (view?.parent as? ViewGroup)?.removeView(view)
                                        view?.destroy()
                                    }
                                    onUpdateBrowserPageState(uiState.activeBrowserUrl, false, 100, null)
                                    webViewGeneration++
                                    return true
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    super.onProgressChanged(view, newProgress)
                                    onUpdateBrowserPageState(view?.url, newProgress < 100, newProgress, view?.title)
                                    if (newProgress >= 65) {
                                        view?.evaluateJavascript(WebMediaScannerEngine.DOM_SCANNER_JS, null)
                                    }
                                }
                            }
                        }
                    },
                    update = { webView ->
                        val targetUrl = uiState.activeBrowserUrl
                        if (targetUrl.isNotBlank() && webView.tag != targetUrl) {
                            webView.tag = targetUrl
                            if (targetUrl.startsWith("pulsegrab://showcase") ||
                                targetUrl.contains("showcase.pulsegrab.local")
                            ) {
                                val pageNum = Regex("""page-(\d+)""")
                                    .find(targetUrl)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
                                webView.loadDataWithBaseURL(
                                    "https://showcase.pulsegrab.local/gallery/page-$pageNum",
                                    WebMediaScannerEngine.buildShowcaseHtml(pageNum),
                                    "text/html",
                                    "UTF-8",
                                    null
                                )
                            } else {
                                webView.loadUrl(targetUrl)
                            }
                        }
                    },
                    onRelease = { webView ->
                        runCatching {
                            if (webViewRef === webView) {
                                webViewRef = null
                            }
                            webView.stopLoading()
                            webView.webChromeClient = null
                            webView.removeAllViews()
                            webView.destroy()
                        }
                    }
                )
            }
        }

        // Sleek Floating Frosted Glass Grabber Pill (Bottom Right)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF0B1325).copy(alpha = 0.90f),
                            Color(0xFF14213D).copy(alpha = 0.92f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(listOf(ElectricCyan, VividViolet)),
                    shape = RoundedCornerShape(50)
                )
                .clickable(onClick = onViewDetectedMedia)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag("floating_detected_media_pill")
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${uiState.allDetectedCount} files (${uiState.detectedImageCount} IMG · ${uiState.detectedVideoCount} VID)",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary
                )
            }
        }
    }
}
