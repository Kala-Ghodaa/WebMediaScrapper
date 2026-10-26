package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.DownloadedAlbumRecord

@Entity(tableName = "downloaded_albums")
data class DownloadedAlbumEntity(
    @PrimaryKey
    val normalizedAlbumUrl: String,
    val albumUrl: String,
    val albumTitle: String,
    val folderName: String,
    val mediaCount: Int,
    val downloadedAt: Long
) {
    fun toDomain(): DownloadedAlbumRecord {
        return DownloadedAlbumRecord(
            normalizedAlbumUrl = normalizedAlbumUrl,
            albumUrl = albumUrl,
            albumTitle = albumTitle,
            folderName = folderName,
            mediaCount = mediaCount,
            downloadedAt = downloadedAt
        )
    }
}
