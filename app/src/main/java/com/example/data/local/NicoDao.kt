package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NicoDao {
    // Downloads
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE contentId = :contentId LIMIT 1")
    suspend fun getDownload(contentId: String): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDownload(download: DownloadEntity)

    @Query("UPDATE downloads SET progress = :progress, status = :status, fileSizeBytes = :sizeBytes WHERE contentId = :contentId")
    suspend fun updateDownloadProgress(contentId: String, progress: Int, status: String, sizeBytes: Long)

    @Query("DELETE FROM downloads WHERE contentId = :contentId")
    suspend fun deleteDownload(contentId: String)

    // Watch History
    @Query("SELECT * FROM watch_history ORDER BY watchedAt DESC LIMIT 100")
    fun observeHistory(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(history: HistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistoryAll(items: List<HistoryEntity>)

    @Query("DELETE FROM watch_history")
    suspend fun clearHistory()

    // Favorites
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeFavorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE contentId = :contentId)")
    fun observeIsFavorite(contentId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavorite(favorite: FavoriteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavoritesAll(items: List<FavoriteEntity>)

    @Query("DELETE FROM favorites WHERE contentId = :contentId")
    suspend fun deleteFavorite(contentId: String)

    // Danmaku Keywords
    @Query("SELECT * FROM danmaku_keywords ORDER BY createdAt DESC")
    fun observeBlockedKeywords(): Flow<List<DanmakuKeywordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedKeyword(keyword: DanmakuKeywordEntity)

    @Query("DELETE FROM danmaku_keywords WHERE keyword = :keyword")
    suspend fun deleteBlockedKeyword(keyword: String)
}
