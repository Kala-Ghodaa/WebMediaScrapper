package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.DownloadStatus
import com.example.domain.model.DownloadTask
import com.example.domain.model.FormatUtils
import com.example.domain.model.MediaType
import com.example.ui.MainUiState
import com.example.ui.components.GlassBackground
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassHeaderSurface
import com.example.ui.components.GlassPill
import com.example.ui.components.TelegramMediaShareHelper
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.GlassBorderLight
import com.example.ui.theme.GlassSurfaceWhite
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VividViolet

@Composable
fun DownloadsScreen(
    uiState: MainUiState,
    onSelectStatusFilter: (DownloadStatus?) -> Unit,
    onPauseTask: (String) -> Unit,
    onResumeTask: (String) -> Unit,
    onRetryTask: (String) -> Unit,
    onDeleteTask: (String) -> Unit,
    onOpenCompletedFile: (DownloadTask) -> Unit,
    onShareCompletedFile: (DownloadTask) -> Unit,
    onClearCompleted: () -> Unit,
    onClearUnfinishedAndError: () -> Unit = {},
    onClearFailedOnly: () -> Unit = {},
    onClearAllDownloads: () -> Unit = {},
    onPauseAllActive: () -> Unit = {},
    onResumeAllPaused: () -> Unit = {},
    onRetryAllUnfinished: () -> Unit = {},
    onSmartRedownloadFailed: () -> Unit = {},
    onMergeExistingFolders: () -> Unit = {},
    onPauseAlbumFolder: (String) -> Unit = {},
    onResumeOrRetryAlbumFolder: (String) -> Unit = {},
    onSmartRedownloadAlbumFolder: (String) -> Unit = {},
    onCleanAlbumFolderUnfinished: (String) -> Unit = {},
    onToggleFolderCollapsed: (String) -> Unit = {},
    onToggleCollapseAllFolders: () -> Unit = {},
    onDownloadSearchQueryChange: (String) -> Unit = {},
    onOpenTelegramShareForFolder: (String) -> Unit = {},
    onRedownloadAlbumByFolderOrUrl: (folderName: String, albumUrl: String?, albumTitle: String) -> Unit = { _, _, _ -> },
    onCopyAlbumLink: (String) -> Unit = {},
    onOpenAlbumLinkInBrowser: (String) -> Unit = {},
    onSyncAllFoldersToGallery: () -> Unit = {},
    onOpenLogDialog: (String?) -> Unit = {},
    onExploreDetectedMedia: () -> Unit,
    onOpenFolderSheet: () -> Unit = {},
    onOpenBrowser: () -> Unit = {},
    onVerifyAndRedownloadMissing: () -> Unit = {}
) {
    val downloadingCount = uiState.downloadTasks.count {
        it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED
    }
    val pausedCount = uiState.downloadTasks.count { it.status == DownloadStatus.PAUSED }
    val finishedCount = uiState.downloadTasks.count { it.status == DownloadStatus.COMPLETED }
    val errorCount = uiState.downloadTasks.count {
        it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELLED
    }
    val unfinishedTotalCount = downloadingCount + pausedCount + errorCount

    val allVisibleFolders = uiState.filteredDownloadTasks
        .map { it.folderName.ifBlank { "PulseGrab" } }
        .distinct()
    val areAllCollapsed = allVisibleFolders.isNotEmpty() &&
        allVisibleFolders.all { uiState.collapsedFolderNames.contains(it) }
    var isSavedAlbumHistoryExpanded by rememberSaveable { mutableStateOf(false) }

    GlassBackground(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Responsive 2-Tier Glass Header + Search + Tab Bar + Bulk Action Strip
            GlassHeaderSurface(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Tier 1: Screen Title + Live Transfer Speed Telemetry Pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PulseGrab Downloads",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Live Transfer Speed Glass Pill (Tabular numerals prevent width jitter)
                        GlassPill(
                            selected = uiState.totalDownloadSpeed > 0,
                            accentColor = ElectricCyan,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = "Transfer Speed",
                                tint = ElectricCyan,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = FormatUtils.formatSpeed(uiState.totalDownloadSpeed),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFeatureSettings = "tnum"
                                ),
                                fontWeight = FontWeight.SemiBold,
                                color = ElectricCyan
                            )
                        }
                    }

                    // Tier 2: Destination Folder Path + Global Toolbar Icon Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onOpenFolderSheet() }
                                .padding(vertical = 4.dp, horizontal = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = EmeraldSuccess,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Download/PulseGrab/${uiState.folderConfig.activeSubfolderName} (Unified Folder)",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldSuccess,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // Diagnostic Logs Button
                            IconButton(
                                onClick = { onOpenLogDialog(null) },
                                modifier = Modifier
                                    .size(40.dp)
                                    .minimumInteractiveComponentSize()
                                    .testTag("open_diagnostic_logs_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = "Diagnostic Logs",
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            if (pausedCount + errorCount > 0) {
                                IconButton(
                                    onClick = onResumeAllPaused,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .minimumInteractiveComponentSize()
                                        .testTag("resume_all_icon_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Resume All",
                                        tint = EmeraldSuccess,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            if (downloadingCount > 0) {
                                IconButton(
                                    onClick = onPauseAllActive,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .minimumInteractiveComponentSize()
                                        .testTag("pause_all_icon_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Pause,
                                        contentDescription = "Pause All",
                                        tint = AmberWarning,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = onOpenFolderSheet,
                                modifier = Modifier
                                    .size(40.dp)
                                    .minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = "Destination Folder",
                                    tint = EmeraldSuccess,
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            IconButton(
                                onClick = onOpenBrowser,
                                modifier = Modifier
                                    .size(40.dp)
                                    .minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = "Open Browser",
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            if (unfinishedTotalCount > 0) {
                                IconButton(
                                    onClick = onClearUnfinishedAndError,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .minimumInteractiveComponentSize()
                                        .testTag("clean_unfinished_downloads_icon_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Clean all unfinished and failed downloads",
                                        tint = CoralError,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }

                            if (finishedCount > 0 || uiState.downloadedAlbumsHistory.isNotEmpty()) {
                                IconButton(
                                    onClick = onVerifyAndRedownloadMissing,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .minimumInteractiveComponentSize()
                                        .testTag("verify_missing_downloads_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Check & Re-download Missing Files from Disk",
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }

                            if (finishedCount > 0) {
                                IconButton(
                                    onClick = onClearCompleted,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .minimumInteractiveComponentSize()
                                        .testTag("clear_completed_downloads_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CleaningServices,
                                        contentDescription = "Clear completed downloads",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Tier 2.5: Search Bar (Search by Folder Name, #Hashtag, Filename, or Album Link)
                    if (uiState.downloadTasks.isNotEmpty() || uiState.downloadedAlbumsHistory.isNotEmpty() || uiState.downloadSearchQuery.isNotEmpty()) {
                        OutlinedTextField(
                            value = uiState.downloadSearchQuery,
                            onValueChange = onDownloadSearchQueryChange,
                            placeholder = {
                                Text(
                                    text = "Search folders, #hashtag, filenames, or album links…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search Downloads",
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = {
                                if (uiState.downloadSearchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { onDownloadSearchQueryChange("") },
                                        modifier = Modifier.testTag("clear_download_search_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear Search",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricCyan,
                                unfocusedBorderColor = GlassBorderLight,
                                focusedContainerColor = GlassSurfaceWhite,
                                unfocusedContainerColor = GlassSurfaceWhite
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("download_search_input")
                        )
                    }

                    // Status Filter Tabs (`ALL`, `DOWNLOADING`, `PAUSED`, `FINISHED`, `ERROR`)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val tabs: List<Triple<String, DownloadStatus?, Color>> = listOf(
                            Triple("ALL (${uiState.downloadTasks.size})", null, ElectricCyan),
                            Triple("DOWNLOADING ($downloadingCount)", DownloadStatus.DOWNLOADING, ElectricCyan),
                            Triple("PAUSED ($pausedCount)", DownloadStatus.PAUSED, AmberWarning),
                            Triple("FINISHED ($finishedCount)", DownloadStatus.COMPLETED, EmeraldSuccess),
                            Triple("ERROR ($errorCount)", DownloadStatus.FAILED, CoralError)
                        )

                        tabs.forEach { (label, status, accent) ->
                            val selected = uiState.downloadFilterStatus == status
                            GlassPill(
                                selected = selected,
                                accentColor = accent,
                                contentPadding = PaddingValues(horizontal = 13.dp, vertical = 7.dp),
                                onClick = { onSelectStatusFilter(status) },
                                modifier = Modifier.testTag("download_filter_${status?.name ?: "ALL"}")
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFeatureSettings = "tnum"
                                    ),
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) accent else TextSecondary
                                )
                            }
                        }
                    }

                    // 1-Tap Bulk Control & Cleanup Strip (Includes Collapse All / Expand All & Sync to Gallery)
                    if (uiState.downloadTasks.isNotEmpty() || uiState.downloadedAlbumsHistory.isNotEmpty()) {
                        val resumableCount = pausedCount + errorCount
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (allVisibleFolders.isNotEmpty()) {
                                GlassPill(
                                    selected = areAllCollapsed,
                                    accentColor = VividViolet,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                                    onClick = onToggleCollapseAllFolders,
                                    modifier = Modifier.testTag("collapse_all_folders_pill")
                                ) {
                                    Icon(
                                        imageVector = if (areAllCollapsed) Icons.Default.UnfoldMore else Icons.Default.UnfoldLess,
                                        contentDescription = null,
                                        tint = VividViolet,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = if (areAllCollapsed) "Expand All (${allVisibleFolders.size})" else "Collapse All (${allVisibleFolders.size})",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                        fontWeight = FontWeight.SemiBold,
                                        color = VividViolet
                                    )
                                }
                            }

                            if (finishedCount > 0) {
                                GlassPill(
                                    selected = false,
                                    accentColor = EmeraldSuccess,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                                    onClick = onSyncAllFoldersToGallery,
                                    modifier = Modifier.testTag("sync_all_gallery_pill")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Collections,
                                        contentDescription = null,
                                        tint = EmeraldSuccess,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Sync Albums to Telegram Gallery",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = EmeraldSuccess
                                    )
                                }
                            }

                            if (errorCount > 0) {
                                GlassPill(
                                    selected = true,
                                    accentColor = ElectricCyan,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                                    onClick = onSmartRedownloadFailed,
                                    modifier = Modifier.testTag("smart_redownload_failed_pill")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Smart Re-Download Failed ($errorCount)",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                        fontWeight = FontWeight.Bold,
                                        color = ElectricCyan
                                    )
                                }
                            }

                            GlassPill(
                                selected = resumableCount > 0,
                                accentColor = EmeraldSuccess,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                                onClick = onResumeAllPaused,
                                modifier = Modifier.testTag("resume_all_downloads_pill")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = EmeraldSuccess,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (resumableCount > 0) "Resume All ($resumableCount)" else "Resume All",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                    fontWeight = FontWeight.SemiBold,
                                    color = EmeraldSuccess
                                )
                            }

                            GlassPill(
                                selected = downloadingCount > 0,
                                accentColor = AmberWarning,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                                onClick = onPauseAllActive,
                                modifier = Modifier.testTag("pause_all_active_pill")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Pause,
                                    contentDescription = null,
                                    tint = AmberWarning,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (downloadingCount > 0) "Pause All ($downloadingCount)" else "Pause All",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                    fontWeight = FontWeight.SemiBold,
                                    color = AmberWarning
                                )
                            }

                            GlassPill(
                                selected = false,
                                accentColor = ElectricCyan,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                                onClick = onMergeExistingFolders,
                                modifier = Modifier.testTag("merge_existing_folders_pill")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Merge Pics & Videos Folders",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ElectricCyan
                                )
                            }

                            if (unfinishedTotalCount > 0) {
                                GlassPill(
                                    selected = true,
                                    accentColor = CoralError,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                                    onClick = onClearUnfinishedAndError,
                                    modifier = Modifier.testTag("clean_all_unfinished_pill")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = null,
                                        tint = CoralError,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Clean Unfinished/Hung ($unfinishedTotalCount)",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                        fontWeight = FontWeight.SemiBold,
                                        color = CoralError
                                    )
                                }
                            }

                            if (errorCount > 0) {
                                GlassPill(
                                    selected = false,
                                    accentColor = CoralError,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                                    onClick = onClearFailedOnly,
                                    modifier = Modifier.testTag("clean_errors_only_pill")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CleaningServices,
                                        contentDescription = null,
                                        tint = CoralError,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Clean Errors ($errorCount)",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                        color = CoralError
                                    )
                                }
                            }

                            if (errorCount + pausedCount > 0) {
                                GlassPill(
                                    selected = false,
                                    accentColor = ElectricCyan,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                                    onClick = onRetryAllUnfinished,
                                    modifier = Modifier.testTag("retry_all_unfinished_pill")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Retry All (${errorCount + pausedCount})",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                        color = ElectricCyan
                                    )
                                }
                            }

                            if (finishedCount > 0) {
                                GlassPill(
                                    selected = false,
                                    accentColor = EmeraldSuccess,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                                    onClick = onClearCompleted
                                ) {
                                    Text(
                                        text = "Clear Done ($finishedCount)",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                        color = EmeraldSuccess
                                    )
                                }
                            }

                            GlassPill(
                                selected = false,
                                accentColor = TextSecondary,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                                onClick = onClearAllDownloads,
                                modifier = Modifier.testTag("wipe_all_downloads_pill")
                            ) {
                                Text(
                                    text = "Wipe All (${uiState.downloadTasks.size})",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.filteredDownloadTasks.isEmpty() && uiState.downloadedAlbumsHistory.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    GlassCard(
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 480.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(ElectricCyan.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Downloading,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Text(
                                text = "No Transfers in This Tab",
                                style = MaterialTheme.typography.titleLarge,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Select detected images or videos and tap Download to stream them with pause, resume, and auto-recovery support.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = onExploreDetectedMedia,
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan)
                            ) {
                                Text(
                                    text = "Browse Detected Media",
                                    color = Color.Black
                                )
                            }
                        }
                    }
                }
            } else {
                val groupedByFolder = uiState.filteredDownloadTasks.groupBy {
                    it.folderName.ifBlank { "PulseGrab" }
                }
                val searchLower = uiState.downloadSearchQuery.trim().removePrefix("#").lowercase()
                val matchingSavedAlbums = if (searchLower.isEmpty()) {
                    uiState.downloadedAlbumsHistory
                } else {
                    uiState.downloadedAlbumsHistory.filter { album ->
                        album.albumTitle.lowercase().contains(searchLower) ||
                            album.folderName.lowercase().contains(searchLower) ||
                            album.albumUrl.lowercase().contains(searchLower)
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("downloads_list"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Saved Album History & Source Links Card (Always preserved even if files were deleted or tasks cleared)
                    if (matchingSavedAlbums.isNotEmpty()) {
                        item(key = "saved_album_history_section") {
                            Surface(
                                color = GlassSurfaceWhite,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, VividViolet.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("saved_album_history_card")
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                isSavedAlbumHistoryExpanded = !isSavedAlbumHistoryExpanded
                                            }
                                            .padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CollectionsBookmark,
                                                contentDescription = null,
                                                tint = VividViolet,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = "Saved Album History & Links (${matchingSavedAlbums.size})",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextPrimary
                                                )
                                                Text(
                                                    text = "Permanent album link history — tap Re-Download if any folder or files are missing",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = TextSecondary
                                                )
                                            }
                                        }
                                        Icon(
                                            imageVector = if (isSavedAlbumHistoryExpanded || groupedByFolder.isEmpty()) {
                                                Icons.Default.ExpandLess
                                            } else {
                                                Icons.Default.ExpandMore
                                            },
                                            contentDescription = "Toggle Saved Album History",
                                            tint = TextSecondary
                                        )
                                    }

                                    if (isSavedAlbumHistoryExpanded || groupedByFolder.isEmpty()) {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            matchingSavedAlbums.forEach { album ->
                                                Surface(
                                                    color = Color.Black.copy(alpha = 0.24f),
                                                    shape = RoundedCornerShape(10.dp),
                                                    border = BorderStroke(1.dp, GlassBorderLight),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(10.dp),
                                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = "📁 ${album.folderName} • ${album.mediaCount} files",
                                                                style = MaterialTheme.typography.labelMedium,
                                                                fontWeight = FontWeight.Bold,
                                                                color = EmeraldSuccess,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                            Text(
                                                                text = TelegramMediaShareHelper.buildHashtag(album.folderName),
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = ElectricCyan
                                                            )
                                                        }
                                                        if (album.albumUrl.isNotBlank()) {
                                                            Text(
                                                                text = "🔗 ${album.albumUrl}",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = ElectricCyan,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            GlassPill(
                                                                selected = true,
                                                                accentColor = ElectricCyan,
                                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                                                                onClick = {
                                                                    onRedownloadAlbumByFolderOrUrl(
                                                                        album.folderName,
                                                                        album.albumUrl,
                                                                        album.albumTitle
                                                                    )
                                                                },
                                                                modifier = Modifier.testTag("history_redownload_${album.folderName}")
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Refresh,
                                                                    contentDescription = null,
                                                                    tint = ElectricCyan,
                                                                    modifier = Modifier.size(13.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(4.dp))
                                                                Text(
                                                                    text = "Re-Download Album",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    fontWeight = FontWeight.SemiBold,
                                                                    color = ElectricCyan
                                                                )
                                                            }
                                                            if (album.albumUrl.isNotBlank()) {
                                                                GlassPill(
                                                                    selected = false,
                                                                    accentColor = TextPrimary,
                                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                                                                    onClick = { onCopyAlbumLink(album.albumUrl) }
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.ContentCopy,
                                                                        contentDescription = "Copy Link",
                                                                        tint = TextPrimary,
                                                                        modifier = Modifier.size(12.dp)
                                                                    )
                                                                    Spacer(modifier = Modifier.width(4.dp))
                                                                    Text(
                                                                        text = "Copy Link",
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = TextPrimary
                                                                    )
                                                                }
                                                                GlassPill(
                                                                    selected = false,
                                                                    accentColor = EmeraldSuccess,
                                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                                                                    onClick = { onOpenAlbumLinkInBrowser(album.albumUrl) }
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                                                        contentDescription = "Open Link",
                                                                        tint = EmeraldSuccess,
                                                                        modifier = Modifier.size(12.dp)
                                                                    )
                                                                    Spacer(modifier = Modifier.width(4.dp))
                                                                    Text(
                                                                        text = "Open Link",
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = EmeraldSuccess
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    groupedByFolder.forEach { (folderName, folderTasks) ->
                        val isCollapsed = uiState.collapsedFolderNames.contains(folderName)
                        val sourceAlbumUrl = uiState.folderSourceLinks[folderName]
                            ?: folderTasks.firstNotNullOfOrNull { it.refererUrl.takeIf { ref -> ref.isNotBlank() } }
                        item(key = "album_folder_header_$folderName") {
                            AlbumFolderMergedGlassBanner(
                                folderName = folderName,
                                tasks = folderTasks,
                                sourceAlbumUrl = sourceAlbumUrl,
                                isCollapsed = isCollapsed,
                                onToggleCollapse = { onToggleFolderCollapsed(folderName) },
                                onShareToTelegram = { onOpenTelegramShareForFolder(folderName) },
                                onRedownloadFromSourceLink = {
                                    onRedownloadAlbumByFolderOrUrl(
                                        folderName,
                                        sourceAlbumUrl,
                                        folderName.replace('_', ' ')
                                    )
                                },
                                onCopySourceLink = {
                                    if (!sourceAlbumUrl.isNullOrBlank()) {
                                        onCopyAlbumLink(sourceAlbumUrl)
                                    }
                                },
                                onOpenSourceLink = {
                                    if (!sourceAlbumUrl.isNullOrBlank()) {
                                        onOpenAlbumLinkInBrowser(sourceAlbumUrl)
                                    }
                                },
                                onOpenAlbumLogs = { onOpenLogDialog(folderName) },
                                onPauseAlbum = { onPauseAlbumFolder(folderName) },
                                onResumeOrRetryAlbum = { onResumeOrRetryAlbumFolder(folderName) },
                                onSmartRedownloadAlbum = { onSmartRedownloadAlbumFolder(folderName) },
                                onCleanAlbumUnfinished = { onCleanAlbumFolderUnfinished(folderName) }
                            )
                        }
                        if (!isCollapsed) {
                            items(
                                items = folderTasks,
                                key = { it.id }
                            ) { task ->
                                DownloadTask1DMGlassRow(
                                    task = task,
                                    onPause = { onPauseTask(task.id) },
                                    onResume = { onResumeTask(task.id) },
                                    onRetry = { onRetryTask(task.id) },
                                    onDelete = { onDeleteTask(task.id) },
                                    onOpen = { onOpenCompletedFile(task) },
                                    onShare = { onShareCompletedFile(task) },
                                    onOpenLogs = { onOpenLogDialog(task.url) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Merged Single-Folder Album Banner showing combined Pictures + Videos for that album
 * with interactive Collapsible drawers, aggregate progress bar, source album link,
 * 1-tap Share Folder to Telegram, Re-Download from Link, and per-album logs.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlbumFolderMergedGlassBanner(
    folderName: String,
    tasks: List<DownloadTask>,
    sourceAlbumUrl: String? = null,
    isCollapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onShareToTelegram: () -> Unit = {},
    onRedownloadFromSourceLink: () -> Unit = {},
    onCopySourceLink: () -> Unit = {},
    onOpenSourceLink: () -> Unit = {},
    onOpenAlbumLogs: () -> Unit,
    onPauseAlbum: () -> Unit,
    onResumeOrRetryAlbum: () -> Unit,
    onSmartRedownloadAlbum: () -> Unit,
    onCleanAlbumUnfinished: () -> Unit
) {
    val picCount = tasks.count { it.mediaType == MediaType.IMAGE }
    val vidCount = tasks.count { it.mediaType == MediaType.VIDEO }
    val activeCount = tasks.count {
        it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED
    }
    val failedCount = tasks.count {
        it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELLED
    }
    val pausedCount = tasks.count { it.status == DownloadStatus.PAUSED }
    val pausedOrFailedCount = pausedCount + failedCount
    val doneCount = tasks.count { it.status == DownloadStatus.COMPLETED }
    val unfinishedCount = activeCount + pausedOrFailedCount
    val totalCount = tasks.size
    val progressFloat = if (totalCount > 0) doneCount.toFloat() / totalCount else 0f
    val progressPercent = (progressFloat * 100).toInt()
    val hashtag = TelegramMediaShareHelper.buildHashtag(folderName)

    Surface(
        color = GlassSurfaceWhite,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, GlassBorderLight),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("album_folder_banner_$folderName")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top Clickable Header: Folder Path, Counts, Done Status, Chevron
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onToggleCollapse() }
                    .padding(vertical = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = EmeraldSuccess,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Download/PulseGrab/$folderName",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Text(
                            text = "$progressPercent%",
                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.Bold,
                            color = if (doneCount == totalCount) EmeraldSuccess else ElectricCyan
                        )
                    }
                    Text(
                        text = "$picCount JPG/IMG • $vidCount MP4/VID  |  $doneCount/$totalCount Done  •  $hashtag" +
                            if (failedCount > 0) " • $failedCount Failed" else "",
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = if (failedCount > 0) CoralError else TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onToggleCollapse,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("toggle_collapse_$folderName")
                ) {
                    Icon(
                        imageVector = if (isCollapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                        contentDescription = if (isCollapsed) "Expand Album" else "Collapse Album",
                        tint = TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Source Album Link Row (Shows the downloaded album link directly on each folder with Copy & Open buttons)
            if (!sourceAlbumUrl.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.22f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onCopySourceLink() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = sourceAlbumUrl,
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = onCopySourceLink,
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("copy_album_link_$folderName")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Album Link",
                                tint = TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        IconButton(
                            onClick = onOpenSourceLink,
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("open_album_link_$folderName")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Open Album Link",
                                tint = EmeraldSuccess,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // Aggregate Progress Bar
            LinearProgressIndicator(
                progress = { progressFloat },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = when {
                    failedCount > 0 -> CoralError
                    doneCount == totalCount -> EmeraldSuccess
                    else -> ElectricCyan
                },
                trackColor = Color.White.copy(alpha = 0.12f)
            )

            // Action Pills Row: Share to Telegram, Re-Download Album, Logs, Re-Download Failed, Resume, Pause, Clean
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (doneCount > 0) {
                    GlassPill(
                        selected = true,
                        accentColor = ElectricCyan,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        onClick = onShareToTelegram,
                        modifier = Modifier.testTag("share_folder_telegram_$folderName")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Share $folderName to Telegram",
                            tint = ElectricCyan,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Share to Telegram ($doneCount)",
                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.Bold,
                            color = ElectricCyan
                        )
                    }
                }

                if (!sourceAlbumUrl.isNullOrBlank()) {
                    GlassPill(
                        selected = false,
                        accentColor = EmeraldSuccess,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        onClick = onRedownloadFromSourceLink,
                        modifier = Modifier.testTag("redownload_album_link_$folderName")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Re-Download Album from Link",
                            tint = EmeraldSuccess,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Re-Download Album",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = EmeraldSuccess
                        )
                    }
                }

                // Per-Album Diagnostic Logs button
                GlassPill(
                    selected = false,
                    accentColor = ElectricCyan,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    onClick = onOpenAlbumLogs,
                    modifier = Modifier.testTag("album_logs_$folderName")
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = "Logs for $folderName",
                        tint = ElectricCyan,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Logs",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = ElectricCyan
                    )
                }

                if (failedCount > 0) {
                    GlassPill(
                        selected = true,
                        accentColor = ElectricCyan,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        onClick = onSmartRedownloadAlbum,
                        modifier = Modifier.testTag("album_redownload_failed_$folderName")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Re-Download Failed in Album",
                            tint = ElectricCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Re-Download ($failedCount)",
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
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        onClick = onResumeOrRetryAlbum
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Resume Album",
                            tint = EmeraldSuccess,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Resume ($pausedCount)",
                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                            color = EmeraldSuccess
                        )
                    }
                }

                if (activeCount > 0) {
                    GlassPill(
                        selected = true,
                        accentColor = AmberWarning,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        onClick = onPauseAlbum
                    ) {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = "Pause Album",
                            tint = AmberWarning,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Pause ($activeCount)",
                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                            color = AmberWarning
                        )
                    }
                }

                if (unfinishedCount > 0) {
                    GlassPill(
                        selected = false,
                        accentColor = CoralError,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        onClick = onCleanAlbumUnfinished
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Clean unfinished in this album",
                            tint = CoralError,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
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

/**
 * 1DM-Inspired Full-Row Glass Progress Download Item:
 * - Dynamic-height translucent progress fill (never clips when text wraps or expands)
 * - Up to 2 lines for filenames by default, with tap-to-expand full filename, URL, and folder path
 * - Tabular numerals (`tnum`) so live speed & byte updates never jitter
 * - Dedicated 40dp accessible action buttons that never overlap text
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DownloadTask1DMGlassRow(
    task: DownloadTask,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onOpenLogs: () -> Unit = {}
) {
    var isExpanded by rememberSaveable(task.id) { mutableStateOf(false) }

    val statusColor = when (task.status) {
        DownloadStatus.COMPLETED -> EmeraldSuccess
        DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> ElectricCyan
        DownloadStatus.PAUSED -> AmberWarning
        DownloadStatus.FAILED, DownloadStatus.CANCELLED -> CoralError
    }

    GlassCard(
        shape = RoundedCornerShape(16.dp),
        accentColor = statusColor,
        onClick = { isExpanded = !isExpanded },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("download_task_card_${task.id}")
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Dynamic-Height Full-Row Translucent Progress Fill Bar
            if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.PAUSED) {
                val fillFraction = if (task.totalBytes > 0L) {
                    task.progressFraction.coerceIn(0.03f, 1f)
                } else {
                    0.35f
                }
                Box(modifier = Modifier.matchParentSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fillFraction)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        statusColor.copy(alpha = 0.20f),
                                        statusColor.copy(alpha = 0.08f)
                                    )
                                )
                            )
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    // Format Icon Badge
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (task.mediaType == MediaType.VIDEO) VividViolet.copy(alpha = 0.18f)
                                else ElectricCyan.copy(alpha = 0.16f)
                            )
                            .border(
                                1.dp,
                                if (task.mediaType == MediaType.VIDEO) VividViolet.copy(alpha = 0.4f)
                                else ElectricCyan.copy(alpha = 0.4f),
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = if (task.mediaType == MediaType.VIDEO) Icons.Default.Videocam else Icons.Default.Image,
                                contentDescription = null,
                                tint = if (task.mediaType == MediaType.VIDEO) VividViolet else ElectricCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = task.extension.uppercase().take(4),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Center Telemetry Column (Filename + Status/Progress Metadata)
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = task.fileName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isExpanded) "Collapse details" else "Expand full filename and URL",
                                tint = TextSecondary,
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Responsive Wrapping Metadata Row (Size/Progress + Speed/Folder + Status)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val sizeLine = buildString {
                                if (task.status != DownloadStatus.COMPLETED && task.totalBytes > 0L) {
                                    append("${task.progressPercent}% • ")
                                }
                                append(FormatUtils.formatBytes(task.downloadedBytes))
                                if (task.totalBytes > 0L && task.status != DownloadStatus.COMPLETED) {
                                    append(" / ${FormatUtils.formatBytes(task.totalBytes)}")
                                }
                            }
                            Text(
                                text = sizeLine,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFeatureSettings = "tnum"
                                ),
                                color = TextSecondary
                            )

                            val middleTag = when {
                                task.status == DownloadStatus.DOWNLOADING && task.speedBytesPerSec > 0 ->
                                    FormatUtils.formatSpeed(task.speedBytesPerSec)
                                task.status == DownloadStatus.PAUSED -> "Resume: Yes"
                                task.folderName.isNotBlank() -> "📁 ${task.folderName}"
                                else -> ""
                            }
                            if (middleTag.isNotEmpty()) {
                                Text(
                                    text = middleTag,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFeatureSettings = "tnum"
                                    ),
                                    color = ElectricCyan,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            val rightStatus = when (task.status) {
                                DownloadStatus.COMPLETED -> "Completed"
                                DownloadStatus.DOWNLOADING -> "Downloading"
                                DownloadStatus.PAUSED -> "Paused"
                                DownloadStatus.FAILED -> "Error"
                                DownloadStatus.QUEUED -> "Queued"
                                DownloadStatus.CANCELLED -> "Cancelled"
                            }
                            Text(
                                text = rightStatus,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = statusColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Right 1-Tap Circular Glass Action Controls (Accessible 38dp targets)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        when (task.status) {
                            DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> {
                                IconButton(
                                    onClick = onPause,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(GlassSurfaceWhite)
                                        .border(1.dp, GlassBorderLight, CircleShape)
                                        .testTag("pause_download_${task.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Pause,
                                        contentDescription = "Pause ${task.fileName}",
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            DownloadStatus.PAUSED -> {
                                IconButton(
                                    onClick = onResume,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(ElectricCyan.copy(alpha = 0.2f))
                                        .border(1.dp, ElectricCyan, CircleShape)
                                        .testTag("resume_download_${task.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Resume ${task.fileName}",
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            DownloadStatus.FAILED, DownloadStatus.CANCELLED -> {
                                IconButton(
                                    onClick = onRetry,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(AmberWarning.copy(alpha = 0.2f))
                                        .border(1.dp, AmberWarning, CircleShape)
                                        .testTag("retry_download_${task.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Retry ${task.fileName}",
                                        tint = AmberWarning,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            DownloadStatus.COMPLETED -> {
                                IconButton(
                                    onClick = onShare,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .testTag("share_download_${task.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Share ${task.fileName}",
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = onOpen,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(EmeraldSuccess.copy(alpha = 0.2f))
                                        .border(1.dp, EmeraldSuccess, CircleShape)
                                        .testTag("open_download_${task.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = "Open ${task.fileName}",
                                        tint = EmeraldSuccess,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("delete_download_${task.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete ${task.fileName}",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Expandable Full Filename, Source URL & Destination Details (preserves full original text)
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.24f))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "File: ${task.fileName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary
                        )
                        Text(
                            text = "Path: Download/PulseGrab/${task.folderName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldSuccess
                        )
                        Text(
                            text = "Source URL: ${task.url}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = "View Diagnostic Logs →",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onOpenLogs() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Thin Bottom Luminous Progress Line for Active / Paused Downloads
                if (task.status == DownloadStatus.DOWNLOADING ||
                    task.status == DownloadStatus.PAUSED ||
                    task.status == DownloadStatus.QUEUED
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (task.totalBytes > 0L) {
                        LinearProgressIndicator(
                            progress = { task.progressFraction },
                            color = statusColor,
                            trackColor = GlassSurfaceWhite,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                        )
                    } else {
                        LinearProgressIndicator(
                            color = statusColor,
                            trackColor = GlassSurfaceWhite,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                        )
                    }
                }

                task.errorMessage?.let { errorMsg ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = errorMsg,
                        style = MaterialTheme.typography.labelSmall,
                        color = CoralError
                    )
                }
            }
        }
    }
}

