package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.DeepScanProgress
import com.example.domain.model.DiscoveredPathPattern
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.VividViolet

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DomainCrawlerDialog(
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit,
    isAnalyzing: Boolean,
    discoveredPatterns: List<DiscoveredPathPattern>,
    crawlerProgress: DeepScanProgress,
    maxDepth: Int,
    maxPages: Int,
    sameDomainOnly: Boolean,
    onMaxDepthChange: (Int) -> Unit,
    onMaxPagesChange: (Int) -> Unit,
    onSameDomainChange: (Boolean) -> Unit,
    onAnalyzePatterns: () -> Unit,
    onTogglePatternSelection: (String) -> Unit,
    onSelectAllPatterns: (Boolean) -> Unit,
    onStartCrawl: () -> Unit,
    onStopCrawl: () -> Unit,
    onViewDiscoveredMedia: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("domain_crawler_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Explore,
                    contentDescription = null,
                    tint = VividViolet,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Site crawler",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Find media across linked pages on a site",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Base URL Input
            OutlinedTextField(
                value = baseUrl,
                onValueChange = onBaseUrlChange,
                label = { Text("Base URL to Crawl") },
                placeholder = { Text("https://commons.wikimedia.org/wiki/Featured") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("crawler_base_url_input")
            )

            // Pattern Analyzer Trigger Button
            Button(
                onClick = onAnalyzePatterns,
                enabled = !isAnalyzing && baseUrl.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = VividViolet),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("analyze_patterns_button")
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Analyzing Page Links & Path Patterns…")
                } else {
                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Analyze Common Path Patterns")
                }
            }

            // Discovered Path Patterns List
            if (discoveredPatterns.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Discovered Path Patterns (${discoveredPatterns.count { it.isSelected }}/${discoveredPatterns.size})",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Row {
                        TextButton(onClick = { onSelectAllPatterns(true) }) {
                            Text("All", color = MaterialTheme.colorScheme.primary)
                        }
                        TextButton(onClick = { onSelectAllPatterns(false) }) {
                            Text("None", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    discoveredPatterns.forEach { item ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                1.dp,
                                if (item.isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTogglePatternSelection(item.id) }
                                .testTag("pattern_chip_${item.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (item.isSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                    contentDescription = null,
                                    tint = if (item.isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = item.pattern,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (item.isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (item.isLikelyMediaGallery) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = EmeraldSuccess.copy(alpha = 0.18f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "GALLERY",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = EmeraldSuccess,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "${item.matchCount} sub-links • e.g. ${item.sampleUrl}",
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
            }

            // Crawl Recursion Depth & Max Pages Controls
            Text(
                text = "Recursion Depth & Max Sub-Pages",
                style = MaterialTheme.typography.titleSmall
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    1 to "Depth 1",
                    2 to "Depth 2",
                    3 to "Depth 3"
                ).forEach { (d, label) ->
                    FilterChip(
                        selected = maxDepth == d,
                        onClick = { onMaxDepthChange(d) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
                listOf(15, 30, 50).forEach { p ->
                    FilterChip(
                        selected = maxPages == p,
                        onClick = { onMaxPagesChange(p) },
                        label = { Text("$p pgs", style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Stay Within Base Domain",
                    style = MaterialTheme.typography.bodySmall
                )
                Switch(
                    checked = sameDomainOnly,
                    onCheckedChange = onSameDomainChange
                )
            }

            // Live Crawl Status Card
            if (crawlerProgress.isScanning || crawlerProgress.pagesScanned > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (crawlerProgress.isScanning) AmberWarning else EmeraldSuccess
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (crawlerProgress.isScanning) "Crawling Sub-Pages…" else "Crawl Completed",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (crawlerProgress.isScanning) AmberWarning else EmeraldSuccess
                            )
                            Text(
                                text = "${crawlerProgress.mediaFoundCount} Media Found",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        val frac = if (crawlerProgress.pagesTotalOrQueued > 0) {
                            (crawlerProgress.pagesScanned.toFloat() / crawlerProgress.pagesTotalOrQueued.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        LinearProgressIndicator(
                            progress = { frac },
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Scanned: ${crawlerProgress.pagesScanned} / ${crawlerProgress.pagesTotalOrQueued} pages • Current: ${crawlerProgress.currentScanningUrl}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (crawlerProgress.isScanning) {
                    Button(
                        onClick = onStopCrawl,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("stop_crawler_button")
                    ) {
                        Icon(imageVector = Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Stop Crawl")
                    }
                } else {
                    Button(
                        onClick = onStartCrawl,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("start_domain_crawler_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Start Recursive Crawl",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                if (crawlerProgress.mediaFoundCount > 0) {
                    OutlinedButton(
                        onClick = onViewDiscoveredMedia,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("crawler_view_media_button")
                    ) {
                        Text("View Media (${crawlerProgress.mediaFoundCount})")
                    }
                }
            }
        }
    }
}
