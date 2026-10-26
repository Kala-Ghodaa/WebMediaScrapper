package com.example.data.scanner

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.domain.model.DeepScanConfig
import com.example.domain.model.DeepScanMode
import com.example.domain.model.DeepScanProgress
import com.example.domain.model.DetectedMediaItem
import com.example.domain.model.DiscoveredPathPattern
import com.example.domain.model.ExtensionFilterConfig
import com.example.domain.usecase.DomainPatternAnalyzer
import com.example.domain.usecase.ExpandUrlPatternUseCase
import com.example.domain.usecase.UrlMediaUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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

object DomainCrawlerController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val patternAnalyzer = DomainPatternAnalyzer()
    private val expander = ExpandUrlPatternUseCase()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val _discoveredPatterns = MutableStateFlow<List<DiscoveredPathPattern>>(emptyList())
    val discoveredPatterns: StateFlow<List<DiscoveredPathPattern>> = _discoveredPatterns.asStateFlow()

    private val _isAnalyzingPatterns = MutableStateFlow(false)
    val isAnalyzingPatterns: StateFlow<Boolean> = _isAnalyzingPatterns.asStateFlow()

    private val _crawlerProgress = MutableStateFlow(DeepScanProgress())
    val crawlerProgress: StateFlow<DeepScanProgress> = _crawlerProgress.asStateFlow()

    var onMediaDiscoveredCallback: ((List<DetectedMediaItem>) -> Unit)? = null

    private var crawlerJob: Job? = null

    fun analyzeBaseUrlPatterns(baseUrl: String) {
        val normalized = UrlMediaUtils.normalizeInputUrl(baseUrl)
        if (normalized.isBlank()) return

        scope.launch {
            _isAnalyzingPatterns.value = true
            try {
                val candidateLinks = fetchLinksForUrl(normalized)
                val patterns = patternAnalyzer.discoverPatterns(normalized, candidateLinks)
                _discoveredPatterns.value = patterns
            } catch (_: Exception) {
                _discoveredPatterns.value = emptyList()
            } finally {
                _isAnalyzingPatterns.value = false
            }
        }
    }

    fun togglePatternSelection(patternId: String) {
        _discoveredPatterns.update { list ->
            list.map {
                if (it.id == patternId) it.copy(isSelected = !it.isSelected) else it
            }
        }
    }

    fun selectAllPatterns(select: Boolean) {
        _discoveredPatterns.update { list ->
            list.map { it.copy(isSelected = select) }
        }
    }

    fun startCrawl(
        context: Context,
        config: DeepScanConfig,
        selectedPatternStrings: List<String>,
        extensionConfig: ExtensionFilterConfig
    ) {
        crawlerJob?.cancel()
        val intent = Intent(context, DomainCrawlerService::class.java).apply {
            action = DomainCrawlerService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }

        crawlerJob = scope.launch {
            try {
                _crawlerProgress.value = DeepScanProgress(
                    isScanning = true,
                    mode = config.mode,
                    pagesScanned = 0,
                    pagesTotalOrQueued = 1,
                    currentScanningUrl = config.seedUrl
                )

                runRecursivePatternCrawl(
                    config = config,
                    selectedPatterns = selectedPatternStrings,
                    extensionConfig = extensionConfig
                )

                _crawlerProgress.update {
                    it.copy(
                        isScanning = false,
                        currentScanningUrl = "Crawling Complete"
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                _crawlerProgress.update {
                    it.copy(
                        isScanning = false,
                        errorMessage = e.localizedMessage ?: "Crawl stopped"
                    )
                }
            } finally {
                DomainCrawlerService.stop(context)
            }
        }
    }

    fun stopCrawl(context: Context) {
        crawlerJob?.cancel()
        crawlerJob = null
        _crawlerProgress.update {
            it.copy(
                isScanning = false,
                currentScanningUrl = "Stopped"
            )
        }
        DomainCrawlerService.stop(context)
    }

    private suspend fun runRecursivePatternCrawl(
        config: DeepScanConfig,
        selectedPatterns: List<String>,
        extensionConfig: ExtensionFilterConfig
    ) {
        val visited = HashSet<String>()
        val queue = ArrayDeque<Pair<String, Int>>()
        val seed = UrlMediaUtils.normalizeInputUrl(config.seedUrl)

        visited.add(seed)
        queue.add(seed to 0)

        val seedHost = runCatching { URI(seed).host?.lowercase(Locale.US)?.removePrefix("www.") }.getOrNull()
        var scanned = 0

        while (queue.isNotEmpty() && scanned < config.maxPages) {
            if (!kotlinx.coroutines.currentCoroutineContext().isActive) break
            val (currentUrl, depth) = queue.poll() ?: break
            scanned++

            _crawlerProgress.update {
                it.copy(
                    pagesScanned = scanned,
                    pagesTotalOrQueued = minOf(config.maxPages, scanned + queue.size),
                    currentScanningUrl = currentUrl,
                    currentDepth = depth,
                    recentScannedUrls = (listOf(currentUrl) + it.recentScannedUrls).take(6)
                )
            }

            val html = fetchHtmlContent(currentUrl)
            if (html != null) {
                val media = DeepScanEngine.extractMediaFromHtml(html, currentUrl, depth, extensionConfig)
                if (media.isNotEmpty()) {
                    onMediaDiscoveredCallback?.invoke(media)
                }

                var matchedCount = 0
                if (depth < config.maxDepth && visited.size < config.maxPages * 2) {
                    val links = DeepScanEngine.extractLinksFromHtml(html, currentUrl)
                    for (link in links) {
                        if (visited.size >= config.maxPages * 2) break
                        if (config.sameDomainOnly && seedHost != null) {
                            val linkHost = runCatching { URI(link).host?.lowercase(Locale.US)?.removePrefix("www.") }.getOrNull()
                            if (linkHost != seedHost) continue
                        }

                        // Check if link matches any selected path pattern
                        val matchesPattern = if (selectedPatterns.isEmpty()) {
                            true
                        } else {
                            selectedPatterns.any { pattern ->
                                val cleanedPattern = pattern.replace("[id]", "*").replace("page-[N]", "page-*")
                                expander.matchesWildcardPattern(link, cleanedPattern)
                            }
                        }

                        if (matchesPattern && visited.add(link)) {
                            queue.add(link to (depth + 1))
                            matchedCount++
                        }
                    }
                }

                _crawlerProgress.update {
                    it.copy(
                        pagesTotalOrQueued = minOf(config.maxPages, scanned + queue.size),
                        matchedUrlsCount = it.matchedUrlsCount + matchedCount,
                        mediaFoundCount = it.mediaFoundCount + media.size
                    )
                }
            }
        }
    }

    private suspend fun fetchLinksForUrl(url: String): List<String> {
        val html = fetchHtmlContent(url) ?: return emptyList()
        return DeepScanEngine.extractLinksFromHtml(html, url)
    }

    private suspend fun fetchHtmlContent(url: String): String? {
        if (url.startsWith("pulsegrab://") || url.contains("showcase.pulsegrab.local")) {
            val pageNum = Regex("""page-(\d+)""").find(url)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
            return WebMediaScannerEngine.buildShowcaseHtml(pageNum)
        }

        return BrowserHttpBridge.fetchHtmlResilient(
            url = url,
            refererUrl = BrowserHttpBridge.getOriginUrl(url),
            allowWebViewFallback = true
        ).html
    }
}

class DomainCrawlerService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildForegroundNotification("Discovering domain media…"))
        return START_NOT_STICKY
    }

    private fun buildForegroundNotification(status: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("PulseGrab Domain Crawler")
            .setContentText(status)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Domain Crawler Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live status while crawling websites and extracting media"
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_START = "com.example.action.START_CRAWLER"
        const val ACTION_STOP = "com.example.action.STOP_CRAWLER"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "pulsegrab_crawler_channel"

        fun stop(context: Context) {
            val intent = Intent(context, DomainCrawlerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
