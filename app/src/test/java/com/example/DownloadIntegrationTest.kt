package com.example

import android.content.Context
import android.os.Environment
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.download.DownloadRepository
import com.example.data.local.PulseGrabDatabase
import com.example.domain.model.*
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/** Local HTTP only: exercise enqueue → pause → resume → persisted file, not just callbacks. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class DownloadIntegrationTest {
    @Before fun resetProviderPathCache() {
        // AndroidX caches absolute roots; Robolectric creates a different data/external dir per test.
        val cache = androidx.core.content.FileProvider::class.java.getDeclaredField("sCache")
        cache.isAccessible = true
        (cache.get(null) as MutableMap<*, *>).clear()
    }

    @Test fun `pause and valid range resume preserve exact bytes`() = transfer(ignoreRange = false)
    @Test fun `server ignoring resume range restarts without duplicating bytes`() = transfer(ignoreRange = true)

    private fun transfer(ignoreRange: Boolean) = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, PulseGrabDatabase::class.java).build()
        val repository = DownloadRepository(context, db.downloadDao(), recoverOnStartup = false)
        val bytes = ByteArray(2 * 1024 * 1024) { (it % 251).toByte() }
        val ranges = CopyOnWriteArrayList<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val folder = "Transfer_${UUID.randomUUID().toString().take(8)}"
        server.createContext("/video.mp4") { exchange ->
            val range = exchange.requestHeaders.getFirst("Range")
            if (range != null) ranges.add(range)
            val offset = if (ignoreRange) 0 else range?.substringAfter("bytes=")?.substringBefore('-')?.toIntOrNull() ?: 0
            exchange.responseHeaders.add("Content-Type", "video/mp4")
            if (offset > 0) exchange.responseHeaders.add("Content-Range", "bytes $offset-${bytes.lastIndex}/${bytes.size}")
            try {
                exchange.sendResponseHeaders(if (offset > 0) 206 else 200, (bytes.size - offset).toLong())
                exchange.responseBody.use { output ->
                    var index = offset
                    while (index < bytes.size) {
                        val size = minOf(16 * 1024, bytes.size - index)
                        output.write(bytes, index, size); output.flush(); index += size
                        Thread.sleep(8)
                    }
                }
            } catch (_: java.io.IOException) { /* Expected when the client pauses. */ }
            finally { exchange.close() }
        }
        server.start()
        val url = "http://127.0.0.1:${server.address.port}/video.mp4"
        try {
            val item = DetectedMediaItem("transfer", url, url, "", title = "video.mp4", mediaType = MediaType.VIDEO,
                extension = "mp4", mimeType = "video/mp4")
            assertEquals(1, repository.enqueueMediaItems(listOf(item), true,
                DownloadFolderConfig(DownloadFolderMode.CUSTOM_SUBFOLDER, customSubfolderName = folder)))
            val active = withTimeout(20_000) {
                repository.allDownloadTasks.first { list -> list.any { it.status == DownloadStatus.DOWNLOADING && it.downloadedBytes > 0 } }.single()
            }
            repository.pauseDownload(active.id)
            val paused = withTimeout(10_000) { repository.allDownloadTasks.first { it.singleOrNull()?.status == DownloadStatus.PAUSED }.single() }
            assertTrue(File(paused.tempFilePath!!).length() > 0)
            repository.resumeDownload(active.id)
            val terminal = withTimeout(20_000) {
                repository.allDownloadTasks.first { it.singleOrNull()?.status in setOf(DownloadStatus.COMPLETED, DownloadStatus.FAILED) }.single()
            }
            assertEquals(terminal.errorMessage, DownloadStatus.COMPLETED, terminal.status)
            assertNotNull(terminal.localUri)
            val actual = context.contentResolver.openInputStream(android.net.Uri.parse(terminal.localUri))!!.use { it.readBytes() }
            assertArrayEquals(bytes, actual)
            assertEquals(bytes.size.toLong(), terminal.downloadedBytes)
            assertTrue("Resume must send a byte range", ranges.isNotEmpty())
        } finally {
            server.stop(0); repository.close(); db.close()
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "PulseGrab/$folder").deleteRecursively()
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { File(it, "PulseGrab/$folder").deleteRecursively() }
            File(context.filesDir, "downloads/PulseGrab/$folder").deleteRecursively()
        }
    }
}
