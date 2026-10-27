package com.example

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.isRoot
import com.example.data.scanner.WebMediaScannerEngine
import com.example.domain.model.*
import com.example.ui.MainUiState
import com.example.ui.components.*
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Actual Compose rendering of every top-level screen/sheet; PNGs are for human visual review. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiAuditTest(private val surface: String, private val dark: Boolean) {
    @get:Rule val compose = createComposeRule()

    @Test fun renderSurface() = render()

    @Test @Config(qualifiers = "w840dp-h1000dp")
    fun renderWideSurface() = render()

    @OptIn(InternalComposeUiApi::class)
    private fun render() {
        compose.setContent {
            MyApplicationTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize()) { Content() } }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        // ModalBottomSheet/Dialog adds a second window; capture the topmost Compose root.
        val roots = compose.onAllNodes(isRoot()).fetchSemanticsNodes()
        check(roots.isNotEmpty())
        val view = (roots.last().root as ViewRootForTest).view
        lateinit var bitmap: Bitmap
        compose.runOnIdle {
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
        }
        val dir = File("build/reports/ui-audit").apply { mkdirs() }
        File(dir, "$surface-${if (dark) "dark" else "light"}-${view.width}.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    @Composable private fun Content() {
        val tasks = (1..11).map { sampleTask("photo-$it", if (it == 2) MediaType.VIDEO else MediaType.IMAGE) }
        val state = MainUiState(downloadTasks = tasks, filteredDownloadTasks = tasks,
            folderSourceLinks = mapOf("Travel_collection_with_a_long_album_title" to "https://example.com/albums/travel"))
        when (surface) {
            "home" -> HomeScreen(MainUiState(), WebMediaScannerEngine {}, {}, {}, {}, { _, _, _, _ -> }, {}, { _, _ -> }, {}, {},
                onNavigateToDownloads = {}, onOpenCompletedFile = {}, onShareCompletedFile = {}, onShowMessage = {})
            "downloads", "downloads-empty" -> DownloadsScreen(
                uiState = if (surface == "downloads") state else MainUiState(), onSelectStatusFilter = {},
                onPauseTask = {}, onResumeTask = {}, onRetryTask = {}, onDeleteTask = {}, onClearCompleted = {},
                onOpenCompletedFile = {}, onShareCompletedFile = {}, onExploreDetectedMedia = {})
            "detected", "detected-empty" -> {
                val items = if (surface == "detected") listOf(DetectedMediaItem("1", "https://example.com/a.jpg", "https://example.com/a.jpg",
                    "https://example.com/album", title = "Long media title for compact screen verification.jpg", mediaType = MediaType.IMAGE,
                    extension = "jpg", mimeType = "image/jpeg")) else emptyList()
                DetectedMediaScreen(MainUiState(filteredMediaItems = items, allDetectedCount = items.size), {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
            }
            "telegram", "telegram-empty", "telegram-preparing" -> TelegramFolderShareSheet(
                folderName = "Travel_collection_with_a_long_album_title",
                folderTasks = if (surface == "telegram-empty") emptyList() else tasks,
                sourceAlbumUrl = "https://example.com/albums/travel", onShareFolderBatchToTelegram = { _, _, _ -> },
                onSyncFolderToGallery = {}, onCopyCaption = {}, onDismiss = {}, isPreparing = surface == "telegram-preparing")
            "formats" -> ExtensionFilterSheet(ExtensionFilterConfig(), {}, {}, {}, {}, {}, { _, _ -> }, {})
            "folder" -> FolderSelectionDialog(DownloadFolderConfig(), {}, {}, {}, {})
            "scan" -> DeepScanSheet(DeepScanConfig(), DeepScanProgress(), ExtensionFilterConfig(), emptyList(), {}, {}, {}, {}, {}, {}, {})
            "albums" -> AlbumBatchGrabberSheet("", true, true, batchProgress = AlbumBatchProgress(), downloadedAlbumsHistory = emptyList(),
                onAlbumFolderUrlChange = {}, onToggleStrictJpgMp4 = {}, onToggleSkipDownloadedAlbums = {}, onStartAlbumBatch = {},
                onStopAlbumBatch = {}, onRemoveAlbumFromHistory = {}, onClearAlbumHistory = {}, onOpenDownloads = {}, onDismiss = {})
            "crawler" -> DomainCrawlerDialog("", {}, false, emptyList(), DeepScanProgress(), 2, 30, true, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
            "logs" -> DiagnosticLogDialog(onDismissRequest = {})
        }
    }

    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-dark={1}")
        fun cases(): List<Array<Any>> = listOf("home", "downloads", "downloads-empty", "detected", "detected-empty",
            "telegram", "telegram-empty", "telegram-preparing", "formats", "folder", "scan", "albums", "crawler", "logs")
            .flatMap { name -> listOf(arrayOf(name, false), arrayOf(name, true)) }
    }
}

internal fun sampleTask(id: String, type: MediaType = MediaType.IMAGE, status: DownloadStatus = DownloadStatus.COMPLETED) = DownloadTask(
    id, "https://example.com/$id.jpg", "https://example.com/$id.jpg", fileName = "$id.jpg",
    folderName = "Travel_collection_with_a_long_album_title", extension = "jpg", mediaType = type,
    mimeType = if (type == MediaType.VIDEO) "video/mp4" else "image/jpeg", width = 1920, height = 1080,
    status = status, downloadedBytes = 100, totalBytes = 100, speedBytesPerSec = 0,
    localUri = "content://media/external/images/$id", tempFilePath = null, errorMessage = null, createdAt = 1, completedAt = 2)
