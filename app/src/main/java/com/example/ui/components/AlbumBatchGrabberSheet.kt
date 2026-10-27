package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.AlbumBatchProgress
import com.example.domain.model.AlbumScrapeStatus
import com.example.domain.model.DownloadedAlbumRecord
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.VividViolet

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AlbumBatchGrabberSheet(
    albumFolderUrl: String,
    strictJpgMp4Only: Boolean,
    skipAlreadyDownloadedAlbums: Boolean,
    redownloadIfMissing: Boolean = true,
    batchProgress: AlbumBatchProgress,
    downloadedAlbumsHistory: List<DownloadedAlbumRecord>,
    onAlbumFolderUrlChange: (String) -> Unit,
    onToggleStrictJpgMp4: (Boolean) -> Unit,
    onToggleSkipDownloadedAlbums: (Boolean) -> Unit,
    onToggleRedownloadIfMissing: (Boolean) -> Unit = {},
    onVerifyAndRedownloadMissingAlbums: () -> Unit = {},
    onStartAlbumBatch: () -> Unit,
    onStopAlbumBatch: () -> Unit,
    onRemoveAlbumFromHistory: (String) -> Unit,
    onRedownloadAlbum: (DownloadedAlbumRecord) -> Unit = {},
    onClearAlbumHistory: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenUrlInBrowserForCookies: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("album_batch_grabber_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CollectionsBookmark,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Album grabber",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Scans each album, skips already-downloaded albums & saves all pictures + videos together into one single Download/PulseGrab/<Album_Title> folder",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Album Folder URL Input
            OutlinedTextField(
                value = albumFolderUrl,
                onValueChange = onAlbumFolderUrlChange,
                label = { Text("Album Folder / Category URL (or [1-N] range)") },
                placeholder = { Text("https://site.com/albums or site.com/albums/page-[1-5]") },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val pasted = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
                            if (!pasted.isNullOrBlank()) {
                                onAlbumFolderUrlChange(pasted)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Paste Album Folder URL",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("album_folder_url_input")
            )

            // Scraping Rules Summary Pill
            GlassCard(
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Skip Already Downloaded Albums",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${downloadedAlbumsHistory.size} album(s) recorded in database",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldSuccess
                            )
                        }
                        Switch(
                            checked = skipAlreadyDownloadedAlbums,
                            onCheckedChange = onToggleSkipDownloadedAlbums,
                            modifier = Modifier.testTag("skip_downloaded_albums_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Re-download If Folder/Files Missing",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Checks disk: if files exist, checks and skips; if deleted or missing, re-downloads",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (redownloadIfMissing) EmeraldSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = redownloadIfMissing,
                            onCheckedChange = onToggleRedownloadIfMissing,
                            modifier = Modifier.testTag("redownload_if_missing_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Strict .JPG / .JPEG / .MP4 Filter",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Excludes /thumb/ links, extracts <h1> title, data-src/data-full & /uploads/ CDN links",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = strictJpgMp4Only,
                            onCheckedChange = onToggleStrictJpgMp4,
                            modifier = Modifier.testTag("strict_jpg_mp4_switch")
                        )
                    }
                }
            }

            // Primary Start / Stop Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (batchProgress.isScanning) {
                    Button(
                        onClick = onStopAlbumBatch,
                        colors = ButtonDefaults.buttonColors(containerColor = CoralError),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("stop_album_batch_button")
                    ) {
                        Icon(imageVector = Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Stop Batch")
                    }
                } else {
                    Button(
                        onClick = onStartAlbumBatch,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("start_album_batch_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.Black
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Collect albums",
                            color = Color.Black,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                if (batchProgress.totalMediaQueued > 0) {
                    OutlinedButton(
                        onClick = onOpenDownloads,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Downloads (${batchProgress.totalMediaQueued})")
                    }
                }
            }

            // Anti-403 Cookie & Browser DOM Helper Row
            OutlinedButton(
                onClick = { onOpenUrlInBrowserForCookies(albumFolderUrl) },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("warm_session_in_browser_button")
            ) {
                Text(
                    text = "🛡 Open source in browser",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Live Batch Telemetry & Per-Album Log
            if (batchProgress.isScanning || batchProgress.albumsProcessed > 0 || batchProgress.errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        1.dp,
                        if (batchProgress.isScanning) MaterialTheme.colorScheme.primary else EmeraldSuccess.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = batchProgress.currentAlbumTitle.ifBlank { "Scanning…" },
                                style = MaterialTheme.typography.titleSmall,
                                color = if (batchProgress.isScanning) MaterialTheme.colorScheme.primary else EmeraldSuccess,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${batchProgress.albumsProcessed}/${batchProgress.totalAlbumsFound} albums",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (batchProgress.totalAlbumsFound > 0) {
                            val frac = (batchProgress.albumsProcessed.toFloat() / batchProgress.totalAlbumsFound.toFloat()).coerceIn(0f, 1f)
                            LinearProgressIndicator(
                                progress = { frac },
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                        }

                        // Counters row (FlowRow prevents overlap at large font scales)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "New Albums: ${batchProgress.newAlbumsDownloaded}",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldSuccess
                            )
                            Text(
                                text = "Skipped: ${batchProgress.skippedAlbumsCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = AmberWarning
                            )
                            Text(
                                text = "Files Queued: ${batchProgress.totalMediaQueued}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        batchProgress.errorMessage?.let { err ->
                            Text(
                                text = err,
                                style = MaterialTheme.typography.labelSmall,
                                color = CoralError
                            )
                        }

                        // Per-Album Results Log
                        if (batchProgress.albumLogs.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                batchProgress.albumLogs.take(10).forEach { log ->
                                    val badgeColor = when (log.status) {
                                        AlbumScrapeStatus.NEW_DOWNLOADED -> EmeraldSuccess
                                        AlbumScrapeStatus.SKIPPED_ALREADY_DOWNLOADED -> AmberWarning
                                        AlbumScrapeStatus.NO_MEDIA_FOUND -> MaterialTheme.colorScheme.onSurfaceVariant
                                        AlbumScrapeStatus.ERROR -> CoralError
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = when (log.status) {
                                                AlbumScrapeStatus.NEW_DOWNLOADED -> Icons.Default.FolderSpecial
                                                AlbumScrapeStatus.SKIPPED_ALREADY_DOWNLOADED -> Icons.Default.SkipNext
                                                else -> Icons.Default.DoneAll
                                            },
                                            contentDescription = null,
                                            tint = badgeColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = log.albumTitle,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = log.message,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = badgeColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Downloaded Albums History Section (So user can see & manage skipped albums)
            if (downloadedAlbumsHistory.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Downloaded Albums (${downloadedAlbumsHistory.size})",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = onVerifyAndRedownloadMissingAlbums,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Verify on Disk", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        TextButton(
                            onClick = onClearAlbumHistory,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Clear", style = MaterialTheme.typography.labelSmall, color = CoralError)
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    downloadedAlbumsHistory.forEach { album ->
                        GlassCard(
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderSpecial,
                                    contentDescription = null,
                                    tint = EmeraldSuccess,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = album.albumTitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "📁 ${album.folderName} • ${album.mediaCount} files",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldSuccess,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "🔗 ${album.albumUrl}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        clipboard?.setPrimaryClip(ClipData.newPlainText("Album Link", album.albumUrl))
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy album link",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onRedownloadAlbum(album) },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = "Re-download this album from saved link",
                                        tint = VividViolet,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onRemoveAlbumFromHistory(album.normalizedAlbumUrl) },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Remove from history",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(17.dp)
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
