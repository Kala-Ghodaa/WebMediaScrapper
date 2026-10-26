package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.DownloadStatus
import com.example.domain.model.DownloadTask
import com.example.domain.model.MediaType

@Entity(tableName = "download_tasks")
data class DownloadTaskEntity(
    @PrimaryKey val id: String,
    val url: String,
    val normalizedUrl: String,
    val refererUrl: String = "",
    val fileName: String,
    val folderName: String = "PulseGrab",
    val extension: String,
    val mediaType: String,
    val mimeType: String,
    val width: Int = 0,
    val height: Int = 0,
    val status: String,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val speedBytesPerSec: Long = 0L,
    val localUri: String? = null,
    val tempFilePath: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
) {
    fun toDomain(): DownloadTask {
        return DownloadTask(
            id = id,
            url = url,
            normalizedUrl = normalizedUrl,
            refererUrl = refererUrl,
            fileName = fileName,
            folderName = folderName,
            extension = extension,
            mediaType = runCatching { MediaType.valueOf(mediaType) }.getOrDefault(MediaType.IMAGE),
            mimeType = mimeType,
            width = width,
            height = height,
            status = runCatching { DownloadStatus.valueOf(status) }.getOrDefault(DownloadStatus.QUEUED),
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            speedBytesPerSec = speedBytesPerSec,
            localUri = localUri,
            tempFilePath = tempFilePath,
            errorMessage = errorMessage,
            createdAt = createdAt,
            completedAt = completedAt
        )
    }
}
