package com.example.domain.usecase

import com.example.domain.model.DetectedMediaItem
import com.example.domain.model.ExtensionFilterConfig
import com.example.domain.model.MediaTabFilter
import com.example.domain.model.MediaType
import java.net.URI
import java.util.Locale

object UrlMediaUtils {

    private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "webp", "gif", "svg", "avif", "bmp", "ico", "heic")
    private val VIDEO_EXTS = setOf("mp4", "webm", "mkv", "mov", "m4v", "avi", "mp3", "m4a", "ogv", "ts")

    fun normalizeInputUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.startsWith("pulsegrab://", ignoreCase = true)) {
            return trimmed.lowercase(Locale.US)
        }
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return trimmed
        }
        // If it looks like a domain or URL pattern
        if (trimmed.contains(".") && !trimmed.contains(" ")) {
            return "https://$trimmed"
        }
        // Otherwise treat as a search query on Wikimedia Commons media search
        val encoded = java.net.URLEncoder.encode(trimmed, "UTF-8")
        return "https://commons.wikimedia.org/w/index.php?search=$encoded&title=Special:MediaSearch&type=image"
    }

    private val HPT_THUMBNAIL_REGEX = Regex("""HPT\d+\.(?:jpg|jpeg|png|webp)$""", RegexOption.IGNORE_CASE)

    fun isThumbnailOrHptUrl(url: String): Boolean {
        if (url.isBlank()) return true
        val cleanUrl = url.substringBefore('?').substringBefore('#').trim()
        val lower = cleanUrl.lowercase(Locale.US)

        // 1. Directory and path thumbnail indicators
        if (lower.contains("/thumb/") ||
            lower.contains("/thumbs/") ||
            lower.contains("/thumbnails/") ||
            lower.contains("/preview/") ||
            lower.contains("/preview_") ||
            lower.contains("/cache/") ||
            lower.contains("_thumb.") ||
            lower.contains("-thumb.") ||
            lower.contains("thumb_")
        ) {
            return true
        }

        // 2. SEO protected thumbnail pattern (e.g. <AlbumId>HPT<number>.jpg)
        val filename = cleanUrl.substringAfterLast('/')
        if (HPT_THUMBNAIL_REGEX.containsMatchIn(filename)) {
            return true
        }

        return false
    }

    fun canonicalizeMediaUrl(rawUrl: String, baseUrl: String? = null): String? {
        val cleaned = rawUrl.trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .replace("\\/", "/")
            .replace("\\u002F", "/")
            .replace("\\u002f", "/")
            .replace("&amp;", "&")
            .replace("&quot;", "")
            .replace("&#39;", "'")
            .replace("&#2F;", "/")
            .replace("&#x2F;", "/")
        if (cleaned.isEmpty() ||
            cleaned.startsWith("data:", ignoreCase = true) ||
            cleaned.startsWith("blob:", ignoreCase = true) ||
            cleaned.startsWith("javascript:", ignoreCase = true) ||
            cleaned.startsWith("about:", ignoreCase = true)
        ) {
            return null
        }

        val resolved = try {
            when {
                cleaned.startsWith("//") -> "https:$cleaned"
                cleaned.startsWith("http://", ignoreCase = true) ||
                    cleaned.startsWith("https://", ignoreCase = true) -> cleaned
                !baseUrl.isNullOrBlank() && (baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) -> {
                    URI(baseUrl).resolve(cleaned).toString()
                }
                else -> return null
            }
        } catch (_: Exception) {
            if (cleaned.startsWith("http://") || cleaned.startsWith("https://")) cleaned else return null
        }

        // Strip URL fragment (#...) for deduplication
        val hashIndex = resolved.indexOf('#')
        return if (hashIndex >= 0) resolved.substring(0, hashIndex) else resolved
    }

    fun extractExtension(url: String, mimeTypeHint: String? = null): String {
        try {
            val withoutQuery = url.substringBefore('?').substringBefore('#')
            val lastSegment = withoutQuery.substringAfterLast('/', "")
            if (lastSegment.contains('.')) {
                val ext = lastSegment.substringAfterLast('.').lowercase(Locale.US).trim()
                if (ext.length in 2..5 && ext.all { it.isLetterOrDigit() }) {
                    return if (ext == "jpeg") "jpg" else ext
                }
            }
            // Check query parameters like ?format=jpg or ?ext=mp4 or fm=webp
            val query = url.substringAfter('?', "").lowercase(Locale.US)
            for (known in (IMAGE_EXTS + VIDEO_EXTS)) {
                if (query.contains("format=$known") ||
                    query.contains("fm=$known") ||
                    query.contains("ext=$known") ||
                    query.contains("type=$known")
                ) {
                    return if (known == "jpeg") "jpg" else known
                }
            }
        } catch (_: Exception) {
        }

        if (!mimeTypeHint.isNullOrBlank()) {
            val lowerMime = mimeTypeHint.lowercase(Locale.US)
            return when {
                lowerMime.contains("jpeg") || lowerMime.contains("jpg") -> "jpg"
                lowerMime.contains("png") -> "png"
                lowerMime.contains("webp") -> "webp"
                lowerMime.contains("gif") -> "gif"
                lowerMime.contains("svg") -> "svg"
                lowerMime.contains("avif") -> "avif"
                lowerMime.contains("bmp") -> "bmp"
                lowerMime.contains("mp4") -> "mp4"
                lowerMime.contains("webm") -> "webm"
                lowerMime.contains("quicktime") || lowerMime.contains("mov") -> "mov"
                lowerMime.contains("x-matroska") || lowerMime.contains("mkv") -> "mkv"
                lowerMime.contains("mpeg") || lowerMime.contains("mp3") -> "mp3"
                else -> ""
            }
        }
        return ""
    }

    fun inferMediaType(url: String, extension: String, tagHint: String = ""): MediaType? {
        val ext = extension.lowercase(Locale.US)
        if (ext in VIDEO_EXTS) return MediaType.VIDEO
        if (ext in IMAGE_EXTS) return MediaType.IMAGE

        val lowerTag = tagHint.lowercase(Locale.US)
        if (lowerTag == "video" || lowerTag == "audio") return MediaType.VIDEO
        if (lowerTag == "img" || lowerTag == "picture" || lowerTag == "bg") return MediaType.IMAGE

        val lowerUrl = url.lowercase(Locale.US)
        if (VIDEO_EXTS.any { lowerUrl.contains(".$it") }) return MediaType.VIDEO
        if (IMAGE_EXTS.any { lowerUrl.contains(".$it") }) return MediaType.IMAGE

        return null
    }

    fun inferMimeType(extension: String, mediaType: MediaType): String {
        return when (extension.lowercase(Locale.US)) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "avif" -> "image/avif"
            "bmp" -> "image/bmp"
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            else -> if (mediaType == MediaType.VIDEO) "video/mp4" else "image/jpeg"
        }
    }

    fun deriveTitleFromUrl(url: String, fallbackTitle: String? = null, extension: String = "jpg"): String {
        val pathPart = url.substringBefore('?').substringBefore('#').substringAfterLast('/')
        val baseName = pathPart.substringBeforeLast('.').replace(Regex("[^a-zA-Z0-9._-]"), "_").trim('_')
        if (baseName.length >= 2) {
            return baseName.take(64)
        }
        if (!fallbackTitle.isNullOrBlank() && fallbackTitle.length in 2..80 && !fallbackTitle.startsWith("http")) {
            val sanitized = fallbackTitle.replace(Regex("[^a-zA-Z0-9._ -]"), "_").trim()
            if (sanitized.isNotEmpty()) return sanitized
        }
        return "media_${kotlin.math.abs(url.hashCode())}.$extension"
    }
}

class FilterAndDeduplicateMediaUseCase {
    operator fun invoke(
        items: List<DetectedMediaItem>,
        tabFilter: MediaTabFilter,
        selectedExtensionChips: Set<String>, // Empty set = all enabled extensions; non-empty = union of selected
        extensionConfig: ExtensionFilterConfig,
        skipDuplicates: Boolean,
        searchQuery: String,
        downloadedNormalizedUrls: Set<String>
    ): List<DetectedMediaItem> {
        val seenUrls = HashSet<String>()
        val result = ArrayList<DetectedMediaItem>(items.size)
        val cleanQuery = searchQuery.trim().lowercase(Locale.US)
        val cleanSelectedChips = selectedExtensionChips.map {
            it.lowercase(Locale.US).removePrefix(".")
        }.toSet()

        for (item in items) {
            val isDownloaded = item.normalizedUrl in downloadedNormalizedUrls
            val updatedItem = if (item.isAlreadyDownloaded != isDownloaded) {
                item.copy(isAlreadyDownloaded = isDownloaded)
            } else {
                item
            }

            if (skipDuplicates && !seenUrls.add(updatedItem.normalizedUrl)) {
                continue
            }

            // Global ExtensionConfig filter
            if (!extensionConfig.isExtensionAllowed(updatedItem.extension, updatedItem.mediaType)) {
                continue
            }

            // Minimum dimensions filter (only when dimensions are known)
            if (updatedItem.width > 0 && updatedItem.height > 0) {
                if (updatedItem.width < extensionConfig.minWidthPx ||
                    updatedItem.height < extensionConfig.minHeightPx
                ) {
                    continue
                }
            }

            // Tab filter (All / Images / Videos)
            val matchesTab = when (tabFilter) {
                MediaTabFilter.ALL -> true
                MediaTabFilter.IMAGES -> updatedItem.mediaType == MediaType.IMAGE
                MediaTabFilter.VIDEOS -> updatedItem.mediaType == MediaType.VIDEO
            }
            if (!matchesTab) continue

            // Multi-Select extension chips filter (if any chips are selected, item must match at least one)
            if (cleanSelectedChips.isNotEmpty()) {
                val itemExt = updatedItem.extension.lowercase(Locale.US)
                val matchesAnyChip = cleanSelectedChips.any { chip ->
                    if (chip == "jpg") itemExt == "jpg" || itemExt == "jpeg"
                    else itemExt == chip
                }
                if (!matchesAnyChip) continue
            }

            // Search query filter
            if (cleanQuery.isNotEmpty()) {
                val matchesSearch = updatedItem.title.lowercase(Locale.US).contains(cleanQuery) ||
                    updatedItem.url.lowercase(Locale.US).contains(cleanQuery) ||
                    updatedItem.extension.lowercase(Locale.US).contains(cleanQuery)
                if (!matchesSearch) continue
            }

            result.add(updatedItem)
        }
        return result
    }
}

class ExpandUrlPatternUseCase {
    /**
     * Expands bracketed numeric range patterns like:
     *   https://example.com/gallery/page-[1-5]
     *   https://cdn.example.com/img_[001-015].jpg
     * Returns the expanded list of URLs (capped at maxCount).
     */
    private val rangeRegex = Regex("""\[(\d+)-(\d+)]""")

    fun hasRangePattern(patternUrl: String): Boolean {
        return rangeRegex.containsMatchIn(patternUrl)
    }

    fun expandPattern(patternUrl: String, maxCount: Int = 100): List<String> {
        val trimmed = patternUrl.trim()
        if (trimmed.isEmpty()) return emptyList()
        val match = rangeRegex.find(trimmed) ?: return listOf(trimmed)

        val startStr = match.groupValues[1]
        val endStr = match.groupValues[2]
        val padLength = if (startStr.startsWith("0") && startStr.length > 1) startStr.length else 0

        val start = startStr.toIntOrNull() ?: return listOf(trimmed)
        val end = endStr.toIntOrNull() ?: return listOf(trimmed)

        val progression = if (start <= end) {
            start..minOf(end, start + maxCount - 1)
        } else {
            start downTo maxOf(end, start - maxCount + 1)
        }

        return progression.map { num ->
            val formatted = if (padLength > 0) {
                num.toString().padStart(padLength, '0')
            } else {
                num.toString()
            }
            trimmed.replaceRange(match.range, formatted)
        }
    }

    // Checks if a candidate URL matches an optional user wildcard pattern
    // such as "*gallery*" or "https://site.com/albums/*"
    fun matchesWildcardPattern(candidateUrl: String, pattern: String): Boolean {
        val cleanPattern = pattern.trim()
        if (cleanPattern.isEmpty()) return true
        // If pattern had [a-b], strip brackets for general matching or check prefix
        if (hasRangePattern(cleanPattern)) return true

        return try {
            if (cleanPattern.contains("*")) {
                val regexPattern = cleanPattern
                    .split("*")
                    .joinToString(".*") { Regex.escape(it) }
                Regex(regexPattern, RegexOption.IGNORE_CASE).containsMatchIn(candidateUrl)
            } else {
                candidateUrl.contains(cleanPattern, ignoreCase = true)
            }
        } catch (_: Exception) {
            candidateUrl.contains(cleanPattern, ignoreCase = true)
        }
    }
}
