package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DownloadTaskEntity::class, DownloadedAlbumEntity::class],
    version = 4,
    exportSchema = false
)
abstract class PulseGrabDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao

    companion object {
        @Volatile
        private var INSTANCE: PulseGrabDatabase? = null

        fun getInstance(context: Context): PulseGrabDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PulseGrabDatabase::class.java,
                    "pulsegrab_downloads.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
