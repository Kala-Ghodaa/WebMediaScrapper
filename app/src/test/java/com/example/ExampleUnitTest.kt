package com.example

import com.example.data.scanner.AlbumBatchScraperEngine
import com.example.data.scanner.DeepScanEngine
import com.example.data.scanner.WebMediaScannerEngine
import com.example.domain.model.DownloadFolderConfig
import com.example.domain.model.DownloadFolderMode
import com.example.domain.model.ExtensionFilterConfig
import com.example.domain.model.MediaTabFilter
import com.example.domain.model.MediaType
import com.example.domain.usecase.DomainPatternAnalyzer
import com.example.domain.usecase.ExpandUrlPatternUseCase
import com.example.domain.usecase.FilterAndDeduplicateMediaUseCase
import com.example.domain.usecase.UrlMediaUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun urlNormalization_and_extensionExtraction_worksCorrectly() {
        val normalized = UrlMediaUtils.normalizeInputUrl("commons.wikimedia.org/wiki/Main_Page")
        assertEquals("https://commons.wikimedia.org/wiki/Main_Page", normalized)

        val extJpg = UrlMediaUtils.extractExtension("https://cdn.example.com/photos/sunset.jpeg?width=1080")
        assertEquals("jpg", extJpg)

        val extMp4 = UrlMediaUtils.extractExtension("https://media.example.com/stream/clip.mp4#t=10")
        assertEquals("mp4", extMp4)

        val mediaTypeVid = UrlMediaUtils.inferMediaType("https://media.example.com/stream/clip.webm", "webm")
        assertEquals(MediaType.VIDEO, mediaTypeVid)
    }

    @Test
    fun domainPatternAnalyzer_discoversAndClustersCommonPaths() {
        val analyzer = DomainPatternAnalyzer()
        val baseUrl = "https://example.com"
        val candidateLinks = listOf(
            "https://example.com/gallery/page-1",
            "https://example.com/gallery/page-2",
            "https://example.com/gallery/page-3",
            "https://example.com/albums/2026/photo1",
            "https://example.com/albums/2026/photo2",
            "https://otherdomain.com/outside"
        )
        val patterns = analyzer.discoverPatterns(baseUrl, candidateLinks)
        assertTrue(patterns.isNotEmpty())
        assertTrue(patterns.any { it.pattern.contains("/gallery") })
        // Should ignore links on different domains
        assertFalse(patterns.any { it.pattern.contains("otherdomain") })
    }

    @Test
    fun expandUrlPatternUseCase_expandsNumericAndZeroPaddedRanges() {
        val expander = ExpandUrlPatternUseCase()
        assertTrue(expander.hasRangePattern("https://example.com/gallery/page-[1-4]"))

        val simpleExpanded = expander.expandPattern("https://example.com/gallery/page-[1-3]")
        assertEquals(
            listOf(
                "https://example.com/gallery/page-1",
                "https://example.com/gallery/page-2",
                "https://example.com/gallery/page-3"
            ),
            simpleExpanded
        )

        val paddedExpanded = expander.expandPattern("https://cdn.example.com/img_[01-03].png")
        assertEquals(
            listOf(
                "https://cdn.example.com/img_01.png",
                "https://cdn.example.com/img_02.png",
                "https://cdn.example.com/img_03.png"
            ),
            paddedExpanded
        )

        assertTrue(expander.matchesWildcardPattern("https://example.com/albums/2026/photo1", "*/albums/*"))
        assertFalse(expander.matchesWildcardPattern("https://example.com/contact", "*/albums/*"))
    }

    @Test
    fun filterAndDeduplicateUseCase_supportsMultiSelectExtensionChips() {
        val filterUseCase = FilterAndDeduplicateMediaUseCase()
        val html = WebMediaScannerEngine.buildShowcaseHtml(1)
        val extracted = DeepScanEngine.extractMediaFromHtml(
            html = html,
            baseUrl = "https://showcase.pulsegrab.local/gallery/page-1",
            crawlDepth = 0,
            extensionFilterConfig = ExtensionFilterConfig()
        )
        assertTrue(extracted.isNotEmpty())

        // Empty set = matches all
        val allItems = filterUseCase(
            items = extracted,
            tabFilter = MediaTabFilter.ALL,
            selectedExtensionChips = emptySet(),
            extensionConfig = ExtensionFilterConfig(),
            skipDuplicates = true,
            searchQuery = "",
            downloadedNormalizedUrls = emptySet()
        )
        assertEquals(extracted.size, allItems.size)

        // Multi-select set = {"png", "mp4"}
        val multiSelected = filterUseCase(
            items = extracted,
            tabFilter = MediaTabFilter.ALL,
            selectedExtensionChips = setOf("png", "mp4"),
            extensionConfig = ExtensionFilterConfig(),
            skipDuplicates = true,
            searchQuery = "",
            downloadedNormalizedUrls = emptySet()
        )
        assertTrue(multiSelected.isNotEmpty())
        assertTrue(multiSelected.all { it.extension == "png" || it.extension == "mp4" })
    }

    @Test
    fun downloadFolderConfig_sanitizesPageTitlesProperly() {
        val sanitized = DownloadFolderConfig.sanitizeFolderName("Wikimedia Commons: Featured Pictures / HD?")
        assertEquals("Wikimedia_Commons__Featured_Pictures___HD", sanitized)

        val cfg = DownloadFolderConfig(
            folderMode = DownloadFolderMode.AUTO_BY_PAGE_TITLE,
            currentPageTitle = "NASA Space Shots 2026"
        )
        assertEquals("NASA_Space_Shots_2026", cfg.activeSubfolderName)
    }

    @Test
    fun getAlbumMedia_scrapingLogic_extractsH1Title_skipsThumbs_and_findsDirectCdnLinks() {
        val sampleHtml = """
            <html>
              <head><title>Fallback Page Title</title></head>
              <body>
                <h1>  Summer   Editorial   Shoot 2026  </h1>
                <img data-src="/media/highres_01.jpg" src="/media/thumb/lowres_01.jpg" />
                <img src="/media/thumb/skip_me.jpg" />
                <img data-full="https://cdn.example.com/albums/photo_02.jpeg" />
                <video src="/videos/clip_main.mp4">
                  <source src="/videos/thumb/preview.mp4" />
                  <source src="/videos/clip_hd.mp4" />
                </video>
                <a href="/downloads/full_pack_03.jpg">Download JPG</a>
                <a href="/downloads/thumb/tiny.jpg">Tiny</a>
                <script>
                  const rawCdn = "https://cdn.example.com/uploads/2026/05/secret_master_04.mp4";
                  const thumbCdn = "https://cdn.example.com/uploads/thumb/ignore_05.jpg";
                </script>
              </body>
            </html>
        """.trimIndent()

        val result = AlbumBatchScraperEngine.parseAlbumMediaFromHtml(
            albumUrl = "https://example.com/albums/summer-2026",
            htmlText = sampleHtml,
            strictJpgMp4Only = true
        )

        assertEquals("Summer Editorial Shoot 2026", result.title)
        val urls = result.mediaItems.map { it.url }

        // Should include highres_01.jpg, photo_02.jpeg, clip_main.mp4, clip_hd.mp4, full_pack_03.jpg, secret_master_04.mp4
        assertTrue(urls.contains("https://example.com/media/highres_01.jpg"))
        assertTrue(urls.contains("https://cdn.example.com/albums/photo_02.jpeg"))
        assertTrue(urls.contains("https://example.com/videos/clip_main.mp4"))
        assertTrue(urls.contains("https://example.com/videos/clip_hd.mp4"))
        assertTrue(urls.contains("https://example.com/downloads/full_pack_03.jpg"))
        assertTrue(urls.contains("https://cdn.example.com/uploads/2026/05/secret_master_04.mp4"))

        // Should strictly exclude any /thumb/ links
        assertFalse(urls.any { it.contains("/thumb/") })

        // Every extracted item should carry the H1 album title so it routes to its own folder
        assertTrue(result.mediaItems.all { it.pageTitle == "Summer Editorial Shoot 2026" })
        assertTrue(result.mediaItems.all { it.pageUrl == "https://example.com/albums/summer-2026" })
    }

    @Test
    fun browserHttpBridge_buildsAnti403NavigationHeadersAndReferer() {
        val req = com.example.data.scanner.BrowserHttpBridge.buildHtmlNavigationRequest(
            url = "https://example.com/albums/summer-2026",
            refererUrl = "https://example.com/albums/",
            includeSecFetchHeaders = true
        )
        assertEquals("https://example.com/albums/", req.header("Referer"))
        assertEquals("document", req.header("Sec-Fetch-Dest"))
        assertEquals("navigate", req.header("Sec-Fetch-Mode"))
        assertEquals("same-origin", req.header("Sec-Fetch-Site"))
        assertEquals("1", req.header("Upgrade-Insecure-Requests"))
    }

    @Test
    fun isThumbnailOrHptUrl_identifiesHptThumbnailsAndAllowedMedia() {
        // HPT SEO thumbnails ending in <AlbumId>HPT<number>.jpg should be identified as thumbnails
        assertTrue(UrlMediaUtils.isThumbnailOrHptUrl("4qIF3mPBQUJ7oHPT0.jpg"))
        assertTrue(UrlMediaUtils.isThumbnailOrHptUrl("https://example.com/uploads/4qIF3mPBQUJ7o/4qIF3mPBQUJ7oHPT0.jpg"))
        assertTrue(UrlMediaUtils.isThumbnailOrHptUrl("https://example.com/uploads/4qIF3mPBQUJ7o/4qIF3mPBQUJ7oHPT12.webp?sig=abc"))
        assertTrue(UrlMediaUtils.isThumbnailOrHptUrl("album_HPT5.jpeg"))
        assertTrue(UrlMediaUtils.isThumbnailOrHptUrl("https://example.com/thumb/photo.jpg"))
        assertTrue(UrlMediaUtils.isThumbnailOrHptUrl("https://example.com/thumbnails/photo.jpg"))

        // Real media files should NOT be identified as thumbnails
        assertFalse(UrlMediaUtils.isThumbnailOrHptUrl("https://example.com/uploads/4qIF3mPBQUJ7o/file_01.jpg"))
        assertFalse(UrlMediaUtils.isThumbnailOrHptUrl("https://example.com/uploads/4qIF3mPBQUJ7o/4qIF3mPBQUJ7o.jpg"))
        assertFalse(UrlMediaUtils.isThumbnailOrHptUrl("https://example.com/uploads/4qIF3mPBQUJ7o/master_clip.mp4"))
        assertFalse(UrlMediaUtils.isThumbnailOrHptUrl("https://example.com/gallery/photo_1080p.png"))
    }

    @Test
    fun jsonLdExtraction_extractsRealMedia_and_ignoresThumbnailUrlAndHpt() {
        val sampleJsonLd = """
            {
              "@context": "https://schema.org",
              "@type": "ImageGallery",
              "name": "Cyberpunk City Shoot",
              "image": [
                {
                  "@type": "ImageObject",
                  "contentUrl": "https://example.com/uploads/4qIF3mPBQUJ7o/photo_master_1.jpg",
                  "thumbnailUrl": "https://example.com/uploads/4qIF3mPBQUJ7o/4qIF3mPBQUJ7oHPT0.jpg",
                  "name": "photo_master_1"
                },
                {
                  "@type": "ImageObject",
                  "url": "https://example.com/uploads/4qIF3mPBQUJ7o/photo_master_2.jpeg",
                  "thumbnailUrl": "https://example.com/uploads/4qIF3mPBQUJ7o/4qIF3mPBQUJ7oHPT1.jpg",
                  "name": "photo_master_2"
                }
              ],
              "video": {
                "@type": "VideoObject",
                "name": "Bonus Scene",
                "contentUrl": "https://example.com/uploads/4qIF3mPBQUJ7o/scene.mp4",
                "thumbnailUrl": "https://example.com/uploads/4qIF3mPBQUJ7o/4qIF3mPBQUJ7oHPT2.jpg"
              }
            }
        """.trimIndent()

        val extracted = AlbumBatchScraperEngine.extractMediaUrlsFromJsonLd(
            jsonText = sampleJsonLd,
            baseUrl = "https://example.com/4qIF3mPBQUJ7o",
            strictJpgMp4Only = true
        )

        assertEquals(3, extracted.size)
        assertTrue(extracted.contains("https://example.com/uploads/4qIF3mPBQUJ7o/photo_master_1.jpg"))
        assertTrue(extracted.contains("https://example.com/uploads/4qIF3mPBQUJ7o/photo_master_2.jpeg"))
        assertTrue(extracted.contains("https://example.com/uploads/4qIF3mPBQUJ7o/scene.mp4"))

        // Strictly verify that NO thumbnailUrl or HPT URLs are extracted
        assertFalse(extracted.any { it.contains("HPT") })
        assertFalse(extracted.any { it.contains("4qIF3mPBQUJ7oHPT0.jpg") })
    }

    @Test
    fun parseAlbumMediaFromHtml_handlesJsonLdAndStripsHptSeoThumbnails() {
        val albumHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>4qIF3mPBQUJ7o - Neon Lights Album</title>
                <script type="application/ld+json">
                {
                  "@context": "https://schema.org",
                  "@type": "ImageGallery",
                  "name": "Neon Lights Album",
                  "image": [
                    {
                      "@type": "ImageObject",
                      "contentUrl": "https://example.com/uploads/4qIF3mPBQUJ7o/neon_highres_01.jpg",
                      "thumbnailUrl": "https://example.com/uploads/4qIF3mPBQUJ7o/4qIF3mPBQUJ7oHPT0.jpg"
                    },
                    {
                      "@type": "ImageObject",
                      "contentUrl": "https://example.com/uploads/4qIF3mPBQUJ7o/neon_highres_02.jpg",
                      "thumbnailUrl": "https://example.com/uploads/4qIF3mPBQUJ7o/4qIF3mPBQUJ7oHPT1.jpg"
                    }
                  ]
                }
                </script>
            </head>
            <body>
                <h1>Neon Lights Album</h1>
                <div class="gallery">
                    <img src="https://example.com/uploads/4qIF3mPBQUJ7o/4qIF3mPBQUJ7oHPT0.jpg" alt="Preview 0" />
                    <img src="https://example.com/uploads/4qIF3mPBQUJ7o/4qIF3mPBQUJ7oHPT1.jpg" alt="Preview 1" />
                    <a href="https://example.com/uploads/4qIF3mPBQUJ7o/neon_highres_03.jpg">Original 3</a>
                </div>
            </body>
            </html>
        """.trimIndent()

        val parsed = AlbumBatchScraperEngine.parseAlbumMediaFromHtml(
            albumUrl = "https://example.com/4qIF3mPBQUJ7o",
            htmlText = albumHtml,
            strictJpgMp4Only = true
        )

        assertEquals("Neon Lights Album", parsed.title)
        val extractedUrls = parsed.mediaItems.map { it.url }

        // Must extract the real media files from JSON-LD contentUrl and <a> tag
        assertTrue(extractedUrls.contains("https://example.com/uploads/4qIF3mPBQUJ7o/neon_highres_01.jpg"))
        assertTrue(extractedUrls.contains("https://example.com/uploads/4qIF3mPBQUJ7o/neon_highres_02.jpg"))
        assertTrue(extractedUrls.contains("https://example.com/uploads/4qIF3mPBQUJ7o/neon_highres_03.jpg"))

        // Must NOT contain any 4qIF3mPBQUJ7oHPT<number>.jpg thumbnails
        assertEquals(3, extractedUrls.size)
        assertFalse(extractedUrls.any { it.contains("HPT") })
    }

    @Test
    fun deepScanAndWebScanner_extractsJsonLdContentUrl_and_dropsHptThumbnailsInNormalBrowsing() {
        val pageHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Normal Website & Pattern Scan Test</title>
                <script type="application/ld+json">
                {
                  "@context": "https://schema.org",
                  "@type": "MediaGallery",
                  "associatedMedia": [
                    {
                      "@type": "ImageObject",
                      "contentUrl": "https://example.com/uploads/4qIF3mPBQUJ7o/full_res_photo.jpg",
                      "thumbnailUrl": "https://example.com/uploads/4qIF3mPBQUJ7o/4qIF3mPBQUJ7oHPT0.jpg"
                    },
                    {
                      "@type": "VideoObject",
                      "embedUrl": "https://example.com/uploads/4qIF3mPBQUJ7o/full_res_video.mp4",
                      "thumbnailUrl": "https://example.com/uploads/4qIF3mPBQUJ7o/4qIF3mPBQUJ7oHPT1.webp"
                    }
                  ]
                }
                </script>
            </head>
            <body>
                <h1>Normal Website Gallery</h1>
                <img src="https://example.com/uploads/4qIF3mPBQUJ7o/4qIF3mPBQUJ7oHPT0.jpg" />
                <img src="https://example.com/uploads/thumb/preview_small.jpg" />
                <a href="https://example.com/uploads/4qIF3mPBQUJ7o/direct_extra.png">Extra PNG</a>
            </body>
            </html>
        """.trimIndent()

        val extracted = com.example.data.scanner.DeepScanEngine.extractMediaFromHtml(
            html = pageHtml,
            baseUrl = "https://example.com/4qIF3mPBQUJ7o",
            crawlDepth = 1,
            extensionFilterConfig = com.example.domain.model.ExtensionFilterConfig()
        )
        val urls = extracted.map { it.url }

        assertEquals(3, urls.size)
        assertTrue(urls.contains("https://example.com/uploads/4qIF3mPBQUJ7o/full_res_photo.jpg"))
        assertTrue(urls.contains("https://example.com/uploads/4qIF3mPBQUJ7o/full_res_video.mp4"))
        assertTrue(urls.contains("https://example.com/uploads/4qIF3mPBQUJ7o/direct_extra.png"))
        assertFalse(urls.any { it.contains("HPT") || it.contains("/thumb/") })

        // Also verify WebMediaScannerEngine.buildDetectedItem rejects HPT URLs directly
        val blockedItem = com.example.data.scanner.WebMediaScannerEngine.buildDetectedItem(
            rawUrl = "https://example.com/uploads/4qIF3mPBQUJ7o/4qIF3mPBQUJ7oHPT0.jpg",
            baseUrl = "https://example.com/4qIF3mPBQUJ7o",
            tagHint = "img"
        )
        assertEquals(null, blockedItem)
    }
}
