package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.example.domain.model.DownloadFolderConfig
import com.example.domain.model.DownloadFolderMode
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.MidnightSurfaceElevated
import com.example.ui.theme.VividViolet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderSelectionDialog(
    folderConfig: DownloadFolderConfig,
    onSelectMode: (DownloadFolderMode) -> Unit,
    onUpdateCustomSubfolder: (String) -> Unit,
    onLaunchFolderPicker: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var customInput by remember { mutableStateOf(folderConfig.customSubfolderName) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("folder_selection_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Download Destination Folder",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Choose how grabs are organized into subfolders",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Option 1: Auto-Subfolder by Page Title / Grab Session
            FolderOptionCard(
                selected = folderConfig.folderMode == DownloadFolderMode.AUTO_BY_PAGE_TITLE,
                onClick = { onSelectMode(DownloadFolderMode.AUTO_BY_PAGE_TITLE) },
                icon = Icons.Default.CreateNewFolder,
                title = "Auto-Subfolder per Album / Webpage Title",
                description = "Saves ALL pictures & videos together in one single folder: Download/PulseGrab/${DownloadFolderConfig.sanitizeFolderName(folderConfig.currentPageTitle)}/",
                tag = "auto_title_folder_option"
            )

            // Option 2: Custom Named Subfolder
            FolderOptionCard(
                selected = folderConfig.folderMode == DownloadFolderMode.CUSTOM_SUBFOLDER,
                onClick = { onSelectMode(DownloadFolderMode.CUSTOM_SUBFOLDER) },
                icon = Icons.Default.FolderShared,
                title = "Custom Subfolder Name for this Grab",
                description = "Saves both pictures & videos together into Download/PulseGrab/<Custom_Name>/",
                tag = "custom_subfolder_option"
            ) {
                if (folderConfig.folderMode == DownloadFolderMode.CUSTOM_SUBFOLDER) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = customInput,
                        onValueChange = {
                            customInput = it
                            onUpdateCustomSubfolder(it)
                        },
                        label = { Text("Subfolder Name") },
                        placeholder = { Text("e.g. Wallpapers_2026") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_folder_name_input")
                    )
                }
            }

            // Option 3: Android Storage Access Framework (SAF) Custom Device Directory
            FolderOptionCard(
                selected = folderConfig.folderMode == DownloadFolderMode.SAF_CUSTOM_TREE,
                onClick = { onSelectMode(DownloadFolderMode.SAF_CUSTOM_TREE) },
                icon = Icons.Default.FolderOpen,
                title = "Pick Custom Device Folder (SAF)",
                description = if (!folderConfig.customTreeDisplayName.isNullOrBlank()) {
                    "Currently selected: ${folderConfig.customTreeDisplayName}"
                } else {
                    "Select any directory on internal storage or SD card via the system folder picker"
                },
                tag = "saf_device_folder_option"
            ) {
                if (folderConfig.folderMode == DownloadFolderMode.SAF_CUSTOM_TREE) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onLaunchFolderPicker,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("launch_system_folder_picker_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = VividViolet,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (folderConfig.customTreeDisplayName.isNullOrBlank()) {
                                "Browse & Pick Folder…"
                            } else {
                                "Change Folder (${folderConfig.customTreeDisplayName})"
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("confirm_folder_selection_button")
            ) {
                Text(
                    text = "Apply: ${folderConfig.displayLabel}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
private fun FolderOptionCard(
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    tag: String,
    extraContent: (@Composable () -> Unit)? = null
) {
    Surface(
        color = MidnightSurfaceElevated,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            if (selected) ElectricCyan else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selected,
                    onClick = onClick,
                    colors = RadioButtonDefaults.colors(selectedColor = ElectricCyan)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            extraContent?.invoke()
        }
    }
}
