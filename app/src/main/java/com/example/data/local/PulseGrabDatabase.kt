package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DownloadTaskEntity::class, DownloadedAlbumEntity::class],
    version = 5,
    exportSchema = false
)
abstract class PulseGrabDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao

    companion object {
        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE download_tasks ADD COLUMN destinationTreeUri TEXT")
            }
        }

        @Volatile
        private var INSTANCE: PulseGrabDatabase? = null

        fun getInstance(context: Context): PulseGrabDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PulseGrabDatabase::class.java,
                    "pulsegrab_downloads.db"
                )
                    .addMigrations(MIGRATION_4_5)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
