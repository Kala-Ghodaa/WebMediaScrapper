package com.example.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import com.example.data.local.DownloadDao
import com.example.data.local.DownloadTaskEntity
import com.example.data.logging.DiagnosticLogManager
import com.example.data.logging.LogCategory
import com.example.data.logging.LogSeverity
import com.example.data.scanner.BrowserHttpBridge
import com.example.domain.model.DetectedMediaItem
import com.example.domain.model.DownloadStatus
import com.example.domain.model.DownloadTask
import com.example.domain.model.FormatUtils
import com.example.domain.model.MediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class DownloadRepository(
    private val context: Context,
    private val downloadDao: DownloadDao,
    recoverOnStartup: Boolean = true
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val concurrencySemaphore = Semaphore(3)
    private val enqueueMutex = kotlinx.coroutines.sync.Mutex()
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val albumRefreshCache = ConcurrentHashMap<String, Pair<Long, List<DetectedMediaItem>>>()
    private val albumRefreshLocks = ConcurrentHashMap<String, kotlinx.coroutines.sync.Mutex>()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        com.example.data.scanner.BrowserHttpBridge.init(context)
        createNotificationChannel()
        if (recoverOnStartup) recoverHungAndMergeExistingFoldersOnStartup()
    }

    fun close() {
        repositoryScope.cancel()
        activeJobs.clear()
    }

    /**
     * 1. Recovers any task left in DOWNLOADING or QUEUED from a previous killed session so it is not hung.
     * 2. Automatically merges & unifies all existing COMPLETED, FAILED, PAUSED, QUEUED, and PROCESSING
     *    pictures and videos into single unified album folders (`Download/PulseGrab/<Album_Title>/`).
     */
    private fun recoverHungAndMergeExistingFoldersOnStartup() {
        repositoryScope.launch {
            val unfinished = downloadDao.getUnfinishedTasksOnce()
            for (task in unfinished) {
                if ((task.status == DownloadStatus.DOWNLOADING.name || task.status == DownloadStatus.QUEUED.name) &&
                    !activeJobs.containsKey(task.id)
                ) {
                    downloadDao.updateStatusAndError(
                        id = task.id,
                        status = DownloadStatus.PAUSED.name,
                        errorMessage = "Session interrupted — tap Resume All or Clean Unfinished"
                    )
                    cancelNotification(task.id)
                }
            }
            syncAlbumRegistryFromTasks()
            mergeAndUnifyAllExistingAndActiveFoldersInternal()
        }
    }

    /**
     * Reconstructs and syncs permanent `DownloadedAlbumEntity` history records from all tasks in `download_tasks`
     * that have a source `refererUrl` and `folderName`. This ensures no album link or folder history is ever lost.
     */
    suspend fun syncAlbumRegistryFromTasks(): Int {
        var syncedCount = 0
        try {
            val allTasks = downloadDao.getAllTasksOnce()
            val validTasks = allTasks.filter {
                it.refererUrl.isNotBlank() &&
                    (it.refererUrl.startsWith("http://") ||
                        it.refererUrl.startsWith("https://") ||
                        it.refererUrl.startsWith("pulsegrab://")) &&
                    !com.example.domain.usecase.UrlMediaUtils.isThumbnailOrHptUrl(it.url)
            }
            val grouped = validTasks.groupBy { task ->
                val canonicalReferer = com.example.domain.usecase.UrlMediaUtils.canonicalizeMediaUrl(task.refererUrl)
                    ?: task.refererUrl.trim()
                canonicalReferer
            }
            for ((canonicalUrl, tasksForAlbum) in grouped) {
                if (canonicalUrl.isBlank()) continue
                val existing = downloadDao.getDownloadedAlbum(canonicalUrl)
                val bestFolder = tasksForAlbum
                    .map { com.example.domain.model.DownloadFolderConfig.sanitizeFolderName(it.folderName) }
                    .firstOrNull {
                        it.isNotBlank() &&
                            !it.equals("PulseGrab", ignoreCase = true) &&
                            !it.equals("Web_Media_Grab", ignoreCase = true)
                    } ?: tasksForAlbum.first().folderName.ifBlank { "PulseGrab" }
                val safeFolder = com.example.domain.model.DownloadFolderConfig.sanitizeFolderName(bestFolder)
                val rawAlbumUrl = tasksForAlbum.first().refererUrl.trim()
                val latestTime = tasksForAlbum.maxOfOrNull { it.completedAt ?: it.createdAt } ?: System.currentTimeMillis()
                if (existing == null) {
                    downloadDao.upsertDownloadedAlbum(
                        com.example.data.local.DownloadedAlbumEntity(
                            normalizedAlbumUrl = canonicalUrl,
                            albumUrl = rawAlbumUrl,
                            albumTitle = safeFolder.replace('_', ' '),
                            folderName = safeFolder,
                            mediaCount = tasksForAlbum.size,
                            downloadedAt = latestTime
                        )
                    )
                    syncedCount++
                } else if (existing.mediaCount < tasksForAlbum.size || existing.folderName.isBlank()) {
                    downloadDao.upsertDownloadedAlbum(
                        existing.copy(
                            folderName = existing.folderName.ifBlank { safeFolder },
                            mediaCount = maxOf(existing.mediaCount, tasksForAlbum.size)
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        return syncedCount
    }

    /**
     * Merges all existing downloaded pictures (`Pictures/PulseGrab/<Album>`) and videos (`Movies/PulseGrab/<Album>`)
     * into one unified folder per album (`DCIM/PulseGrab/<Album>` & `Download/PulseGrab/<Album>`), and unifies the target `folderName`
     * for all existing COMPLETED, FAILED, PAUSED, QUEUED, CANCELLED, and currently PROCESSING tasks.
     */
    fun mergeAndUnifyAllExistingAndActiveFolders(onFinished: ((Int) -> Unit)? = null) {
        repositoryScope.launch {
            syncAlbumRegistryFromTasks()
            val count = mergeAndUnifyAllExistingAndActiveFoldersInternal()
            onFinished?.invoke(count)
        }
    }

    private suspend fun mergeAndUnifyAllExistingAndActiveFoldersInternal(): Int {
        var mergedOrUpdatedCount = 0
        // 1. Consolidate any files from DCIM/PulseGrab, Pictures/PulseGrab, or Movies/PulseGrab back into Download/PulseGrab
        mergedOrUpdatedCount += sweepLegacySplitMediaStoreDirectories()

        val refererToFolder = buildRefererToUnifiedFolderMap()
        val allTasks = downloadDao.getAllTasksOnce()

        for (task in allTasks) {
            val unifiedFolder = resolveUnifiedFolderForTask(task, refererToFolder)
            val mediaType = runCatching { MediaType.valueOf(task.mediaType) }.getOrDefault(MediaType.IMAGE)

            // Check if the task's file already exists in Download/PulseGrab/<unifiedFolder> (or migrate from DCIM)
            val migratedOrHealedUri = migrateCompletedTaskUriIfNeeded(
                oldUriString = task.localUri,
                fileName = task.fileName,
                mimeType = task.mimeType,
                mediaType = mediaType,
                unifiedFolder = unifiedFolder
            ) ?: findExistingFileInDownloadFolderUri(unifiedFolder, task.fileName)
                ?: findExistingFileInDownloadFolderUri(task.folderName, task.fileName)

            if (task.status == DownloadStatus.COMPLETED.name) {
                val finalUriString = migratedOrHealedUri?.toString() ?: task.localUri
                if (task.folderName != unifiedFolder || finalUriString != task.localUri) {
                    downloadDao.updateTaskFolderAndUri(
                        id = task.id,
                        folderName = unifiedFolder,
                        localUri = finalUriString
                    )
                    mergedOrUpdatedCount++
                }
            } else {
                // If a task was reset to PAUSED/QUEUED/FAILED/CANCELLED, but its file ALREADY exists in Download/PulseGrab, heal it to COMPLETED!
                val isActivelyRunning = (task.status == DownloadStatus.DOWNLOADING.name) && activeJobs.containsKey(task.id)
                if (!isActivelyRunning && migratedOrHealedUri != null) {
                    val fileSize = getUriOrFileSize(migratedOrHealedUri, unifiedFolder, task.fileName)
                        .takeIf { it > 0L } ?: task.totalBytes.takeIf { it > 0L } ?: 1024L
                    downloadDao.markCompleted(
                        id = task.id,
                        status = DownloadStatus.COMPLETED.name,
                        downloadedBytes = fileSize,
                        totalBytes = fileSize,
                        localUri = migratedOrHealedUri.toString(),
                        completedAt = task.completedAt ?: System.currentTimeMillis()
                    )
                    if (task.folderName != unifiedFolder) {
                        downloadDao.updateTaskFolderAndUri(task.id, unifiedFolder, migratedOrHealedUri.toString())
                    }
                    mergedOrUpdatedCount++
                } else if (task.folderName != unifiedFolder) {
                    downloadDao.updateTaskFolderAndUri(
                        id = task.id,
                        folderName = unifiedFolder,
                        localUri = task.localUri
                    )
                    mergedOrUpdatedCount++
                }
            }
        }

        // 2. Discover any orphaned files sitting in Download/PulseGrab/<Folder> on disk that aren't in Room yet
        mergedOrUpdatedCount += recoverOrphanedFilesInDownloadPulseGrab()
        return mergedOrUpdatedCount
    }

    private suspend fun buildRefererToUnifiedFolderMap(): Map<String, String> {
        val map = HashMap<String, String>()
        val albums = downloadDao.getAllDownloadedAlbumsOnce()
        for (album in albums) {
            val safe = com.example.domain.model.DownloadFolderConfig.sanitizeFolderName(
                album.folderName.ifBlank { album.albumTitle }
            )
            if (album.albumUrl.isNotBlank()) map[album.albumUrl] = safe
            if (album.normalizedAlbumUrl.isNotBlank()) map[album.normalizedAlbumUrl] = safe
        }

        val allTasks = downloadDao.getAllTasksOnce()
        val tasksByReferer = allTasks.filter { it.refererUrl.isNotBlank() }.groupBy { it.refererUrl }
        for ((referer, group) in tasksByReferer) {
            if (map.containsKey(referer)) continue
            val candidate = group
                .map { com.example.domain.model.DownloadFolderConfig.sanitizeFolderName(it.folderName) }
                .firstOrNull {
                    it.isNotBlank() &&
                        !it.equals("PulseGrab", ignoreCase = true) &&
                        !it.equals("Web_Media_Grab", ignoreCase = true) &&
                        !it.equals("Media_Grab", ignoreCase = true)
                }
            if (candidate != null) {
                map[referer] = candidate
            }
        }
        return map
    }

    private fun resolveUnifiedFolderForTask(
        task: DownloadTaskEntity,
        refererToFolder: Map<String, String>
    ): String {
        if (task.destinationTreeUri != null) return com.example.domain.model.DownloadFolderConfig.sanitizeFolderName(task.folderName)
        val fromReferer = if (task.refererUrl.isNotBlank()) refererToFolder[task.refererUrl] else null
        if (!fromReferer.isNullOrBlank()) {
            return fromReferer
        }
        return com.example.domain.model.DownloadFolderConfig.sanitizeFolderName(
            task.folderName.ifBlank { activeFolderConfig.activeSubfolderName }
        )
    }

    private suspend fun resolveUnifiedFolderForSingleTask(task: DownloadTaskEntity): String {
        if (task.destinationTreeUri != null) return com.example.domain.model.DownloadFolderConfig.sanitizeFolderName(task.folderName)
        if (task.refererUrl.isNotBlank()) {
            val album = downloadDao.getDownloadedAlbum(task.refererUrl)
            if (album != null) {
                return com.example.domain.model.DownloadFolderConfig.sanitizeFolderName(
                    album.folderName.ifBlank { album.albumTitle }
                )
            }
            val siblings = downloadDao.getAllTasksOnce().filter { it.refererUrl == task.refererUrl }
            val bestSiblingFolder = siblings
                .map { com.example.domain.model.DownloadFolderConfig.sanitizeFolderName(it.folderName) }
                .firstOrNull {
                    it.isNotBlank() &&
                        !it.equals("PulseGrab", ignoreCase = true) &&
                        !it.equals("Web_Media_Grab", ignoreCase = true)
                }
            if (!bestSiblingFolder.isNullOrBlank()) {
                return bestSiblingFolder
            }
        }
        return com.example.domain.model.DownloadFolderConfig.sanitizeFolderName(
            task.folderName.ifBlank { activeFolderConfig.activeSubfolderName }
        )
    }

    /**
     * Ensures a completed task's `localUri` is stored in `Download/PulseGrab/<unifiedFolder>/` (`MediaStore.Downloads`).
     * If the task's `oldUriString` was previously placed in `DCIM/PulseGrab`, `Pictures/PulseGrab`, or `Movies/PulseGrab`,
     * it copies the file back into `Download/PulseGrab/<unifiedFolder>/` and removes the stray DCIM/Pictures/Movies copy
     * only after verifying the `Download/PulseGrab` file is safely written.
     */
    private fun migrateCompletedTaskUriIfNeeded(
        oldUriString: String?,
        fileName: String,
        mimeType: String,
        mediaType: MediaType,
        unifiedFolder: String
    ): Uri? {
        val safeFolder = com.example.domain.model.DownloadFolderConfig.sanitizeFolderName(unifiedFolder)
        oldUriString?.let { stored ->
            val uri = Uri.parse(stored)
            if (android.provider.DocumentsContract.isDocumentUri(context, uri) && isContentUriReadable(uri)) return uri
        }
        // First check if the file is already in Download/PulseGrab/<safeFolder>/<fileName>
        val existingInDownload = findExistingFileInDownloadFolderUri(safeFolder, fileName)
        if (!oldUriString.isNullOrBlank()) {
            val isDownloadUri = oldUriString.contains("/downloads") ||
                oldUriString.contains("Download/PulseGrab/$safeFolder", ignoreCase = true)
            if (isDownloadUri) {
                val parsed = runCatching { Uri.parse(oldUriString) }.getOrNull()
                if (parsed != null && isUriOrFileReadable(parsed)) {
                    return parsed
                }
            }
        }

        if (existingInDownload != null && isUriOrFileReadable(existingInDownload)) {
            // If oldUri was in DCIM/Images/Video, clean up the duplicate in DCIM now that Download copy is confirmed
            if (!oldUriString.isNullOrBlank() &&
                (oldUriString.contains("/images/media") || oldUriString.contains("/video/media"))
            ) {
                runCatching {
                    val oldUri = Uri.parse(oldUriString)
                    if (oldUri != existingInDownload) {
                        context.contentResolver.delete(oldUri, null, null)
                    }
                }
            }
            return existingInDownload
        }

        if (oldUriString.isNullOrBlank()) return null

        val resolver = context.contentResolver
        val oldUri = runCatching { Uri.parse(oldUriString) }.getOrNull() ?: return null
        val tempMigrateFile = File(getTempDir(), "migrate_${System.currentTimeMillis()}_$fileName")
        return try {
            val copied = if (oldUri.scheme == "file") {
                val srcFile = File(oldUri.path ?: return null)
                if (!srcFile.exists() || srcFile.length() == 0L) return null
                srcFile.copyTo(tempMigrateFile, overwrite = true)
                true
            } else {
                resolver.openInputStream(oldUri)?.use { input ->
                    FileOutputStream(tempMigrateFile).use { output ->
                        input.copyTo(output, bufferSize = 16 * 1024)
                    }
                    true
                } ?: false
            }

            if (!copied || !tempMigrateFile.exists() || tempMigrateFile.length() == 0L) {
                tempMigrateFile.delete()
                return null
            }

            val newUri = saveFileToMediaStore(
                sourceFile = tempMigrateFile,
                fileName = fileName,
                mimeType = mimeType,
                mediaType = mediaType,
                subfolderName = safeFolder
            )
            tempMigrateFile.delete()
            // If we migrated from DCIM (/images/media or /video/media) to Download/PulseGrab, remove the old DCIM duplicate
            if (newUri != null && newUri != oldUri &&
                (oldUriString.contains("/images/media") || oldUriString.contains("/video/media"))
            ) {
                runCatching { resolver.delete(oldUri, null, null) }
            }
            newUri
        } catch (_: Exception) {
            tempMigrateFile.delete()
            null
        }
    }

    /**
     * Consolidates any files that were placed in `DCIM/PulseGrab/<Album>`, `Pictures/PulseGrab/<Album>`,
     * or `Movies/PulseGrab/<Album>` back into `Download/PulseGrab/<Album>` (`MediaStore.Downloads`),
     * and deletes the stray DCIM/Pictures/Movies copy ONLY after verifying the `Download/PulseGrab` file is non-empty.
     */
    private fun sweepLegacySplitMediaStoreDirectories(): Int {
        var sweptCount = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val legacyCollections = listOf(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to MediaType.IMAGE,
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to MediaType.VIDEO
            )
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.RELATIVE_PATH
            )

            for ((collection, defaultMediaType) in legacyCollections) {
                try {
                    val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} NOT LIKE ?"
                    val selectionArgs = arrayOf("%PulseGrab/%", "${Environment.DIRECTORY_DOWNLOADS}/%")
                    resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                        val idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                        val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                        val mimeCol = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                        val pathCol = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)

                        while (cursor.moveToNext()) {
                            val id = if (idCol >= 0) cursor.getLong(idCol) else continue
                            val displayName = if (nameCol >= 0) cursor.getString(nameCol) ?: continue else continue
                            val mimeType = if (mimeCol >= 0) {
                                cursor.getString(mimeCol) ?: if (defaultMediaType == MediaType.VIDEO) "video/mp4" else "image/jpeg"
                            } else {
                                if (defaultMediaType == MediaType.VIDEO) "video/mp4" else "image/jpeg"
                            }
                            val relPath = if (pathCol >= 0) cursor.getString(pathCol) ?: "" else ""
                            val subfolder = relPath.substringAfter("PulseGrab/", "")
                                .trim('/')
                                .substringBefore('/')
                                .ifBlank { "PulseGrab" }

                            val itemUri = android.content.ContentUris.withAppendedId(collection, id)
                            val existingDownloadUri = findExistingFileInDownloadFolderUri(subfolder, displayName)
                            if (existingDownloadUri != null && isUriOrFileReadable(existingDownloadUri)) {
                                runCatching { resolver.delete(itemUri, null, null) }
                                sweptCount++
                                continue
                            }

                            val tempFile = File(getTempDir(), "consolidate_${id}_$displayName")
                            val copied = runCatching {
                                resolver.openInputStream(itemUri)?.use { input ->
                                    FileOutputStream(tempFile).use { output ->
                                        input.copyTo(output, bufferSize = 16 * 1024)
                                    }
                                    true
                                } ?: false
                            }.getOrDefault(false)

                            if (copied && tempFile.exists() && tempFile.length() > 0L) {
                                val newUri = saveFileToMediaStore(
                                    sourceFile = tempFile,
                                    fileName = displayName,
                                    mimeType = mimeType,
                                    mediaType = defaultMediaType,
                                    subfolderName = subfolder
                                )
                                tempFile.delete()
                                if (newUri != null) {
                                    runCatching { resolver.delete(itemUri, null, null) }
                                    sweptCount++
                                }
                            } else {
                                tempFile.delete()
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }

        // Also check direct filesystem DCIM/PulseGrab directory if accessible
        runCatching {
            val dcimPulseGrab = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "PulseGrab")
            if (dcimPulseGrab.exists() && dcimPulseGrab.isDirectory) {
                dcimPulseGrab.listFiles()?.filter { it.isDirectory }?.forEach { albumDir ->
                    val subfolder = albumDir.name.ifBlank { "PulseGrab" }
                    albumDir.listFiles()?.filter { it.isFile && it.length() > 0L }?.forEach { file ->
                        val isVideo = file.name.endsWith(".mp4", ignoreCase = true) ||
                            file.name.endsWith(".webm", ignoreCase = true) ||
                            file.name.endsWith(".mov", ignoreCase = true)
                        val mime = if (isVideo) "video/mp4" else "image/jpeg"
                        val mediaType = if (isVideo) MediaType.VIDEO else MediaType.IMAGE
                        val saved = saveFileToMediaStore(
                            sourceFile = file,
                            fileName = file.name,
                            mimeType = mime,
                            mediaType = mediaType,
                            subfolderName = subfolder
                        )
                        if (saved != null) {
                            runCatching { file.delete() }
                            sweptCount++
                        }
                    }
                    if (albumDir.listFiles()?.isEmpty() == true) {
                        runCatching { albumDir.delete() }
                    }
                }
            }
        }
        return sweptCount
    }

    /**
     * Scans `Download/PulseGrab/<Album>` on disk and in `MediaStore.Downloads` to recover any downloaded files
     * whose database records were cleared or missing, ensuring no file in `Download/PulseGrab` is ever invisible.
     */
    private suspend fun recoverOrphanedFilesInDownloadPulseGrab(): Int {
        var recoveredCount = 0
        try {
            val existingTasks = downloadDao.getAllTasksOnce()
            val knownFolderAndFile = existingTasks.map {
                "${com.example.domain.model.DownloadFolderConfig.sanitizeFolderName(it.folderName).lowercase(Locale.US)}/${it.fileName.lowercase(Locale.US)}"
            }.toHashSet()
            val albumsByFolder = downloadDao.getAllDownloadedAlbumsOnce().associateBy {
                com.example.domain.model.DownloadFolderConfig.sanitizeFolderName(it.folderName).lowercase(Locale.US)
            }

            val publicDownloadsPulseGrab = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "PulseGrab"
            )
            if (publicDownloadsPulseGrab.exists() && publicDownloadsPulseGrab.isDirectory) {
                publicDownloadsPulseGrab.listFiles()?.filter { it.isDirectory }?.forEach { folderDir ->
                    val safeFolder = com.example.domain.model.DownloadFolderConfig.sanitizeFolderName(folderDir.name)
                    val albumMeta = albumsByFolder[safeFolder.lowercase(Locale.US)]
                    folderDir.listFiles()?.filter { it.isFile && it.length() > 0L && !it.name.endsWith(".part") }?.forEach { file ->
                        val key = "${safeFolder.lowercase(Locale.US)}/${file.name.lowercase(Locale.US)}"
                        if (key !in knownFolderAndFile) {
                            val ext = file.extension.lowercase(Locale.US).ifBlank { "jpg" }
                            val isVideo = ext in setOf("mp4", "webm", "mkv", "mov", "m4v", "avi")
                            val mime = if (isVideo) "video/$ext" else "image/${if (ext == "jpg") "jpeg" else ext}"
                            val uri = findExistingFileInDownloadFolderUri(safeFolder, file.name) ?: Uri.fromFile(file)
                            val syntheticUrl = "file://${file.absolutePath}"
                            val entity = DownloadTaskEntity(
                                id = "recovered_${safeFolder}_${file.name}".hashCode().toUInt().toString(16),
                                url = syntheticUrl,
                                normalizedUrl = syntheticUrl,
                                refererUrl = albumMeta?.albumUrl ?: "",
                                fileName = file.name,
                                folderName = safeFolder,
                                extension = ext,
                                mediaType = if (isVideo) MediaType.VIDEO.name else MediaType.IMAGE.name,
                                mimeType = mime,
                                width = 0,
                                height = 0,
                                status = DownloadStatus.COMPLETED.name,
                                downloadedBytes = file.length(),
                                totalBytes = file.length(),
                                speedBytesPerSec = 0L,
                                localUri = uri.toString(),
                                tempFilePath = null,
                                errorMessage = null,
                                createdAt = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis(),
                                completedAt = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
                            )
                            downloadDao.upsertTask(entity)
                            knownFolderAndFile.add(key)
                            recoveredCount++
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return recoveredCount
    }

    val allDownloadTasks: Flow<List<DownloadTask>> = downloadDao.observeAllTasks()
        .map { entities -> entities.map { it.toDomain() } }

    @Volatile
    var activeFolderConfig: com.example.domain.model.DownloadFolderConfig =
        com.example.domain.model.DownloadFolderConfig()

    suspend fun enqueueMediaItems(
        items: List<DetectedMediaItem>,
        skipDuplicates: Boolean,
        folderConfig: com.example.domain.model.DownloadFolderConfig = activeFolderConfig
    ): Int = withContext(Dispatchers.IO) {
        enqueueMutex.withLock {
        this@DownloadRepository.activeFolderConfig = folderConfig
        val defaultFolder = folderConfig.activeSubfolderName
        val refererToFolder = buildRefererToUnifiedFolderMap()
        val touchedAlbums = LinkedHashMap<String, Triple<String, String, Int>>()
        var enqueuedCount = 0
        for (item in items) {
            // Strict regex filter: drop any URL whose filename matches HPT\d+\.(jpg|jpeg|png|webp)$ or contains /thumb/
            if (com.example.domain.usecase.UrlMediaUtils.isThumbnailOrHptUrl(item.url) ||
                com.example.domain.usecase.UrlMediaUtils.isThumbnailOrHptUrl(item.normalizedUrl)
            ) {
                DiagnosticLogManager.log(
                    category = LogCategory.ALBUM_QUEUE,
                    severity = LogSeverity.INFO,
                    tag = "DownloadRepository",
                    url = item.url,
                    message = "Strict filter dropped protected SEO thumbnail / HPT asset: ${item.title}"
                )
                continue
            }

            // Always organize each album's pictures AND videos into the exact same single folder
            val itemFolder = when {
                folderConfig.folderMode == com.example.domain.model.DownloadFolderMode.CUSTOM_SUBFOLDER ->
                    com.example.domain.model.DownloadFolderConfig.sanitizeFolderName(folderConfig.customSubfolderName)
                item.pageTitle.isNotBlank() ->
                    com.example.domain.model.DownloadFolderConfig.sanitizeFolderName(item.pageTitle)
                item.pageUrl.isNotBlank() && refererToFolder.containsKey(item.pageUrl) ->
                    refererToFolder.getValue(item.pageUrl)
                else ->
                    defaultFolder
            }

            if (item.pageUrl.isNotBlank() &&
                (item.pageUrl.startsWith("http://") ||
                    item.pageUrl.startsWith("https://") ||
                    item.pageUrl.startsWith("pulsegrab://"))
            ) {
                val prev = touchedAlbums[item.pageUrl]
                val albumTitle = item.pageTitle.ifBlank { itemFolder.replace('_', ' ') }
                touchedAlbums[item.pageUrl] = Triple(itemFolder, albumTitle, (prev?.third ?: 0) + 1)
            }

            if (skipDuplicates) {
                val existing = downloadDao.getTaskByNormalizedUrl(item.normalizedUrl)
                if (existing != null) {
                    // Ensure existing task's folder is unified with this album folder as well
                    if (existing.folderName != itemFolder) {
                        val migratedUri = if (existing.status == DownloadStatus.COMPLETED.name) {
                            migrateCompletedTaskUriIfNeeded(
                                oldUriString = existing.localUri,
                                fileName = existing.fileName,
                                mimeType = existing.mimeType,
                                mediaType = item.mediaType,
                                unifiedFolder = itemFolder
                            )
                        } else null
                        downloadDao.updateTaskFolderAndUri(
                            id = existing.id,
                            folderName = itemFolder,
                            localUri = migratedUri?.toString() ?: existing.localUri
                        )
                    }
                    val isTrulyActive = (existing.status == DownloadStatus.DOWNLOADING.name ||
                        existing.status == DownloadStatus.QUEUED.name) &&
                        activeJobs.containsKey(existing.id)
                    if (isTrulyActive) {
                        continue
                    }
                    if (existing.status == DownloadStatus.COMPLETED.name) {
                        val fileExists = doesTaskFileExist(existing)
                        if (fileExists) {
                            // File exists on disk: just check and skip!
                            continue
                        }
                        // File does NOT exist on disk: allow re-download!
                    }
                }
            }

            val cleanExt = item.extension.lowercase(Locale.US).ifBlank {
                if (item.mediaType == MediaType.VIDEO) "mp4" else "jpg"
            }
            val existingTasks = downloadDao.getTasksByFolderNameOnce(itemFolder)
            val fileName = existingTasks.firstOrNull { it.id == item.id }?.fileName
                ?: MediaFileNames.choose(item.title, cleanExt, item.normalizedUrl,
                    existingTasks.map { it.fileName }.toSet())

            val tempFile = File(getTempDir(), "${item.id}.part")
            val entity = DownloadTaskEntity(
                id = item.id,
                url = item.url,
                normalizedUrl = item.normalizedUrl,
                refererUrl = item.pageUrl,
                fileName = fileName,
                folderName = itemFolder,
                extension = cleanExt,
                mediaType = item.mediaType.name,
                mimeType = item.mimeType,
                width = item.width,
                height = item.height,
                status = DownloadStatus.QUEUED.name,
                downloadedBytes = if (tempFile.exists()) tempFile.length() else 0L,
                totalBytes = item.estimatedSizeBytes,
                speedBytesPerSec = 0L,
                tempFilePath = tempFile.absolutePath,
                createdAt = System.currentTimeMillis(),
                destinationTreeUri = folderConfig.customTreeUri.takeIf {
                    folderConfig.folderMode == com.example.domain.model.DownloadFolderMode.SAF_CUSTOM_TREE
                }
            )
            downloadDao.upsertTask(entity)
            startDownloadWorker(entity.id)
            enqueuedCount++
        }

        // Permanently record/update album link & folder history for all queued sources
        for ((pageUrl, meta) in touchedAlbums) {
            val (folder, title, count) = meta
            val canonicalAlbumUrl = com.example.domain.usecase.UrlMediaUtils.canonicalizeMediaUrl(pageUrl) ?: pageUrl
            val existingAlbum = downloadDao.getDownloadedAlbum(canonicalAlbumUrl)
            downloadDao.upsertDownloadedAlbum(
                com.example.data.local.DownloadedAlbumEntity(
                    normalizedAlbumUrl = canonicalAlbumUrl,
                    albumUrl = pageUrl,
                    albumTitle = existingAlbum?.albumTitle?.takeIf { it.isNotBlank() } ?: title,
                    folderName = folder,
                    mediaCount = maxOf(existingAlbum?.mediaCount ?: 0, count),
                    downloadedAt = System.currentTimeMillis()
                )
            )
        }

        enqueuedCount
        }
    }

    fun pauseDownload(taskId: String) {
        val previous = activeJobs[taskId]
        previous?.cancel()
        repositoryScope.launch {
            previous?.join()
            val task = downloadDao.getTaskById(taskId)
            if (task != null && task.status != DownloadStatus.COMPLETED.name) {
                val unifiedFolder = resolveUnifiedFolderForSingleTask(task)
                downloadDao.upsertTask(
                    task.copy(
                        folderName = unifiedFolder,
                        status = DownloadStatus.PAUSED.name,
                        speedBytesPerSec = 0L,
                        errorMessage = null
                    )
                )
            }
            cancelNotification(taskId)
        }
    }

    fun resumeDownload(taskId: String) {
        repositoryScope.launch {
            val task = downloadDao.getTaskById(taskId) ?: return@launch
            if (com.example.domain.usecase.UrlMediaUtils.isThumbnailOrHptUrl(task.url)) {
                downloadDao.updateStatusAndError(task.id, DownloadStatus.CANCELLED.name, "Skipped protected thumbnail")
                return@launch
            }
            val unifiedFolder = resolveUnifiedFolderForSingleTask(task)
            val tempFile = task.tempFilePath?.let { File(it) }
            val existingBytes = if (tempFile != null && tempFile.exists()) tempFile.length() else task.downloadedBytes
            val updated = task.copy(
                folderName = unifiedFolder,
                status = DownloadStatus.QUEUED.name,
                downloadedBytes = existingBytes,
                speedBytesPerSec = 0L,
                errorMessage = null
            )
            downloadDao.upsertTask(updated)
            startDownloadWorker(updated.id)
        }
    }

    fun retryDownload(taskId: String) {
        repositoryScope.launch {
            val task = downloadDao.getTaskById(taskId) ?: return@launch
            if (com.example.domain.usecase.UrlMediaUtils.isThumbnailOrHptUrl(task.url)) {
                downloadDao.updateStatusAndError(task.id, DownloadStatus.CANCELLED.name, "Skipped protected thumbnail")
                return@launch
            }
            activeJobs[taskId]?.cancelAndJoin()
            task.tempFilePath?.let { runCatching { File(it).delete() } }
            val unifiedFolder = resolveUnifiedFolderForSingleTask(task)
            downloadDao.updateStatusAndError(
                id = taskId,
                status = DownloadStatus.QUEUED.name,
                errorMessage = "Smart Re-Download: Refreshing album page & cookies…"
            )
            val refreshed = refreshTaskFromSourceAlbum(
                task = task.copy(
                    folderName = unifiedFolder,
                    downloadedBytes = 0L,
                    speedBytesPerSec = 0L
                ),
                forceWebViewDom = false,
                bypassCache = true
            )
            val resetTask = refreshed.copy(
                folderName = unifiedFolder,
                status = DownloadStatus.QUEUED.name,
                downloadedBytes = 0L,
                speedBytesPerSec = 0L,
                errorMessage = null
            )
            downloadDao.upsertTask(resetTask)
            startDownloadWorker(resetTask.id)
        }
    }

    fun deleteDownloadTask(taskId: String) {
        val previous = activeJobs[taskId]
        previous?.cancel()
        repositoryScope.launch {
            previous?.join()
            val task = downloadDao.getTaskById(taskId)
            task?.tempFilePath?.let { File(it).delete() }
            downloadDao.deleteTaskById(taskId)
            cancelNotification(taskId)
        }
    }

    fun clearCompleted() {
        repositoryScope.launch {
            downloadDao.clearCompletedTasks()
        }
    }

    /**
     * Cancels all active/hung jobs, deletes temporary .part files, clears notifications,
     * and deletes all non-completed tasks (DOWNLOADING, QUEUED, PAUSED, FAILED, CANCELLED) at once.
     */
    fun clearAllUnfinishedAndError() {
        val runningIds = activeJobs.keys.toList()
        for (id in runningIds) {
            activeJobs[id]?.cancel()
            cancelNotification(id)
        }
        repositoryScope.launch {
            runningIds.forEach { activeJobs[it]?.join() }
            val unfinished = downloadDao.getUnfinishedTasksOnce()
            for (task in unfinished) {
                activeJobs[task.id]?.cancelAndJoin()
                task.tempFilePath?.let { runCatching { File(it).delete() } }
                cancelNotification(task.id)
            }
            runCatching {
                getTempDir().listFiles()?.forEach { it.delete() }
            }
            downloadDao.deleteAllUnfinishedTasks()
        }
    }

    /**
     * Deletes all FAILED and CANCELLED download tasks and their temporary .part files at once.
     */
    fun clearAllFailed() {
        repositoryScope.launch {
            val failed = downloadDao.getFailedTasksOnce()
            for (task in failed) {
                activeJobs[task.id]?.cancelAndJoin()
                task.tempFilePath?.let { runCatching { File(it).delete() } }
                cancelNotification(task.id)
            }
            downloadDao.deleteAllFailedTasks()
        }
    }

    /**
     * Stops all active downloads and wipes the entire download list (unfinished + completed).
     */
    fun clearAllDownloads() {
        val runningIds = activeJobs.keys.toList()
        for (id in runningIds) {
            activeJobs[id]?.cancel()
            cancelNotification(id)
        }
        repositoryScope.launch {
            runningIds.forEach { activeJobs[it]?.join() }
            val all = downloadDao.getAllTasksOnce()
            for (task in all) {
                task.tempFilePath?.let { runCatching { File(it).delete() } }
                cancelNotification(task.id)
            }
            runCatching {
                getTempDir().listFiles()?.forEach { it.delete() }
            }
            downloadDao.deleteAllTasks()
        }
    }

    /**
     * Pauses/stops all currently progressing (DOWNLOADING) or queued (QUEUED) downloads at once,
     * while unifying their album folder names.
     */
    fun pauseAllActive() {
        val runningIds = activeJobs.keys.toList()
        for (id in runningIds) {
            activeJobs[id]?.cancel()
            cancelNotification(id)
        }
        repositoryScope.launch {
            runningIds.forEach { activeJobs[it]?.join() }
            val refererToFolder = buildRefererToUnifiedFolderMap()
            val unfinished = downloadDao.getUnfinishedTasksOnce()
            for (task in unfinished) {
                val unifiedFolder = resolveUnifiedFolderForTask(task, refererToFolder)
                if (task.status == DownloadStatus.DOWNLOADING.name || task.status == DownloadStatus.QUEUED.name) {
                    downloadDao.upsertTask(
                        task.copy(
                            folderName = unifiedFolder,
                            status = DownloadStatus.PAUSED.name,
                            speedBytesPerSec = 0L,
                            errorMessage = null
                        )
                    )
                    cancelNotification(task.id)
                } else if (task.folderName != unifiedFolder) {
                    downloadDao.updateTaskFolderAndUri(task.id, unifiedFolder, task.localUri)
                }
            }
        }
    }

    /**
     * Resumes all PAUSED, idle QUEUED, hung DOWNLOADING, and FAILED/CANCELLED downloads at once,
     * preserving partial progress where available and unifying their album folder names so all
     * pictures and videos land in the same album folder.
     */
    fun resumeAllPaused() {
        repositoryScope.launch {
            val refererToFolder = buildRefererToUnifiedFolderMap()
            val unfinished = downloadDao.getUnfinishedTasksOnce()
            for (thumb in unfinished.filter { com.example.domain.usecase.UrlMediaUtils.isThumbnailOrHptUrl(it.url) }) {
                downloadDao.updateStatusAndError(thumb.id, DownloadStatus.CANCELLED.name, "Skipped protected thumbnail")
            }
            val targets = unfinished.filter { task ->
                val isCurrentlyRunning = (task.status == DownloadStatus.DOWNLOADING.name) &&
                    activeJobs.containsKey(task.id)
                !isCurrentlyRunning && !com.example.domain.usecase.UrlMediaUtils.isThumbnailOrHptUrl(task.url)
            }

            for (task in targets) {
                val unifiedFolder = resolveUnifiedFolderForTask(task, refererToFolder)
                val isFailedOrCancelled = task.status == DownloadStatus.FAILED.name ||
                    task.status == DownloadStatus.CANCELLED.name
                val tempFile = task.tempFilePath?.let { File(it) }
                // Never preserve corrupt .part files for FAILED/CANCELLED tasks
                if (isFailedOrCancelled) {
                    tempFile?.let { runCatching { it.delete() } }
                }
                val existingBytes = if (!isFailedOrCancelled && tempFile != null && tempFile.exists()) {
                    tempFile.length()
                } else if (!isFailedOrCancelled) {
                    task.downloadedBytes
                } else {
                    0L
                }
                val resumedTask = task.copy(
                    folderName = unifiedFolder,
                    status = DownloadStatus.QUEUED.name,
                    downloadedBytes = existingBytes,
                    speedBytesPerSec = 0L,
                    errorMessage = null
                )
                downloadDao.upsertTask(resumedTask)
                startDownloadWorker(resumedTask.id)
            }
        }
    }

    /**
     * Retries all FAILED, CANCELLED, PAUSED, or hung downloads at once into their unified album folders,
     * refreshing source album pages & cookies for failed items.
     */
    fun retryAllFailedAndUnfinished() {
        smartRedownloadAllFailed(includePaused = true)
    }

    /**
     * Smart Re-Download All Failed:
     * 1. Finds all FAILED / CANCELLED tasks (and optionally PAUSED / hung tasks).
     * 2. Wipes any corrupted .part files so rejected Range headers or HTML error bytes are eliminated.
     * 3. Groups tasks by source album (`refererUrl` / `folderName`) and re-fetches each album page once
     *    via `BrowserHttpBridge` (with WebView DOM fallback) to refresh Cloudflare/session cookies and
     *    resolve updated CDN media links matched by filename.
     * 4. Re-queues every item cleanly into its unified single album folder (`Download/PulseGrab/<Album_Title>/`).
     */
    fun smartRedownloadAllFailed(
        includePaused: Boolean = false,
        onComplete: ((Int) -> Unit)? = null
    ) {
        repositoryScope.launch {
            albumRefreshCache.clear()
            val refererToFolder = buildRefererToUnifiedFolderMap()
            val candidates = if (includePaused) {
                downloadDao.getUnfinishedTasksOnce()
            } else {
                val failed = downloadDao.getFailedTasksOnce()
                if (failed.isNotEmpty()) failed else downloadDao.getUnfinishedTasksOnce().filter {
                    it.status == DownloadStatus.FAILED.name ||
                        it.status == DownloadStatus.CANCELLED.name ||
                        !activeJobs.containsKey(it.id)
                }
            }

            for (thumb in candidates.filter { com.example.domain.usecase.UrlMediaUtils.isThumbnailOrHptUrl(it.url) }) {
                downloadDao.updateStatusAndError(thumb.id, DownloadStatus.CANCELLED.name, "Skipped protected thumbnail")
            }
            val validCandidates = candidates.filterNot { com.example.domain.usecase.UrlMediaUtils.isThumbnailOrHptUrl(it.url) }

            var redownloadedCount = 0
            for (task in validCandidates) {
                val isCurrentlyRunning = (task.status == DownloadStatus.DOWNLOADING.name) &&
                    activeJobs.containsKey(task.id)
                val unifiedFolder = resolveUnifiedFolderForTask(task, refererToFolder)
                if (isCurrentlyRunning) {
                    if (task.folderName != unifiedFolder) {
                        downloadDao.updateTaskFolderAndUri(task.id, unifiedFolder, task.localUri)
                    }
                    continue
                }

                activeJobs[task.id]?.cancelAndJoin()
                val isPausedOnly = task.status == DownloadStatus.PAUSED.name && !includePaused
                if (!isPausedOnly) {
                    task.tempFilePath?.let { runCatching { File(it).delete() } }
                }

                downloadDao.updateStatusAndError(
                    id = task.id,
                    status = DownloadStatus.QUEUED.name,
                    errorMessage = "Smart Re-Download: Refreshing album session & link…"
                )

                val refreshed = refreshTaskFromSourceAlbum(
                    task = task.copy(
                        folderName = unifiedFolder,
                        downloadedBytes = 0L,
                        speedBytesPerSec = 0L
                    ),
                    forceWebViewDom = false,
                    bypassCache = false // uses fresh per-batch cache so 10 files in 1 album only scrape the album once!
                )

                val resetTask = refreshed.copy(
                    folderName = unifiedFolder,
                    status = DownloadStatus.QUEUED.name,
                    downloadedBytes = 0L,
                    speedBytesPerSec = 0L,
                    errorMessage = null
                )
                downloadDao.upsertTask(resetTask)
                startDownloadWorker(resetTask.id)
                redownloadedCount++
            }
            onComplete?.invoke(redownloadedCount)
        }
    }

    // ================= PER-ALBUM FOLDER MERGED CONTROLS =================

    fun pauseAlbumFolder(folderName: String) {
        repositoryScope.launch {
            val tasks = downloadDao.getTasksByFolderNameOnce(folderName)
            for (task in tasks) {
                if (task.status == DownloadStatus.DOWNLOADING.name || task.status == DownloadStatus.QUEUED.name) {
                    activeJobs[task.id]?.cancelAndJoin()
                    downloadDao.updateStatusAndError(task.id, DownloadStatus.PAUSED.name, null)
                    cancelNotification(task.id)
                }
            }
        }
    }

    fun resumeOrRetryAlbumFolder(folderName: String) {
        repositoryScope.launch {
            val tasks = downloadDao.getTasksByFolderNameOnce(folderName)
            val hasFailed = tasks.any {
                it.status == DownloadStatus.FAILED.name || it.status == DownloadStatus.CANCELLED.name
            }
            if (hasFailed) {
                albumRefreshCache.clear()
            }
            for (task in tasks) {
                if (task.status != DownloadStatus.COMPLETED.name) {
                    val isRunning = task.status == DownloadStatus.DOWNLOADING.name && activeJobs.containsKey(task.id)
                    if (!isRunning) {
                        val isFailed = task.status == DownloadStatus.FAILED.name ||
                            task.status == DownloadStatus.CANCELLED.name
                        val tempFile = task.tempFilePath?.let { File(it) }
                        if (isFailed) {
                            tempFile?.let { runCatching { it.delete() } }
                        }
                        val baseTask = if (isFailed) {
                            refreshTaskFromSourceAlbum(
                                task = task.copy(folderName = folderName, downloadedBytes = 0L),
                                forceWebViewDom = false,
                                bypassCache = false
                            )
                        } else {
                            task
                        }
                        val existingBytes = if (!isFailed && tempFile != null && tempFile.exists()) {
                            tempFile.length()
                        } else if (!isFailed) {
                            task.downloadedBytes
                        } else {
                            0L
                        }
                        val updated = baseTask.copy(
                            folderName = folderName,
                            status = DownloadStatus.QUEUED.name,
                            downloadedBytes = existingBytes,
                            speedBytesPerSec = 0L,
                            errorMessage = null
                        )
                        downloadDao.upsertTask(updated)
                        startDownloadWorker(updated.id)
                    }
                }
            }
        }
    }

    /**
     * Smart Re-Download for a specific unified Album Folder (`folderName`):
     * Refreshes the source album page once, updates cookies & media links for all failed/unfinished
     * tasks in that folder, wipes corrupted .part files, and re-downloads them into `Download/PulseGrab/<folderName>`.
     */
    fun smartRedownloadAlbumFolder(
        folderName: String,
        onComplete: ((Int) -> Unit)? = null
    ) {
        repositoryScope.launch {
            albumRefreshCache.clear()
            val tasks = downloadDao.getTasksByFolderNameOnce(folderName)
            val targets = tasks.filter { it.status != DownloadStatus.COMPLETED.name }
            var count = 0
            for (task in targets) {
                activeJobs[task.id]?.cancelAndJoin()
                task.tempFilePath?.let { runCatching { File(it).delete() } }
                downloadDao.updateStatusAndError(
                    id = task.id,
                    status = DownloadStatus.QUEUED.name,
                    errorMessage = "Smart Re-Download: Refreshing album '$folderName'…"
                )
                val refreshed = refreshTaskFromSourceAlbum(
                    task = task.copy(folderName = folderName, downloadedBytes = 0L, speedBytesPerSec = 0L),
                    forceWebViewDom = false,
                    bypassCache = false
                )
                val resetTask = refreshed.copy(
                    folderName = folderName,
                    status = DownloadStatus.QUEUED.name,
                    downloadedBytes = 0L,
                    speedBytesPerSec = 0L,
                    errorMessage = null
                )
                downloadDao.upsertTask(resetTask)
                startDownloadWorker(resetTask.id)
                count++
            }
            onComplete?.invoke(count)
        }
    }

    fun cleanAlbumFolderUnfinished(folderName: String) {
        repositoryScope.launch {
            val tasks = downloadDao.getTasksByFolderNameOnce(folderName)
            for (task in tasks) {
                if (task.status != DownloadStatus.COMPLETED.name) {
                    activeJobs[task.id]?.cancelAndJoin()
                    task.tempFilePath?.let { runCatching { File(it).delete() } }
                    cancelNotification(task.id)
                    downloadDao.deleteTaskById(task.id)
                }
            }
        }
    }

    /**
     * Suspends until all tasks for a specific album folder are completed or failed,
     * allowing AlbumBatchScraperEngine to process albums sequentially.
     */
    suspend fun awaitAlbumTasksCompletion(folderName: String, timeoutMs: Long = 180_000L): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            val allTasks = downloadDao.getTasksByFolderNameOnce(folderName)
            if (allTasks.isEmpty()) return true
            // Filter out any HPT/protected SEO thumbnail tasks that should not block completion
            val realTasks = allTasks.filterNot { com.example.domain.usecase.UrlMediaUtils.isThumbnailOrHptUrl(it.url) }
            if (realTasks.isEmpty()) return true
            val hasActive = realTasks.any {
                it.status == DownloadStatus.DOWNLOADING.name || it.status == DownloadStatus.QUEUED.name
            }
            if (!hasActive) {
                return true
            }
            kotlinx.coroutines.delay(1000L)
        }
        return false
    }

    // ================= SOURCE ALBUM REFRESH & URL RE-RESOLUTION =================

    private suspend fun resolveSourceAlbumUrlForTask(task: DownloadTaskEntity): String? {
        val directReferer = task.refererUrl?.trim()?.takeIf { it.isNotEmpty() }
        if (directReferer != null) return directReferer

        val albumRecord = downloadDao.getDownloadedAlbumByFolderName(task.folderName)
        if (albumRecord != null && albumRecord.albumUrl.isNotBlank()) {
            return albumRecord.albumUrl
        }

        // Check if any sibling task in the same folder has a non-blank refererUrl
        val siblings = downloadDao.getTasksByFolderNameOnce(task.folderName)
        return siblings.firstNotNullOfOrNull { it.refererUrl?.trim()?.takeIf { ref -> ref.isNotEmpty() } }
    }

    private suspend fun fetchFreshAlbumItems(
        albumPageUrl: String,
        forceWebViewDom: Boolean = false,
        bypassCache: Boolean = false
    ): List<DetectedMediaItem> {
        val normalizedKey = albumPageUrl.trim()
        val mutex = albumRefreshLocks.getOrPut(normalizedKey) { kotlinx.coroutines.sync.Mutex() }
        return mutex.withLock {
            val now = System.currentTimeMillis()
            val cached = albumRefreshCache[normalizedKey]
            if (!bypassCache && !forceWebViewDom && cached != null && (now - cached.first) < 25_000L) {
                return@withLock cached.second
            }

            val htmlOutcome = if (normalizedKey.startsWith("pulsegrab://") ||
                normalizedKey.contains("showcase.pulsegrab.local")
            ) {
                val html = if (normalizedKey.contains("/album/")) {
                    com.example.data.scanner.AlbumBatchScraperEngine.buildShowcaseAlbumHtml(normalizedKey)
                } else {
                    val pageNum = Regex("""page-(\d+)""").find(normalizedKey)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
                    com.example.data.scanner.WebMediaScannerEngine.buildShowcaseHtml(pageNum)
                }
                com.example.data.scanner.HtmlFetchOutcome(html = html, statusCode = 200)
            } else {
                com.example.data.scanner.BrowserHttpBridge.fetchHtmlResilient(
                    url = normalizedKey,
                    refererUrl = com.example.data.scanner.BrowserHttpBridge.getOriginUrl(normalizedKey),
                    allowWebViewFallback = true,
                    forceWebViewDom = forceWebViewDom
                )
            }

            val html = htmlOutcome.html ?: return@withLock emptyList()

            val combined = LinkedHashMap<String, DetectedMediaItem>()
            val albumParsed = com.example.data.scanner.AlbumBatchScraperEngine.parseAlbumMediaFromHtml(
                albumUrl = normalizedKey,
                htmlText = html,
                strictJpgMp4Only = false
            )
            for (item in albumParsed.mediaItems) {
                combined[item.normalizedUrl] = item
            }

            val deepExtracted = com.example.data.scanner.DeepScanEngine.extractMediaFromHtml(
                html = html,
                baseUrl = normalizedKey,
                crawlDepth = 1,
                extensionFilterConfig = com.example.domain.model.ExtensionFilterConfig()
            )
            for (item in deepExtracted) {
                combined.putIfAbsent(item.normalizedUrl, item)
            }

            val freshList = combined.values.filterNot {
                com.example.domain.usecase.UrlMediaUtils.isThumbnailOrHptUrl(it.url) ||
                com.example.domain.usecase.UrlMediaUtils.isThumbnailOrHptUrl(it.normalizedUrl)
            }
            if (freshList.isNotEmpty()) {
                albumRefreshCache[normalizedKey] = System.currentTimeMillis() to freshList
            }
            freshList
        }
    }

    private suspend fun refreshTaskFromSourceAlbum(
        task: DownloadTaskEntity,
        forceWebViewDom: Boolean = false,
        bypassCache: Boolean = false
    ): DownloadTaskEntity {
        val sourceAlbumUrl = resolveSourceAlbumUrlForTask(task) ?: return task
        val freshItems = runCatching {
            fetchFreshAlbumItems(
                albumPageUrl = sourceAlbumUrl,
                forceWebViewDom = forceWebViewDom,
                bypassCache = bypassCache
            )
        }.getOrDefault(emptyList())

        if (freshItems.isEmpty()) {
            return if (task.refererUrl.isNullOrBlank()) {
                task.copy(refererUrl = sourceAlbumUrl)
            } else {
                task
            }
        }

        val taskUrlPathName = task.url.substringBefore('?').substringAfterLast('/').lowercase(Locale.US)
        val taskFileNameLower = task.fileName.lowercase(Locale.US)
        val taskStem = taskFileNameLower.substringBeforeLast('.')

        val matchedItem = freshItems.firstOrNull { candidate ->
            val candPathName = candidate.url.substringBefore('?').substringAfterLast('/').lowercase(Locale.US)
            candPathName.isNotEmpty() && (candPathName == taskUrlPathName || candPathName == taskFileNameLower)
        } ?: freshItems.firstOrNull { candidate ->
            candidate.url.substringBefore('?').equals(task.url.substringBefore('?'), ignoreCase = true)
        } ?: freshItems.firstOrNull { candidate ->
            val candStem = candidate.title.lowercase(Locale.US).replace(Regex("[^a-z0-9._-]"), "_")
            candStem.isNotEmpty() && taskStem.contains(candStem) &&
                candidate.mediaType.name == task.mediaType
        }

        val newUrl = matchedItem?.url ?: task.url
        val newNormalizedUrl = matchedItem?.normalizedUrl ?: task.normalizedUrl
        val updatedEntity = task.copy(
            url = newUrl,
            normalizedUrl = newNormalizedUrl,
            refererUrl = sourceAlbumUrl
        )
        downloadDao.updateTaskForSmartRedownload(
            id = updatedEntity.id,
            url = updatedEntity.url,
            normalizedUrl = updatedEntity.normalizedUrl,
            refererUrl = updatedEntity.refererUrl,
            folderName = updatedEntity.folderName,
            status = updatedEntity.status,
            errorMessage = updatedEntity.errorMessage
        )
        return updatedEntity
    }

    private fun startDownloadWorker(taskId: String) {
        val job = repositoryScope.launch(start = CoroutineStart.LAZY) {
            try {
                concurrencySemaphore.withPermit { executeDownload(taskId) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                downloadDao.updateStatusAndError(taskId, DownloadStatus.FAILED.name,
                    error.localizedMessage ?: "Download or storage failed. Retry when storage is available.")
                cancelNotification(taskId)
                DiagnosticLogManager.error(tag = "MediaDownload", message = "Worker failed", throwable = error)
            }
        }
        if (activeJobs.putIfAbsent(taskId, job) != null) {
            job.cancel()
            return
        }
        job.invokeOnCompletion { activeJobs.remove(taskId, job) }
        job.start()
    }

    private suspend fun executeDownload(taskId: String) {
        val rawEntity = downloadDao.getTaskById(taskId) ?: return
        val unifiedInitialFolder = resolveUnifiedFolderForSingleTask(rawEntity)
        var currentEntity = if (rawEntity.folderName != unifiedInitialFolder) {
            downloadDao.updateTaskFolderAndUri(taskId, unifiedInitialFolder, rawEntity.localUri)
            rawEntity.copy(folderName = unifiedInitialFolder)
        } else {
            rawEntity
        }

        // Requirement 5: Do not attempt to bypass Cloudflare for HPT URLs; they are intentionally protected SEO assets. Just skip them cleanly.
        if (com.example.domain.usecase.UrlMediaUtils.isThumbnailOrHptUrl(currentEntity.url)) {
            DiagnosticLogManager.log(
                category = LogCategory.HTTP_NETWORK,
                severity = LogSeverity.INFO,
                tag = "DownloadEngine",
                url = currentEntity.url,
                message = "Cleanly skipped protected SEO thumbnail / HPT asset (${currentEntity.fileName}) without bypass attempts."
            )
            downloadDao.updateProgress(
                id = taskId,
                status = DownloadStatus.CANCELLED.name,
                downloadedBytes = 0L,
                totalBytes = 0L,
                speedBytesPerSec = 0L
            )
            return
        }

        val tempFile = File(currentEntity.tempFilePath ?: File(getTempDir(), "$taskId.part").absolutePath)
        tempFile.parentFile?.mkdirs()

        val mediaTypeEnum = runCatching { MediaType.valueOf(currentEntity.mediaType) }
            .getOrDefault(MediaType.IMAGE)

        var lastException: Exception? = null

        for (attempt in 1..MAX_AUTO_RECOVERY_ATTEMPTS) {
            if (!kotlinx.coroutines.currentCoroutineContext().isActive) {
                throw CancellationException("Download paused or cancelled")
            }

            // Only resume from .part on Attempt 1 if it wasn't a retry after failure; on recovery attempts 2..3 wipe .part
            if (attempt > 1) {
                runCatching { if (tempFile.exists()) tempFile.delete() }
            }
            val existingBytes = if (attempt == 1 && tempFile.exists()) tempFile.length() else 0L

            try {
                downloadDao.updateProgress(
                    id = taskId,
                    status = DownloadStatus.DOWNLOADING.name,
                    downloadedBytes = existingBytes,
                    totalBytes = currentEntity.totalBytes,
                    speedBytesPerSec = 0L
                )
                postProgressNotification(currentEntity.fileName, taskId, existingBytes, currentEntity.totalBytes, 0L)

                com.example.data.scanner.BrowserHttpBridge.executeMediaDownloadWithRetry(
                    mediaUrl = currentEntity.url,
                    refererPageUrl = currentEntity.refererUrl,
                    existingBytes = existingBytes,
                    mediaType = mediaTypeEnum
                ).use { response ->
                    if (!response.isSuccessful && response.code != 206) {
                        throw IllegalStateException("HTTP ${response.code}: ${response.message}")
                    }

                    val contentType = response.header("Content-Type").orEmpty().lowercase(Locale.US)
                    if (contentType.contains("text/html")) {
                        throw IllegalStateException("HTTP 403 Soft-Block (Server returned HTML instead of media)")
                    }

                    val body = response.body ?: throw IllegalStateException("Empty response body")
                    val transfer = DownloadResponseValidator.validate(
                        response.code, existingBytes, body.contentLength(), response.header("Content-Range")
                    )
                    val isResumed = transfer.append
                    val totalBytes = transfer.totalBytes

                    var bytesWritten = if (isResumed) existingBytes else 0L
                    var windowStartMs = System.currentTimeMillis()
                    var windowBytes = 0L
                    var currentSpeed = 0L

                    FileOutputStream(tempFile, isResumed).use { output ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(16 * 1024)
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                if (!kotlinx.coroutines.currentCoroutineContext().isActive) {
                                    throw CancellationException("Download paused or cancelled")
                                }
                                output.write(buffer, 0, read)
                                bytesWritten += read
                                windowBytes += read

                                val now = System.currentTimeMillis()
                                val elapsedMs = now - windowStartMs
                                if (elapsedMs >= 350L) {
                                    currentSpeed = (windowBytes * 1000L) / elapsedMs
                                    windowStartMs = now
                                    windowBytes = 0L

                                    downloadDao.updateProgress(
                                        id = taskId,
                                        status = DownloadStatus.DOWNLOADING.name,
                                        downloadedBytes = bytesWritten,
                                        totalBytes = totalBytes,
                                        speedBytesPerSec = currentSpeed
                                    )
                                    postProgressNotification(
                                        fileName = currentEntity.fileName,
                                        taskId = taskId,
                                        downloaded = bytesWritten,
                                        total = totalBytes,
                                        speed = currentSpeed
                                    )
                                }
                            }
                            output.flush()
                        }
                    }

                    DownloadResponseValidator.requireComplete(bytesWritten, transfer)
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()

                    // Always resolve the latest unified folder right when saving so even in-flight/processing tasks merge cleanly
                    val latestTask = downloadDao.getTaskById(taskId) ?: currentEntity
                    val finalUnifiedFolder = resolveUnifiedFolderForSingleTask(latestTask)

                    val savedUri = saveFileToMediaStore(
                        sourceFile = tempFile,
                        fileName = currentEntity.fileName,
                        mimeType = currentEntity.mimeType,
                        mediaType = mediaTypeEnum,
                        subfolderName = finalUnifiedFolder,
                        destinationTreeUri = currentEntity.destinationTreeUri
                    ) ?: throw java.io.IOException("Could not save downloaded file. Check available storage and folder access.")
                    tempFile.delete()

                    if (latestTask.folderName != finalUnifiedFolder) {
                        downloadDao.updateTaskFolderAndUri(taskId, finalUnifiedFolder, savedUri?.toString())
                    }

                    downloadDao.markCompleted(
                        id = taskId,
                        status = DownloadStatus.COMPLETED.name,
                        downloadedBytes = bytesWritten,
                        totalBytes = if (totalBytes > 0) totalBytes else bytesWritten,
                        localUri = savedUri?.toString(),
                        completedAt = System.currentTimeMillis()
                    )
                    postCompletedNotification(currentEntity.fileName, taskId, bytesWritten)
                    return
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                lastException = e
                runCatching { if (tempFile.exists()) tempFile.delete() }

                if (attempt < MAX_AUTO_RECOVERY_ATTEMPTS) {
                    val nextAttempt = attempt + 1
                    downloadDao.updateStatusAndError(
                        id = taskId,
                        status = DownloadStatus.DOWNLOADING.name,
                        errorMessage = "Auto-recovering ($nextAttempt/$MAX_AUTO_RECOVERY_ATTEMPTS): Refreshing album cookies & link…"
                    )
                    val backoffMs = if (attempt == 1) 1_200L else 2_500L
                    kotlinx.coroutines.delay(backoffMs)

                    // Refresh source album page & cookies (and force WebView DOM warmup on final recovery attempt)
                    currentEntity = refreshTaskFromSourceAlbum(
                        task = currentEntity.copy(downloadedBytes = 0L),
                        forceWebViewDom = (nextAttempt >= MAX_AUTO_RECOVERY_ATTEMPTS),
                        bypassCache = (nextAttempt >= MAX_AUTO_RECOVERY_ATTEMPTS)
                    )
                }
            }
        }

        // All standard recovery attempts exhausted:
        // For stubborn JPG / image files, attempt in-page WebView DOM blob fetch before failing
        if (currentEntity.mediaType == MediaType.IMAGE.name) {
            DiagnosticLogManager.log(
                category = LogCategory.RECOVERY,
                severity = LogSeverity.INFO,
                tag = "MediaDownload",
                message = "Attempting in-page WebView fetch() fallback for ${currentEntity.fileName}",
                url = currentEntity.url
            )
            val webViewBytes = BrowserHttpBridge.fetchImageBytesViaWebView(currentEntity.url, currentEntity.refererUrl)
            if (webViewBytes != null && webViewBytes.isNotEmpty()) {
                val written = runCatching {
                    FileOutputStream(tempFile).use { it.write(webViewBytes) }
                    true
                }.getOrDefault(false)

                if (written && tempFile.exists() && tempFile.length() > 0L) {
                    val latestTask = downloadDao.getTaskById(taskId) ?: currentEntity
                    val finalUnifiedFolder = resolveUnifiedFolderForSingleTask(latestTask)
                    val savedUri = saveFileToMediaStore(
                        sourceFile = tempFile,
                        fileName = currentEntity.fileName,
                        mimeType = currentEntity.mimeType,
                        mediaType = mediaTypeEnum,
                        subfolderName = finalUnifiedFolder,
                        destinationTreeUri = currentEntity.destinationTreeUri
                    )
                    if (savedUri != null) {
                        tempFile.delete()
                        val size = webViewBytes.size.toLong()
                        downloadDao.markCompleted(
                            id = taskId,
                            status = DownloadStatus.COMPLETED.name,
                            downloadedBytes = size,
                            totalBytes = size,
                            localUri = savedUri.toString(),
                            completedAt = System.currentTimeMillis()
                        )
                        postCompletedNotification(currentEntity.fileName, taskId, size)
                        DiagnosticLogManager.log(
                            category = LogCategory.RECOVERY,
                            severity = LogSeverity.INFO,
                            tag = "MediaDownload",
                            message = "Successfully recovered and saved ${currentEntity.fileName} via WebView blob ($size bytes)",
                            url = currentEntity.url
                        )
                        return
                    }
                }
            }
        }

        val errMsg = "${lastException?.localizedMessage ?: "Transfer failed"} (Tap Re-Download Failed to refresh album)"
        DiagnosticLogManager.error(
            tag = "MediaDownload",
            message = "Task $taskId (${currentEntity.fileName}) failed: $errMsg",
            url = currentEntity.url,
            throwable = lastException
        )

        downloadDao.updateStatusAndError(
            id = taskId,
            status = DownloadStatus.FAILED.name,
            errorMessage = errMsg
        )
        cancelNotification(taskId)
    }

    /**
     * Saves BOTH pictures and videos for an album into ONE unified folder inside `Download/PulseGrab/<Album_Title>/`
     * (`Environment.DIRECTORY_DOWNLOADS/PulseGrab/<Album_Title>/` or `<SAF_Custom_Root>/<Album_Title>/`).
     * Never splits or moves files into `DCIM`. Also runs `MediaScannerConnection.scanFile` on the saved file
     * inside `Download/PulseGrab/<Album_Title>/` so Android indexes it in-place for Gallery & Telegram sharing.
     */
    private fun copyFileToContentUri(sourceFile: File, uri: Uri) {
        val output = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw java.io.IOException("Storage provider did not open an output stream")
        output.use { out -> FileInputStream(sourceFile).use { it.copyTo(out, 16 * 1024) } }
    }

    private fun saveFileToMediaStore(
        sourceFile: File,
        fileName: String,
        mimeType: String,
        mediaType: MediaType,
        subfolderName: String = "PulseGrab",
        destinationTreeUri: String? = null
    ): Uri? {
        val resolver = context.contentResolver
        val safeSubfolder = subfolderName.trim()
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .ifBlank { "PulseGrab" }

        val isVideo = mediaType == MediaType.VIDEO ||
            mimeType.startsWith("video/", ignoreCase = true) ||
            fileName.endsWith(".mp4", ignoreCase = true) ||
            fileName.endsWith(".webm", ignoreCase = true) ||
            fileName.endsWith(".mov", ignoreCase = true)
        val resolvedMime = when {
            isVideo && !mimeType.startsWith("video/", ignoreCase = true) -> "video/mp4"
            !isVideo && !mimeType.startsWith("image/", ignoreCase = true) -> {
                when {
                    fileName.endsWith(".png", ignoreCase = true) -> "image/png"
                    fileName.endsWith(".webp", ignoreCase = true) -> "image/webp"
                    fileName.endsWith(".gif", ignoreCase = true) -> "image/gif"
                    else -> "image/jpeg"
                }
            }
            else -> mimeType
        }

        // 1. SAF Custom Tree Directory (if user chose custom device folder)
        if (!destinationTreeUri.isNullOrBlank()) {
            try {
                val treeUri = Uri.parse(destinationTreeUri)
                val rootDocId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
                val rootDocUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId)
                val targetDirUri = if (safeSubfolder.isNotBlank()) {
                    findOrCreateSafSubfolder(resolver, treeUri, rootDocId, rootDocUri, safeSubfolder)
                        ?: throw java.io.IOException("Could not create album folder")
                } else {
                    rootDocUri
                }
                val createdUri = android.provider.DocumentsContract.createDocument(resolver, targetDirUri, resolvedMime, fileName)
                if (createdUri != null) {
                    try {
                        copyFileToContentUri(sourceFile, createdUri)
                        return createdUri
                    } catch (error: Exception) {
                        runCatching { android.provider.DocumentsContract.deleteDocument(resolver, createdUri) }
                        throw error
                    }
                }
            } catch (error: Exception) {
                DiagnosticLogManager.error(tag = "MediaStorage", message = "Selected folder is not writable", throwable = error)
            }
            return null
        }

        // 2. Primary Unified Download Directory (`Download/PulseGrab/<safeSubfolder>/`)
        // Always stores both pictures and videos in Download/PulseGrab/<safeSubfolder>/ as expected by the user.
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val unifiedRelativePath = "${Environment.DIRECTORY_DOWNLOADS}/PulseGrab/$safeSubfolder"
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

                findExistingUnifiedDownloadUri(resolver, collection, unifiedRelativePath, fileName)?.let { existingUri ->
                    val wrote = runCatching {
                        resolver.openOutputStream(existingUri, "wt")?.use { outStream ->
                            FileInputStream(sourceFile).use { inStream ->
                                inStream.copyTo(outStream, bufferSize = 16 * 1024)
                            }
                            true
                        } ?: false
                    }.getOrDefault(false)
                    if (wrote) {
                        scanDownloadFileInPlace(safeSubfolder, fileName, resolvedMime)
                        return existingUri
                    }
                }

                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, resolvedMime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, unifiedRelativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val targetUri = resolver.insert(collection, values)
                if (targetUri != null) {
                    try {
                        copyFileToContentUri(sourceFile, targetUri)
                        val completeValues = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                        check(resolver.update(targetUri, completeValues, null, null) > 0) { "Could not publish saved file" }
                    } catch (error: Exception) {
                        runCatching { resolver.delete(targetUri, null, null) }
                        throw error
                    }
                    scanDownloadFileInPlace(safeSubfolder, fileName, resolvedMime)
                    return targetUri
                }
            }

            // Fallback unified single folder on public Downloads or external app Downloads
            val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val publicAlbumDir = File(publicDownloads, "PulseGrab/$safeSubfolder")
            if (runCatching { publicAlbumDir.mkdirs() || publicAlbumDir.exists() }.getOrDefault(false)) {
                val publicDest = File(publicAlbumDir, fileName)
                val wrotePublic = runCatching {
                    sourceFile.copyTo(publicDest, overwrite = true)
                    publicDest.exists() && publicDest.length() > 0L
                }.getOrDefault(false)
                if (wrotePublic) {
                    scanDownloadFileInPlace(safeSubfolder, fileName, resolvedMime)
                    return toShareableFileProviderUri(publicDest)
                }
            }

            val baseDownloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(context.filesDir, "downloads")
            val unifiedAlbumDir = File(baseDownloadsDir, "PulseGrab/$safeSubfolder").apply { mkdirs() }
            val dest = File(unifiedAlbumDir, fileName)
            sourceFile.copyTo(dest, overwrite = true)
            runCatching {
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(dest.absolutePath),
                    arrayOf(resolvedMime),
                    null
                )
            }
            toShareableFileProviderUri(dest)
        } catch (_: Exception) {
            // Final fallback to internal unified album directory
            try {
                val exportedDir = File(context.filesDir, "downloads/PulseGrab/$safeSubfolder").apply { mkdirs() }
                val dest = File(exportedDir, fileName)
                sourceFile.copyTo(dest, overwrite = true)
                toShareableFileProviderUri(dest)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun scanDownloadFileInPlace(safeSubfolder: String, fileName: String, resolvedMime: String) {
        runCatching {
            val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val publicFile = File(publicDownloads, "PulseGrab/$safeSubfolder/$fileName")
            if (publicFile.exists()) {
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(publicFile.absolutePath),
                    arrayOf(resolvedMime),
                    null
                )
            }
        }
    }

    private fun toShareableFileProviderUri(file: File): Uri {
        return runCatching {
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        }.getOrThrow()
    }

    private fun findExistingFileInDownloadFolderUri(folderName: String, fileName: String): Uri? {
        if (fileName.isBlank()) return null
        val safeFolder = com.example.domain.model.DownloadFolderConfig.sanitizeFolderName(folderName)
        val rawFolder = folderName.trim().ifBlank { safeFolder }
        val candidates = listOf(safeFolder, rawFolder).distinct()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            for (folder in candidates) {
                val relPath = "${Environment.DIRECTORY_DOWNLOADS}/PulseGrab/$folder"
                val uri = findExistingUnifiedDownloadUri(context.contentResolver, collection, relPath, fileName)
                if (uri != null && isContentUriReadable(uri)) {
                    return uri
                }
            }
        }

        val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        for (folder in candidates) {
            val publicFile = File(publicDownloads, "PulseGrab/$folder/$fileName")
            if (publicFile.exists() && publicFile.length() > 0L) {
                return toShareableFileProviderUri(publicFile)
            }
            val appDownloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            if (appDownloads != null) {
                val appFile = File(appDownloads, "PulseGrab/$folder/$fileName")
                if (appFile.exists() && appFile.length() > 0L) {
                    return toShareableFileProviderUri(appFile)
                }
            }
            val internalFile = File(context.filesDir, "downloads/PulseGrab/$folder/$fileName")
            if (internalFile.exists() && internalFile.length() > 0L) {
                return toShareableFileProviderUri(internalFile)
            }
        }
        return null
    }

    private fun isUriOrFileReadable(uri: Uri): Boolean {
        return when (uri.scheme) {
            "file" -> {
                val f = uri.path?.let { File(it) }
                f != null && f.exists() && f.length() > 0L
            }
            "content" -> isContentUriReadable(uri)
            else -> false
        }
    }

    private fun getUriOrFileSize(uri: Uri, folderName: String, fileName: String): Long {
        if (uri.scheme == "file") {
            val f = uri.path?.let { File(it) }
            if (f != null && f.exists()) return f.length()
        }
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                if (pfd.statSize > 0L) return pfd.statSize
            }
        } catch (_: Exception) {
        }
        val publicFile = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "PulseGrab/$folderName/$fileName"
        )
        if (publicFile.exists()) return publicFile.length()
        return 0L
    }

    /** Resolve only complete, readable files on IO. Never mutate task status while preparing a share. */
    suspend fun prepareGalleryMediaUrisForFolder(
        folderName: String,
        specificTaskIds: Set<String>? = null
    ): List<Uri> = withContext(Dispatchers.IO) {
        val safeFolder = com.example.domain.model.DownloadFolderConfig.sanitizeFolderName(folderName)
        downloadDao.getAllTasksOnce().asSequence().filter { task ->
            task.status == DownloadStatus.COMPLETED.name &&
                (specificTaskIds == null || task.id in specificTaskIds) &&
                (task.folderName.equals(folderName, ignoreCase = true) ||
                    com.example.domain.model.DownloadFolderConfig.sanitizeFolderName(task.folderName)
                        .equals(safeFolder, ignoreCase = true))
        }.sortedBy { it.createdAt }.mapNotNull { task ->
            resolveShareableContentUriForTask(task, safeFolder)
        }.distinct().toList()
    }

    private fun resolveShareableContentUriForTask(task: DownloadTaskEntity, safeFolder: String): Uri? {
        // Prefer the recorded URI, including SAF folders. No stale cache copies or folder-wide substitution.
        task.localUri?.let { stored ->
            val uri = runCatching { Uri.parse(stored) }.getOrNull()
            if (uri?.scheme == "content" && isContentUriReadable(uri)) return uri
            if (uri?.scheme == "file") {
                val file = uri.path?.let { File(it) }
                if (file != null && file.isFile && file.length() > 0 && file.canRead()) {
                    return runCatching { toShareableFileProviderUri(file) }.getOrNull()
                }
            }
        }
        return findExistingFileInDownloadFolderUri(safeFolder, task.fileName)?.takeIf { uri ->
            uri.scheme == "content" && isContentUriReadable(uri)
        }
    }

    /**
     * Consolidates any stray DCIM files back into `Download/PulseGrab/<Album>` and scans all folders in-place
     * inside `Download/PulseGrab/<Album>` so every folder is both in `Download/PulseGrab` and indexed by MediaScanner.
     */
    fun syncAllFoldersToGalleryBuckets(onComplete: ((Int) -> Unit)? = null) {
        repositoryScope.launch {
            syncAlbumRegistryFromTasks()
            mergeAndUnifyAllExistingAndActiveFoldersInternal()
            val allTasks = downloadDao.getAllTasksOnce()
            val folders = allTasks
                .filter { it.status == DownloadStatus.COMPLETED.name }
                .map { it.folderName.ifBlank { "PulseGrab" } }
                .distinct()
            var totalIndexed = 0
            for (folder in folders) {
                val uris = prepareGalleryMediaUrisForFolder(folder)
                totalIndexed += uris.size
            }
            onComplete?.invoke(totalIndexed)
        }
    }

    private fun findExistingUnifiedDownloadUri(
        resolver: android.content.ContentResolver,
        collection: Uri,
        relativePath: String,
        fileName: String
    ): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            val normalizedRelPath = if (relativePath.endsWith("/")) relativePath else "$relativePath/"
            val projection = arrayOf(MediaStore.MediaColumns._ID)
            val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(normalizedRelPath, fileName)
            resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    return android.content.ContentUris.withAppendedId(collection, id)
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun findOrCreateSafSubfolder(
        resolver: android.content.ContentResolver,
        treeUri: Uri,
        parentDocId: String,
        parentDocUri: Uri,
        subfolderName: String
    ): Uri? {
        return try {
            val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
            val projection = arrayOf(
                android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE
            )
            resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val docId = if (idCol >= 0) cursor.getString(idCol) else continue
                    val name = if (nameCol >= 0) cursor.getString(nameCol) else ""
                    val mime = if (mimeCol >= 0) cursor.getString(mimeCol) else ""
                    if (mime == android.provider.DocumentsContract.Document.MIME_TYPE_DIR &&
                        name.equals(subfolderName, ignoreCase = true)
                    ) {
                        return android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    }
                }
            }
            android.provider.DocumentsContract.createDocument(
                resolver,
                parentDocUri,
                android.provider.DocumentsContract.Document.MIME_TYPE_DIR,
                subfolderName
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun getTempDir(): File {
        return File(context.cacheDir, "partial_downloads").apply { mkdirs() }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "PulseGrab Media Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time transfer progress for media downloads"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun postProgressNotification(
        fileName: String,
        taskId: String,
        downloaded: Long,
        total: Long,
        speed: Long
    ) {
        try {
            val progressMax = 100
            val progressVal = if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 100) else 0
            val contentText = buildString {
                append(FormatUtils.formatBytes(downloaded))
                if (total > 0) append(" / ${FormatUtils.formatBytes(total)}")
                if (speed > 0) append(" • ${FormatUtils.formatSpeed(speed)}")
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(fileName)
                .setContentText(contentText)
                .setProgress(progressMax, progressVal, total <= 0L)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()

            notificationManager.notify(taskId.hashCode(), notification)
        } catch (_: SecurityException) {
        }
    }

    private fun postCompletedNotification(fileName: String, taskId: String, sizeBytes: Long) {
        try {
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Saved $fileName")
                .setContentText("Completed • ${FormatUtils.formatBytes(sizeBytes)}")
                .setAutoCancel(true)
                .build()

            notificationManager.notify(taskId.hashCode(), notification)
        } catch (_: SecurityException) {
        }
    }

    private fun cancelNotification(taskId: String) {
        try {
            notificationManager.cancel(taskId.hashCode())
        } catch (_: Exception) {
        }
    }

    private fun isContentUriReadable(uri: Uri): Boolean {
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                if (pfd.statSize > 0L) return true
            }
        } catch (_: Exception) {
        }
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.read() != -1
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun doesTaskFileExist(task: DownloadTaskEntity): Boolean {
        val uriString = task.localUri
        if (!uriString.isNullOrBlank()) {
            val uri = runCatching { Uri.parse(uriString) }.getOrNull()
            if (uri != null) {
                if (uri.scheme == "file") {
                    val path = uri.path
                    if (path != null && File(path).exists() && File(path).length() > 0L) {
                        return true
                    }
                } else if (uri.scheme == "content") {
                    if (isContentUriReadable(uri)) return true
                }
            }
        }

        val safeFolder = com.example.domain.model.DownloadFolderConfig.sanitizeFolderName(task.folderName)
        val rawFolder = task.folderName.trim().ifBlank { safeFolder }
        val candidateFolders = listOf(safeFolder, rawFolder).distinct()
        val fileName = task.fileName

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collections = listOf(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            )
            for (folder in candidateFolders) {
                val relPaths = listOf(
                    "${Environment.DIRECTORY_DCIM}/PulseGrab/$folder",
                    "${Environment.DIRECTORY_DOWNLOADS}/PulseGrab/$folder"
                )
                for (collection in collections) {
                    for (relPath in relPaths) {
                        val existing = findExistingUnifiedDownloadUri(context.contentResolver, collection, relPath, fileName)
                        if (existing != null && isContentUriReadable(existing)) {
                            return true
                        }
                    }
                }
            }
        }

        for (folder in candidateFolders) {
            val publicDcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            val dcimFile = File(publicDcim, "PulseGrab/$folder/$fileName")
            if (dcimFile.exists() && dcimFile.length() > 0L) return true

            val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val publicFile = File(publicDownloads, "PulseGrab/$folder/$fileName")
            if (publicFile.exists() && publicFile.length() > 0L) return true

            val appDownloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            if (appDownloads != null) {
                val appFile = File(appDownloads, "PulseGrab/$folder/$fileName")
                if (appFile.exists() && appFile.length() > 0L) return true
            }

            val internalFile = File(context.filesDir, "downloads/PulseGrab/$folder/$fileName")
            if (internalFile.exists() && internalFile.length() > 0L) return true
        }

        return false
    }

    suspend fun doesAlbumFolderOrFilesExist(folderName: String): Boolean {
        val safeFolder = com.example.domain.model.DownloadFolderConfig.sanitizeFolderName(folderName)
        val rawFolder = folderName.trim().ifBlank { safeFolder }
        val candidateFolders = listOf(safeFolder, rawFolder).distinct()

        for (folder in candidateFolders) {
            val tasks = runCatching { downloadDao.getTasksByFolderNameOnce(folder) }.getOrDefault(emptyList())
            val completedTasks = tasks.filter { it.status == DownloadStatus.COMPLETED.name }
            if (completedTasks.isNotEmpty() && completedTasks.any { doesTaskFileExist(it) }) {
                return true
            }
        }

        for (folder in candidateFolders) {
            val dcimDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "PulseGrab/$folder")
            if (dcimDir.exists() && (dcimDir.listFiles()?.any { it.isFile && it.length() > 0L } == true)) {
                return true
            }

            val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "PulseGrab/$folder")
            if (publicDir.exists() && (publicDir.listFiles()?.any { it.isFile && it.length() > 0L } == true)) {
                return true
            }

            val appDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { File(it, "PulseGrab/$folder") }
            if (appDir != null && appDir.exists() && (appDir.listFiles()?.any { it.isFile && it.length() > 0L } == true)) {
                return true
            }

            val internalDir = File(context.filesDir, "downloads/PulseGrab/$folder")
            if (internalDir.exists() && (internalDir.listFiles()?.any { it.isFile && it.length() > 0L } == true)) {
                return true
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collections = listOf(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            )
            val projection = arrayOf(MediaStore.MediaColumns._ID)
            val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
            for (folder in candidateFolders) {
                val relPaths = listOf(
                    "${Environment.DIRECTORY_DCIM}/PulseGrab/$folder/",
                    "${Environment.DIRECTORY_DOWNLOADS}/PulseGrab/$folder/"
                )
                for (collection in collections) {
                    for (relPath in relPaths) {
                        try {
                            context.contentResolver.query(collection, projection, selection, arrayOf(relPath), null)?.use { cursor ->
                                if (cursor.moveToFirst()) return true
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }

        return false
    }

    private suspend fun verifyAndRedownloadMissingTasksInternal(): Int {
        // First consolidate any DCIM/split files back into Download/PulseGrab and heal existing files on disk
        mergeAndUnifyAllExistingAndActiveFoldersInternal()

        val allTasks = downloadDao.getAllTasksOnce()
        var redownloadedCount = 0
        for (task in allTasks) {
            if (task.status == DownloadStatus.COMPLETED.name &&
                !task.url.startsWith("file://") &&
                !com.example.domain.usecase.UrlMediaUtils.isThumbnailOrHptUrl(task.url)
            ) {
                val fileExists = doesTaskFileExist(task)
                if (!fileExists) {
                    task.tempFilePath?.let { runCatching { File(it).delete() } }
                    val reset = task.copy(
                        status = DownloadStatus.QUEUED.name,
                        downloadedBytes = 0L,
                        speedBytesPerSec = 0L,
                        localUri = null,
                        errorMessage = "File missing from disk — re-downloading"
                    )
                    downloadDao.upsertTask(reset)
                    startDownloadWorker(reset.id)
                    redownloadedCount++
                }
            }
        }
        return redownloadedCount
    }

    fun verifyAndRedownloadMissingTasks(onComplete: ((Int) -> Unit)? = null) {
        repositoryScope.launch {
            syncAlbumRegistryFromTasks()
            val taskCount = verifyAndRedownloadMissingTasksInternal()
            val albumScrapeCount = verifyAndRedownloadMissingAlbumsInternal()
            onComplete?.invoke(taskCount + albumScrapeCount)
        }
    }

    /**
     * Verifies all recorded albums in `downloaded_albums` on disk.
     * CRITICAL: NEVER deletes `DownloadedAlbumEntity` history records!
     * If an album's folder or files are missing on disk, it keeps the album record and
     * automatically re-scrapes `album.albumUrl` and re-enqueues the missing media files.
     */
    private suspend fun verifyAndRedownloadMissingAlbumsInternal(): Int {
        val albums = downloadDao.getAllDownloadedAlbumsOnce()
        var requeuedFilesCount = 0
        for (album in albums) {
            val exists = doesAlbumFolderOrFilesExist(album.folderName)
            if (!exists && album.albumUrl.isNotBlank()) {
                val count = redownloadAlbumFromUrlInternal(
                    albumUrl = album.albumUrl,
                    folderName = album.folderName,
                    albumTitle = album.albumTitle
                )
                requeuedFilesCount += count
            }
        }
        return requeuedFilesCount
    }

    fun verifyAndRedownloadMissingAlbums(onComplete: ((Int) -> Unit)? = null) {
        repositoryScope.launch {
            syncAlbumRegistryFromTasks()
            val taskCount = verifyAndRedownloadMissingTasksInternal()
            val albumCount = verifyAndRedownloadMissingAlbumsInternal()
            onComplete?.invoke(taskCount + albumCount)
        }
    }

    /**
     * Re-downloads an album from its saved source URL (`albumUrl`) into `folderName` without
     * losing its history record. Re-queues any missing/failed tasks in Room and scrapes the
     * album page to enqueue any missing files.
     */
    fun redownloadAlbumFromUrl(
        albumUrl: String,
        folderName: String,
        albumTitle: String = folderName.replace('_', ' '),
        onComplete: ((Int) -> Unit)? = null
    ) {
        repositoryScope.launch {
            val count = redownloadAlbumFromUrlInternal(albumUrl, folderName, albumTitle)
            onComplete?.invoke(count)
        }
    }

    private suspend fun redownloadAlbumFromUrlInternal(
        albumUrl: String,
        folderName: String,
        albumTitle: String
    ): Int {
        val safeFolder = com.example.domain.model.DownloadFolderConfig.sanitizeFolderName(
            folderName.ifBlank { albumTitle }
        )
        var totalQueued = 0

        // 1. Check existing tasks in Room for this folder or refererUrl and re-queue any that are missing on disk or failed
        val allTasks = downloadDao.getAllTasksOnce()
        val matchingTasks = allTasks.filter {
            !com.example.domain.usecase.UrlMediaUtils.isThumbnailOrHptUrl(it.url) &&
                (it.folderName.equals(folderName, ignoreCase = true) ||
                    com.example.domain.model.DownloadFolderConfig.sanitizeFolderName(it.folderName)
                        .equals(safeFolder, ignoreCase = true) ||
                    (albumUrl.isNotBlank() && it.refererUrl.equals(albumUrl, ignoreCase = true)))
        }

        for (task in matchingTasks) {
            val fileExists = task.status == DownloadStatus.COMPLETED.name && doesTaskFileExist(task)
            val isRunning = (task.status == DownloadStatus.DOWNLOADING.name || task.status == DownloadStatus.QUEUED.name) &&
                activeJobs.containsKey(task.id)
            if (!fileExists && !isRunning) {
                task.tempFilePath?.let { runCatching { File(it).delete() } }
                val reset = task.copy(
                    folderName = safeFolder,
                    status = DownloadStatus.QUEUED.name,
                    downloadedBytes = 0L,
                    speedBytesPerSec = 0L,
                    localUri = null,
                    errorMessage = null
                )
                downloadDao.upsertTask(reset)
                startDownloadWorker(reset.id)
                totalQueued++
            }
        }

        // 2. Also scrape the source album URL directly to discover & enqueue any files not in Room
        if (albumUrl.isNotBlank()) {
            val freshItems = runCatching {
                fetchFreshAlbumItems(
                    albumPageUrl = albumUrl,
                    forceWebViewDom = false,
                    bypassCache = true
                )
            }.getOrDefault(emptyList())

            if (freshItems.isNotEmpty()) {
                val taggedItems = freshItems.map { item ->
                    item.copy(
                        pageUrl = albumUrl,
                        pageTitle = safeFolder
                    )
                }
                val folderCfg = activeFolderConfig.copy(
                    folderMode = com.example.domain.model.DownloadFolderMode.CUSTOM_SUBFOLDER,
                    customSubfolderName = safeFolder
                )
                val newlyEnqueued = enqueueMediaItems(
                    items = taggedItems,
                    skipDuplicates = true,
                    folderConfig = folderCfg
                )
                totalQueued += newlyEnqueued
            }

            // Ensure the album record remains saved in downloaded_albums
            val canonicalUrl = com.example.domain.usecase.UrlMediaUtils.canonicalizeMediaUrl(albumUrl) ?: albumUrl
            val existingAlbum = downloadDao.getDownloadedAlbum(canonicalUrl)
            downloadDao.upsertDownloadedAlbum(
                com.example.data.local.DownloadedAlbumEntity(
                    normalizedAlbumUrl = canonicalUrl,
                    albumUrl = albumUrl,
                    albumTitle = existingAlbum?.albumTitle?.takeIf { it.isNotBlank() } ?: albumTitle,
                    folderName = safeFolder,
                    mediaCount = maxOf(existingAlbum?.mediaCount ?: 0, freshItems.size, matchingTasks.size),
                    downloadedAt = System.currentTimeMillis()
                )
            )
        }

        return totalQueued
    }

    companion object {
        private const val CHANNEL_ID = "pulsegrab_download_channel"
        private const val MAX_AUTO_RECOVERY_ATTEMPTS = 3
    }
}
