package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.domain.model.DetectedMediaItem
import com.example.domain.model.MediaTabFilter
import com.example.domain.model.MediaType
import com.example.ui.ExtensionChipPreset
import com.example.ui.MainUiState
import com.example.ui.components.GlassBackground
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassHeaderSurface
import com.example.ui.components.GlassPill
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.VividViolet

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DetectedMediaScreen(
    uiState: MainUiState,
    onSelectTab: (MediaTabFilter) -> Unit,
    onToggleExtensionChip: (String) -> Unit,
    onApplyExtensionPreset: (ExtensionChipPreset) -> Unit,
    onClearExtensionChips: () -> Unit,
    onToggleSkipDuplicates: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleItemSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onBulkDownloadSelected: () -> Unit,
    onDownloadSingleItem: (DetectedMediaItem) -> Unit,
    onPreviewItem: (DetectedMediaItem?) -> Unit,
    onOpenExtensionSheet: () -> Unit,
    onOpenDeepScanSheet: () -> Unit,
    onOpenCrawlerSheet: () -> Unit,
    onOpenFolderSheet: () -> Unit,
    onClearDetected: () -> Unit
) {
    var showSearchBar by rememberSaveable { mutableStateOf(false) }
    var isListView by rememberSaveable { mutableStateOf(false) }

    GlassBackground(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Streamlined 1DM-Inspired Glass Header ("File(s) detected!")
            GlassHeaderSurface(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Row 1: Title + Folder Destination Pill + Compact Action Icons
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Detected media",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "${uiState.filteredMediaItems.size} items",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onOpenFolderSheet() }
                                        .padding(vertical = 2.dp)
                                        .testTag("top_folder_selector_chip")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = "Destination folder",
                                        tint = EmeraldSuccess,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = uiState.folderConfig.activeSubfolderName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldSuccess,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Domain Crawler Pill
                            GlassPill(
                                selected = uiState.crawlerProgress.isScanning,
                                accentColor = if (uiState.crawlerProgress.isScanning) AmberWarning else VividViolet,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                onClick = onOpenCrawlerSheet,
                                modifier = Modifier.testTag("open_domain_crawler_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Explore,
                                    contentDescription = "Domain Crawler",
                                    tint = if (uiState.crawlerProgress.isScanning) AmberWarning else VividViolet,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (uiState.crawlerProgress.isScanning) "Crawling…" else "Crawler",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Pattern Scan Icon
                            IconButton(
                                onClick = onOpenDeepScanSheet,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("detected_deep_scan_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Radar,
                                    contentDescription = "Pattern Scan",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Search & Filter Toggle Icon
                            IconButton(
                                onClick = { showSearchBar = !showSearchBar },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search and quick filters",
                                    tint = if (showSearchBar || uiState.searchQuery.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Extension Grabber Rules Icon
                            IconButton(
                                onClick = onOpenExtensionSheet,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("detected_extension_rules_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = "Extension Grabber Rules",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Reset / Clear Detected
                            if (uiState.allDetectedCount > 0) {
                                IconButton(
                                    onClick = onClearDetected,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("clear_detected_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteSweep,
                                        contentDescription = "Clear detected list",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Row 2: Clean Glass Category Pills + List/Grid Toggle + Select All Checkbox
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().testTag("detected_media_tabs"),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            GlassPill(
                                selected = uiState.tabFilter == MediaTabFilter.ALL,
                                accentColor = MaterialTheme.colorScheme.primary,
                                contentPadding = PaddingValues(horizontal = 11.dp, vertical = 6.dp),
                                onClick = { onSelectTab(MediaTabFilter.ALL) },
                                modifier = Modifier.testTag("tab_all_media")
                            ) {
                                Text(
                                    text = "ALL (${uiState.allDetectedCount})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (uiState.tabFilter == MediaTabFilter.ALL) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            GlassPill(
                                selected = uiState.tabFilter == MediaTabFilter.IMAGES,
                                accentColor = EmeraldSuccess,
                                contentPadding = PaddingValues(horizontal = 11.dp, vertical = 6.dp),
                                onClick = { onSelectTab(MediaTabFilter.IMAGES) },
                                modifier = Modifier.testTag("tab_images")
                            ) {
                                Text(
                                    text = "IMG (${uiState.detectedImageCount})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (uiState.tabFilter == MediaTabFilter.IMAGES) EmeraldSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            GlassPill(
                                selected = uiState.tabFilter == MediaTabFilter.VIDEOS,
                                accentColor = VividViolet,
                                contentPadding = PaddingValues(horizontal = 11.dp, vertical = 6.dp),
                                onClick = { onSelectTab(MediaTabFilter.VIDEOS) },
                                modifier = Modifier.testTag("tab_videos")
                            ) {
                                Text(
                                    text = "VID (${uiState.detectedVideoCount})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (uiState.tabFilter == MediaTabFilter.VIDEOS) VividViolet else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // List vs Grid Toggle (1DM List View vs Thumbnail Grid)
                            IconButton(
                                onClick = { isListView = !isListView },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (isListView) Icons.Default.GridView else Icons.AutoMirrored.Filled.ViewList,
                                    contentDescription = "Switch View Mode",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            val allSelected = uiState.filteredMediaItems.isNotEmpty() &&
                                uiState.filteredMediaItems.all { it.id in uiState.selectedIds }

                            IconButton(
                                onClick = { if (allSelected) onDeselectAll() else onSelectAll() },
                                modifier = Modifier.size(48.dp).testTag("select_all_button")
                            ) {
                                Icon(
                                    imageVector = if (allSelected) Icons.Default.CheckBox else Icons.Default.SelectAll,
                                    contentDescription = if (allSelected) "Deselect all media" else "Select all visible media",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            if (uiState.selectedIds.isNotEmpty() && !allSelected) {
                                TextButton(
                                    onClick = onDeselectAll,
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier.testTag("deselect_all_button")
                                ) {
                                    Text("Clear", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    // Quick format presets are secondary; reveal them with search/filter controls.
                    if (showSearchBar || uiState.selectedExtensionChips.isNotEmpty()) {
                    // Row 3: Single Sleek Multi-Select Extension Chip Bar + Presets
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // All Formats Reset Chip
                        GlassPill(
                            selected = uiState.selectedExtensionChips.isEmpty(),
                            accentColor = MaterialTheme.colorScheme.primary,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                            onClick = { onApplyExtensionPreset(ExtensionChipPreset.ALL) },
                            modifier = Modifier.testTag("preset_all_extensions")
                        ) {
                            Text(
                                text = "All Formats",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (uiState.selectedExtensionChips.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Multi-select extension chips with live counts (JPG, PNG, WEBP, MP4, WEBM, etc.)
                        uiState.extensionCounts.entries.forEach { (ext, count) ->
                            val isSelected = ext in uiState.selectedExtensionChips
                            FilterChip(
                                selected = isSelected,
                                onClick = { onToggleExtensionChip(ext) },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                } else null,
                                label = {
                                    Text(
                                        text = "${ext.uppercase()} ($count)",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    selectedBorderColor = MaterialTheme.colorScheme.primary,
                                    borderColor = MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .heightIn(min = 32.dp)
                                    .testTag("multi_ext_chip_$ext")
                            )
                        }

                        // Quick Presets at end of scroll strip
                        GlassPill(
                            selected = false,
                            accentColor = EmeraldSuccess,
                            contentPadding = PaddingValues(horizontal = 9.dp, vertical = 5.dp),
                            onClick = { onApplyExtensionPreset(ExtensionChipPreset.PHOTOS) },
                            modifier = Modifier.testTag("preset_photos")
                        ) {
                            Text("Photos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        GlassPill(
                            selected = false,
                            accentColor = VividViolet,
                            contentPadding = PaddingValues(horizontal = 9.dp, vertical = 5.dp),
                            onClick = { onApplyExtensionPreset(ExtensionChipPreset.VIDEOS) },
                            modifier = Modifier.testTag("preset_videos")
                        ) {
                            Text("Videos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        GlassPill(
                            selected = false,
                            accentColor = MaterialTheme.colorScheme.primary,
                            contentPadding = PaddingValues(horizontal = 9.dp, vertical = 5.dp),
                            onClick = { onApplyExtensionPreset(ExtensionChipPreset.VECTORS) },
                            modifier = Modifier.testTag("preset_vectors")
                        ) {
                            Text("Vectors", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    }

                    // Collapsible Search & Skip Duplicates Bar (Zero Clutter by Default)
                    AnimatedVisibility(
                        visible = showSearchBar || uiState.searchQuery.isNotEmpty(),
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = uiState.searchQuery,
                                onValueChange = onSearchQueryChange,
                                placeholder = { Text("Filter by filename, URL, or extension…") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                trailingIcon = if (uiState.searchQuery.isNotEmpty()) {
                                    {
                                        IconButton(onClick = { onSearchQueryChange("") }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                        }
                                    }
                                } else null,
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("detected_search_input")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Skip duplicate URLs",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Switch(
                                    checked = uiState.skipDuplicates,
                                    onCheckedChange = onToggleSkipDuplicates,
                                    modifier = Modifier.testTag("skip_duplicates_switch")
                                )
                            }
                        }
                    }
                }
            }

            // Content Area: 1DM Glass List View OR Frosted Glass Grid View
            if (uiState.filteredMediaItems.isEmpty()) {
                EmptyDetectedState(
                    onOpenDeepScan = onOpenDeepScanSheet,
                    onOpenExtensions = onOpenExtensionSheet,
                    onOpenCrawler = onOpenCrawlerSheet
                )
            } else if (isListView) {
                // 1DM-Inspired Clean Glass List View (`image_168e95ab.png`)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("detected_media_grid"),
                    contentPadding = PaddingValues(
                        start = 14.dp,
                        end = 14.dp,
                        top = 12.dp,
                        bottom = if (uiState.selectedIds.isNotEmpty()) 110.dp else 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = uiState.filteredMediaItems,
                        key = { it.id }
                    ) { item ->
                        val isSelected = item.id in uiState.selectedIds
                        DetectedMedia1DMListRow(
                            item = item,
                            isSelected = isSelected,
                            onToggleSelect = { onToggleItemSelection(item.id) },
                            onPreview = { onPreviewItem(item) },
                            onQuickDownload = { onDownloadSingleItem(item) }
                        )
                    }
                }
            } else {
                // Frosted Glass Grid View
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 156.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("detected_media_grid"),
                    contentPadding = PaddingValues(
                        start = 14.dp,
                        end = 14.dp,
                        top = 12.dp,
                        bottom = if (uiState.selectedIds.isNotEmpty()) 110.dp else 24.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = uiState.filteredMediaItems,
                        key = { it.id }
                    ) { item ->
                        val isSelected = item.id in uiState.selectedIds
                        DetectedMediaGlassGridCard(
                            item = item,
                            isSelected = isSelected,
                            onToggleSelect = { onToggleItemSelection(item.id) },
                            onPreview = { onPreviewItem(item) },
                            onQuickDownload = { onDownloadSingleItem(item) }
                        )
                    }
                }
            }
        }

        // Sleek Floating Frosted Glass Bulk Download Bar (`CLEAR ALL` · `📁 Folder` · `DOWNLOAD`)
        AnimatedVisibility(
            visible = uiState.selectedIds.isNotEmpty(),
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF142038).copy(alpha = 0.95f),
                                Color(0xFF0B1222).copy(alpha = 0.97f)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, VividViolet)),
                        shape = RoundedCornerShape(22.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Folder Pill + Selection Count
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${uiState.selectedIds.size} selected",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onOpenFolderSheet() }
                                .padding(vertical = 2.dp)
                                .testTag("bottom_bar_folder_selector")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = EmeraldSuccess,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Save to: ${uiState.folderConfig.activeSubfolderName} (Change)",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldSuccess,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = onBulkDownloadSelected,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        modifier = Modifier.testTag("bulk_download_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "DOWNLOAD (${uiState.selectedIds.size})",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.Black
                        )
                    }
                }
            }
        }

        // Media Full-Screen Glass Preview Dialog
        uiState.previewMediaItem?.let { preview ->
            MediaPreviewDialog(
                item = preview,
                onDismiss = { onPreviewItem(null) },
                onDownload = {
                    onDownloadSingleItem(preview)
                    onPreviewItem(null)
                }
            )
        }
    }
}

/**
 * 1DM-Inspired Glass List Row (`image_168e95ab.png`):
 * Shows thumbnail/media badge, resolution pill, URL, filename, and 1-tap download/select.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetectedMedia1DMListRow(
    item: DetectedMediaItem,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onPreview: () -> Unit,
    onQuickDownload: () -> Unit
) {
    val accent = when {
        isSelected -> MaterialTheme.colorScheme.primary
        item.isAlreadyDownloaded -> EmeraldSuccess
        else -> null
    }

    GlassCard(
        shape = RoundedCornerShape(16.dp),
        accentColor = accent,
        elevated = isSelected,
        onClick = onToggleSelect,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("media_card_${item.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Thumbnail / Format Box
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                if (item.mediaType == MediaType.IMAGE) {
                    AsyncImage(
                        model = item.url,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayCircleFilled,
                        contentDescription = null,
                        tint = VividViolet,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(3.dp)
                ) {
                    Text(
                        text = item.extensionBadge,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (item.mediaType == MediaType.VIDEO) VividViolet else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Middle Info (Title up to 2 lines, URL, Wrapping Resolution Badges)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.url,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (item.hasDimensions) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = item.dimensionsLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                    if (item.crawlDepth > 0) {
                        Surface(
                            color = AmberWarning.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Depth L${item.crawlDepth}",
                                style = MaterialTheme.typography.labelSmall,
                                color = AmberWarning,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                    if (item.isAlreadyDownloaded) {
                        Surface(
                            color = EmeraldSuccess.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "SAVED",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldSuccess,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            IconButton(onClick = onPreview, modifier = Modifier.size(38.dp)) {
                Icon(
                    imageVector = Icons.Default.ZoomIn,
                    contentDescription = "Preview ${item.title}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onQuickDownload,
                modifier = Modifier
                    .size(38.dp)
                    .testTag("single_download_${item.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Download ${item.title}",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Icon(
                imageVector = if (isSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                contentDescription = if (isSelected) "Selected" else "Not selected",
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(20.dp)
            )
        }
    }
}

@Composable
private fun DetectedMediaGlassGridCard(
    item: DetectedMediaItem,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onPreview: () -> Unit,
    onQuickDownload: () -> Unit
) {
    val accent = when {
        isSelected -> MaterialTheme.colorScheme.primary
        item.isAlreadyDownloaded -> EmeraldSuccess
        else -> null
    }

    GlassCard(
        shape = RoundedCornerShape(18.dp),
        accentColor = accent,
        elevated = isSelected,
        onClick = onToggleSelect,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("media_card_${item.id}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.25f)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = if (item.mediaType == MediaType.VIDEO) {
                                    listOf(Color(0xFF1E1B4B), Color(0xFF0F172A))
                                } else {
                                    listOf(Color(0xFF0F172A), Color(0xFF1E293B))
                                }
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (item.mediaType == MediaType.VIDEO) Icons.Default.PlayCircleFilled else Icons.Default.Image,
                        contentDescription = null,
                        tint = if (item.mediaType == MediaType.VIDEO) VividViolet.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        modifier = Modifier.size(40.dp)
                    )
                }

                if (item.mediaType == MediaType.IMAGE) {
                    AsyncImage(
                        model = item.url,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Top-Left Format & Crawl Depth Glass Badge
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        color = if (item.mediaType == MediaType.VIDEO) VividViolet.copy(alpha = 0.88f) else MaterialTheme.colorScheme.background.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = item.extensionBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (item.mediaType == MediaType.VIDEO) Color.White else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (item.crawlDepth > 0) {
                        Surface(
                            color = AmberWarning.copy(alpha = 0.88f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "L${item.crawlDepth}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Black,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Top-Right Selection Indicator
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background.copy(alpha = 0.65f))
                        .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.Check else Icons.Default.CheckBoxOutlineBlank,
                        contentDescription = if (isSelected) "Selected" else "Not selected",
                        tint = if (isSelected) Color.Black else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Bottom-Left Dimensions Glass Badge
                if (item.hasDimensions) {
                    Surface(
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = item.dimensionsLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Bottom-Right Saved Indicator
                if (item.isAlreadyDownloaded) {
                    Surface(
                        color = EmeraldSuccess,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DownloadDone,
                                contentDescription = "Already downloaded",
                                tint = Color.Black,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "SAVED",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Black
                            )
                        }
                    }
                }
            }

            // Clean Card Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.url.substringAfter("://").substringBefore('/'),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onPreview,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomIn,
                        contentDescription = "Preview ${item.title}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                }

                IconButton(
                    onClick = onQuickDownload,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("single_download_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download ${item.title}",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyDetectedState(
    onOpenDeepScan: () -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenCrawler: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        GlassCard(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
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
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Collections,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Text(
                    text = "No Matching Media",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Browse a page, toggle extension filter chips above, or run the Domain Crawler to discover files.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onOpenCrawler,
                        colors = ButtonDefaults.buttonColors(containerColor = VividViolet)
                    ) {
                        Icon(imageVector = Icons.Default.Explore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Domain Crawler")
                    }
                    OutlinedButton(onClick = onOpenExtensions, modifier = Modifier.fillMaxWidth()) {
                        Icon(imageVector = Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Rules")
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaPreviewDialog(
    item: DetectedMediaItem,
    onDismiss: () -> Unit,
    onDownload: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = ".${item.extensionBadge}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.mediaType == MediaType.IMAGE) {
                        AsyncImage(
                            model = item.url,
                            contentDescription = item.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = null,
                                tint = VividViolet,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Direct Video Stream (${item.extensionBadge})",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = item.url,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.hasDimensions) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${item.dimensionsLabel} • ${item.mimeType}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onDownload,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = Color.Black
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Download", color = Color.Black)
                    }
                }
            }
        }
    }
}
