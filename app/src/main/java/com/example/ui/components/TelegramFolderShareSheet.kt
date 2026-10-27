package com.example.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.DownloadStatus
import com.example.domain.model.DownloadTask
import com.example.domain.model.MediaType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramFolderShareSheet(
    folderName: String,
    folderTasks: List<DownloadTask>,
    sourceAlbumUrl: String?,
    onShareFolderBatchToTelegram: (List<DownloadTask>, String, Boolean) -> Unit,
    onSyncFolderToGallery: () -> Unit,
    onCopyCaption: (String) -> Unit,
    onDismiss: () -> Unit,
    isPreparing: Boolean = false,
    statusMessage: String? = null
) {
    // Never share queued, failed, or partial downloads as though they were complete.
    val completed = remember(folderTasks) {
        folderTasks.filter { it.status == DownloadStatus.COMPLETED }.sortedBy { it.createdAt }
    }
    val parts = remember(completed) { completed.chunked(10) }
    var hashtag by rememberSaveable(folderName) { mutableStateOf(TelegramMediaShareHelper.buildHashtag(folderName)) }
    var title by rememberSaveable(folderName) { mutableStateOf(folderName.replace('_', ' ')) }
    var includeSource by rememberSaveable(sourceAlbumUrl) { mutableStateOf(!sourceAlbumUrl.isNullOrBlank()) }
    var editCaption by rememberSaveable(folderName) { mutableStateOf(false) }
    fun caption(tasks: List<DownloadTask>, part: Int? = null) = TelegramMediaShareHelper.buildStructuredCaption(
        albumTitle = title, hashtag = hashtag,
        imageCount = tasks.count { it.mediaType == MediaType.IMAGE },
        videoCount = tasks.count { it.mediaType == MediaType.VIDEO },
        sourceUrl = sourceAlbumUrl, includeSourceUrl = includeSource,
        partIndex = part, totalParts = parts.size
    )
    val fullCaption = caption(completed)
    ModalBottomSheet(
        onDismissRequest = { if (!isPreparing) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("telegram_folder_share_sheet")
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Share to Telegram", style = MaterialTheme.typography.headlineSmall)
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                "${completed.size} completed files · ${completed.count { it.mediaType == MediaType.IMAGE }} photos · " +
                    "${completed.count { it.mediaType == MediaType.VIDEO }} videos",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "This opens Telegram, not an automatic upload. Choose a chat and confirm Send. " +
                    "Telegram controls grouping, file support and captions; paste the copied caption if needed.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (completed.isEmpty()) {
                Text("No completed files yet. Finish or retry your downloads before sharing.",
                    color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("telegram_empty_state"))
            }
            if (isPreparing) {
                LinearProgressIndicator(Modifier.fillMaxWidth().testTag("telegram_preparing_progress"))
                Text("Preparing files…", style = MaterialTheme.typography.bodyMedium)
            }
            statusMessage?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("telegram_share_status"))
            }
            if (parts.size <= 1) {
                Button(
                    onClick = { onShareFolderBatchToTelegram(completed, fullCaption, true) },
                    enabled = completed.isNotEmpty() && !isPreparing,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .testTag("share_entire_folder_telegram_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open Telegram · ${completed.size} files")
                }
            } else {
                Text("Share in parts", style = MaterialTheme.typography.titleSmall)
                Text("Up to 10 files per part. Return here for the next part after sending.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                parts.forEachIndexed { index, tasks ->
                    OutlinedButton(
                        onClick = { onShareFolderBatchToTelegram(tasks, caption(tasks, index + 1), true) },
                        enabled = !isPreparing,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("share_telegram_chunk_${index + 1}")
                    ) { Text("Part ${index + 1} of ${parts.size} · files ${index * 10 + 1}–${index * 10 + tasks.size}") }
                }
            }
            OutlinedButton(
                onClick = { onShareFolderBatchToTelegram(completed, fullCaption, false) },
                enabled = completed.isNotEmpty() && !isPreparing,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("share_entire_folder_chooser_button")
            ) {
                Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp)); Text("Share with another app")
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Caption", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { editCaption = !editCaption }) { Text(if (editCaption) "Done" else "Edit caption") }
            }
            if (editCaption) {
                OutlinedTextField(title, { title = it }, label = { Text("Album title") },
                    modifier = Modifier.fillMaxWidth().testTag("telegram_album_title_input"))
                OutlinedTextField(hashtag, { hashtag = it }, label = { Text("Hashtag") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("telegram_hashtag_input"))
                if (!sourceAlbumUrl.isNullOrBlank()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Include source link", Modifier.weight(1f))
                        Switch(includeSource, { includeSource = it })
                    }
                }
            }
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Text(fullCaption, Modifier.fillMaxWidth().padding(16.dp), style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { onCopyCaption(fullCaption) }, modifier = Modifier.testTag("copy_telegram_caption_button")) {
                Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp)); Text("Copy caption")
            }
            TextButton(onClick = onSyncFolderToGallery, enabled = !isPreparing) { Text("Refresh file index") }
        }
    }
}
