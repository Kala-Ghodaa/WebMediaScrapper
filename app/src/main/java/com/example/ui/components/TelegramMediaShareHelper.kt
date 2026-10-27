package com.example.ui.components

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Android share handoff, not a Telegram uploader. The user chooses a chat and confirms Send. */
object TelegramMediaShareHelper {
    private val telegramPackages = listOf(
        "org.telegram.messenger", "org.telegram.messenger.web", "org.telegram.plus",
        "org.thunderdog.challegram", "tw.nekomimi.nekogram"
    )

    fun buildHashtag(rawFolderName: String): String {
        val cleaned = rawFolderName.trim().removePrefix("#")
            .replace(Regex("[^\\p{L}\\p{N}_]+"), "_")
            .replace(Regex("_+"), "_").trim('_').ifBlank { "PulseGrab_Album" }
        return "#" + if (cleaned.first().isDigit()) "Album_$cleaned" else cleaned
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
    ): String = buildString {
        append(buildHashtag(hashtag))
        append("\n📁 ")
        append(albumTitle.trim().ifBlank { "PulseGrab Album" })
        if (partIndex != null && totalParts != null && totalParts > 1) {
            append(" (Part $partIndex/$totalParts)")
        }
        val counts = buildList {
            if (imageCount > 0) add("🖼 $imageCount Photo${if (imageCount == 1) "" else "s"}")
            if (videoCount > 0) add("🎬 $videoCount Video${if (videoCount == 1) "" else "s"}")
        }
        if (counts.isNotEmpty()) append("\n" + counts.joinToString(" • "))
        if (includeSourceUrl && !sourceUrl.isNullOrBlank()) append("\n🔗 ${sourceUrl.trim()}")
    }

    fun copyCaptionToClipboard(context: Context, caption: String) {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
            ?.setPrimaryClip(ClipData.newPlainText("Album caption", caption))
    }

    /** Keep EXTRA_STREAM and ClipData identical so all attachments receive temporary read grants. */
    fun buildShareIntent(uris: List<Uri>, mimeTypes: List<String>, caption: String): Intent {
        require(uris.isNotEmpty() && uris.size == mimeTypes.size)
        require(uris.all { it.scheme == "content" }) { "Only content URIs can be shared" }
        val types = mimeTypes.map { it.ifBlank { "application/octet-stream" } }.distinct()
        val primaryType = when {
            types.size == 1 -> types.single()
            types.all { it.startsWith("image/") } -> "image/*"
            types.all { it.startsWith("video/") } -> "video/*"
            else -> "*/*" // Mixed photo/video albums must not masquerade as image-only shares.
        }
        return Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
            type = primaryType
            if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris.single())
            else putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            putExtra(Intent.EXTRA_MIME_TYPES, types.toTypedArray())
            putExtra(Intent.EXTRA_TEXT, caption)
            clipData = ClipData(ClipDescription("Album media", types.toTypedArray()), ClipData.Item(uris.first())).also { clip ->
                uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
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
        val safeUris = uris.distinct().mapNotNull { uri ->
            when (uri.scheme) {
                "content" -> uri
                "file" -> uri.path?.let { path ->
                    val file = File(path)
                    if (!file.isFile || file.length() == 0L) null else runCatching {
                        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    }.getOrNull()
                }
                else -> null
            }
        }.distinct()
        if (safeUris.isEmpty()) {
            onMessage("No readable media to share. Re-download missing files and try again.")
            return
        }
        val mimeTypes = safeUris.map { uri ->
            runCatching { context.contentResolver.getType(uri) }.getOrNull()
                ?.takeUnless { it == "application/octet-stream" }
                ?: android.webkit.MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase())
                ?: when {
                    hasImages && !hasVideos -> "image/*"
                    hasVideos && !hasImages -> "video/*"
                    else -> "application/octet-stream"
                }
        }
        val intent = buildShareIntent(safeUris, mimeTypes, caption)
        val handlers = context.packageManager.queryIntentActivities(intent, 0)
            .mapNotNull { it.activityInfo?.packageName }.toSet()
        val telegram = if (preferTelegramDirect) telegramPackages.firstOrNull { it in handlers } else null
        fun launchChooser() {
            context.startActivity(Intent.createChooser(intent, "Share ${safeUris.size} files").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = intent.clipData
            })
        }
        try {
            if (telegram != null) {
                try {
                    context.startActivity(Intent(intent).setPackage(telegram).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: ActivityNotFoundException) {
                    launchChooser()
                } catch (_: SecurityException) {
                    launchChooser()
                }
            } else launchChooser()
            copyCaptionToClipboard(context, caption)
            onMessage(if (telegram != null) "Choose a chat in Telegram, then confirm Send. Caption copied."
                else "Choose a sharing app, then confirm Send. Caption copied.")
        } catch (e: Exception) {
            onMessage("Could not open sharing: ${e.localizedMessage ?: "no compatible app installed"}")
        }
    }
}
