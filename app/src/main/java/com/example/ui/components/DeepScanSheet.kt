package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoAwesomeMotion
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.DeepScanConfig
import com.example.domain.model.DeepScanMode
import com.example.domain.model.DeepScanProgress
import com.example.domain.model.ExtensionFilterConfig
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.MidnightSurfaceElevated
import com.example.ui.theme.VividViolet

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DeepScanSheet(
    config: DeepScanConfig,
    progress: DeepScanProgress,
    extensionConfig: ExtensionFilterConfig,
    expandedPatternPreview: List<String>,
    onUpdateConfig: ((DeepScanConfig) -> DeepScanConfig) -> Unit,
    onOpenExtensionRules: () -> Unit,
    onStartScan: () -> Unit,
    onTogglePauseScan: () -> Unit,
    onStopScan: () -> Unit,
    onViewDetectedResults: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("deep_scan_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Radar,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Advanced Deep Domain & Pattern Scanner",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Multi-level domain crawling & repeating URL sequence harvester",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Mode Switch: Multi-Level Domain BFS vs URL Pattern / Sequence
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = config.mode == DeepScanMode.DOMAIN_BFS,
                    onClick = { onUpdateConfig { it.copy(mode = DeepScanMode.DOMAIN_BFS) } },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.AccountTree,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.testTag("mode_domain_bfs_button")
                ) {
                    Text("Multi-Level Domain")
                }
                SegmentedButton(
                    selected = config.mode == DeepScanMode.URL_PATTERN,
                    onClick = { onUpdateConfig { it.copy(mode = DeepScanMode.URL_PATTERN) } },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.AutoAwesomeMotion,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.testTag("mode_url_pattern_button")
                ) {
                    Text("URL Pattern [1-N]")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (config.mode == DeepScanMode.DOMAIN_BFS) {
                OutlinedTextField(
                    value = config.seedUrl,
                    onValueChange = { url -> onUpdateConfig { it.copy(seedUrl = url) } },
                    label = { Text("Starting Domain or Webpage URL") },
                    placeholder = { Text("https://example.com/gallery") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("deep_scan_seed_url_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = config.urlPattern,
                    onValueChange = { pat -> onUpdateConfig { it.copy(urlPattern = pat) } },
                    label = { Text("Optional Sub-URL Pattern Filter (* wildcard or [1-N])") },
                    placeholder = { Text("e.g. */gallery/* or https://site.com/page-[1-10]") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("deep_scan_pattern_input")
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Crawl Depth Level (1, 2, 3)
                Text(
                    text = "Multi-Level Crawl Depth (Follow Internal Links)",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        1 to "Depth 1 (Linked Pages)",
                        2 to "Depth 2 (Sub-Galleries)",
                        3 to "Depth 3 (Whole Domain)"
                    ).forEach { (depthVal, label) ->
                        val selected = config.maxDepth == depthVal
                        FilterChip(
                            selected = selected,
                            onClick = { onUpdateConfig { it.copy(maxDepth = depthVal) } },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                                selectedLabelColor = ElectricCyan
                            ),
                            modifier = Modifier.testTag("depth_chip_$depthVal")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Stay Within Same Domain Host",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Only follow links belonging to the starting domain",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = config.sameDomainOnly,
                        onCheckedChange = { same -> onUpdateConfig { it.copy(sameDomainOnly = same) } }
                    )
                }
            } else {
                // URL Pattern & Sequence Mode
                OutlinedTextField(
                    value = config.urlPattern,
                    onValueChange = { pat ->
                        onUpdateConfig { it.copy(urlPattern = pat, seedUrl = pat) }
                    },
                    label = { Text("Repeating URL Pattern ([start-end] or [001-050])") },
                    placeholder = { Text("https://site.com/gallery/page-[1-10] or img_[01-20].jpg") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pattern_mode_url_input")
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Quick Pattern Presets:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val presets = listOf(
                        "Showcase Pages [1-3]" to "https://showcase.pulsegrab.local/gallery/page-[1-3]",
                        "Direct Photos [10-18]" to "https://picsum.photos/id/[10-18]/1280/720.jpg",
                        "Zero-Padded [01-12]" to "https://showcase.pulsegrab.local/gallery/page-[01-12]"
                    )
                    presets.forEach { (label, presetUrl) ->
                        AssistChip(
                            onClick = {
                                onUpdateConfig { it.copy(urlPattern = presetUrl, seedUrl = presetUrl) }
                            },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                if (expandedPatternPreview.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = MidnightSurfaceElevated,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, VividViolet.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Generated Sequence Preview:",
                                style = MaterialTheme.typography.labelMedium,
                                color = VividViolet
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            expandedPatternPreview.forEach { previewUrl ->
                                Text(
                                    text = "• $previewUrl",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Max Pages Limit
            Text(
                text = "Max Pages / Pattern URLs to Scan",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(10, 25, 50, 100).forEach { limit ->
                    FilterChip(
                        selected = config.maxPages == limit,
                        onClick = { onUpdateConfig { it.copy(maxPages = limit) } },
                        label = { Text("$limit URLs", style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Active Extension Filter Summary Row
            Surface(
                color = MidnightSurfaceElevated,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Target Extensions (${extensionConfig.allEnabledExtensions.size} enabled)",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = extensionConfig.allEnabledExtensions.take(8)
                                .joinToString(", ") { ".${it.uppercase()}" } +
                                if (extensionConfig.allEnabledExtensions.size > 8) " …" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan
                        )
                    }
                    OutlinedButton(
                        onClick = onOpenExtensionRules,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Edit Formats")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Live Deep Scan Telemetry Card
            if (progress.isScanning || progress.pagesScanned > 0 || progress.errorMessage != null) {
                Surface(
                    color = MidnightSurfaceElevated,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        1.dp,
                        if (progress.isScanning) AmberWarning else EmeraldSuccess
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("deep_scan_telemetry_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (progress.isScanning) Icons.Default.Radar else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (progress.isScanning) AmberWarning else EmeraldSuccess,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = when {
                                        progress.isPaused -> "Deep Scan Paused (Depth ${progress.currentDepth})"
                                        progress.isScanning -> "Scanning Domain (Level ${progress.currentDepth})…"
                                        else -> "Deep Scan Finished"
                                    },
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            Text(
                                text = "${progress.mediaFoundCount} Media Grabbed",
                                style = MaterialTheme.typography.labelMedium,
                                color = ElectricCyan
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val fraction = if (progress.pagesTotalOrQueued > 0) {
                            (progress.pagesScanned.toFloat() / progress.pagesTotalOrQueued.toFloat()).coerceIn(0f, 1f)
                        } else 0f

                        LinearProgressIndicator(
                            progress = { fraction },
                            color = ElectricCyan,
                            trackColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Pages Scanned: ${progress.pagesScanned} / ${progress.pagesTotalOrQueued}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Matched Links: ${progress.matchedUrlsCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = VividViolet
                            )
                        }

                        if (progress.currentScanningUrl.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = progress.currentScanningUrl,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        progress.errorMessage?.let { err ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (progress.isScanning) {
                    OutlinedButton(
                        onClick = onTogglePauseScan,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 50.dp)
                            .testTag("pause_deep_scan_button")
                    ) {
                        Icon(
                            imageVector = if (progress.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (progress.isPaused) "Resume" else "Pause")
                    }
                    OutlinedButton(
                        onClick = onStopScan,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 50.dp)
                            .testTag("stop_deep_scan_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Stop", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    Button(
                        onClick = onStartScan,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 50.dp)
                            .testTag("start_deep_scan_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Radar,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Start Deep Scan",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }

                    if (progress.mediaFoundCount > 0) {
                        Button(
                            onClick = onViewDetectedResults,
                            colors = ButtonDefaults.buttonColors(containerColor = VividViolet),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 50.dp)
                                .testTag("view_deep_scan_results_button")
                        ) {
                            Text(
                                text = "View Media (${progress.mediaFoundCount})",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}
