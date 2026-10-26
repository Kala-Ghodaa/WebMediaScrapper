package com.example.data.scanner

import com.example.domain.model.DeepScanConfig
import com.example.domain.model.DeepScanMode
import com.example.domain.model.DeepScanProgress
import com.example.domain.model.DetectedMediaItem
import com.example.domain.model.ExtensionFilterConfig
import com.example.domain.usecase.ExpandUrlPatternUseCase
import com.example.domain.usecase.UrlMediaUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.TimeUnit

class DeepScanEngine(
    private val scope: CoroutineScope,
    private val expandUrlPatternUseCase: ExpandUrlPatternUseCase = ExpandUrlPatternUseCase(),
    private val onMediaDiscovered: (List<DetectedMediaItem>) -> Unit
) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val _progress = MutableStateFlow(DeepScanProgress())
    val progress: StateFlow<DeepScanProgress> = _progress.asStateFlow()

    private var activeScanJob: Job? = null

    fun startDeepScan(
        config: DeepScanConfig,
        extensionFilterConfig: ExtensionFilterConfig
    ) {
        activeScanJob?.cancel()
        val rawSeed = config.seedUrl.trim().ifEmpty {
            config.urlPattern.trim()
        }
        if (rawSeed.isEmpty()) {
            _progress.value = DeepScanProgress(
                isScanning = false,
                errorMessage = "Please enter a starting URL or URL pattern to scan."
            )
            return
        }

        val normalizedSeed = UrlMediaUtils.normalizeInputUrl(rawSeed)

        activeScanJob = scope.launch(Dispatchers.IO) {
            try {
                _progress.value = DeepScanProgress(
                    isScanning = true,
                    isPaused = false,
                    mode = config.mode,
                    pagesScanned = 0,
                    pagesTotalOrQueued = 1,
                    matchedUrlsCount = 0,
                    mediaFoundCount = 0,
                    currentScanningUrl = normalizedSeed,
                    currentDepth = 0,
                    recentScannedUrls = emptyList(),
                    errorMessage = null
                )

                val patternSource = config.urlPattern.trim().ifEmpty { normalizedSeed }
                val hasRange = expandUrlPatternUseCase.hasRangePattern(patternSource)

                if (config.mode == DeepScanMode.URL_PATTERN && hasRange) {
                    runRangePatternScan(
                        patternUrl = UrlMediaUtils.normalizeInputUrl(patternSource),
                        maxPages = config.maxPages,
                        extensionFilterConfig = extensionFilterConfig
                    )
                } else {
                    runMultiLevelDomainBfsScan(
                        seedUrl = normalizedSeed,
                        config = config,
                        extensionFilterConfig = extensionFilterConfig
                    )
                }

                _progress.update {
                    it.copy(
                        isScanning = false,
                        isPaused = false,
                        currentScanningUrl = "Scan Complete"
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                _progress.update {
                    it.copy(
                        isScanning = false,
                        isPaused = false,
                        errorMessage = e.localizedMessage ?: "Deep scan interrupted"
                    )
                }
            }
        }
    }

    fun togglePause() {
        _progress.update { current ->
            if (current.isScanning) {
                current.copy(isPaused = !current.isPaused)
            } else {
                current
            }
        }
    }

    fun stopDeepScan() {
        activeScanJob?.cancel()
        activeScanJob = null
        _progress.update {
            it.copy(
                isScanning = false,
                isPaused = false,
                currentScanningUrl = "Stopped"
            )
        }
    }

    private suspend fun waitWhilePaused() {
        while (_progress.value.isPaused) {
            delay(250)
        }
    }

    private suspend fun runRangePatternScan(
        patternUrl: String,
        maxPages: Int,
        extensionFilterConfig: ExtensionFilterConfig
    ) {
        val expandedUrls = expandUrlPatternUseCase.expandPattern(patternUrl, maxCount = maxPages)
        _progress.update {
            it.copy(
                pagesTotalOrQueued = expandedUrls.size,
                matchedUrlsCount = expandedUrls.size
            )
        }

        for ((index, targetUrl) in expandedUrls.withIndex()) {
            if (! kotlinx.coroutines.currentCoroutineContext().isActive) break
            waitWhilePaused()

            _progress.update {
                it.copy(
                    currentScanningUrl = targetUrl,
                    currentDepth = 1,
                    recentScannedUrls = (listOf(targetUrl) + it.recentScannedUrls).take(8)
                )
            }

            // Check if the expanded URL itself is a direct media file (e.g. img_[01-20].jpg)
            if (UrlMediaUtils.isThumbnailOrHptUrl(targetUrl)) {
                _progress.update { it.copy(pagesScanned = index + 1) }
                continue
            }
            val directExt = UrlMediaUtils.extractExtension(targetUrl)
            val directType = UrlMediaUtils.inferMediaType(targetUrl, directExt)
            if (directType != null && directExt.isNotEmpty()) {
                val item = WebMediaScannerEngine.buildDetectedItem(
                    rawUrl = targetUrl,
                    baseUrl = targetUrl,
                    tagHint = if (directType == com.example.domain.model.MediaType.VIDEO) "video" else "img",
                    titleHint = "Pattern Item #${index + 1}",
                    crawlDepth = 1
                )
                if (item != null && extensionFilterConfig.isExtensionAllowed(item.extension, item.mediaType)) {
                    onMediaDiscovered(listOf(item))
                    _progress.update {
                        it.copy(
                            pagesScanned = index + 1,
                            mediaFoundCount = it.mediaFoundCount + 1
                        )
                    }
                } else {
                    _progress.update { it.copy(pagesScanned = index + 1) }
                }
                delay(60)
                continue
            }

            // Otherwise fetch HTML page and extract media
            val html = fetchHtmlOrShowcase(targetUrl, index + 1)
            if (html != null) {
                val mediaItems = extractMediaFromHtml(html, targetUrl, crawlDepth = 1, extensionFilterConfig)
                if (mediaItems.isNotEmpty()) {
                    onMediaDiscovered(mediaItems)
                }
                _progress.update {
                    it.copy(
                        pagesScanned = index + 1,
                        mediaFoundCount = it.mediaFoundCount + mediaItems.size
                    )
                }
            } else {
                _progress.update { it.copy(pagesScanned = index + 1) }
            }
        }
    }

    private suspend fun runMultiLevelDomainBfsScan(
        seedUrl: String,
        config: DeepScanConfig,
        extensionFilterConfig: ExtensionFilterConfig
    ) {
        val visitedPages = HashSet<String>()
        val queue = ArrayDeque<Pair<String, Int>>()

        // If user also supplied a range pattern inside urlPattern, seed with expanded URLs
        if (config.urlPattern.isNotBlank() && expandUrlPatternUseCase.hasRangePattern(config.urlPattern)) {
            val expanded = expandUrlPatternUseCase.expandPattern(
                UrlMediaUtils.normalizeInputUrl(config.urlPattern),
                maxCount = config.maxPages
            )
            for (u in expanded) {
                if (visitedPages.add(u)) {
                    queue.add(u to 1)
                }
            }
        } else {
            visitedPages.add(seedUrl)
            queue.add(seedUrl to 0)
        }

        val seedHost = runCatching { URI(seedUrl).host?.lowercase(Locale.US)?.removePrefix("www.") }.getOrNull()
        var scannedCount = 0

        while (queue.isNotEmpty() && scannedCount < config.maxPages) {
            if (!kotlinx.coroutines.currentCoroutineContext().isActive) break
            waitWhilePaused()

            val (currentUrl, depth) = queue.poll() ?: break
            scannedCount++

            _progress.update {
                it.copy(
                    pagesScanned = scannedCount,
                    pagesTotalOrQueued = minOf(config.maxPages, scannedCount + queue.size),
                    currentScanningUrl = currentUrl,
                    currentDepth = depth,
                    recentScannedUrls = (listOf(currentUrl) + it.recentScannedUrls).take(8)
                )
            }

            val html = fetchHtmlOrShowcase(currentUrl, scannedCount)
            if (html != null) {
                val mediaItems = extractMediaFromHtml(html, currentUrl, depth, extensionFilterConfig)
                if (mediaItems.isNotEmpty()) {
                    onMediaDiscovered(mediaItems)
                }

                var newLinksMatched = 0
                if (depth < config.maxDepth && visitedPages.size < config.maxPages * 2) {
                    val links = extractLinksFromHtml(html, currentUrl)
                    for (link in links) {
                        if (visitedPages.size >= config.maxPages * 2) break
                        if (config.sameDomainOnly && seedHost != null) {
                            val linkHost = runCatching {
                                URI(link).host?.lowercase(Locale.US)?.removePrefix("www.")
                            }.getOrNull()
                            if (linkHost != seedHost) continue
                        }
                        if (config.urlPattern.isNotBlank() &&
                            !expandUrlPatternUseCase.matchesWildcardPattern(link, config.urlPattern)
                        ) {
                            continue
                        }
                        if (visitedPages.add(link)) {
                            queue.add(link to (depth + 1))
                            newLinksMatched++
                        }
                    }
                }

                _progress.update {
                    it.copy(
                        pagesTotalOrQueued = minOf(config.maxPages, scannedCount + queue.size),
                        matchedUrlsCount = it.matchedUrlsCount + newLinksMatched,
                        mediaFoundCount = it.mediaFoundCount + mediaItems.size
                    )
                }
            }
        }
    }

    private suspend fun fetchHtmlOrShowcase(url: String, pageIndex: Int): String? {
        if (url.startsWith("pulsegrab://") || url.contains("showcase.pulsegrab.local")) {
            val pageNum = Regex("""page-(\d+)""").find(url)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: pageIndex.coerceIn(1, 10)
            return WebMediaScannerEngine.buildShowcaseHtml(pageNum)
        }

        val outcome = BrowserHttpBridge.fetchHtmlResilient(
            url = url,
            refererUrl = BrowserHttpBridge.getOriginUrl(url),
            allowWebViewFallback = true
        )
        return outcome.html
    }

    companion object {
        private val imgTagRegex = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)
        private val videoTagRegex = Regex("""<(?:video|source)\b[^>]*>""", RegexOption.IGNORE_CASE)
        private val metaTagRegex = Regex(
            """<meta\b[^>]+(?:property|name)\s*=\s*["'](?:og:image|og:video|twitter:image)["'][^>]*>""",
            RegexOption.IGNORE_CASE
        )
        private val dataSrcAttrRegex = Regex("""\bdata-src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        private val onlySrcAttrRegex = Regex("""\bsrc\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        private val dataFullAttrRegex = Regex("""\bdata-full\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        private val srcAttrRegex = Regex(
            """\b(?:data-src|src|data-full|data-original|data-lazy-src|poster|content)\s*=\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        private val srcsetAttrRegex = Regex("""\bsrcset\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        private val altAttrRegex = Regex("""\b(?:alt|title)\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        private val widthAttrRegex = Regex("""\bwidth\s*=\s*["']?(\d+)""", RegexOption.IGNORE_CASE)
        private val heightAttrRegex = Regex("""\bheight\s*=\s*["']?(\d+)""", RegexOption.IGNORE_CASE)
        private val hrefAttrRegex = Regex("""<a\b[^>]+href\s*=\s*["']([^"'#]+)["']""", RegexOption.IGNORE_CASE)
        private val h1TitleRegex = Regex("""<h1\b[^>]*>(.*?)</h1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val pageTitleRegex = Regex("""<title\b[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val fallbackUploadsCdnRegex = Regex(
            """(https?://[^\s"']+/(?:uploads/[^"'\s]+\.(?:jpg|jpeg|png|mp4|webm)))""",
            RegexOption.IGNORE_CASE
        )

        fun extractMediaFromHtml(
            html: String,
            baseUrl: String,
            crawlDepth: Int,
            extensionFilterConfig: ExtensionFilterConfig
        ): List<DetectedMediaItem> {
            val discovered = LinkedHashMap<String, DetectedMediaItem>()
            val rawPageTitle = (h1TitleRegex.find(html)?.groupValues?.getOrNull(1)
                ?: pageTitleRegex.find(html)?.groupValues?.getOrNull(1))
                ?.replace(Regex("<[^>]+>"), " ")
                ?.replace(Regex("\\s+"), " ")
                ?.trim()
                .orEmpty()

            // 0. <script type="application/ld+json"> blocks (extracts url/contentUrl/embedUrl and ignores thumbnailUrl)
            val jsonLdRegex = Regex(
                """<script\b[^>]*\btype\s*=\s*["']application/ld\+json["'][^>]*>(.*?)</script>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
            for (jsonLdMatch in jsonLdRegex.findAll(html)) {
                val script = jsonLdMatch.groupValues.getOrNull(1).orEmpty()
                val urls = com.example.data.scanner.AlbumBatchScraperEngine.extractMediaUrlsFromJsonLd(script, baseUrl, false)
                for (url in urls) {
                    val item = WebMediaScannerEngine.buildDetectedItem(
                        rawUrl = url,
                        baseUrl = baseUrl,
                        tagHint = "json-ld",
                        titleHint = rawPageTitle,
                        crawlDepth = crawlDepth
                    )?.copy(pageTitle = rawPageTitle)
                    if (item != null && extensionFilterConfig.isExtensionAllowed(item.extension, item.mediaType)) {
                        discovered[item.normalizedUrl] = item
                    }
                }
            }

            // Strip JSON-LD block from HTML so fallback regexes never parse raw JSON strings inside JSON-LD
            val htmlWithoutJsonLd = html.replace(jsonLdRegex, " ")

            // 1. <img> tags (prioritizing data-src -> src -> data-full and skipping /thumb/ and HPT)
            for (match in imgTagRegex.findAll(htmlWithoutJsonLd)) {
                val tagText = match.value
                val src = dataSrcAttrRegex.find(tagText)?.groupValues?.getOrNull(1)
                    ?: onlySrcAttrRegex.find(tagText)?.groupValues?.getOrNull(1)
                    ?: dataFullAttrRegex.find(tagText)?.groupValues?.getOrNull(1)
                    ?: srcAttrRegex.find(tagText)?.groupValues?.getOrNull(1)

                val alt = altAttrRegex.find(tagText)?.groupValues?.getOrNull(1).orEmpty().ifBlank { rawPageTitle }
                val w = widthAttrRegex.find(tagText)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                val h = heightAttrRegex.find(tagText)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0

                if (!src.isNullOrBlank() && !UrlMediaUtils.isThumbnailOrHptUrl(src)) {
                    val item = WebMediaScannerEngine.buildDetectedItem(
                        rawUrl = src,
                        baseUrl = baseUrl,
                        tagHint = "img",
                        titleHint = alt,
                        width = w,
                        height = h,
                        crawlDepth = crawlDepth
                    )?.copy(pageTitle = rawPageTitle)
                    if (item != null && extensionFilterConfig.isExtensionAllowed(item.extension, item.mediaType)) {
                        discovered[item.normalizedUrl] = item
                    }
                }

                val dataFull = dataFullAttrRegex.find(tagText)?.groupValues?.getOrNull(1)
                if (!dataFull.isNullOrBlank() && !UrlMediaUtils.isThumbnailOrHptUrl(dataFull)) {
                    val item = WebMediaScannerEngine.buildDetectedItem(
                        rawUrl = dataFull,
                        baseUrl = baseUrl,
                        tagHint = "img",
                        titleHint = alt,
                        width = w,
                        height = h,
                        crawlDepth = crawlDepth
                    )?.copy(pageTitle = rawPageTitle)
                    if (item != null && extensionFilterConfig.isExtensionAllowed(item.extension, item.mediaType)) {
                        discovered[item.normalizedUrl] = item
                    }
                }

                val srcset = srcsetAttrRegex.find(tagText)?.groupValues?.getOrNull(1)
                if (!srcset.isNullOrBlank()) {
                    srcset.split(',').forEach { entry ->
                        val urlPart = entry.trim().substringBefore(' ')
                        if (!UrlMediaUtils.isThumbnailOrHptUrl(urlPart)) {
                            val item = WebMediaScannerEngine.buildDetectedItem(
                                rawUrl = urlPart,
                                baseUrl = baseUrl,
                                tagHint = "img",
                                titleHint = alt,
                                width = w,
                                height = h,
                                crawlDepth = crawlDepth
                            )?.copy(pageTitle = rawPageTitle)
                            if (item != null && extensionFilterConfig.isExtensionAllowed(item.extension, item.mediaType)) {
                                discovered[item.normalizedUrl] = item
                            }
                        }
                    }
                }
            }

            // 2. <video> and <source> tags (skipping /thumb/ and HPT)
            for (match in videoTagRegex.findAll(htmlWithoutJsonLd)) {
                val tagText = match.value
                val src = srcAttrRegex.find(tagText)?.groupValues?.getOrNull(1) ?: continue
                if (UrlMediaUtils.isThumbnailOrHptUrl(src)) continue
                val title = altAttrRegex.find(tagText)?.groupValues?.getOrNull(1).orEmpty().ifBlank { rawPageTitle }
                val w = widthAttrRegex.find(tagText)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                val h = heightAttrRegex.find(tagText)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                val isVid = tagText.startsWith("<video", ignoreCase = true) ||
                    tagText.contains("video/", ignoreCase = true) ||
                    src.endsWith(".mp4", ignoreCase = true) ||
                    src.endsWith(".webm", ignoreCase = true)

                val item = WebMediaScannerEngine.buildDetectedItem(
                    rawUrl = src,
                    baseUrl = baseUrl,
                    tagHint = if (isVid) "video" else "img",
                    titleHint = title,
                    width = w,
                    height = h,
                    crawlDepth = crawlDepth
                )?.copy(pageTitle = rawPageTitle)
                if (item != null && extensionFilterConfig.isExtensionAllowed(item.extension, item.mediaType)) {
                    discovered[item.normalizedUrl] = item
                }
            }

            // 3. <meta og:image / og:video>
            for (match in metaTagRegex.findAll(htmlWithoutJsonLd)) {
                val tagText = match.value
                val content = srcAttrRegex.find(tagText)?.groupValues?.getOrNull(1) ?: continue
                if (UrlMediaUtils.isThumbnailOrHptUrl(content)) continue
                val isVid = tagText.contains("video", ignoreCase = true)
                val item = WebMediaScannerEngine.buildDetectedItem(
                    rawUrl = content,
                    baseUrl = baseUrl,
                    tagHint = if (isVid) "video" else "img",
                    titleHint = rawPageTitle,
                    crawlDepth = crawlDepth
                )?.copy(pageTitle = rawPageTitle)
                if (item != null && extensionFilterConfig.isExtensionAllowed(item.extension, item.mediaType)) {
                    discovered[item.normalizedUrl] = item
                }
            }

            // 4. Direct media <a href="..."> links (skipping /thumb/ and HPT)
            for (match in hrefAttrRegex.findAll(htmlWithoutJsonLd)) {
                val rawHref = match.groupValues[1]
                if (UrlMediaUtils.isThumbnailOrHptUrl(rawHref)) continue
                val ext = UrlMediaUtils.extractExtension(rawHref)
                if (ext.isNotEmpty()) {
                    val mediaType = UrlMediaUtils.inferMediaType(rawHref, ext)
                    if (mediaType != null) {
                        val item = WebMediaScannerEngine.buildDetectedItem(
                            rawUrl = rawHref,
                            baseUrl = baseUrl,
                            tagHint = if (mediaType == com.example.domain.model.MediaType.VIDEO) "video" else "img",
                            titleHint = rawPageTitle,
                            crawlDepth = crawlDepth
                        )?.copy(pageTitle = rawPageTitle)
                        if (item != null && extensionFilterConfig.isExtensionAllowed(item.extension, item.mediaType)) {
                            discovered[item.normalizedUrl] = item
                        }
                    }
                }
            }

            // 5. Fallback regex for direct CDN links (/uploads/...(jpg|jpeg|png|mp4|webm))
            for (fallbackMatch in fallbackUploadsCdnRegex.findAll(htmlWithoutJsonLd)) {
                val link = fallbackMatch.groupValues[1].trim()
                if (UrlMediaUtils.isThumbnailOrHptUrl(link)) continue
                val ext = UrlMediaUtils.extractExtension(link)
                val mediaType = UrlMediaUtils.inferMediaType(link, ext)
                    ?: if (ext == "mp4" || ext == "webm") com.example.domain.model.MediaType.VIDEO else com.example.domain.model.MediaType.IMAGE
                val item = WebMediaScannerEngine.buildDetectedItem(
                    rawUrl = link,
                    baseUrl = baseUrl,
                    tagHint = if (mediaType == com.example.domain.model.MediaType.VIDEO) "video" else "img",
                    titleHint = rawPageTitle,
                    crawlDepth = crawlDepth
                )?.copy(pageTitle = rawPageTitle)
                if (item != null && extensionFilterConfig.isExtensionAllowed(item.extension, item.mediaType)) {
                    discovered[item.normalizedUrl] = item
                }
            }

            return discovered.values.toList()
        }

        fun extractLinksFromHtml(html: String, baseUrl: String): List<String> {
            val links = LinkedHashSet<String>()
            for (match in hrefAttrRegex.findAll(html)) {
                val rawHref = match.groupValues[1].trim()
                if (UrlMediaUtils.isThumbnailOrHptUrl(rawHref)) continue
                val canonical = UrlMediaUtils.canonicalizeMediaUrl(rawHref, baseUrl) ?: continue
                if (UrlMediaUtils.isThumbnailOrHptUrl(canonical)) continue
                // Skip direct media files when queuing HTML pages for BFS
                val ext = UrlMediaUtils.extractExtension(canonical)
                if (ext.isNotEmpty() && UrlMediaUtils.inferMediaType(canonical, ext) != null) {
                    continue
                }
                if (canonical.startsWith("http://") || canonical.startsWith("https://")) {
                    links.add(canonical)
                }
            }
            return links.toList()
        }
    }
}
