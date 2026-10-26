package com.example.data.scanner

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.util.Base64
import com.example.data.logging.DiagnosticLogManager
import com.example.data.logging.LogCategory
import com.example.data.logging.LogSeverity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.net.URI
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

data class HtmlFetchOutcome(
    val html: String?,
    val statusCode: Int,
    val usedWebViewFallback: Boolean = false,
    val errorSummary: String? = null
)

/**
 * Centralized anti-403 HTTP & WebView session bridge.
 *
 * Solves frequent HTTP 403 Forbidden errors by:
 * 1. Synchronizing cookies bidirectionally between Android's WebView [CookieManager] and [OkHttpClient]
 *    (preserving `cf_clearance`, session tokens, and age-gate cookies).
 * 2. Sending realistic Chrome `Sec-Fetch-*`, `Sec-Ch-Ua*`, `Upgrade-Insecure-Requests`, and `Referer` headers.
 * 3. Cascading across multiple User-Agent / Referer profiles when a CDN or WAF returns 403/401/429.
 * 4. Automatically falling back to an offscreen Android [WebView] DOM evaluation (`document.documentElement.outerHTML`)
 *    when OkHttp is blocked by Cloudflare / JS anti-bot protection (HTTP 403 / 503).
 */
object BrowserHttpBridge {

    const val MOBILE_CHROME_UA =
        "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    const val DESKTOP_CHROME_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    @Volatile
    var activeUserAgent: String = DESKTOP_CHROME_UA

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var isWebViewEngineInitialized: Boolean = false

    private val webViewDomMutex = Mutex()

    private val memoryCookieStore = ConcurrentHashMap<String, MutableMap<String, String>>()

    fun init(context: Context) {
        val appCtx = context.applicationContext
        appContext = appCtx
        purgeCorruptWebViewVariationsSeeds(appCtx)
    }

    /**
     * Removes unsigned/corrupt Chromium `variations_seed` files from `app_webview` so that
     * Android System WebView on emulators does not log:
     * `E/chromium: [ERROR:variations_seed_loader.cc(37)] Seed missing signature.`
     */
    fun purgeCorruptWebViewVariationsSeeds(context: Context? = appContext) {
        val ctx = context ?: return
        runCatching {
            val webViewDir = java.io.File(ctx.applicationInfo.dataDir, "app_webview")
            if (webViewDir.exists()) {
                listOf("variations_seed", "variations_seed_new", "variations_stamp").forEach { fileName ->
                    val seedFile = java.io.File(webViewDir, fileName)
                    if (seedFile.exists()) {
                        seedFile.delete()
                    }
                }
            }
        }
    }

    fun syncWebViewSettings(webView: WebView) {
        purgeCorruptWebViewVariationsSeeds(webView.context)
        isWebViewEngineInitialized = true
        runCatching {
            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            cm.setAcceptThirdPartyCookies(webView, true)
            // Sync any cookies collected by OkHttp in memory into WebView's CookieManager
            memoryCookieStore.forEach { (host, cookies) ->
                val domainUrl = "https://$host/"
                cookies.forEach { (name, value) ->
                    cm.setCookie(domainUrl, "$name=$value; Path=/")
                }
            }
            cm.flush()
        }
        val ua = webView.settings.userAgentString
        if (!ua.isNullOrBlank()) {
            // Strip "; wv" token which many WAFs/Cloudflare rules flag as an embedded scraper WebView!
            val cleanUa = ua.replace("; wv)", ")").replace("Version/4.0 ", "")
            webView.settings.userAgentString = cleanUa
            activeUserAgent = cleanUa
        } else {
            webView.settings.userAgentString = MOBILE_CHROME_UA
            activeUserAgent = MOBILE_CHROME_UA
        }
    }

    private val sharedCookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val host = url.host.lowercase(Locale.US).removePrefix("www.")
            val hostMap = memoryCookieStore.getOrPut(host) { ConcurrentHashMap() }
            for (cookie in cookies) {
                hostMap[cookie.name] = cookie.value
                if (isWebViewEngineInitialized) {
                    runCatching {
                        CookieManager.getInstance().setCookie(url.toString(), cookie.toString())
                    }
                }
            }
            if (isWebViewEngineInitialized) {
                runCatching {
                    CookieManager.getInstance().flush()
                }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val result = mutableListOf<Cookie>()
            val seenNames = HashSet<String>()

            // 1. Pull from Android WebView CookieManager (only if WebView engine is already active)
            val rawCookieHeader = getCombinedCookieHeader(url.toString(), null)
            if (!rawCookieHeader.isNullOrBlank()) {
                for (pair in rawCookieHeader.split(";")) {
                    val trimmed = pair.trim()
                    val eqIdx = trimmed.indexOf('=')
                    if (eqIdx > 0) {
                        val name = trimmed.substring(0, eqIdx).trim()
                        val value = trimmed.substring(eqIdx + 1).trim()
                        if (name.isNotEmpty() && seenNames.add(name)) {
                            runCatching {
                                Cookie.Builder()
                                    .name(name)
                                    .value(value)
                                    .domain(url.host)
                                    .path("/")
                                    .build()
                            }.getOrNull()?.let { result.add(it) }
                        }
                    }
                }
            }

            // 2. Pull from in-memory cookie map
            val host = url.host.lowercase(Locale.US).removePrefix("www.")
            memoryCookieStore[host]?.forEach { (name, value) ->
                if (seenNames.add(name)) {
                    runCatching {
                        Cookie.Builder()
                            .name(name)
                            .value(value)
                            .domain(url.host)
                            .path("/")
                            .build()
                    }.getOrNull()?.let { result.add(it) }
                }
            }
            return result
        }
    }

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(sharedCookieJar)
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(35, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun getOriginUrl(url: String): String {
        return runCatching {
            val uri = URI(url)
            if (uri.scheme != null && uri.host != null) {
                "${uri.scheme}://${uri.host}/"
            } else {
                url
            }
        }.getOrDefault(url)
    }

    fun getOriginWithoutTrailingSlash(url: String): String {
        return runCatching {
            val uri = URI(url)
            if (uri.scheme != null && uri.host != null) {
                "${uri.scheme}://${uri.host}"
            } else {
                url.trimEnd('/')
            }
        }.getOrDefault(url.trimEnd('/'))
    }

    fun getCombinedCookieHeader(targetUrl: String, refererUrl: String?): String? {
        val cookieParts = LinkedHashMap<String, String>()

        fun collectFromManager(u: String?) {
            if (u.isNullOrBlank()) return
            if (isWebViewEngineInitialized) {
                val raw = runCatching { CookieManager.getInstance().getCookie(u) }.getOrNull()
                if (!raw.isNullOrBlank()) {
                    for (segment in raw.split(";")) {
                        val s = segment.trim()
                        val idx = s.indexOf('=')
                        if (idx > 0) {
                            val k = s.substring(0, idx).trim()
                            val v = s.substring(idx + 1).trim()
                            if (k.isNotEmpty()) cookieParts[k] = v
                        }
                    }
                }
            }
            val host = runCatching { URI(u).host?.lowercase(Locale.US)?.removePrefix("www.") }.getOrNull()
            if (!host.isNullOrBlank()) {
                memoryCookieStore[host]?.forEach { (k, v) ->
                    cookieParts.putIfAbsent(k, v)
                }
            }
        }

        collectFromManager(targetUrl)
        collectFromManager(getOriginUrl(targetUrl))
        if (!refererUrl.isNullOrBlank()) {
            collectFromManager(refererUrl)
            collectFromManager(getOriginUrl(refererUrl))
        }

        if (cookieParts.isEmpty()) return null
        return cookieParts.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    /**
     * Builds an anti-403 HTML page navigation request.
     */
    fun buildHtmlNavigationRequest(
        url: String,
        refererUrl: String? = null,
        userAgent: String = activeUserAgent,
        includeSecFetchHeaders: Boolean = true
    ): Request {
        val effectiveReferer = when {
            !refererUrl.isNullOrBlank() -> refererUrl
            else -> getOriginUrl(url)
        }
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8"
            )
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Cache-Control", "no-cache")
            .header("Pragma", "no-cache")
            .header("Upgrade-Insecure-Requests", "1")

        if (effectiveReferer.isNotBlank()) {
            builder.header("Referer", effectiveReferer)
        }

        val cookies = getCombinedCookieHeader(url, effectiveReferer)
        if (!cookies.isNullOrBlank()) {
            builder.header("Cookie", cookies)
        }

        if (includeSecFetchHeaders) {
            val isMobile = userAgent.contains("Mobile", ignoreCase = true)
            builder.header("Sec-Ch-Ua", "\"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\", \"Google Chrome\";v=\"131\"")
            builder.header("Sec-Ch-Ua-Mobile", if (isMobile) "?1" else "?0")
            builder.header("Sec-Ch-Ua-Platform", if (isMobile) "\"Android\"" else "\"Windows\"")
            builder.header("Sec-Fetch-Dest", "document")
            builder.header("Sec-Fetch-Mode", "navigate")
            builder.header("Sec-Fetch-Site", if (refererUrl.isNullOrBlank()) "none" else "same-origin")
            builder.header("Sec-Fetch-User", "?1")
        }

        return builder.build()
    }

    /**
     * Fetches HTML with automatic anti-403 retry profiles + WebView DOM evaluation fallback.
     */
    suspend fun fetchHtmlResilient(
        url: String,
        refererUrl: String? = null,
        allowWebViewFallback: Boolean = true,
        forceWebViewDom: Boolean = false
    ): HtmlFetchOutcome = withContext(Dispatchers.IO) {
        if (forceWebViewDom && allowWebViewFallback && appContext != null) {
            val webViewHtml = fetchHtmlViaWebViewDom(url, refererUrl)
            if (!webViewHtml.isNullOrBlank() && webViewHtml.length > 60) {
                return@withContext HtmlFetchOutcome(
                    html = webViewHtml,
                    statusCode = 200,
                    usedWebViewFallback = true
                )
            }
        }

        val profiles = listOf(
            // Attempt 1: Active/Synced Browser User-Agent + exact Referer + Sec-Fetch headers
            Triple(activeUserAgent, refererUrl ?: getOriginUrl(url), true),
            // Attempt 2: Desktop Windows Chrome User-Agent + site origin Referer
            Triple(DESKTOP_CHROME_UA, getOriginUrl(url), true),
            // Attempt 3: Mobile Chrome User-Agent + no Sec-Fetch headers (some older servers dislike Sec-Fetch)
            Triple(MOBILE_CHROME_UA, refererUrl ?: getOriginUrl(url), false)
        )

        var lastCode = 0
        var lastError: String? = null

        for ((attemptIndex, profile) in profiles.withIndex()) {
            val (ua, ref, includeSec) = profile
            try {
                val request = buildHtmlNavigationRequest(
                    url = url,
                    refererUrl = ref,
                    userAgent = ua,
                    includeSecFetchHeaders = includeSec
                )
                httpClient.newCall(request).execute().use { response ->
                    lastCode = response.code
                    val bodyText = response.body?.string()

                    if (response.isSuccessful && !bodyText.isNullOrBlank() && !isCloudflareChallengeHtml(bodyText)) {
                        return@withContext HtmlFetchOutcome(
                            html = bodyText,
                            statusCode = response.code,
                            usedWebViewFallback = false
                        )
                    }

                    // If 404 Not Found, don't keep retrying other profiles
                    if (response.code == 404) {
                        return@withContext HtmlFetchOutcome(
                            html = null,
                            statusCode = 404,
                            errorSummary = "HTTP 404 Not Found"
                        )
                    }

                    lastError = "HTTP ${response.code}"
                }
            } catch (e: Exception) {
                lastError = e.localizedMessage ?: "Network error"
            }

            if (attemptIndex < profiles.lastIndex) {
                kotlinx.coroutines.delay(250L * (attemptIndex + 1))
            }
        }

        // Attempt 4: If blocked with 403 / 503 / 429 / Cloudflare challenge, use offscreen Android WebView DOM extraction!
        if (allowWebViewFallback && appContext != null && (lastCode == 403 || lastCode == 503 || lastCode == 401 || lastCode == 429)) {
            val webViewHtml = fetchHtmlViaWebViewDom(url, refererUrl)
            if (!webViewHtml.isNullOrBlank() && webViewHtml.length > 60) {
                return@withContext HtmlFetchOutcome(
                    html = webViewHtml,
                    statusCode = 200,
                    usedWebViewFallback = true
                )
            }
        }

        HtmlFetchOutcome(
            html = null,
            statusCode = lastCode,
            errorSummary = if (lastCode == 403) {
                "HTTP 403 Forbidden (Protected site — open page in Browser tab once to sync cookies)"
            } else {
                lastError ?: "HTTP $lastCode"
            }
        )
    }

    private fun isCloudflareChallengeHtml(html: String): Boolean {
        val sample = html.take(2500)
        return (sample.contains("<title>Just a moment...</title>", ignoreCase = true) ||
            sample.contains("cf-browser-verification", ignoreCase = true) ||
            sample.contains("__cf_chl_opt", ignoreCase = true)) &&
            !sample.contains("<img", ignoreCase = true)
    }

    /**
     * Loads the target URL inside an offscreen Android WebView on the Main thread, allowing WebView's
     * native BoringSSL TLS stack, CookieManager, and JS engine to execute, then extracts `document.documentElement.outerHTML`.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun fetchHtmlViaWebViewDom(
        url: String,
        refererUrl: String?
    ): String? = webViewDomMutex.withLock {
        val ctx = appContext ?: return@withLock null
        purgeCorruptWebViewVariationsSeeds(ctx)
        withTimeoutOrNull(18_000L) {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    var completed = false
                    val mainHandler = Handler(Looper.getMainLooper())
                    val webView = WebView(ctx)

                    fun cleanupAndResume(htmlResult: String?) {
                        if (completed) return
                        completed = true
                        mainHandler.removeCallbacksAndMessages(null)
                        runCatching {
                            CookieManager.getInstance().flush()
                            webView.stopLoading()
                        }
                        mainHandler.post {
                            runCatching {
                                webView.removeAllViews()
                                webView.destroy()
                            }
                        }
                        if (cont.isActive) {
                            cont.resume(htmlResult)
                        }
                    }

                    cont.invokeOnCancellation {
                        mainHandler.post {
                            runCatching {
                                webView.stopLoading()
                                webView.removeAllViews()
                                webView.destroy()
                            }
                        }
                    }

                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, true)
                        }
                        syncWebViewSettings(webView)
                        webView.settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            loadsImagesAutomatically = false // faster DOM load when only scraping HTML links
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            cacheMode = WebSettings.LOAD_NO_CACHE
                        }

                        fun extractOuterHtml(attempt: Int) {
                            if (completed) return
                            webView.evaluateJavascript(
                                "(function() { return document.documentElement ? document.documentElement.outerHTML : ''; })();"
                            ) { rawJsString ->
                                val decoded = decodeJsString(rawJsString)
                                if (decoded != null && isCloudflareChallengeHtml(decoded) && attempt < 3) {
                                    // Wait a bit longer for Cloudflare JS challenge to auto-resolve
                                    mainHandler.postDelayed({ extractOuterHtml(attempt + 1) }, 2200L)
                                } else {
                                    cleanupAndResume(decoded)
                                }
                            }
                        }

                        webView.webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                                super.onPageFinished(view, finishedUrl)
                                // Give dynamic DOM / lazy attributes 700ms to settle before grabbing outerHTML
                                mainHandler.postDelayed({ extractOuterHtml(1) }, 700L)
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                return false
                            }

                            override fun onRenderProcessGone(
                                view: WebView?,
                                detail: RenderProcessGoneDetail?
                            ): Boolean {
                                cleanupAndResume(null)
                                return true
                            }
                        }

                        // Safety timeout in case onPageFinished hangs on an ad tracker
                        mainHandler.postDelayed({ extractOuterHtml(3) }, 12_000L)

                        val extraHeaders = mutableMapOf<String, String>()
                        if (!refererUrl.isNullOrBlank()) {
                            extraHeaders["Referer"] = refererUrl
                        }
                        webView.loadUrl(url, extraHeaders)
                    } catch (_: Exception) {
                        cleanupAndResume(null)
                    }
                }
            }
        }
    }

    /**
     * Unescapes the JSON-encoded string returned by `WebView.evaluateJavascript`.
     */
    fun decodeJsString(raw: String?): String? {
        if (raw.isNullOrBlank() || raw == "null") return null
        return try {
            org.json.JSONTokener(raw).nextValue()?.toString()
        } catch (_: Exception) {
            raw.removeSurrounding("\"")
                .replace("\\u003C", "<")
                .replace("\\u003E", ">")
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\\\", "\\")
        }
    }

    /**
     * Executes a media file download request with an automatic 4-step Anti-403 Retry Cascade.
     *
     * Many image/video CDNs return HTTP 403 if:
     * - `Referer` is missing (hotlink protection)
     * - `Referer` has a full path instead of origin, or vice-versa
     * - `Cookie` header from the browsing session is missing
     * - `Sec-Fetch-Dest: image/video` is expected
     * - `Origin` is incorrectly included for standard image tags (which browsers omit on `no-cors` GET)
     */
    fun executeMediaDownloadWithRetry(
        mediaUrl: String,
        refererPageUrl: String?,
        existingBytes: Long,
        mediaType: com.example.domain.model.MediaType
    ): Response {
        if (com.example.domain.usecase.UrlMediaUtils.isThumbnailOrHptUrl(mediaUrl)) {
            DiagnosticLogManager.log(
                category = LogCategory.HTTP_NETWORK,
                severity = LogSeverity.INFO,
                tag = "MediaDownload",
                message = "Cleanly skipped protected SEO thumbnail / HPT asset without bypass attempts.",
                url = mediaUrl
            )
            throw IllegalStateException("Protected SEO thumbnail skipped")
        }

        val mediaOrigin = getOriginUrl(mediaUrl)
        val pageOrigin = if (!refererPageUrl.isNullOrBlank()) getOriginUrl(refererPageUrl) else mediaOrigin
        val effectivePageReferer = refererPageUrl?.takeIf { it.isNotBlank() } ?: mediaOrigin

        data class MediaAttemptProfile(
            val name: String,
            val userAgent: String,
            val referer: String?,
            val origin: String?,
            val includeSecFetch: Boolean
        )

        // For images: standard browser <img> tags with no-cors do NOT send an Origin header.
        // Sending Origin on no-cors image GETs causes many CDNs (Cloudflare, Bunny, Akamai) to reject with 403.
        val allowOrigin = mediaType != com.example.domain.model.MediaType.IMAGE

        val strategies = listOf(
            // Strategy 1: Exact album/page URL as Referer + synced Browser User-Agent + Cookies
            MediaAttemptProfile(
                name = "Active-Browser-UA + Page-Referer",
                userAgent = activeUserAgent,
                referer = effectivePageReferer,
                origin = if (allowOrigin) getOriginWithoutTrailingSlash(effectivePageReferer) else null,
                includeSecFetch = true
            ),
            // Strategy 2: Desktop Chrome UA + Site root origin as Referer (bypasses strict path-level hotlink rules)
            MediaAttemptProfile(
                name = "Desktop-Chrome + Site-Origin-Referer",
                userAgent = DESKTOP_CHROME_UA,
                referer = pageOrigin,
                origin = if (allowOrigin) getOriginWithoutTrailingSlash(pageOrigin) else null,
                includeSecFetch = true
            ),
            // Strategy 3: CDN's own origin as Referer (for CDNs that only allow self-referrals)
            MediaAttemptProfile(
                name = "Desktop-Chrome + CDN-Origin-Referer",
                userAgent = DESKTOP_CHROME_UA,
                referer = mediaOrigin,
                origin = if (allowOrigin) getOriginWithoutTrailingSlash(mediaOrigin) else null,
                includeSecFetch = false
            ),
            // Strategy 4: Omit Referer & Origin completely (for hosts that block any cross-domain referer)
            MediaAttemptProfile(
                name = "Mobile-Chrome + Clean-No-Referer",
                userAgent = MOBILE_CHROME_UA,
                referer = null,
                origin = null,
                includeSecFetch = false
            )
        )

        var lastResponse: Response? = null

        for ((index, strat) in strategies.withIndex()) {
            val reqBuilder = Request.Builder()
                .url(mediaUrl)
                .header("User-Agent", strat.userAgent)
                .header(
                    "Accept",
                    if (mediaType == com.example.domain.model.MediaType.VIDEO) {
                        "video/webm,video/ogg,video/*;q=0.9,application/ogg;q=0.7,audio/*;q=0.6,*/*;q=0.5"
                    } else {
                        "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"
                    }
                )
                .header("Accept-Language", "en-US,en;q=0.9")

            if (!strat.referer.isNullOrBlank()) {
                reqBuilder.header("Referer", strat.referer)
            }
            if (!strat.origin.isNullOrBlank()) {
                reqBuilder.header("Origin", strat.origin)
            }

            val cookieHeader = getCombinedCookieHeader(mediaUrl, refererPageUrl)
            if (!cookieHeader.isNullOrBlank()) {
                reqBuilder.header("Cookie", cookieHeader)
            }

            if (strat.includeSecFetch) {
                reqBuilder.header(
                    "Sec-Fetch-Dest",
                    if (mediaType == com.example.domain.model.MediaType.VIDEO) "video" else "image"
                )
                reqBuilder.header("Sec-Fetch-Mode", "no-cors")
                val sameHost = runCatching {
                    URI(mediaUrl).host.equals(URI(strat.referer ?: mediaUrl).host, ignoreCase = true)
                }.getOrDefault(true)
                reqBuilder.header("Sec-Fetch-Site", if (sameHost) "same-origin" else "cross-site")
            }

            if (existingBytes > 0L) {
                reqBuilder.header("Range", "bytes=$existingBytes-")
            }

            val request = reqBuilder.build()
            val reqHeadersMap = request.headers.toMultimap().mapValues { it.value.joinToString(", ") }

            DiagnosticLogManager.log(
                category = LogCategory.HTTP_NETWORK,
                severity = LogSeverity.INFO,
                tag = "MediaDownload",
                message = "Strategy ${index + 1}/${strategies.size} (${strat.name}): Fetching ${mediaType.name}",
                url = mediaUrl,
                requestHeaders = reqHeadersMap
            )

            val response = httpClient.newCall(request).execute()
            val contentType = response.header("Content-Type").orEmpty().lowercase(Locale.US)
            val isHtmlSoftBlock = contentType.contains("text/html")
            val respHeadersMap = response.headers.toMultimap().mapValues { it.value.joinToString(", ") }

            DiagnosticLogManager.log(
                category = LogCategory.HTTP_NETWORK,
                severity = if (response.isSuccessful || response.code == 206) LogSeverity.INFO else LogSeverity.WARN,
                tag = "MediaDownload",
                message = "Response ${response.code} (ContentType: $contentType, isHtmlSoftBlock: $isHtmlSoftBlock)",
                url = mediaUrl,
                responseCode = response.code,
                responseHeaders = respHeadersMap
            )

            if ((response.isSuccessful || response.code == 206) && !isHtmlSoftBlock) {
                return response
            }

            // If 416 Range Not Satisfiable when resuming, retry once without Range header
            if (response.code == 416 && existingBytes > 0L) {
                response.close()
                val noRangeReq = reqBuilder.removeHeader("Range").build()
                val retryNoRange = httpClient.newCall(noRangeReq).execute()
                val retryContentType = retryNoRange.header("Content-Type").orEmpty().lowercase(Locale.US)
                if (retryNoRange.isSuccessful && !retryContentType.contains("text/html")) {
                    return retryNoRange
                }
                retryNoRange.close()
            }

            // If not 403 / 401 / 429 / 503 / soft-HTML block, don't keep trying other referer profiles
            if (!isHtmlSoftBlock &&
                response.code != 403 &&
                response.code != 401 &&
                response.code != 429 &&
                response.code != 503 &&
                response.code != 410
            ) {
                return response
            }

            if (index < strategies.lastIndex) {
                response.close()
            } else {
                lastResponse = response
            }
        }

        return lastResponse ?: throw IllegalStateException("HTTP 403 Forbidden after all retry strategies")
    }

    /**
     * In-page WebView fetch fallback:
     * When OkHttp receives repeated 403s on a .jpg (e.g. strict Cloudflare browser verification),
     * loads the source album page in the offscreen WebView DOM and runs `fetch(mediaUrl)` inside
     * the authenticated page context, returning the decoded ByteArray.
     */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun fetchImageBytesViaWebView(
        mediaUrl: String,
        refererPageUrl: String?
    ): ByteArray? = webViewDomMutex.withLock {
        val ctx = appContext ?: return@withLock null
        purgeCorruptWebViewVariationsSeeds(ctx)

        DiagnosticLogManager.log(
            category = LogCategory.RECOVERY,
            severity = LogSeverity.INFO,
            tag = "WebViewBlobFetch",
            message = "Starting in-page WebView fetch() fallback for image",
            url = mediaUrl
        )

        withTimeoutOrNull(22_000L) {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    var completed = false
                    val mainHandler = Handler(Looper.getMainLooper())
                    val webView = WebView(ctx)

                    fun cleanupAndResume(bytes: ByteArray?) {
                        if (completed) return
                        completed = true
                        mainHandler.removeCallbacksAndMessages(null)
                        runCatching {
                            CookieManager.getInstance().flush()
                            webView.stopLoading()
                        }
                        mainHandler.post {
                            runCatching {
                                webView.removeAllViews()
                                webView.destroy()
                            }
                        }
                        if (cont.isActive) {
                            cont.resume(bytes)
                        }
                    }

                    cont.invokeOnCancellation {
                        mainHandler.post {
                            runCatching {
                                webView.stopLoading()
                                webView.removeAllViews()
                                webView.destroy()
                            }
                        }
                    }

                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, true)
                        }
                        syncWebViewSettings(webView)
                        webView.settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            loadsImagesAutomatically = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            cacheMode = WebSettings.LOAD_DEFAULT
                        }

                        val targetPageUrl = refererPageUrl?.takeIf { it.isNotBlank() } ?: mediaUrl

                        fun extractBlobBytes() {
                            if (completed) return
                            val escapedUrl = org.json.JSONObject.quote(mediaUrl)
                            val jsCode = """
                                (function() {
                                    return new Promise((resolve) => {
                                        fetch($escapedUrl, { credentials: 'include', mode: 'cors' })
                                            .catch(() => fetch($escapedUrl, { credentials: 'include', mode: 'no-cors' }))
                                            .then(r => r.blob())
                                            .then(blob => {
                                                const reader = new FileReader();
                                                reader.onloadend = () => resolve(reader.result || '');
                                                reader.onerror = () => resolve('');
                                                reader.readAsDataURL(blob);
                                            })
                                            .catch(() => resolve(''));
                                    });
                                })();
                            """.trimIndent()

                            webView.evaluateJavascript(
                                "(function() { return window.__pulseGrabBlob || ''; })();"
                            ) { _ ->
                                webView.evaluateJavascript(
                                    "(async function() { try { const r = await fetch($escapedUrl, { credentials: 'include' }); const b = await r.blob(); return new Promise(res => { const fr = new FileReader(); fr.onloadend = () => res(fr.result); fr.onerror = () => res(''); fr.readAsDataURL(b); }); } catch(e) { return ''; } })();"
                                ) { rawDataUrlJson ->
                                    val decodedDataUrl = decodeJsString(rawDataUrlJson)
                                    if (!decodedDataUrl.isNullOrBlank() && decodedDataUrl.contains("base64,")) {
                                        val base64Data = decodedDataUrl.substringAfter("base64,")
                                        val bytes = runCatching { Base64.decode(base64Data, Base64.DEFAULT) }.getOrNull()
                                        if (bytes != null && bytes.isNotEmpty()) {
                                            DiagnosticLogManager.log(
                                                category = LogCategory.RECOVERY,
                                                severity = LogSeverity.INFO,
                                                tag = "WebViewBlobFetch",
                                                message = "Successfully extracted ${bytes.size} bytes via WebView fetch()",
                                                url = mediaUrl
                                            )
                                            cleanupAndResume(bytes)
                                            return@evaluateJavascript
                                        }
                                    }
                                    cleanupAndResume(null)
                                }
                            }
                        }

                        webView.webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                mainHandler.postDelayed({ extractBlobBytes() }, 800L)
                            }

                            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                                cleanupAndResume(null)
                                return true
                            }
                        }

                        mainHandler.postDelayed({ extractBlobBytes() }, 10_000L)

                        val extraHeaders = mutableMapOf<String, String>()
                        if (!refererPageUrl.isNullOrBlank()) {
                            extraHeaders["Referer"] = refererPageUrl
                        }
                        webView.loadUrl(targetPageUrl, extraHeaders)
                    } catch (e: Exception) {
                        DiagnosticLogManager.error(
                            tag = "WebViewBlobFetch",
                            message = "Failed to launch WebView for image fetch: ${e.message}",
                            url = mediaUrl,
                            throwable = e
                        )
                        cleanupAndResume(null)
                    }
                }
            }
        }
    }
}

