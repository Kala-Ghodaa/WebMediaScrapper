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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.ExtensionFilterConfig
import com.example.ui.theme.VividViolet

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExtensionFilterSheet(
    config: ExtensionFilterConfig,
    onToggleImageExt: (String) -> Unit,
    onToggleVideoExt: (String) -> Unit,
    onSelectAllExtensions: (Boolean) -> Unit,
    onAddCustomExtension: (String) -> Unit,
    onRemoveCustomExtension: (String) -> Unit,
    onSetMinResolution: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var customExtInput by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("extension_filter_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "File types",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Choose formats to detect and download",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row {
                    TextButton(
                        onClick = { onSelectAllExtensions(true) },
                        modifier = Modifier.testTag("ext_enable_all_button")
                    ) {
                        Text("All", color = MaterialTheme.colorScheme.primary)
                    }
                    TextButton(
                        onClick = { onSelectAllExtensions(false) },
                        modifier = Modifier.testTag("ext_clear_all_button")
                    ) {
                        Text("None", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Image Extensions Section
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Image Extensions (${config.enabledImageExtensions.size}/${ExtensionFilterConfig.ALL_IMAGE_EXTENSIONS.size})",
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExtensionFilterConfig.ALL_IMAGE_EXTENSIONS.forEach { ext ->
                    val selected = ext in config.enabledImageExtensions
                    FilterChip(
                        selected = selected,
                        onClick = { onToggleImageExt(ext) },
                        label = {
                            Text(
                                text = ".${ext.uppercase()}",
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        leadingIcon = if (selected) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            selectedBorderColor = MaterialTheme.colorScheme.primary,
                            borderColor = MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier.testTag("toggle_img_ext_$ext")
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Video & Audio Stream Extensions Section
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = null,
                    tint = VividViolet,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Video & Audio Extensions (${config.enabledVideoExtensions.size}/${ExtensionFilterConfig.ALL_VIDEO_EXTENSIONS.size})",
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExtensionFilterConfig.ALL_VIDEO_EXTENSIONS.forEach { ext ->
                    val selected = ext in config.enabledVideoExtensions
                    FilterChip(
                        selected = selected,
                        onClick = { onToggleVideoExt(ext) },
                        label = {
                            Text(
                                text = ".${ext.uppercase()}",
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        leadingIcon = if (selected) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VividViolet.copy(alpha = 0.22f),
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedLeadingIconColor = VividViolet
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            selectedBorderColor = VividViolet,
                            borderColor = MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier.testTag("toggle_vid_ext_$ext")
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(14.dp))

            // Custom Extensions Section
            Text(
                text = "Custom Media Extensions",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = customExtInput,
                    onValueChange = { customExtInput = it },
                    placeholder = { Text("Add format (e.g. flac, heic, ts)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("custom_extension_input")
                )
                Spacer(modifier = Modifier.width(10.dp))
                OutlinedButton(
                    onClick = {
                        if (customExtInput.isNotBlank()) {
                            onAddCustomExtension(customExtInput)
                            customExtInput = ""
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("add_custom_extension_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add custom extension")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add")
                }
            }

            if (config.customExtensions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    config.customExtensions.forEach { ext ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                            ) {
                                Text(
                                    text = ".${ext.uppercase()}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(
                                    onClick = { onRemoveCustomExtension(ext) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove $ext",
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Minimum Pixel Dimension Filter
            Text(
                text = "Minimum Resolution Threshold (Skip Tiny Icons)",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            val resolutionPresets = listOf(
                Triple("All Sizes", 0, 0),
                Triple("≥ 32×32 px", 32, 32),
                Triple("≥ 120×120 px", 120, 120),
                Triple("≥ 640×480 (SD)", 640, 480)
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                resolutionPresets.forEach { (label, w, h) ->
                    val selected = config.minWidthPx == w && config.minHeightPx == h
                    FilterChip(
                        selected = selected,
                        onClick = { onSetMinResolution(w, h) },
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("apply_extension_filter_button")
            ) {
                Text(
                    text = "Apply Extension Rules (${config.allEnabledExtensions.size} Active)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}
