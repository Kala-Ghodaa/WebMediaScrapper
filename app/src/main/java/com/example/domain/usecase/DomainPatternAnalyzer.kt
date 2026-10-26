package com.example.domain.usecase

import com.example.domain.model.DiscoveredPathPattern
import java.net.URI
import java.util.Locale

class DomainPatternAnalyzer {

    private val mediaKeywords = setOf(
        "gallery", "galleries", "album", "albums", "photo", "photos",
        "image", "images", "picture", "pictures", "video", "videos",
        "media", "clip", "clips", "collection", "collections", "wallpaper",
        "wallpapers", "asset", "assets", "file", "download"
    )

    fun discoverPatterns(baseUrl: String, candidateUrls: List<String>): List<DiscoveredPathPattern> {
        val baseUri = runCatching { URI(baseUrl) }.getOrNull() ?: return emptyList()
        val baseHost = baseUri.host?.lowercase(Locale.US)?.removePrefix("www.") ?: return emptyList()

        // Cluster candidate URLs by normalized path template
        val clusters = LinkedHashMap<String, MutableList<String>>()

        for (url in candidateUrls) {
            val uri = runCatching { URI(url) }.getOrNull() ?: continue
            val host = uri.host?.lowercase(Locale.US)?.removePrefix("www.") ?: continue
            if (host != baseHost) continue

            val path = uri.path ?: continue
            val template = createPathTemplate(path)
            if (template.isNotBlank()) {
                clusters.getOrPut(template) { mutableListOf() }.add(url)
            }
        }

        val results = mutableListOf<DiscoveredPathPattern>()
        for ((patternTemplate, matchedUrls) in clusters) {
            val fullPattern = "$baseHost$patternTemplate"
            val sample = matchedUrls.firstOrNull() ?: continue
            val lower = fullPattern.lowercase(Locale.US)
            val isLikelyMedia = mediaKeywords.any { lower.contains(it) } ||
                matchedUrls.any { it.contains("page-") || it.contains("page/") || it.contains("id/") }

            results.add(
                DiscoveredPathPattern(
                    id = fullPattern.hashCode().toUInt().toString(16),
                    pattern = fullPattern,
                    sampleUrl = sample,
                    matchCount = matchedUrls.size,
                    isLikelyMediaGallery = isLikelyMedia,
                    isSelected = isLikelyMedia || matchedUrls.size >= 2
                )
            )
        }

        // Sort: likely media first, then highest frequency
        return results.sortedWith(
            compareByDescending<DiscoveredPathPattern> { it.isLikelyMediaGallery }
                .thenByDescending { it.matchCount }
        )
    }

    private fun createPathTemplate(path: String): String {
        val segments = path.trim('/').split('/').filter { it.isNotBlank() }
        if (segments.isEmpty()) return "/*"

        val normalized = segments.map { seg ->
            when {
                // If segment is pure numeric ID (e.g. /12345/) -> replace with wildcard ID
                seg.all { it.isDigit() } -> "[id]"
                // If segment has pagination like page-1 or p2 -> replace with [page]
                Regex("""^(?:page|p)[-_]?\d+$""", RegexOption.IGNORE_CASE).matches(seg) -> "page-[N]"
                // If contains hex hash or long alphanumeric ID -> [id]
                seg.length >= 16 && seg.any { it.isDigit() } && seg.any { it.isLetter() } -> "[id]"
                else -> seg
            }
        }

        val prefix = "/" + normalized.joinToString("/")
        return if (prefix.endsWith("[id]") || prefix.endsWith("page-[N]")) {
            prefix
        } else {
            "$prefix/*"
        }
    }
}
