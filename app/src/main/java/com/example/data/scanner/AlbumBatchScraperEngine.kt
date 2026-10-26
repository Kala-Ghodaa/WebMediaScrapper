package com.example.data.scanner

import com.example.data.download.DownloadRepository
import com.example.data.local.DownloadDao
import com.example.data.local.DownloadedAlbumEntity
import com.example.domain.model.AlbumBatchProgress
import com.example.domain.model.AlbumScrapeLogItem
import com.example.domain.model.AlbumScrapeStatus
import com.example.domain.model.DetectedMediaItem
import com.example.domain.model.DownloadFolderConfig
import com.example.domain.model.DownloadFolderMode
import com.example.data.logging.DiagnosticLogManager
import com.example.data.logging.LogCategory
import com.example.data.logging.LogSeverity
import com.example.domain.model.DownloadStatus
import com.example.domain.model.MediaType
import com.example.domain.usecase.ExpandUrlPatternUseCase
import com.example.domain.usecase.UrlMediaUtils
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.Locale
import java.util.concurrent.TimeUnit

data class ScrapedAlbumResult(
    val albumUrl: String,
    val normalizedAlbumUrl: String,
    val title: String,
    val folderName: String,
    val mediaItems: List<DetectedMediaItem>,
    val error: String? = null
)

class AlbumBatchScraperEngine(
    private val scope: CoroutineScope,
    private val downloadDao: DownloadDao,
    private val downloadRepository: DownloadRepository,
    private val expandUrlPatternUseCase: ExpandUrlPatternUseCase = ExpandUrlPatternUseCase(),
    private val onMediaDiscovered: (List<DetectedMediaItem>) -> Unit
) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val _progress = MutableStateFlow(AlbumBatchProgress())
    val progress: StateFlow<AlbumBatchProgress> = _progress.asStateFlow()

    private var activeBatchJob: Job? = null

    fun startAlbumFolderBatch(
        rawFolderUrl: String,
        strictJpgMp4Only: Boolean = true,
        skipAlreadyDownloadedAlbums: Boolean = true,
        redownloadIfMissing: Boolean = true
    ) {
        activeBatchJob?.cancel()
        val trimmed = rawFolderUrl.trim()
        if (trimmed.isEmpty()) {
            _progress.value = AlbumBatchProgress(
                isScanning = false,
                errorMessage = "Please enter an Album Folder URL or Album URL."
            )
            return
        }

        val normalizedFolderUrl = UrlMediaUtils.normalizeInputUrl(trimmed)

        activeBatchJob = scope.launch(Dispatchers.IO) {
            try {
                _progress.value = AlbumBatchProgress(
                    isScanning = true,
                    folderIndexUrl = normalizedFolderUrl,
                    strictJpgMp4Only = strictJpgMp4Only,
                    skipAlreadyDownloadedAlbums = skipAlreadyDownloadedAlbums,
                    redownloadIfMissing = redownloadIfMissing,
                    currentAlbumTitle = "Scanning album folder index…",
                    currentAlbumUrl = normalizedFolderUrl
                )

                // 1. Expand index URLs if user used a range pattern like https://site.com/albums/page-[1-5]
                val indexUrls = if (expandUrlPatternUseCase.hasRangePattern(normalizedFolderUrl)) {
                    expandUrlPatternUseCase.expandPattern(normalizedFolderUrl, maxCount = 30)
                } else {
                    listOf(normalizedFolderUrl)
                }

                val discoveredAlbumUrls = LinkedHashSet<String>()
                var directIndexHtml: String? = null

                for ((idx, indexUrl) in indexUrls.withIndex()) {
                    if (!kotlinx.coroutines.currentCoroutineContext().isActive) break
                    val outcome = fetchHtmlWithHeaders(
                        url = indexUrl,
                        pageIndex = idx + 1,
                        refererUrl = BrowserHttpBridge.getOriginUrl(indexUrl)
                    )
                    val html = outcome.html ?: continue
                    if (idx == 0) directIndexHtml = html

                    val childAlbums = extractChildAlbumUrls(html, indexUrl)
                    discoveredAlbumUrls.addAll(childAlbums)
                    if (indexUrls.size > 1 && idx < indexUrls.lastIndex) {
                        kotlinx.coroutines.delay(250L)
                    }
                }

                // If the URL has no child album sub-pages, or itself contains direct album media, include it
                if (discoveredAlbumUrls.isEmpty() || (directIndexHtml != null && hasDirectAlbumMedia(directIndexHtml, normalizedFolderUrl, strictJpgMp4Only))) {
                    // If child albums exist, we prioritize child albums; if none exist, treat the input URL itself as an album
                    if (discoveredAlbumUrls.isEmpty()) {
                        discoveredAlbumUrls.add(normalizedFolderUrl)
                    }
                }

                val albumList = discoveredAlbumUrls.toList()
                _progress.update {
                    it.copy(
                        totalAlbumsFound = albumList.size,
                        currentAlbumTitle = if (albumList.isEmpty()) "No albums found" else "Found ${albumList.size} album(s)"
                    )
                }

                // 2. Iterate through each discovered Album URL
                for ((index, albumUrl) in albumList.withIndex()) {
                    if (!kotlinx.coroutines.currentCoroutineContext().isActive) break

                    val canonicalAlbumUrl = UrlMediaUtils.canonicalizeMediaUrl(albumUrl, normalizedFolderUrl) ?: albumUrl

                    // Check if this album was already downloaded in Room
                    if (skipAlreadyDownloadedAlbums) {
                        val existingRecord = downloadDao.getDownloadedAlbum(canonicalAlbumUrl)
                        if (existingRecord != null) {
                            val shouldSkip = if (redownloadIfMissing) {
                                val exists = downloadRepository.doesAlbumFolderOrFilesExist(existingRecord.folderName)
                                if (!exists) {
                                    DiagnosticLogManager.log(
                                        category = LogCategory.ALBUM_QUEUE,
                                        severity = LogSeverity.INFO,
                                        tag = "AlbumBatch",
                                        url = canonicalAlbumUrl,
                                        message = "Album '${existingRecord.albumTitle}' folder/files do not exist on disk — keeping history & re-downloading."
                                    )
                                    false
                                } else {
                                    true
                                }
                            } else {
                                true
                            }

                            if (shouldSkip) {
                                val skipLog = AlbumScrapeLogItem(
                                    albumUrl = canonicalAlbumUrl,
                                    albumTitle = existingRecord.albumTitle,
                                    folderName = existingRecord.folderName,
                                    mediaCount = existingRecord.mediaCount,
                                    status = AlbumScrapeStatus.SKIPPED_ALREADY_DOWNLOADED,
                                    message = "Checked: Folder & files exist in 📁 ${existingRecord.folderName} (Skipped)"
                                )
                                _progress.update { state ->
                                    state.copy(
                                        albumsProcessed = index + 1,
                                        skippedAlbumsCount = state.skippedAlbumsCount + 1,
                                        currentAlbumTitle = "Checked & Skipped: ${existingRecord.albumTitle}",
                                        currentAlbumUrl = canonicalAlbumUrl,
                                        albumLogs = listOf(skipLog) + state.albumLogs
                                    )
                                }
                                continue
                            }
                        }
                    }

                    _progress.update { state ->
                        state.copy(
                            currentAlbumTitle = "Scraping album ${index + 1} of ${albumList.size}…",
                            currentAlbumUrl = canonicalAlbumUrl
                        )
                    }

                    // Polite pacing between consecutive album scrapes to prevent WAF 403/429 blocks
                    if (index > 0) {
                        kotlinx.coroutines.delay(350L)
                    }

                    // Scrape the album using BrowserHttpBridge (with folder URL as Referer + WebView DOM fallback)
                    val fetchOutcome = fetchHtmlWithHeaders(
                        url = canonicalAlbumUrl,
                        pageIndex = index + 1,
                        refererUrl = normalizedFolderUrl
                    )
                    val html = fetchOutcome.html
                    if (html == null) {
                        val errLog = AlbumScrapeLogItem(
                            albumUrl = canonicalAlbumUrl,
                            albumTitle = "Unreachable Album",
                            folderName = "",
                            mediaCount = 0,
                            status = AlbumScrapeStatus.ERROR,
                            message = fetchOutcome.errorSummary ?: "Failed to fetch album HTML"
                        )
                        _progress.update { state ->
                            state.copy(
                                albumsProcessed = index + 1,
                                albumLogs = listOf(errLog) + state.albumLogs
                            )
                        }
                        continue
                    }

                    val scraped = parseAlbumMediaFromHtml(
                        albumUrl = canonicalAlbumUrl,
                        htmlText = html,
                        strictJpgMp4Only = strictJpgMp4Only
                    )

                    if (scraped.mediaItems.isEmpty()) {
                        val emptyLog = AlbumScrapeLogItem(
                            albumUrl = canonicalAlbumUrl,
                            albumTitle = scraped.title,
                            folderName = scraped.folderName,
                            mediaCount = 0,
                            status = AlbumScrapeStatus.NO_MEDIA_FOUND,
                            message = "No matching full-res media links found"
                        )
                        _progress.update { state ->
                            state.copy(
                                albumsProcessed = index + 1,
                                albumLogs = listOf(emptyLog) + state.albumLogs
                            )
                        }
                        continue
                    }

                    // Stream discovered items to Detected Media screen as well
                    onMediaDiscovered(scraped.mediaItems)

                    // Automatically enqueue all pictures & videos into ONE single folder named after this album's title!
                    val existingCfg = downloadRepository.activeFolderConfig
                    val targetFolderName = DownloadFolderConfig.sanitizeFolderName(scraped.title)
                    val albumFolderConfig = if (
                        existingCfg.folderMode == DownloadFolderMode.SAF_CUSTOM_TREE &&
                        !existingCfg.customTreeUri.isNullOrBlank()
                    ) {
                        existingCfg.copy(currentPageTitle = scraped.title)
                    } else {
                        DownloadFolderConfig(
                            folderMode = DownloadFolderMode.AUTO_BY_PAGE_TITLE,
                            currentPageTitle = scraped.title
                        )
                    }

                    val validMediaItems = scraped.mediaItems.filterNot {
                        UrlMediaUtils.isThumbnailOrHptUrl(it.url) || UrlMediaUtils.isThumbnailOrHptUrl(it.normalizedUrl)
                    }

                    val queuedCount = downloadRepository.enqueueMediaItems(
                        items = validMediaItems,
                        skipDuplicates = true,
                        folderConfig = albumFolderConfig
                    )

                    // Save album record in Room so future runs skip this album automatically
                    downloadDao.upsertDownloadedAlbum(
                        DownloadedAlbumEntity(
                            normalizedAlbumUrl = canonicalAlbumUrl,
                            albumUrl = canonicalAlbumUrl,
                            albumTitle = scraped.title,
                            folderName = targetFolderName,
                            mediaCount = validMediaItems.size,
                            downloadedAt = System.currentTimeMillis()
                        )
                    )

                    val modeBadge = if (fetchOutcome.usedWebViewFallback) " (via Browser DOM)" else ""
                    val successLog = AlbumScrapeLogItem(
                        albumUrl = canonicalAlbumUrl,
                        albumTitle = scraped.title,
                        folderName = targetFolderName,
                        mediaCount = validMediaItems.size,
                        status = AlbumScrapeStatus.NEW_DOWNLOADED,
                        message = "Queued $queuedCount pic/vid file(s) → 📁 Download/PulseGrab/$targetFolderName$modeBadge"
                    )

                    _progress.update { state ->
                        state.copy(
                            albumsProcessed = index + 1,
                            newAlbumsDownloaded = state.newAlbumsDownloaded + 1,
                            totalMediaQueued = state.totalMediaQueued + queuedCount,
                            currentAlbumTitle = scraped.title,
                            currentAlbumUrl = canonicalAlbumUrl,
                            albumLogs = listOf(successLog) + state.albumLogs
                        )
                    }

                    // Sequential Album Processing: Wait for this album's files to finish downloading before advancing to next album
                    if (queuedCount > 0) {
                        DiagnosticLogManager.log(
                            category = LogCategory.ALBUM_QUEUE,
                            severity = LogSeverity.INFO,
                            tag = "AlbumBatchScraperEngine",
                            url = canonicalAlbumUrl,
                            message = "Sequential album queue: waiting for completion of $queuedCount items for album '${scraped.title}' (folder: $targetFolderName)"
                        )
                        val completedInTime = downloadRepository.awaitAlbumTasksCompletion(
                            folderName = targetFolderName,
                            timeoutMs = 300_000L
                        )
                        val albumTasks = downloadDao.getTasksByFolderNameOnce(targetFolderName)
                        val completedFiles = albumTasks.count { it.status == DownloadStatus.COMPLETED.name }
                        val finishedLog = AlbumScrapeLogItem(
                            albumUrl = canonicalAlbumUrl,
                            albumTitle = scraped.title,
                            folderName = targetFolderName,
                            mediaCount = validMediaItems.size,
                            status = AlbumScrapeStatus.NEW_DOWNLOADED,
                            message = "Completed $completedFiles/${validMediaItems.size} file(s) → 📁 Download/PulseGrab/$targetFolderName$modeBadge"
                        )
                        _progress.update { state ->
                            state.copy(
                                albumLogs = listOf(finishedLog) + state.albumLogs.filterNot {
                                    it.albumUrl == canonicalAlbumUrl && it.status == AlbumScrapeStatus.NEW_DOWNLOADED
                                }
                            )
                        }
                        DiagnosticLogManager.log(
                            category = LogCategory.ALBUM_QUEUE,
                            severity = if (completedInTime) LogSeverity.INFO else LogSeverity.WARN,
                            tag = "AlbumBatchScraperEngine",
                            url = canonicalAlbumUrl,
                            message = "Album '${scraped.title}' execution finished (completed=$completedInTime, files=$completedFiles/${validMediaItems.size}). Advancing to next album."
                        )
                    }
                }

                _progress.update { state ->
                    state.copy(
                        isScanning = false,
                        currentAlbumTitle = "Batch Complete: ${state.newAlbumsDownloaded} new, ${state.skippedAlbumsCount} skipped"
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                _progress.update { state ->
                    state.copy(
                        isScanning = false,
                        errorMessage = e.localizedMessage ?: "Album batch scan interrupted"
                    )
                }
            }
        }
    }

    fun stopAlbumBatch() {
        activeBatchJob?.cancel()
        activeBatchJob = null
        _progress.update {
            it.copy(
                isScanning = false,
                currentAlbumTitle = "Stopped"
            )
        }
    }

    private suspend fun fetchHtmlWithHeaders(
        url: String,
        pageIndex: Int,
        refererUrl: String? = null
    ): HtmlFetchOutcome {
        if (url.startsWith("pulsegrab://") || url.contains("showcase.pulsegrab.local")) {
            val html = if (url.contains("/album/")) {
                buildShowcaseAlbumHtml(url)
            } else {
                val pageNum = Regex("""page-(\d+)""").find(url)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    ?: pageIndex.coerceIn(1, 10)
                WebMediaScannerEngine.buildShowcaseHtml(pageNum)
            }
            return HtmlFetchOutcome(html = html, statusCode = 200)
        }

        return BrowserHttpBridge.fetchHtmlResilient(
            url = url,
            refererUrl = refererUrl,
            allowWebViewFallback = true
        )
    }

    companion object {
        private val h1Regex = Regex("""<h1\b[^>]*>(.*?)</h1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val titleRegex = Regex("""<title\b[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val htmlTagStripRegex = Regex("""<[^>]+>""")
        private val whitespaceRegex = Regex("""\s+""")

        private val jsonLdScriptRegex = Regex(
            """<script\b[^>]*\btype\s*=\s*["']application/ld\+json["'][^>]*>(.*?)</script>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )

        private val imgTagRegex = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)
        private val videoBlockRegex = Regex("""<video\b[^>]*>(.*?)</video>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val videoSingleTagRegex = Regex("""<video\b[^>]*>""", RegexOption.IGNORE_CASE)
        private val sourceTagRegex = Regex("""<source\b[^>]*>""", RegexOption.IGNORE_CASE)
        private val aTagHrefRegex = Regex("""<a\b[^>]*\bhref\s*=\s*["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)

        private val dataFullAttrRegex = Regex(
            """\b(?:data-orig-file|data-large-file|data-full-url|data-full|data-original|data-highres|data-zoom-image|data-full-src|data-zoom-src|data-raw|data-raw-src|data-file|data-url|data-image)\s*=\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        private val dataSrcAttrRegex = Regex("""\bdata-src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        // Ensure src is not preceded by a hyphen or word character (e.g. data-src, thumb-src)
        private val srcAttrRegex = Regex("""(?<![-\w])src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

        // Matches .(jpg|jpeg|png|mp4|webm) at the end of path (ignoring optional query parameters)
        private val mediaExtRegex = Regex("""\.(jpg|jpeg|png|mp4|webm)(?:\?[^\s"']*)?$""", RegexOption.IGNORE_CASE)

        // Exact fallback regex for direct CDN links:
        // r'(https?://[^\s"\']+/(?:uploads/[^"\']+\.(?:jpg|jpeg|png|mp4|webm)))'
        private val fallbackUploadsRegex = Regex(
            """(https?://[^\s"']+/(?:uploads/[^"'\s]+\.(?:jpg|jpeg|png|mp4|webm)))""",
            RegexOption.IGNORE_CASE
        )

        /**
         * Pure Kotlin recursive JSON reader to support runtime and local unit test execution without android.jar stubbing issues.
         */
        private class SimpleJsonReader(private val text: String) {
            private var index = 0

            fun parse(): Any? {
                skipWs()
                return parseVal()
            }

            private fun skipWs() {
                while (index < text.length && text[index].isWhitespace()) index++
            }

            private fun parseString(): String {
                index++ // skip opening quote
                val sb = StringBuilder()
                while (index < text.length) {
                    val c = text[index++]
                    if (c == '"') break
                    if (c == '\\' && index < text.length) {
                        val esc = text[index++]
                        when (esc) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (index + 4 <= text.length) {
                                    val hex = text.substring(index, index + 4)
                                    index += 4
                                    runCatching { sb.append(hex.toInt(16).toChar()) }
                                }
                            }
                            else -> sb.append(esc)
                        }
                    } else {
                        sb.append(c)
                    }
                }
                return sb.toString()
            }

            private fun parseObject(): Map<String, Any?> {
                index++ // skip '{'
                val map = LinkedHashMap<String, Any?>()
                while (index < text.length) {
                    skipWs()
                    if (index < text.length && text[index] == '}') { index++; break }
                    if (index < text.length && text[index] == '"') {
                        val key = parseString()
                        skipWs()
                        if (index < text.length && text[index] == ':') index++
                        skipWs()
                        val value = parseVal()
                        map[key] = value
                        skipWs()
                        if (index < text.length && text[index] == ',') index++
                    } else {
                        index++
                    }
                }
                return map
            }

            private fun parseArray(): List<Any?> {
                index++ // skip '['
                val list = mutableListOf<Any?>()
                while (index < text.length) {
                    skipWs()
                    if (index < text.length && text[index] == ']') { index++; break }
                    val item = parseVal()
                    list.add(item)
                    skipWs()
                    if (index < text.length && text[index] == ',') index++
                }
                return list
            }

            private fun parseVal(): Any? {
                skipWs()
                if (index >= text.length) return null
                return when (text[index]) {
                    '{' -> parseObject()
                    '[' -> parseArray()
                    '"' -> parseString()
                    't', 'f' -> {
                        if (text.startsWith("true", index)) { index += 4; true }
                        else if (text.startsWith("false", index)) { index += 5; false }
                        else { index++; null }
                    }
                    'n' -> {
                        if (text.startsWith("null", index)) { index += 4; null }
                        else { index++; null }
                    }
                    else -> {
                        val start = index
                        while (index < text.length && text[index] !in ",}] \t\r\n") index++
                        text.substring(start, index)
                    }
                }
            }
        }

        private fun parseJsonValue(text: String): Any? = SimpleJsonReader(text).parse()

        /**
         * Recursively parses application/ld+json blocks:
         * 1. Extracts URLs ONLY from keys named "url", "contentUrl", or "embedUrl".
         * 2. EXPLICITLY IGNORES keys named "thumbnailUrl".
         * 3. Drops any URL whose filename matches HPT\d+\.(jpg|jpeg|png|webp)$ or contains /thumb/.
         */
        fun extractMediaUrlsFromJsonLd(
            jsonText: String,
            baseUrl: String,
            strictJpgMp4Only: Boolean = true
        ): List<String> {
            val results = LinkedHashSet<String>()
            val trimmed = jsonText.trim()
            if (trimmed.isEmpty()) return emptyList()

            val root = try {
                parseJsonValue(trimmed)
            } catch (_: Exception) {
                null
            } ?: return emptyList()

            fun isAllowedMediaExtension(url: String): Boolean {
                val cleanPath = url.substringBefore('?').substringBefore('#').lowercase(Locale.US)
                return if (strictJpgMp4Only) {
                    cleanPath.endsWith(".jpg") || cleanPath.endsWith(".jpeg") || cleanPath.endsWith(".mp4")
                } else {
                    cleanPath.endsWith(".jpg") || cleanPath.endsWith(".jpeg") ||
                        cleanPath.endsWith(".png") || cleanPath.endsWith(".mp4") ||
                        cleanPath.endsWith(".webm") || cleanPath.endsWith(".webp")
                }
            }

            fun handleCandidateUrl(rawCandidate: String) {
                val candidate = rawCandidate.trim()
                if (candidate.isBlank()) return
                if (UrlMediaUtils.isThumbnailOrHptUrl(candidate)) return
                val canonical = UrlMediaUtils.canonicalizeMediaUrl(candidate, baseUrl) ?: return
                if (UrlMediaUtils.isThumbnailOrHptUrl(canonical)) return
                if (isAllowedMediaExtension(canonical)) {
                    results.add(canonical)
                }
            }

            fun recurse(key: String?, node: Any?) {
                if (node == null) return

                // Requirement 2: EXPLICITLY IGNORE keys named "thumbnailUrl"
                if (key != null && key.equals("thumbnailUrl", ignoreCase = true)) {
                    return
                }

                when (node) {
                    is Map<*, *> -> {
                        for ((kAny, value) in node) {
                            val k = kAny as? String ?: continue
                            // Requirement 2: EXPLICITLY IGNORE keys named "thumbnailUrl"
                            if (k.equals("thumbnailUrl", ignoreCase = true)) {
                                continue
                            }
                            // Requirement 1: extract URLs ONLY from keys named "url", "contentUrl", or "embedUrl"
                            if (k.equals("url", ignoreCase = true) ||
                                k.equals("contentUrl", ignoreCase = true) ||
                                k.equals("embedUrl", ignoreCase = true)
                            ) {
                                when (value) {
                                    is String -> handleCandidateUrl(value)
                                    is List<*> -> {
                                        for (elem in value) {
                                            if (elem is String) {
                                                handleCandidateUrl(elem)
                                            } else {
                                                recurse(k, elem)
                                            }
                                        }
                                    }
                                    is Map<*, *> -> recurse(k, value)
                                }
                            } else {
                                // For other keys (e.g. "@graph", "image", "video", "itemListElement", "item", "hasPart"),
                                // recurse only into nested objects and arrays
                                if (value is Map<*, *> || value is List<*>) {
                                    recurse(k, value)
                                }
                            }
                        }
                    }
                    is List<*> -> {
                        for (item in node) {
                            recurse(key, item)
                        }
                    }
                }
            }

            recurse(null, root)
            return results.toList()
        }

        /**
         * Exact Kotlin port of the album media scraping logic:
         * 1. Extracts title from `<h1>` or `<title>`.
         * 2. Extracts from `<script type="application/ld+json">`, pulling ONLY from url/contentUrl/embedUrl and IGNORING thumbnailUrl.
         * 3. Extracts from `img` (prioritizes full-res attributes, then lazyload, then direct src), skipping /thumb/ and HPT.
         * 4. Extracts from `video` (`src`) and `video > source` (`src`), skipping /thumb/ and HPT.
         * 5. Extracts from `a[href]`, skipping /thumb/ and HPT.
         * 6. Extracts fallback direct CDN links matching `https?://.../uploads/...(jpg|jpeg|png|mp4|webm)`.
         * 7. Filters strictly for `.jpg`, `.jpeg`, `.mp4` (or includes `.png`, `.webm` when strictJpgMp4Only = false).
         */
        fun parseAlbumMediaFromHtml(
            albumUrl: String,
            htmlText: String,
            strictJpgMp4Only: Boolean = true
        ): ScrapedAlbumResult {
            val rawH1 = h1Regex.find(htmlText)?.groupValues?.getOrNull(1)
                ?.let { cleanHtmlText(it) }
                ?.takeIf { it.isNotBlank() }

            val rawTitle = titleRegex.find(htmlText)?.groupValues?.getOrNull(1)
                ?.let { cleanHtmlText(it) }
                ?.takeIf { it.isNotBlank() }

            val albumTitle = rawH1 ?: rawTitle ?: "Unknown Album"
            val folderName = DownloadFolderConfig.sanitizeFolderName(albumTitle)

            val mediaLinks = LinkedHashSet<String>()

            // 1. Extract from <script type="application/ld+json"> blocks first (Requirement 1 & 2)
            for (jsonLdMatch in jsonLdScriptRegex.findAll(htmlText)) {
                val scriptContent = jsonLdMatch.groupValues.getOrNull(1).orEmpty()
                val extractedLd = extractMediaUrlsFromJsonLd(scriptContent, albumUrl, strictJpgMp4Only)
                mediaLinks.addAll(extractedLd)
            }

            // Strip JSON-LD blocks before regex parsing to prevent fallback regex from picking up thumbnailUrl or SEO assets in raw JSON
            val htmlWithoutJsonLd = htmlText.replace(jsonLdScriptRegex, " ")

            // 2. Extract from <img> tags: prioritize full-res original attributes, then lazyload, then direct src
            for (imgMatch in imgTagRegex.findAll(htmlWithoutJsonLd)) {
                val tag = imgMatch.value
                val src = dataFullAttrRegex.find(tag)?.groupValues?.getOrNull(1)
                    ?: dataSrcAttrRegex.find(tag)?.groupValues?.getOrNull(1)
                    ?: srcAttrRegex.find(tag)?.groupValues?.getOrNull(1)

                if (!src.isNullOrBlank() &&
                    !UrlMediaUtils.isThumbnailOrHptUrl(src) &&
                    mediaExtRegex.containsMatchIn(src.trim())
                ) {
                    UrlMediaUtils.canonicalizeMediaUrl(src, albumUrl)?.let {
                        if (!UrlMediaUtils.isThumbnailOrHptUrl(it)) mediaLinks.add(it)
                    }
                }
            }

            // 3. Extract from <video> and nested <source> tags
            for (vidBlock in videoBlockRegex.findAll(htmlWithoutJsonLd)) {
                val fullVideoHtml = vidBlock.value
                val vidSrc = srcAttrRegex.find(fullVideoHtml)?.groupValues?.getOrNull(1)
                if (!vidSrc.isNullOrBlank() && !UrlMediaUtils.isThumbnailOrHptUrl(vidSrc)) {
                    UrlMediaUtils.canonicalizeMediaUrl(vidSrc, albumUrl)?.let {
                        if (!UrlMediaUtils.isThumbnailOrHptUrl(it)) mediaLinks.add(it)
                    }
                }
                for (sourceMatch in sourceTagRegex.findAll(fullVideoHtml)) {
                    val sSrc = srcAttrRegex.find(sourceMatch.value)?.groupValues?.getOrNull(1)
                    if (!sSrc.isNullOrBlank() && !UrlMediaUtils.isThumbnailOrHptUrl(sSrc)) {
                        UrlMediaUtils.canonicalizeMediaUrl(sSrc, albumUrl)?.let {
                            if (!UrlMediaUtils.isThumbnailOrHptUrl(it)) mediaLinks.add(it)
                        }
                    }
                }
            }
            // Also check self-closing or unclosed <video> / <source> tags
            for (vidTag in videoSingleTagRegex.findAll(htmlWithoutJsonLd)) {
                val vSrc = srcAttrRegex.find(vidTag.value)?.groupValues?.getOrNull(1)
                if (!vSrc.isNullOrBlank() && !UrlMediaUtils.isThumbnailOrHptUrl(vSrc)) {
                    UrlMediaUtils.canonicalizeMediaUrl(vSrc, albumUrl)?.let {
                        if (!UrlMediaUtils.isThumbnailOrHptUrl(it)) mediaLinks.add(it)
                    }
                }
            }
            for (srcTag in sourceTagRegex.findAll(htmlWithoutJsonLd)) {
                val sSrc = srcAttrRegex.find(srcTag.value)?.groupValues?.getOrNull(1)
                if (!sSrc.isNullOrBlank() && !UrlMediaUtils.isThumbnailOrHptUrl(sSrc)) {
                    UrlMediaUtils.canonicalizeMediaUrl(sSrc, albumUrl)?.let {
                        if (!UrlMediaUtils.isThumbnailOrHptUrl(it)) mediaLinks.add(it)
                    }
                }
            }

            // 4. Extract from <a href="..."> tags
            for (aMatch in aTagHrefRegex.findAll(htmlWithoutJsonLd)) {
                val href = aMatch.groupValues[1].trim()
                if (href.isNotEmpty() &&
                    !UrlMediaUtils.isThumbnailOrHptUrl(href) &&
                    mediaExtRegex.containsMatchIn(href)
                ) {
                    UrlMediaUtils.canonicalizeMediaUrl(href, albumUrl)?.let {
                        if (!UrlMediaUtils.isThumbnailOrHptUrl(it)) mediaLinks.add(it)
                    }
                }
            }

            // 5. Fallback regex for direct CDN links (/uploads/...(jpg|jpeg|png|mp4|webm))
            for (fallbackMatch in fallbackUploadsRegex.findAll(htmlWithoutJsonLd)) {
                val link = fallbackMatch.groupValues[1].trim()
                if (!UrlMediaUtils.isThumbnailOrHptUrl(link)) {
                    UrlMediaUtils.canonicalizeMediaUrl(link, albumUrl)?.let {
                        if (!UrlMediaUtils.isThumbnailOrHptUrl(it)) mediaLinks.add(it)
                    }
                }
            }

            // 6. Strict regex filter (Requirement 3): drop any URL whose filename matches HPT\d+\.(jpg|jpeg|png|webp)$ or contains /thumb/
            val finalLinks = mediaLinks.filterNot { UrlMediaUtils.isThumbnailOrHptUrl(it) }.filter { link ->
                val cleanPath = link.substringBefore('?').lowercase(Locale.US)
                if (strictJpgMp4Only) {
                    cleanPath.endsWith(".jpg") || cleanPath.endsWith(".jpeg") || cleanPath.endsWith(".mp4")
                } else {
                    cleanPath.endsWith(".jpg") || cleanPath.endsWith(".jpeg") ||
                        cleanPath.endsWith(".png") || cleanPath.endsWith(".mp4") || cleanPath.endsWith(".webm")
                }
            }

            val items = finalLinks.mapIndexedNotNull { index, link ->
                val cleanPath = link.substringBefore('?').lowercase(Locale.US)
                val ext = cleanPath.substringAfterLast('.', "jpg")
                val mediaType = if (ext == "mp4" || ext == "webm") MediaType.VIDEO else MediaType.IMAGE
                val mime = UrlMediaUtils.inferMimeType(ext, mediaType)
                val fileTitle = UrlMediaUtils.deriveTitleFromUrl(
                    url = link,
                    fallbackTitle = "${albumTitle}_${index + 1}",
                    extension = ext
                )
                DetectedMediaItem(
                    id = link.hashCode().toUInt().toString(16),
                    url = link,
                    normalizedUrl = link,
                    pageUrl = albumUrl,
                    pageTitle = albumTitle,
                    crawlDepth = 1,
                    title = fileTitle,
                    mediaType = mediaType,
                    extension = ext,
                    mimeType = mime
                )
            }

            val normalizedAlbum = UrlMediaUtils.canonicalizeMediaUrl(albumUrl, albumUrl) ?: albumUrl
            return ScrapedAlbumResult(
                albumUrl = albumUrl,
                normalizedAlbumUrl = normalizedAlbum,
                title = albumTitle,
                folderName = folderName,
                mediaItems = items
            )
        }

        fun hasDirectAlbumMedia(html: String, url: String, strictJpgMp4Only: Boolean): Boolean {
            return parseAlbumMediaFromHtml(url, html, strictJpgMp4Only).mediaItems.isNotEmpty()
        }

        /**
         * Extracts child Album URLs from an Album Folder / Index page.
         */
        fun extractChildAlbumUrls(htmlText: String, folderUrl: String): List<String> {
            val baseHost = runCatching {
                URI(folderUrl).host?.lowercase(Locale.US)?.removePrefix("www.")
            }.getOrNull()
            val normalizedFolder = UrlMediaUtils.canonicalizeMediaUrl(folderUrl, folderUrl) ?: folderUrl

            val albumCandidates = LinkedHashSet<String>()
            val generalSubpages = LinkedHashSet<String>()

            val ignoredKeywords = listOf(
                "/login", "/signin", "/signup", "/register", "/logout",
                "/contact", "/terms", "/privacy", "/dmca", "/2257",
                "/faq", "/help", "/about", "/search", "/tag/", "/tags/",
                "/category/", "/categories/", "/author/", "/feed", "javascript:", "mailto:"
            )

            val albumKeywords = listOf(
                "/album/", "/albums/", "/gallery/", "/galleries/",
                "/v/", "/a/", "/post/", "/posts/", "/video/", "/videos/",
                "/photo/", "/photos/", "/set/", "/collection/", "/view/"
            )

            for (match in aTagHrefRegex.findAll(htmlText)) {
                val rawHref = match.groupValues[1].trim()
                if (rawHref.isEmpty() || rawHref.startsWith("#")) continue
                val canonical = UrlMediaUtils.canonicalizeMediaUrl(rawHref, folderUrl) ?: continue
                if (canonical == normalizedFolder) continue

                val lower = canonical.lowercase(Locale.US)
                if (ignoredKeywords.any { lower.contains(it) }) continue

                // Skip direct media files or static assets
                val ext = UrlMediaUtils.extractExtension(canonical)
                if (ext.isNotEmpty() && ext !in listOf("html", "htm", "php", "asp", "aspx")) continue

                // Ensure same domain
                if (baseHost != null) {
                    val linkHost = runCatching {
                        URI(canonical).host?.lowercase(Locale.US)?.removePrefix("www.")
                    }.getOrNull()
                    if (linkHost != baseHost) continue
                }

                val path = runCatching { URI(canonical).path ?: "" }.getOrDefault("")
                if (path.isBlank() || path == "/") continue

                if (albumKeywords.any { lower.contains(it) }) {
                    albumCandidates.add(canonical)
                } else {
                    val segments = path.trim('/').split('/').filter { it.isNotBlank() }
                    if (segments.isNotEmpty()) {
                        generalSubpages.add(canonical)
                    }
                }
            }

            return if (albumCandidates.isNotEmpty()) {
                albumCandidates.toList()
            } else {
                generalSubpages.toList()
            }
        }

        private fun cleanHtmlText(raw: String): String {
            return raw
                .replace(htmlTagStripRegex, " ")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&ndash;", "-")
                .replace("&mdash;", "-")
                .replace("&nbsp;", " ")
                .replace(whitespaceRegex, " ")
                .trim()
        }

        fun buildShowcaseAlbumHtml(albumUrl: String): String {
            val slug = albumUrl.substringAfterLast('/').ifBlank { "neon-cyber-set" }
            val title = slug.split('-').joinToString(" ") { part ->
                part.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
            }
            val seed = slug.hashCode().toUInt().toInt() % 50 + 10
            return """
                <!DOCTYPE html>
                <html>
                <head><title>$title - High Res Album</title></head>
                <body>
                    <h1>$title</h1>
                    <div class="album-media">
                        <!-- Thumbnail that should be ignored because of /thumb/ -->
                        <img src="https://showcase.pulsegrab.local/thumb/preview_$seed.jpg" />
                        <!-- Full-res images via data-src and data-full -->
                        <img data-src="https://picsum.photos/id/${seed + 1}/1920/1080.jpg" src="https://showcase.pulsegrab.local/thumb/1.jpg" />
                        <img data-full="https://picsum.photos/id/${seed + 2}/1920/1080.jpg" />
                        <a href="https://picsum.photos/id/${seed + 3}/1920/1080.jpeg">Download Original JPEG</a>
                        <video>
                            <source src="https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4" type="video/mp4" />
                        </video>
                        <!-- Direct CDN /uploads/ link matched by fallback regex -->
                        <script>
                            const cdnBackup = "https://cdn.pulsegrab.local/uploads/${slug}_bonus_clip.mp4";
                        </script>
                    </div>
                </body>
                </html>
            """.trimIndent()
        }
    }
}
