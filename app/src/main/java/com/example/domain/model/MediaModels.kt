package com.example.domain.model

enum class MediaType {
    IMAGE,
    VIDEO
}

enum class MediaTabFilter {
    ALL,
    IMAGES,
    VIDEOS
}

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class DeepScanMode {
    DOMAIN_BFS,
    URL_PATTERN
}

enum class DownloadFolderMode {
    AUTO_BY_PAGE_TITLE,
    CUSTOM_SUBFOLDER,
    SAF_CUSTOM_TREE
}

data class DownloadFolderConfig(
    val folderMode: DownloadFolderMode = DownloadFolderMode.AUTO_BY_PAGE_TITLE,
    val currentPageTitle: String = "Web_Media_Grab",
    val customSubfolderName: String = "Web_Media_Collection",
    val customTreeUri: String? = null,
    val customTreeDisplayName: String? = null
) {
    val activeSubfolderName: String
        get() = when (folderMode) {
            DownloadFolderMode.AUTO_BY_PAGE_TITLE -> sanitizeFolderName(currentPageTitle)
            DownloadFolderMode.CUSTOM_SUBFOLDER -> sanitizeFolderName(customSubfolderName)
            DownloadFolderMode.SAF_CUSTOM_TREE -> customTreeDisplayName ?: "Custom_Device_Folder"
        }

    val displayLabel: String
        get() = when (folderMode) {
            DownloadFolderMode.AUTO_BY_PAGE_TITLE -> "Auto Title: $activeSubfolderName"
            DownloadFolderMode.CUSTOM_SUBFOLDER -> "Custom: $activeSubfolderName"
            DownloadFolderMode.SAF_CUSTOM_TREE -> "Device: $activeSubfolderName"
        }

    companion object {
        fun sanitizeFolderName(name: String): String {
            val clean = name.trim()
                .replace(Regex("""[\\/:*?"<>|]"""), "_")
                .replace(Regex("""\s+"""), "_")
                .trim('_', '.')
            return clean.ifBlank { "Media_Grab" }.take(50)
        }
    }
}

data class DiscoveredPathPattern(
    val id: String,
    val pattern: String,
    val sampleUrl: String,
    val matchCount: Int,
    val isLikelyMediaGallery: Boolean = false,
    val isSelected: Boolean = true
)

data class DetectedMediaItem(
    val id: String,
    val url: String,
    val normalizedUrl: String,
    val pageUrl: String,
    val pageTitle: String = "",
    val crawlDepth: Int = 0,
    val title: String,
    val mediaType: MediaType,
    val extension: String,
    val mimeType: String,
    val width: Int = 0,
    val height: Int = 0,
    val estimatedSizeBytes: Long = -1L,
    val isAlreadyDownloaded: Boolean = false,
    val discoveredAt: Long = System.currentTimeMillis()
) {
    val hasDimensions: Boolean
        get() = width > 0 && height > 0

    val dimensionsLabel: String
        get() = if (hasDimensions) "${width}×${height}" else ""

    val extensionBadge: String
        get() = extension.uppercase().ifBlank { if (mediaType == MediaType.VIDEO) "VID" else "IMG" }
}

data class ExtensionFilterConfig(
    val enabledImageExtensions: Set<String> = DEFAULT_IMAGE_EXTENSIONS,
    val enabledVideoExtensions: Set<String> = DEFAULT_VIDEO_EXTENSIONS,
    val customExtensions: Set<String> = emptySet(),
    val minWidthPx: Int = 32,
    val minHeightPx: Int = 32
) {
    val allEnabledExtensions: Set<String>
        get() = enabledImageExtensions + enabledVideoExtensions + customExtensions

    fun isExtensionAllowed(extension: String, mediaType: MediaType): Boolean {
        val clean = extension.lowercase().trim().removePrefix(".")
        if (clean.isEmpty()) return true
        if (clean in customExtensions) return true
        return when (mediaType) {
            MediaType.IMAGE -> clean in enabledImageExtensions
            MediaType.VIDEO -> clean in enabledVideoExtensions
        }
    }

    companion object {
        val ALL_IMAGE_EXTENSIONS = listOf("jpg", "jpeg", "png", "webp", "gif", "svg", "avif", "bmp")
        val ALL_VIDEO_EXTENSIONS = listOf("mp4", "webm", "mkv", "mov", "m4v", "avi", "mp3", "m4a")

        val DEFAULT_IMAGE_EXTENSIONS = ALL_IMAGE_EXTENSIONS.toSet()
        val DEFAULT_VIDEO_EXTENSIONS = ALL_VIDEO_EXTENSIONS.toSet()
    }
}

data class DeepScanConfig(
    val seedUrl: String = "",
    val mode: DeepScanMode = DeepScanMode.DOMAIN_BFS,
    val maxDepth: Int = 2,
    val maxPages: Int = 25,
    val sameDomainOnly: Boolean = true,
    val urlPattern: String = ""
)

data class DeepScanProgress(
    val isScanning: Boolean = false,
    val isPaused: Boolean = false,
    val mode: DeepScanMode = DeepScanMode.DOMAIN_BFS,
    val pagesScanned: Int = 0,
    val pagesTotalOrQueued: Int = 0,
    val matchedUrlsCount: Int = 0,
    val mediaFoundCount: Int = 0,
    val currentScanningUrl: String = "",
    val currentDepth: Int = 0,
    val recentScannedUrls: List<String> = emptyList(),
    val errorMessage: String? = null
)

data class DownloadTask(
    val id: String,
    val url: String,
    val normalizedUrl: String,
    val refererUrl: String = "",
    val fileName: String,
    val folderName: String,
    val extension: String,
    val mediaType: MediaType,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val status: DownloadStatus,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedBytesPerSec: Long,
    val localUri: String?,
    val tempFilePath: String?,
    val errorMessage: String?,
    val createdAt: Long,
    val completedAt: Long?
) {
    val progressFraction: Float
        get() = if (totalBytes > 0L) {
            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else if (status == DownloadStatus.COMPLETED) {
            1f
        } else {
            0f
        }

    val progressPercent: Int
        get() = (progressFraction * 100).toInt()
}

data class PortalShortcut(
    val id: String,
    val title: String,
    val subtitle: String,
    val url: String,
    val badge: String,
    val defaultPatternHint: String = ""
)

data class DownloadedAlbumRecord(
    val normalizedAlbumUrl: String,
    val albumUrl: String,
    val albumTitle: String,
    val folderName: String,
    val mediaCount: Int,
    val downloadedAt: Long
)

enum class AlbumScrapeStatus {
    NEW_DOWNLOADED,
    SKIPPED_ALREADY_DOWNLOADED,
    NO_MEDIA_FOUND,
    ERROR
}

data class AlbumScrapeLogItem(
    val albumUrl: String,
    val albumTitle: String,
    val folderName: String,
    val mediaCount: Int,
    val status: AlbumScrapeStatus,
    val message: String = ""
)

data class AlbumBatchProgress(
    val isScanning: Boolean = false,
    val folderIndexUrl: String = "",
    val strictJpgMp4Only: Boolean = true,
    val skipAlreadyDownloadedAlbums: Boolean = true,
    val redownloadIfMissing: Boolean = true,
    val totalAlbumsFound: Int = 0,
    val albumsProcessed: Int = 0,
    val newAlbumsDownloaded: Int = 0,
    val skippedAlbumsCount: Int = 0,
    val totalMediaQueued: Int = 0,
    val currentAlbumTitle: String = "",
    val currentAlbumUrl: String = "",
    val albumLogs: List<AlbumScrapeLogItem> = emptyList(),
    val errorMessage: String? = null
)

object FormatUtils {
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(java.util.Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(java.util.Locale.US, "%.0f KB", kb)
            else -> "$bytes B"
        }
    }

    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0L) return "0 KB/s"
        return "${formatBytes(bytesPerSec)}/s"
    }
}

