package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM download_tasks ORDER BY createdAt DESC")
    fun observeAllTasks(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: String): DownloadTaskEntity?

    @Query("SELECT * FROM download_tasks WHERE normalizedUrl = :normalizedUrl LIMIT 1")
    suspend fun getTaskByNormalizedUrl(normalizedUrl: String): DownloadTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: DownloadTaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTasks(tasks: List<DownloadTaskEntity>)

    @Query(
        """
        UPDATE download_tasks 
        SET status = :status, 
            downloadedBytes = :downloadedBytes, 
            totalBytes = :totalBytes, 
            speedBytesPerSec = :speedBytesPerSec
        WHERE id = :id
        """
    )
    suspend fun updateProgress(
        id: String,
        status: String,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBytesPerSec: Long
    )

    @Query(
        """
        UPDATE download_tasks 
        SET status = :status, 
            downloadedBytes = :downloadedBytes, 
            totalBytes = :totalBytes, 
            speedBytesPerSec = 0, 
            localUri = :localUri, 
            completedAt = :completedAt,
            errorMessage = NULL
        WHERE id = :id
        """
    )
    suspend fun markCompleted(
        id: String,
        status: String,
        downloadedBytes: Long,
        totalBytes: Long,
        localUri: String?,
        completedAt: Long
    )

    @Query(
        """
        UPDATE download_tasks 
        SET status = :status, 
            speedBytesPerSec = 0, 
            errorMessage = :errorMessage 
        WHERE id = :id
        """
    )
    suspend fun updateStatusAndError(
        id: String,
        status: String,
        errorMessage: String?
    )

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun deleteTaskById(id: String)

    @Query("DELETE FROM download_tasks WHERE status = 'COMPLETED'")
    suspend fun clearCompletedTasks()

    @Query("SELECT * FROM download_tasks")
    suspend fun getAllTasksOnce(): List<DownloadTaskEntity>

    @Query("SELECT * FROM download_tasks WHERE status != 'COMPLETED'")
    suspend fun getUnfinishedTasksOnce(): List<DownloadTaskEntity>

    @Query("SELECT * FROM download_tasks WHERE status IN ('FAILED', 'CANCELLED')")
    suspend fun getFailedTasksOnce(): List<DownloadTaskEntity>

    @Query("DELETE FROM download_tasks WHERE status != 'COMPLETED'")
    suspend fun deleteAllUnfinishedTasks()

    @Query("DELETE FROM download_tasks WHERE status IN ('FAILED', 'CANCELLED')")
    suspend fun deleteAllFailedTasks()

    @Query("DELETE FROM download_tasks")
    suspend fun deleteAllTasks()

    @Query(
        """
        UPDATE download_tasks
        SET folderName = :folderName,
            localUri = :localUri
        WHERE id = :id
        """
    )
    suspend fun updateTaskFolderAndUri(
        id: String,
        folderName: String,
        localUri: String?
    )

    @Query("SELECT * FROM download_tasks WHERE folderName = :folderName")
    suspend fun getTasksByFolderNameOnce(folderName: String): List<DownloadTaskEntity>

    @Query(
        """
        UPDATE download_tasks
        SET url = :url,
            normalizedUrl = :normalizedUrl,
            refererUrl = :refererUrl,
            folderName = :folderName,
            status = :status,
            downloadedBytes = 0,
            speedBytesPerSec = 0,
            errorMessage = :errorMessage
        WHERE id = :id
        """
    )
    suspend fun updateTaskForSmartRedownload(
        id: String,
        url: String,
        normalizedUrl: String,
        refererUrl: String?,
        folderName: String,
        status: String,
        errorMessage: String?
    )

    // ================= DOWNLOADED ALBUMS TRACKING =================

    @Query("SELECT * FROM downloaded_albums ORDER BY downloadedAt DESC")
    fun observeDownloadedAlbums(): Flow<List<DownloadedAlbumEntity>>

    @Query("SELECT * FROM downloaded_albums")
    suspend fun getAllDownloadedAlbumsOnce(): List<DownloadedAlbumEntity>

    @Query("SELECT * FROM downloaded_albums WHERE normalizedAlbumUrl = :normalizedAlbumUrl LIMIT 1")
    suspend fun getDownloadedAlbum(normalizedAlbumUrl: String): DownloadedAlbumEntity?

    @Query("SELECT * FROM downloaded_albums WHERE folderName = :folderName LIMIT 1")
    suspend fun getDownloadedAlbumByFolderName(folderName: String): DownloadedAlbumEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDownloadedAlbum(album: DownloadedAlbumEntity)

    @Query("DELETE FROM downloaded_albums WHERE normalizedAlbumUrl = :normalizedAlbumUrl")
    suspend fun deleteDownloadedAlbum(normalizedAlbumUrl: String)

    @Query("DELETE FROM downloaded_albums")
    suspend fun clearAllDownloadedAlbums()
}
