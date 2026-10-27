package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.download.DownloadRepository
import com.example.data.local.DownloadTaskEntity
import com.example.data.local.DownloaderPreferences
import com.example.data.local.PulseGrabDatabase
import com.example.domain.model.*
import com.example.ui.components.TelegramMediaShareHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 29, 35])
class TelegramSharingTest {
    @Before fun resetProviderPathCache() {
        // AndroidX caches absolute roots; Robolectric creates a different data/external dir per test.
        val cache = androidx.core.content.FileProvider::class.java.getDeclaredField("sCache")
        cache.isAccessible = true
        (cache.get(null) as MutableMap<*, *>).clear()
    }

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun `mixed album advertises both types and grants every stream`() {
        val uris = listOf(Uri.parse("content://media/external/images/1"), Uri.parse("content://media/external/video/2"))
        val intent = TelegramMediaShareHelper.buildShareIntent(uris, listOf("image/jpeg", "video/mp4"), "caption")
        assertEquals(Intent.ACTION_SEND_MULTIPLE, intent.action)
        assertEquals("*/*", intent.type)
        assertArrayEquals(arrayOf("image/jpeg", "video/mp4"), intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES))
        assertEquals(uris, intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals(2, intent.clipData!!.itemCount)
        uris.forEachIndexed { i, uri -> assertEquals(uri, intent.clipData!!.getItemAt(i).uri) }
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals("caption", intent.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test fun `single video has single-stream contract`() {
        val uri = Uri.parse("content://media/video/7")
        val intent = TelegramMediaShareHelper.buildShareIntent(listOf(uri), listOf("video/webm"), "")
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("video/webm", intent.type)
        assertEquals(uri, intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals(1, intent.clipData!!.itemCount)
    }

    @Test fun `different image formats have image wildcard`() {
        val intent = TelegramMediaShareHelper.buildShareIntent(
            listOf(Uri.parse("content://media/1"), Uri.parse("content://media/2")), listOf("image/jpeg", "image/png"), "")
        assertEquals("image/*", intent.type)
    }

    @Test fun `raw filesystem and empty share intents are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            TelegramMediaShareHelper.buildShareIntent(listOf(Uri.parse("file:///private/photo.jpg")), listOf("image/jpeg"), "")
        }
        assertThrows(IllegalArgumentException::class.java) { TelegramMediaShareHelper.buildShareIntent(emptyList(), emptyList(), "") }
    }

    @Test fun `hashtag handles empty unicode and numeric titles`() {
        assertEquals("#PulseGrab_Album", TelegramMediaShareHelper.buildHashtag(" !!! "))
        assertEquals("#Album_2026_Trip", TelegramMediaShareHelper.buildHashtag("2026 Trip"))
        assertEquals("#旅行_写真", TelegramMediaShareHelper.buildHashtag("旅行 写真"))
        assertEquals("#A_B", TelegramMediaShareHelper.buildHashtag("#A B"))
        val caption = TelegramMediaShareHelper.buildStructuredCaption("Trip", "bad tag", 2, 1, "https://example.com", false, 2, 3)
        assertTrue(caption.startsWith("#bad_tag"))
        assertTrue(caption.contains("Part 2/3"))
        assertFalse(caption.contains("https://"))
    }

    @Test fun `share preparation stays inside requested completed batch and does not heal partial tasks`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, PulseGrabDatabase::class.java).build()
        val repository = DownloadRepository(context, db.downloadDao(), recoverOnStartup = false)
        val dir = File(context.filesDir, "downloads/PulseGrab/Trip").apply { mkdirs() }
        fun task(id: String, status: DownloadStatus, present: Boolean = true): DownloadTaskEntity {
            val file = File(dir, "$id.jpg")
            if (present) file.writeBytes(byteArrayOf(1, 2, 3))
            return DownloadTaskEntity(id, "https://example.com/$id.jpg", "https://example.com/$id.jpg",
                fileName = file.name, folderName = "Trip", extension = "jpg", mediaType = "IMAGE", mimeType = "image/jpeg",
                status = status.name, localUri = if (present) FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file).toString() else null)
        }
        try {
            db.downloadDao().upsertTasks(listOf(task("first", DownloadStatus.COMPLETED), task("second", DownloadStatus.COMPLETED),
                task("partial", DownloadStatus.PAUSED), task("missing", DownloadStatus.COMPLETED, false)))
            val one = repository.prepareGalleryMediaUrisForFolder("Trip", setOf("first"))
            assertEquals(1, one.size)
            assertTrue(one.single().toString().endsWith("first.jpg"))
            assertTrue(repository.prepareGalleryMediaUrisForFolder("Trip", setOf("missing")).isEmpty())
            assertTrue(repository.prepareGalleryMediaUrisForFolder("Trip", emptySet()).isEmpty())
            assertTrue(repository.prepareGalleryMediaUrisForFolder("Trip", setOf("partial")).isEmpty())
            assertEquals(DownloadStatus.PAUSED.name, db.downloadDao().getTaskById("partial")!!.status)
            assertEquals(2, repository.prepareGalleryMediaUrisForFolder("Trip").size)
        } finally { repository.close(); db.close(); dir.deleteRecursively() }
    }

    @Test fun `folder and extension choices survive settings recreation including empty filters`() {
        val prefs = DownloaderPreferences(context)
        val folder = DownloadFolderConfig(DownloadFolderMode.SAF_CUSTOM_TREE, customTreeUri = "content://documents/tree/trip", customTreeDisplayName = "Trip")
        val extensions = ExtensionFilterConfig(emptySet(), setOf("mp4"), setOf("heic"), 800, 600)
        prefs.saveFolder(folder); prefs.saveExtensions(extensions); prefs.skipDuplicates = false
        val restored = DownloaderPreferences(context)
        assertEquals(folder, restored.folderConfig())
        assertEquals(extensions, restored.extensions())
        assertFalse(restored.skipDuplicates)
    }

    @Test fun `migration preserves task rows and adds nullable destination`() {
        val helper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context).name(null)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(4) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE download_tasks (id TEXT PRIMARY KEY NOT NULL)")
                        db.execSQL("INSERT INTO download_tasks VALUES ('keep-me')")
                    }
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build())
        try {
            PulseGrabDatabase.MIGRATION_4_5.migrate(helper.writableDatabase)
            helper.writableDatabase.query("SELECT id, destinationTreeUri FROM download_tasks").use {
                assertTrue(it.moveToFirst()); assertEquals("keep-me", it.getString(0)); assertTrue(it.isNull(1))
            }
        } finally { helper.close() }
    }
}
