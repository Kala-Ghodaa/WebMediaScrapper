package com.example.data.scanner

import android.webkit.JavascriptInterface
import com.example.domain.model.DetectedMediaItem
import com.example.domain.model.MediaType
import com.example.domain.usecase.UrlMediaUtils
import org.json.JSONArray
import java.util.Locale

class WebMediaScannerEngine(
    private val onMediaBatchDiscovered: (List<DetectedMediaItem>) -> Unit
) {

    @JavascriptInterface
    fun onDomMediaExtracted(jsonPayload: String, pageUrl: String) {
        try {
            val array = JSONArray(jsonPayload)
            val discovered = ArrayList<DetectedMediaItem>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val rawUrl = obj.optString("url", "")
                val tag = obj.optString("tag", "img")
                val title = obj.optString("title", "")
                val width = obj.optInt("width", 0)
                val height = obj.optInt("height", 0)
                val mimeHint = obj.optString("mime", "")

                val item = buildDetectedItem(
                    rawUrl = rawUrl,
                    baseUrl = pageUrl,
                    tagHint = tag,
                    titleHint = title,
                    width = width,
                    height = height,
                    mimeHint = mimeHint,
                    crawlDepth = 0
                )
                if (item != null) {
                    discovered.add(item)
                }
            }
            if (discovered.isNotEmpty()) {
                onMediaBatchDiscovered(discovered)
            }
        } catch (_: Exception) {
        }
    }

    fun inspectNetworkRequest(requestUrl: String, pageUrl: String) {
        val ext = UrlMediaUtils.extractExtension(requestUrl)
        if (ext.isEmpty()) return
        val mediaType = UrlMediaUtils.inferMediaType(requestUrl, ext) ?: return
        val canonical = UrlMediaUtils.canonicalizeMediaUrl(requestUrl, pageUrl) ?: return
        val finalExt = ext.ifEmpty { if (mediaType == MediaType.VIDEO) "mp4" else "jpg" }
        val mime = UrlMediaUtils.inferMimeType(finalExt, mediaType)
        val title = UrlMediaUtils.deriveTitleFromUrl(canonical, null, finalExt)

        val item = DetectedMediaItem(
            id = canonical.hashCode().toUInt().toString(16),
            url = canonical,
            normalizedUrl = canonical,
            pageUrl = pageUrl,
            crawlDepth = 0,
            title = title,
            mediaType = mediaType,
            extension = finalExt,
            mimeType = mime
        )
        onMediaBatchDiscovered(listOf(item))
    }

    companion object {
        fun buildDetectedItem(
            rawUrl: String,
            baseUrl: String,
            tagHint: String,
            titleHint: String = "",
            width: Int = 0,
            height: Int = 0,
            mimeHint: String = "",
            crawlDepth: Int = 0
        ): DetectedMediaItem? {
            if (UrlMediaUtils.isThumbnailOrHptUrl(rawUrl)) return null
            val canonical = UrlMediaUtils.canonicalizeMediaUrl(rawUrl, baseUrl) ?: return null
            if (UrlMediaUtils.isThumbnailOrHptUrl(canonical)) return null
            val ext = UrlMediaUtils.extractExtension(canonical, mimeHint)
            val mediaType = UrlMediaUtils.inferMediaType(canonical, ext, tagHint) ?: return null
            val finalExt = ext.ifEmpty {
                if (mediaType == MediaType.VIDEO) "mp4" else "jpg"
            }.lowercase(Locale.US)
            val mime = UrlMediaUtils.inferMimeType(finalExt, mediaType)
            val title = UrlMediaUtils.deriveTitleFromUrl(canonical, titleHint, finalExt)

            return DetectedMediaItem(
                id = canonical.hashCode().toUInt().toString(16),
                url = canonical,
                normalizedUrl = canonical,
                pageUrl = baseUrl,
                crawlDepth = crawlDepth,
                title = title,
                mediaType = mediaType,
                extension = finalExt,
                mimeType = mime,
                width = width.coerceAtLeast(0),
                height = height.coerceAtLeast(0)
            )
        }

        val DOM_SCANNER_JS = """
            (function() {
                if (window.__pulseGrabScannerInstalled) {
                    window.__pulseGrabRunScan();
                    return;
                }
                window.__pulseGrabScannerInstalled = true;
                var sentUrls = {};

                function isBlockedThumb(u) {
                    if (!u) return true;
                    var s = u.split('?')[0].split('#')[0];
                    if (s.toLowerCase().indexOf('/thumb/') !== -1) return true;
                    var fn = s.substring(s.lastIndexOf('/') + 1);
                    if (/HPT\d+\.(jpe?g|png|webp)$/i.test(fn)) return true;
                    return false;
                }

                function addCandidate(list, url, tag, title, w, h, mime) {
                    if (!url || typeof url !== 'string') return;
                    var clean = url.trim();
                    if (!clean || clean.indexOf('data:') === 0 || clean.indexOf('blob:') === 0) return;
                    if (isBlockedThumb(clean)) return;
                    var key = clean + '_' + (w || 0) + 'x' + (h || 0);
                    if (sentUrls[key]) return;
                    sentUrls[key] = true;
                    list.push({
                        url: clean,
                        tag: tag || 'img',
                        title: (title || '').substring(0, 80),
                        width: w || 0,
                        height: h || 0,
                        mime: mime || ''
                    });
                }

                function parseSrcset(srcset) {
                    if (!srcset) return [];
                    return srcset.split(',').map(function(part) {
                        return part.trim().split(/\s+/)[0];
                    }).filter(Boolean);
                }

                window.__pulseGrabRunScan = function() {
                    try {
                        var results = [];
                        var h1El = document.querySelector('h1');
                        var pageTitle = (h1El && h1El.textContent ? h1El.textContent.replace(/\s+/g, ' ').trim() : '') || document.title || '';
                        var mediaRegex = /\.(jpe?g|png|webp|gif|svg|avif|bmp|mp4|webm|mkv|mov|m4v|mp3)(\?.*)?$/i;

                        // 0. <script type="application/ld+json"> elements: extract ONLY url/contentUrl/embedUrl and ignore thumbnailUrl
                        var jsonScripts = document.querySelectorAll('script[type="application/ld+json"]');
                        for (var js = 0; js < jsonScripts.length; js++) {
                            try {
                                var rawJson = jsonScripts[js].textContent;
                                if (!rawJson) continue;
                                var parsedObj = JSON.parse(rawJson);
                                function walkJsonLd(key, node) {
                                    if (!node) return;
                                    if (key && key.toLowerCase() === 'thumbnailurl') return;
                                    if (typeof node === 'object') {
                                        if (Array.isArray(node)) {
                                            for (var ai = 0; ai < node.length; ai++) walkJsonLd(key, node[ai]);
                                        } else {
                                            for (var k in node) {
                                                if (!node.hasOwnProperty(k)) continue;
                                                if (k.toLowerCase() === 'thumbnailurl') continue;
                                                var lk = k.toLowerCase();
                                                if (lk === 'url' || lk === 'contenturl' || lk === 'embedurl') {
                                                    var v = node[k];
                                                    if (typeof v === 'string') {
                                                        var isVVid = /\.(mp4|webm)$/i.test(v.split('?')[0]);
                                                        addCandidate(results, v, isVVid ? 'video' : 'img', pageTitle, 0, 0, '');
                                                    } else {
                                                        walkJsonLd(k, v);
                                                    }
                                                } else if (typeof node[k] === 'object') {
                                                    walkJsonLd(k, node[k]);
                                                }
                                            }
                                        }
                                    }
                                }
                                walkJsonLd(null, parsedObj);
                            } catch (e) {}
                        }

                        // 1. Standard <img> elements (prioritizing parent <a> href, full-res original attributes, then lazyload, then src)
                        var imgs = document.querySelectorAll('img');
                        for (var i = 0; i < imgs.length; i++) {
                            var img = imgs[i];
                            var w = img.naturalWidth || img.width || 0;
                            var h = img.naturalHeight || img.height || 0;
                            var alt = img.alt || img.title || pageTitle;

                            // If enclosed in a direct media <a href="..."> link, prioritize master link
                            var parentA = img.closest ? img.closest('a[href]') : null;
                            if (parentA && parentA.href && mediaRegex.test(parentA.href)) {
                                if (!isBlockedThumb(parentA.href)) {
                                    addCandidate(results, parentA.href, 'img', alt, w, h, '');
                                }
                            }

                            var primarySrc = img.getAttribute('data-orig-file') ||
                                img.getAttribute('data-large-file') ||
                                img.getAttribute('data-full-url') ||
                                img.getAttribute('data-full') ||
                                img.getAttribute('data-original') ||
                                img.getAttribute('data-highres') ||
                                img.getAttribute('data-zoom-src') ||
                                img.getAttribute('data-zoom-image') ||
                                img.getAttribute('data-raw') ||
                                img.getAttribute('data-src') ||
                                img.getAttribute('src') ||
                                img.currentSrc;

                            if (primarySrc && !isBlockedThumb(primarySrc)) {
                                addCandidate(results, primarySrc, 'img', alt, w, h, '');
                            }
                            var sets = parseSrcset(img.srcset || img.getAttribute('data-srcset'));
                            for (var s = 0; s < sets.length; s++) {
                                if (!isBlockedThumb(sets[s])) {
                                    addCandidate(results, sets[s], 'img', alt, w, h, '');
                                }
                            }
                        }

                        // 2. <picture> and <source> elements
                        var sources = document.querySelectorAll('picture source, audio source');
                        for (var j = 0; j < sources.length; j++) {
                            var srcEl = sources[j];
                            var typeAttr = srcEl.getAttribute('type') || '';
                            var sList = parseSrcset(srcEl.srcset || srcEl.src);
                            for (var k = 0; k < sList.length; k++) {
                                if (!isBlockedThumb(sList[k])) {
                                    addCandidate(results, sList[k], 'picture', pageTitle, 0, 0, typeAttr);
                                }
                            }
                        }

                        // 3. <video> and <video><source> elements
                        var videos = document.querySelectorAll('video');
                        for (var v = 0; v < videos.length; v++) {
                            var vid = videos[v];
                            var vw = vid.videoWidth || vid.clientWidth || 0;
                            var vh = vid.videoHeight || vid.clientHeight || 0;
                            var vTitle = vid.getAttribute('title') || vid.getAttribute('aria-label') || pageTitle;
                            if (vid.currentSrc && !isBlockedThumb(vid.currentSrc)) addCandidate(results, vid.currentSrc, 'video', vTitle, vw, vh, 'video/mp4');
                            if (vid.src && !isBlockedThumb(vid.src)) addCandidate(results, vid.src, 'video', vTitle, vw, vh, 'video/mp4');
                            if (vid.poster && !isBlockedThumb(vid.poster)) addCandidate(results, vid.poster, 'img', vTitle + '_poster', vw, vh, 'image/jpeg');
                            var vSources = vid.querySelectorAll('source');
                            for (var vs = 0; vs < vSources.length; vs++) {
                                var vsEl = vSources[vs];
                                if (vsEl.src && !isBlockedThumb(vsEl.src)) {
                                    addCandidate(results, vsEl.src, 'video', vTitle, vw, vh, vsEl.type || 'video/mp4');
                                }
                            }
                        }

                        // 4. OpenGraph & Twitter Card meta media
                        var metas = document.querySelectorAll('meta[property^="og:image"], meta[property^="og:video"], meta[name^="twitter:image"]');
                        for (var m = 0; m < metas.length; m++) {
                            var content = metas[m].getAttribute('content');
                            var prop = metas[m].getAttribute('property') || '';
                            var isVid = prop.indexOf('video') !== -1;
                            if (content && !isBlockedThumb(content)) addCandidate(results, content, isVid ? 'video' : 'img', pageTitle, 0, 0, '');
                        }

                        // 5. Direct media links <a href="...(.jpg|.jpeg|.png|.webp|.mp4|.webm)">
                        var links = document.querySelectorAll('a[href]');
                        for (var a = 0; a < links.length; a++) {
                            var href = links[a].href;
                            if (href && !isBlockedThumb(href) && mediaRegex.test(href)) {
                                var linkText = (links[a].textContent || '').trim() || pageTitle;
                                addCandidate(results, href, 'link', linkText, 0, 0, '');
                            }
                        }

                        // 6. Fallback regex for direct CDN /uploads/ links in raw HTML (excluding JSON-LD blocks)
                        var rawHtml = document.documentElement ? document.documentElement.innerHTML : '';
                        var cleanHtml = rawHtml.replace(/<script\b[^>]*\btype\s*=\s*["']application\/ld\+json["'][^>]*>[\s\S]*?<\/script>/gi, ' ');
                        var cdnRegex = /(https?:\/\/[^\s"']+\/(?:uploads\/[^"'\s]+\.(?:jpg|jpeg|png|mp4|webm)))/gi;
                        var cdnMatch;
                        while ((cdnMatch = cdnRegex.exec(cleanHtml)) !== null) {
                            var cdnUrl = cdnMatch[1];
                            if (cdnUrl && !isBlockedThumb(cdnUrl)) {
                                var isCdnVid = /\.(mp4|webm)$/i.test(cdnUrl);
                                addCandidate(results, cdnUrl, isCdnVid ? 'video' : 'img', pageTitle, 0, 0, '');
                            }
                        }

                        // 7. Inline CSS background-image
                        var bgNodes = document.querySelectorAll('[style*="background-image"]');
                        for (var b = 0; b < bgNodes.length; b++) {
                            var style = bgNodes[b].getAttribute('style') || '';
                            var match = style.match(/url\(['"]?([^'")]+)['"]?\)/i);
                            if (match && match[1] && !isBlockedThumb(match[1])) {
                                addCandidate(results, match[1], 'bg', pageTitle, 0, 0, '');
                            }
                        }

                        if (results.length > 0 && window.PulseGrabBridge) {
                            window.PulseGrabBridge.onDomMediaExtracted(JSON.stringify(results), window.location.href);
                        }
                    } catch (e) {}
                };

                window.__pulseGrabRunScan();
                var debounceTimer = null;
                function scheduleScan() {
                    if (debounceTimer) clearTimeout(debounceTimer);
                    debounceTimer = setTimeout(window.__pulseGrabRunScan, 450);
                }
                var observer = new MutationObserver(scheduleScan);
                if (document.body) {
                    observer.observe(document.body, { childList: true, subtree: true, attributes: true });
                }
                window.addEventListener('scroll', scheduleScan, { passive: true });
                window.addEventListener('load', window.__pulseGrabRunScan);
            })();
        """.trimIndent()

        fun buildShowcaseHtml(pageNumber: Int = 1): String {
            return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>PulseGrab Showcase Gallery - Page $pageNumber</title>
                    <style>
                        body { background:#0B0F19; color:#F8FAFC; font-family:sans-serif; margin:0; padding:16px; }
                        h1 { color:#00E5FF; font-size:20px; margin-bottom:4px; }
                        p { color:#94A3B8; font-size:13px; margin-top:0; }
                        .nav { display:flex; gap:8px; margin:12px 0 20px; flex-wrap:wrap; }
                        .nav a { background:#1C2742; color:#00E5FF; padding:8px 12px; border-radius:8px; text-decoration:none; font-size:12px; border:1px solid #2E3C5E; }
                        .grid { display:grid; grid-template-columns:repeat(auto-fill, minmax(150px, 1fr)); gap:12px; }
                        .card { background:#131B2E; border-radius:12px; padding:10px; border:1px solid #233052; }
                        .card img, .card video { width:100%; height:110px; object-fit:cover; border-radius:8px; background:#0B0F19; }
                        .badge { display:inline-block; margin-top:6px; padding:2px 6px; font-size:10px; border-radius:4px; background:#2E1A68; color:#A78BFA; }
                        .title { font-size:12px; margin-top:4px; color:#E2E8F0; }
                    </style>
                </head>
                <body>
                    <h1>Open Media Showcase · Page $pageNumber</h1>
                    <p>Multi-format test hub exposing JPG, PNG, WEBP, SVG & MP4/WEBM streams plus sub-album folder links.</p>
                    <div class="nav">
                        <a href="https://showcase.pulsegrab.local/album/neon-cyber-set">Album: Neon Cyber Set</a>
                        <a href="https://showcase.pulsegrab.local/album/alpine-expedition">Album: Alpine Expedition</a>
                        <a href="https://showcase.pulsegrab.local/album/orbital-cinema">Album: Orbital Cinema</a>
                        <a href="https://showcase.pulsegrab.local/gallery/page-1">Page 1 (JPG/PNG)</a>
                        <a href="https://showcase.pulsegrab.local/gallery/page-2">Page 2 (WEBP/SVG)</a>
                        <a href="https://showcase.pulsegrab.local/gallery/page-3">Page 3 (Videos MP4/WEBM)</a>
                    </div>
                    <div class="grid">
                        <div class="card">
                            <img src="https://picsum.photos/id/${pageNumber * 10 + 1}/1280/720.jpg" alt="Alpine Peak Page $pageNumber" width="1280" height="720" />
                            <span class="badge">JPG · 1280×720</span>
                            <div class="title">Alpine Horizon #$pageNumber</div>
                        </div>
                        <div class="card">
                            <img src="https://picsum.photos/id/${pageNumber * 10 + 2}/1920/1080.webp" alt="Cyber Neon Architecture $pageNumber" width="1920" height="1080" />
                            <span class="badge">WEBP · 1920×1080</span>
                            <div class="title">Cyber Architecture #$pageNumber</div>
                        </div>
                        <div class="card">
                            <img src="https://upload.wikimedia.org/wikipedia/commons/thumb/4/47/PNG_transparency_demonstration_1.png/640px-PNG_transparency_demonstration_1.png" alt="Transparent Dice Render $pageNumber" width="640" height="480" />
                            <span class="badge">PNG · 640×480</span>
                            <div class="title">Crystal Dice Alpha #$pageNumber</div>
                        </div>
                        <div class="card">
                            <img src="https://upload.wikimedia.org/wikipedia/commons/a/a7/React-icon.svg" alt="Vector Atom Geometry $pageNumber" width="512" height="512" />
                            <span class="badge">SVG · 512×512</span>
                            <div class="title">Orbital Vector #$pageNumber</div>
                        </div>
                        <div class="card">
                            <video controls poster="https://picsum.photos/id/${pageNumber * 10 + 5}/800/450.jpg" width="1280" height="720" title="Big Buck Bunny Clip $pageNumber">
                                <source src="https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4" type="video/mp4" />
                            </video>
                            <span class="badge">MP4 · 1280×720</span>
                            <div class="title">For Bigger Blazes HD #$pageNumber</div>
                        </div>
                        <div class="card">
                            <video controls poster="https://picsum.photos/id/${pageNumber * 10 + 6}/800/450.jpg" width="1280" height="720" title="Joyrides Sample Stream $pageNumber">
                                <source src="https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4" type="video/mp4" />
                            </video>
                            <span class="badge">MP4 · 1280×720</span>
                            <div class="title">Joyrides Open Stream #$pageNumber</div>
                        </div>
                    </div>
                </body>
                </html>
            """.trimIndent()
        }
    }
}
