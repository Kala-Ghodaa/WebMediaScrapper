package com.example.ui.components

import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import java.io.File
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.DownloadStatus
import com.example.domain.model.DownloadTask
import com.example.domain.model.MediaType
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.GlassBorderSubtle
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.MidnightSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VividViolet

object TelegramMediaShareHelper {

    private val TELEGRAM_PACKAGES = listOf(
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.telegram.plus",
        "org.thunderdog.challegram",
        "tw.nekomimi.nekogram"
    )

    fun buildHashtag(rawFolderName: String): String {
        val cleaned = rawFolderName
            .trim()
            .replace(Regex("""[^a-zA-Z0-9_]+"""), "_")
            .replace(Regex("""_+"""), "_")
            .trim('_')
            .ifBlank { "PulseGrab_Album" }
        val prefixed = if (cleaned.first().isDigit()) "Album_$cleaned" else cleaned
        return "#$prefixed"
    }

    fun buildStructuredCaption(
        albumTitle: String,
        hashtag: String,
        imageCount: Int,
        videoCount: Int,
        sourceUrl: String? = null,
        includeSourceUrl: Boolean = true,
        partIndex: Int? = null,
        totalParts: Int? = null
    ): String {
        val normalizedTag = if (hashtag.trim().startsWith("#")) hashtag.trim() else "#${hashtag.trim()}"
        val partSuffix = if (partIndex != null && totalParts != null && totalParts > 1) {
            " (Part $partIndex/$totalParts)"
        } else {
            ""
        }
        val countsParts = buildList {
            if (imageCount > 0) add("🖼 $imageCount Photo${if (imageCount > 1) "s" else ""}")
            if (videoCount > 0) add("🎬 $videoCount Video${if (videoCount > 1) "s" else ""}")
        }.joinToString(" • ")

        return buildString {
            append(normalizedTag)
            append("\n📁 ")
            append(albumTitle.trim().ifBlank { "PulseGrab Album" })
            append(partSuffix)
            if (countsParts.isNotEmpty()) {
                append("\n")
                append(countsParts)
            }
            if (includeSourceUrl && !sourceUrl.isNullOrBlank()) {
                append("\n🔗 ")
                append(sourceUrl.trim())
            }
        }
    }

    fun copyCaptionToClipboard(context: Context, caption: String) {
        runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("Telegram Album Caption", caption))
        }
    }

    private tailrec fun findActivity(context: Context): Activity? {
        return when (context) {
            is Activity -> context
            is ContextWrapper -> findActivity(context.baseContext)
            else -> null
        }
    }

    fun shareMediaUrisToTelegram(
        context: Context,
        uris: List<Uri>,
        hasImages: Boolean,
        hasVideos: Boolean,
        caption: String,
        preferTelegramDirect: Boolean = true,
        onMessage: (String) -> Unit
    ) {
        // Always copy the searchable #hashtag caption to clipboard so the user can also paste it anywhere
        copyCaptionToClipboard(context, caption)

        // Ensure no raw file:// URI ever reaches startActivity (convert to FileProvider content:// URI)
        val safeUris = uris.mapNotNull { rawUri ->
            if (rawUri.scheme == "file") {
                val f = rawUri.path?.let { File(it) }
                if (f != null && f.exists()) {
                    runCatching {
                        androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            f
                        )
                    }.getOrNull()
                } else null
            } else {
                rawUri
            }
        }

        if (safeUris.isEmpty()) {
            onMessage("Caption copied! No media files found on disk for this folder — tap Re-Download Album.")
            return
        }

        val primaryMimeType = when {
            hasImages && !hasVideos -> "image/*"
            hasVideos && !hasImages -> "video/*"
            else -> "image/*"
        }

        val uriArrayList = ArrayList(safeUris)
        val intent = if (uriArrayList.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = primaryMimeType
                putExtra(Intent.EXTRA_STREAM, uriArrayList.first())
                putExtra(Intent.EXTRA_TEXT, caption)
                if (hasImages && hasVideos) {
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = primaryMimeType
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uriArrayList)
                putExtra(Intent.EXTRA_TEXT, caption)
                if (hasImages && hasVideos) {
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        // Construct ClipData without querying ContentResolver so Android ALWAYS grants URI read permissions to Telegram
        try {
            val clipDescription = ClipDescription(
                "PulseGrab Album Media",
                if (hasImages && hasVideos) arrayOf("image/*", "video/*") else arrayOf(primaryMimeType)
            )
            val clipData = ClipData(clipDescription, ClipData.Item(uriArrayList.first()))
            for (i in 1 until uriArrayList.size) {
                clipData.addItem(ClipData.Item(uriArrayList[i]))
            }
            intent.clipData = clipData
        } catch (_: Exception) {
        }

        val pm = context.packageManager
        val installedTelegramPkg = if (preferTelegramDirect) {
            TELEGRAM_PACKAGES.firstOrNull { pkg ->
                runCatching {
                    pm.getPackageInfo(pkg, 0)
                    true
                }.getOrDefault(false)
            } ?: runCatching {
                pm.queryIntentActivities(intent, 0)
                    .mapNotNull { it.activityInfo?.packageName }
                    .firstOrNull { pkg ->
                        pkg in TELEGRAM_PACKAGES || pkg.contains("telegram", ignoreCase = true)
                    }
            }.getOrNull()
        } else {
            null
        }

        // Explicitly grant URI read permissions to Telegram and all candidate share targets
        runCatching {
            val candidatePackages = buildSet {
                addAll(TELEGRAM_PACKAGES)
                installedTelegramPkg?.let { add(it) }
                runCatching {
                    pm.queryIntentActivities(intent, 0).forEach { resolveInfo ->
                        resolveInfo.activityInfo?.packageName?.let { add(it) }
                    }
                }
            }
            for (pkg in candidatePackages) {
                for (uri in uriArrayList) {
                    runCatching {
                        context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                }
            }
        }

        val activity = findActivity(context)
        try {
            if (installedTelegramPkg != null) {
                val directIntent = Intent(intent).apply {
                    setPackage(installedTelegramPkg)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    if (activity == null) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                if (activity != null) {
                    activity.startActivity(directIntent)
                } else {
                    context.startActivity(directIntent)
                }
                onMessage("Opening Telegram with ${safeUris.size} media file(s) • #Hashtag caption copied!")
            } else {
                val chooser = Intent.createChooser(
                    intent,
                    "Share Album (${safeUris.size} media) to Telegram"
                ).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    if (activity == null) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                if (activity != null) {
                    activity.startActivity(chooser)
                } else {
                    context.startActivity(chooser)
                }
                onMessage("Sharing ${safeUris.size} media file(s) • #Hashtag caption copied to clipboard!")
            }
        } catch (_: Exception) {
            try {
                val fallbackIntent = Intent(intent).apply {
                    setPackage(null)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val fallbackChooser = Intent.createChooser(
                    fallbackIntent,
                    "Share Album (${safeUris.size} media)"
                ).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    if (activity == null) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                if (activity != null) {
                    activity.startActivity(fallbackChooser)
                } else {
                    context.startActivity(fallbackChooser)
                }
            } catch (e: Exception) {
                onMessage("Unable to launch share sheet: ${e.localizedMessage ?: "unknown error"}")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TelegramFolderShareSheet(
    folderName: String,
    folderTasks: List<DownloadTask>,
    sourceAlbumUrl: String?,
    onShareFolderBatchToTelegram: (
        tasksToShare: List<DownloadTask>,
        caption: String,
        preferTelegramDirect: Boolean
    ) -> Unit,
    onSyncFolderToGallery: () -> Unit,
    onCopyCaption: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val completedTasks = remember(folderTasks) {
        folderTasks.filter { it.status == DownloadStatus.COMPLETED }
    }
    val imageCount = remember(completedTasks) {
        completedTasks.count { it.mediaType == MediaType.IMAGE }
    }
    val videoCount = remember(completedTasks) {
        completedTasks.count { it.mediaType == MediaType.VIDEO }
    }

    var hashtagInput by remember(folderName) {
        mutableStateOf(TelegramMediaShareHelper.buildHashtag(folderName))
    }
    var albumTitleInput by remember(folderName) {
        mutableStateOf(folderName.replace('_', ' '))
    }
    var includeSourceLink by remember(sourceAlbumUrl) {
        mutableStateOf(!sourceAlbumUrl.isNullOrBlank())
    }

    val fullCaption = remember(
        albumTitleInput,
        hashtagInput,
        imageCount,
        videoCount,
        sourceAlbumUrl,
        includeSourceLink
    ) {
        TelegramMediaShareHelper.buildStructuredCaption(
            albumTitle = albumTitleInput,
            hashtag = hashtagInput,
            imageCount = imageCount,
            videoCount = videoCount,
            sourceUrl = sourceAlbumUrl,
            includeSourceUrl = includeSourceLink
        )
    }

    val chunksOfTen = remember(completedTasks) {
        completedTasks.chunked(10)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MidnightSurface,
        modifier = Modifier.testTag("telegram_folder_share_sheet")
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
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ElectricCyan.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Share Album Folder to Telegram",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = "Sends photos & videos as inline playable media grouped in albums + searchable #hashtag caption",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            // Native Telegram Gallery Album Bucket Card
            Surface(
                color = MidnightSurfaceElevated,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Collections,
                                contentDescription = null,
                                tint = EmeraldSuccess,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Telegram Gallery Album: $folderName",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldSuccess,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        OutlinedButton(
                            onClick = onSyncFolderToGallery,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, EmeraldSuccess),
                            modifier = Modifier.testTag("sync_folder_to_gallery_button")
                        ) {
                            Text(
                                text = "Verify & Index",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldSuccess
                            )
                        }
                    }
                    Text(
                        text = "Saved in Download/PulseGrab/$folderName ($imageCount JPG/IMG • $videoCount MP4/VID). All photos and videos stay unified in Download/PulseGrab/$folderName and are shared as inline playable media!",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            // Editable #Hashtag & Album Title for Searchable Telegram Channels/Groups
            OutlinedTextField(
                value = hashtagInput,
                onValueChange = { hashtagInput = it },
                label = { Text("Searchable Telegram #Hashtag") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Tag,
                        contentDescription = null,
                        tint = ElectricCyan
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("telegram_hashtag_input")
            )

            OutlinedTextField(
                value = albumTitleInput,
                onValueChange = { albumTitleInput = it },
                label = { Text("Album Title in Caption") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.FolderSpecial,
                        contentDescription = null,
                        tint = VividViolet
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("telegram_album_title_input")
            )

            if (!sourceAlbumUrl.isNullOrBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Include Source Album Link in Caption",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )
                        Text(
                            text = sourceAlbumUrl,
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Switch(
                        checked = includeSourceLink,
                        onCheckedChange = { includeSourceLink = it }
                    )
                }
            }

            // Structured Caption Preview + Copy Button
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
                        Text(
                            text = "Structured Caption Preview (Auto-Copied)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = ElectricCyan
                        )
                        OutlinedButton(
                            onClick = { onCopyCaption(fullCaption) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, GlassBorderSubtle),
                            modifier = Modifier.testTag("copy_telegram_caption_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Caption",
                                tint = ElectricCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Copy #Hashtag Caption",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan
                            )
                        }
                    }
                    Text(
                        text = fullCaption,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.28f))
                            .padding(10.dp)
                    )
                }
            }

            // Primary Action: Share Entire Folder as Inline Playable Media
            val shareableTasks = if (completedTasks.isNotEmpty()) completedTasks else folderTasks
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        onShareFolderBatchToTelegram(shareableTasks, fullCaption, true)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("share_entire_folder_telegram_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = Color.Black
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Share Folder to Telegram (${shareableTasks.size} Media)",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = {
                        onShareFolderBatchToTelegram(shareableTasks, fullCaption, false)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, GlassBorderSubtle),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("share_entire_folder_chooser_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Other Apps",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Smart 10-Item Telegram Album Group Chunker (Telegram groups up to 10 items per single album post)
            if (chunksOfTen.size > 1) {
                Surface(
                    color = MidnightSurfaceElevated,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, VividViolet.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Smart 10-Item Telegram Album Groups (${chunksOfTen.size} Parts)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = AmberWarning
                        )
                        Text(
                            text = "Telegram groups up to 10 photos/videos into a single album grid post with a unified caption. Tap each part below to post clean 10-item albums with part numbers:",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            chunksOfTen.forEachIndexed { idx, chunk ->
                                val startNum = idx * 10 + 1
                                val endNum = idx * 10 + chunk.size
                                val chunkImgs = chunk.count { it.mediaType == MediaType.IMAGE }
                                val chunkVids = chunk.count { it.mediaType == MediaType.VIDEO }
                                val partCaption = TelegramMediaShareHelper.buildStructuredCaption(
                                    albumTitle = albumTitleInput,
                                    hashtag = hashtagInput,
                                    imageCount = chunkImgs,
                                    videoCount = chunkVids,
                                    sourceUrl = sourceAlbumUrl,
                                    includeSourceUrl = includeSourceLink,
                                    partIndex = idx + 1,
                                    totalParts = chunksOfTen.size
                                )
                                OutlinedButton(
                                    onClick = {
                                        onShareFolderBatchToTelegram(chunk, partCaption, true)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, ElectricCyan),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("share_telegram_chunk_${idx + 1}")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = null,
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Part ${idx + 1} ($startNum–$endNum)",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ElectricCyan
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
