package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DownloadEntity::class,
        HistoryEntity::class,
        FavoriteEntity::class,
        DanmakuKeywordEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class NicoDatabase : RoomDatabase() {
    abstract fun nicoDao(): NicoDao

    companion object {
        @Volatile
        private var INSTANCE: NicoDatabase? = null

        fun getInstance(context: Context): NicoDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NicoDatabase::class.java,
                    "nicobili_database.db"
                ).fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
