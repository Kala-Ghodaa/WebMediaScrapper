package com.example

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.DetectedMediaItem
import com.example.domain.model.DownloadFolderConfig
import com.example.domain.model.DownloadStatus
import com.example.domain.model.DownloadTask
import com.example.domain.model.MediaTabFilter
import com.example.domain.model.MediaType
import com.example.ui.MainDownloaderViewModel
import com.example.ui.MainUiState
import com.example.ui.screens.DetectedMediaScreen
import com.example.ui.screens.DownloadsScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `read string from context and verify viewmodel initialization`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("PulseGrab", appName)

        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MainDownloaderViewModel(application)
        viewModel.openDetectedWithFilter(MediaTabFilter.ALL, null)
        assertTrue(viewModel.uiState.value.extensionConfig.allEnabledExtensions.contains("jpg"))
        assertTrue(viewModel.uiState.value.extensionConfig.allEnabledExtensions.contains("mp4"))
    }

    @Test
    fun `downloads screen renders long filenames and fires all batch and item callbacks without regression`() {
        val longFolderName = "Summer_2026_Ultra_HD_Photography_Showcase_Collection"
        val longFileName = "ultra_high_resolution_panoramic_mountain_landscape_showcase_2026_final_master_4k.jpg"
        val sampleTasks = listOf(
            DownloadTask(
                id = "task_active",
                url = "https://cdn.example.com/uploads/$longFileName",
                normalizedUrl = "https://cdn.example.com/uploads/$longFileName",
                fileName = longFileName,
                folderName = longFolderName,
                extension = "jpg",
                mediaType = MediaType.IMAGE,
                mimeType = "image/jpeg",
                width = 3840,
                height = 2160,
                status = DownloadStatus.DOWNLOADING,
                downloadedBytes = 4_200_000L,
                totalBytes = 10_000_000L,
                speedBytesPerSec = 1_500_000L,
                localUri = null,
                tempFilePath = null,
                errorMessage = null,
                createdAt = 1000L,
                completedAt = null
            ),
            DownloadTask(
                id = "task_paused",
                url = "https://cdn.example.com/uploads/clip_paused.mp4",
                normalizedUrl = "https://cdn.example.com/uploads/clip_paused.mp4",
                fileName = "clip_paused.mp4",
                folderName = longFolderName,
                extension = "mp4",
                mediaType = MediaType.VIDEO,
                mimeType = "video/mp4",
                width = 1920,
                height = 1080,
                status = DownloadStatus.PAUSED,
                downloadedBytes = 2_000_000L,
                totalBytes = 8_000_000L,
                speedBytesPerSec = 0L,
                localUri = null,
                tempFilePath = null,
                errorMessage = null,
                createdAt = 1001L,
                completedAt = null
            ),
            DownloadTask(
                id = "task_failed",
                url = "https://cdn.example.com/uploads/clip_failed.mp4",
                normalizedUrl = "https://cdn.example.com/uploads/clip_failed.mp4",
                fileName = "clip_failed.mp4",
                folderName = longFolderName,
                extension = "mp4",
                mediaType = MediaType.VIDEO,
                mimeType = "video/mp4",
                width = 1920,
                height = 1080,
                status = DownloadStatus.FAILED,
                downloadedBytes = 0L,
                totalBytes = 0L,
                speedBytesPerSec = 0L,
                localUri = null,
                tempFilePath = null,
                errorMessage = "HTTP 403 Forbidden (Auto-Recovered 3/3)",
                createdAt = 1002L,
                completedAt = null
            )
        )

        var pausedId: String? = null
        var resumedId: String? = null
        var retriedId: String? = null
        var deletedId: String? = null
        var smartRedownloadAllCalled = false
        var smartRedownloadAlbumArg: String? = null
        var resumeAllCalled = false
        var pauseAllCalled = false

        val state = MainUiState(
            downloadTasks = sampleTasks,
            filteredDownloadTasks = sampleTasks,
            totalDownloadSpeed = 1_500_000L,
            folderConfig = DownloadFolderConfig(customSubfolderName = longFolderName)
        )

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                DownloadsScreen(
                    uiState = state,
                    onSelectStatusFilter = {},
                    onPauseTask = { pausedId = it },
                    onResumeTask = { resumedId = it },
                    onRetryTask = { retriedId = it },
                    onDeleteTask = { deletedId = it },
                    onOpenCompletedFile = {},
                    onShareCompletedFile = {},
                    onClearCompleted = {},
                    onPauseAllActive = { pauseAllCalled = true },
                    onResumeAllPaused = { resumeAllCalled = true },
                    onSmartRedownloadFailed = { smartRedownloadAllCalled = true },
                    onSmartRedownloadAlbumFolder = { smartRedownloadAlbumArg = it },
                    onExploreDetectedMedia = {}
                )
            }
        }

        // Verify the full human-readable album title is present without arbitrary truncation
        composeTestRule.onNodeWithTag("album_folder_banner_$longFolderName").assertIsDisplayed()
        composeTestRule.onNodeWithText(longFolderName.replace('_', ' ')).assertIsDisplayed()

        // Verify item-level callbacks fire with exact task IDs
        composeTestRule.onNodeWithTag("downloads_list")
            .performScrollToNode(hasTestTag("download_task_card_task_active"))
        composeTestRule.onNodeWithTag("pause_download_task_active").performClick()
        assertEquals("task_active", pausedId)

        // Verify tapping the download card expands full filename & source URL details
        composeTestRule.onNodeWithTag("download_task_card_task_active").performClick()
        composeTestRule.onNodeWithText("Source URL: https://cdn.example.com/uploads/$longFileName").assertIsDisplayed()

        composeTestRule.onNodeWithTag("downloads_list")
            .performScrollToNode(hasTestTag("download_task_card_task_paused"))
        composeTestRule.onNodeWithTag("resume_download_task_paused").performClick()
        assertEquals("task_paused", resumedId)

        composeTestRule.onNodeWithTag("downloads_list")
            .performScrollToNode(hasTestTag("download_task_card_task_failed"))
        composeTestRule.onNodeWithTag("retry_download_task_failed").performClick()
        assertEquals("task_failed", retriedId)

        composeTestRule.onNodeWithTag("delete_download_task_failed").performClick()
        assertEquals("task_failed", deletedId)

        // Verify bulk and per-album callbacks fire as expected
        composeTestRule.onNodeWithTag("smart_redownload_failed_pill").performClick()
        assertTrue(smartRedownloadAllCalled)

        composeTestRule.onNodeWithTag("downloads_list")
            .performScrollToNode(hasTestTag("album_folder_banner_$longFolderName"))
        composeTestRule.onNodeWithTag("album_redownload_failed_$longFolderName").performClick()
        assertEquals(longFolderName, smartRedownloadAlbumArg)

        composeTestRule.onNodeWithTag("resume_all_icon_button").performClick()
        assertTrue(resumeAllCalled)

        composeTestRule.onNodeWithTag("pause_all_icon_button").performClick()
        assertTrue(pauseAllCalled)
    }

    @Test
    fun `detected media screen renders in light theme without folder truncation and preserves selection callbacks`() {
        val longSubfolder = "Complete_Architectural_Digest_Gallery_2026"
        val item = DetectedMediaItem(
            id = "media_1",
            url = "https://images.example.com/gallery/high_res_architecture_2026_01.jpg",
            normalizedUrl = "https://images.example.com/gallery/high_res_architecture_2026_01.jpg",
            pageUrl = "https://images.example.com/gallery",
            title = "high_res_architecture_2026_01.jpg",
            mediaType = MediaType.IMAGE,
            extension = "jpg",
            mimeType = "image/jpeg",
            width = 3840,
            height = 2160
        )

        var toggledId: String? = null
        var singleDownloaded: DetectedMediaItem? = null
        var bulkDownloadCalled = false

        val state = MainUiState(
            allDetectedCount = 1,
            detectedImageCount = 1,
            filteredMediaItems = listOf(item),
            selectedIds = setOf("media_1"),
            extensionCounts = mapOf("jpg" to 1),
            folderConfig = DownloadFolderConfig(currentPageTitle = longSubfolder)
        )

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                DetectedMediaScreen(
                    uiState = state,
                    onSelectTab = {},
                    onToggleExtensionChip = {},
                    onApplyExtensionPreset = {},
                    onClearExtensionChips = {},
                    onToggleSkipDuplicates = {},
                    onSearchQueryChange = {},
                    onToggleItemSelection = { toggledId = it },
                    onSelectAll = {},
                    onDeselectAll = {},
                    onBulkDownloadSelected = { bulkDownloadCalled = true },
                    onDownloadSingleItem = { singleDownloaded = it },
                    onPreviewItem = {},
                    onOpenExtensionSheet = {},
                    onOpenDeepScanSheet = {},
                    onOpenCrawlerSheet = {},
                    onOpenFolderSheet = {},
                    onClearDetected = {}
                )
            }
        }

        // Verify folder chip is displayed without arbitrary .take(16) truncation
        composeTestRule.onNodeWithTag("top_folder_selector_chip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("media_card_media_1").performClick()
        assertEquals("media_1", toggledId)

        composeTestRule.onNodeWithTag("single_download_media_1").performClick()
        assertEquals(item, singleDownloaded)

        composeTestRule.onNodeWithTag("bulk_download_button").performClick()
        assertTrue(bulkDownloadCalled)
    }

    @Test
    fun `diagnostic log manager captures entries and formats export correctly`() {
        val logMgr = com.example.data.logging.DiagnosticLogManager
        logMgr.clear()

        logMgr.info(
            tag = "TestTag",
            message = "Testing HTTP GET image",
            category = com.example.data.logging.LogCategory.HTTP_NETWORK,
            url = "https://example.com/test.jpg"
        )

        logMgr.error(
            tag = "TestTag",
            message = "Simulated CDN 403 error",
            category = com.example.data.logging.LogCategory.RECOVERY,
            url = "https://example.com/test.jpg",
            responseCode = 403
        )

        val logs = logMgr.logsFlow.value
        assertEquals(2, logs.size)
        assertEquals("Simulated CDN 403 error", logs[0].message)
        assertEquals(403, logs[0].responseCode)

        val formattedExport = logMgr.getAllLogsFormatted()
        assertTrue(formattedExport.contains("Testing HTTP GET image"))
        assertTrue(formattedExport.contains("Simulated CDN 403 error"))
        assertTrue(formattedExport.contains("HTTP Status: 403"))
    }

    @Test
    fun `parseAlbumMediaFromHtml prioritizes high-res images and filters thumbnails`() {
        val sampleHtml = """
            <html>
            <head><title>Test Album Photo Gallery</title></head>
            <body>
                <h1>Summer Sunset Album</h1>
                <!-- img with data-full and thumbnail src -->
                <img src="/thumb/photo1_thumb.jpg" data-full="https://cdn.example.com/uploads/photo1_full.jpg" alt="Photo 1">
                <!-- img with data-src -->
                <img data-src="https://cdn.example.com/uploads/photo2.jpg" src="/thumbnails/photo2_preview.jpg">
                <!-- img thumbnail only (should be skipped) -->
                <img src="https://cdn.example.com/thumb/thumbnail_only.jpg">
                <!-- mp4 video -->
                <video src="https://cdn.example.com/uploads/video1.mp4"></video>
            </body>
            </html>
        """.trimIndent()

        val result = com.example.data.scanner.AlbumBatchScraperEngine.parseAlbumMediaFromHtml(
            albumUrl = "https://example.com/gallery/album-1",
            htmlText = sampleHtml,
            strictJpgMp4Only = true
        )

        assertEquals("Summer Sunset Album", result.title)
        assertEquals(3, result.mediaItems.size) // photo1_full.jpg, photo2.jpg, video1.mp4

        val urls = result.mediaItems.map { it.url }
        assertTrue(urls.contains("https://cdn.example.com/uploads/photo1_full.jpg"))
        assertTrue(urls.contains("https://cdn.example.com/uploads/photo2.jpg"))
        assertTrue(urls.contains("https://cdn.example.com/uploads/video1.mp4"))
        assertTrue(urls.none { it.contains("thumb") })
    }

    @Test
    fun `downloads screen supports search bar, collapse all, source album links, and telegram folder share`() {
        val folderName = "Tokyo_Night_Cyberpunk_2026"
        val sourceAlbumUrl = "https://example.com/albums/tokyo-night-2026"
        val hashtag = com.example.ui.components.TelegramMediaShareHelper.buildHashtag(folderName)
        assertEquals("#Tokyo_Night_Cyberpunk_2026", hashtag)

        val caption = com.example.ui.components.TelegramMediaShareHelper.buildStructuredCaption(
            albumTitle = "Tokyo Night Cyberpunk 2026",
            hashtag = hashtag,
            imageCount = 8,
            videoCount = 2,
            sourceUrl = sourceAlbumUrl,
            includeSourceUrl = true,
            partIndex = 1,
            totalParts = 2
        )
        assertTrue(caption.contains("#Tokyo_Night_Cyberpunk_2026"))
        assertTrue(caption.contains("Part 1/2"))
        assertTrue(caption.contains(sourceAlbumUrl))

        val completedTask = DownloadTask(
            id = "task_completed_1",
            url = "https://cdn.example.com/uploads/cyber_01.jpg",
            normalizedUrl = "https://cdn.example.com/uploads/cyber_01.jpg",
            refererUrl = sourceAlbumUrl,
            fileName = "cyber_01.jpg",
            folderName = folderName,
            extension = "jpg",
            mediaType = MediaType.IMAGE,
            mimeType = "image/jpeg",
            width = 1920,
            height = 1080,
            status = DownloadStatus.COMPLETED,
            downloadedBytes = 1024_000L,
            totalBytes = 1024_000L,
            speedBytesPerSec = 0L,
            localUri = "content://media/external/images/media/101",
            tempFilePath = null,
            errorMessage = null,
            createdAt = 1000L,
            completedAt = 2000L
        )

        var collapseAllClicked = false
        var telegramShareFolderClicked: String? = null
        var redownloadFolderClicked: String? = null
        var redownloadUrlClicked: String? = null

        val state = MainUiState(
            downloadTasks = listOf(completedTask),
            filteredDownloadTasks = listOf(completedTask),
            folderSourceLinks = mapOf(folderName to sourceAlbumUrl)
        )

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                DownloadsScreen(
                    uiState = state,
                    onSelectStatusFilter = {},
                    onPauseTask = {},
                    onResumeTask = {},
                    onRetryTask = {},
                    onDeleteTask = {},
                    onOpenCompletedFile = {},
                    onShareCompletedFile = {},
                    onClearCompleted = {},
                    onToggleCollapseAllFolders = { collapseAllClicked = true },
                    onOpenTelegramShareForFolder = { telegramShareFolderClicked = it },
                    onRedownloadAlbumByFolderOrUrl = { folder, url, _ ->
                        redownloadFolderClicked = folder
                        redownloadUrlClicked = url
                    },
                    onExploreDetectedMedia = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("download_search_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("collapse_all_folders_pill").performClick()
        assertTrue(collapseAllClicked)

        composeTestRule.onNodeWithTag("downloads_list")
            .performScrollToNode(hasTestTag("album_folder_banner_$folderName"))
        composeTestRule.onNodeWithTag("share_folder_telegram_$folderName").performClick()
        assertEquals(folderName, telegramShareFolderClicked)

        composeTestRule.onNodeWithTag("redownload_album_link_$folderName").performClick()
        assertEquals(folderName, redownloadFolderClicked)
        assertEquals(sourceAlbumUrl, redownloadUrlClicked)
    }
}


