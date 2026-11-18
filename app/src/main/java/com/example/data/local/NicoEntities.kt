package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.NicoVideo

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val contentId: String,
    val title: String,
    val thumbnailUrl: String,
    val uploaderName: String,
    val lengthSeconds: Int,
    val qualityLabel: String,
    val localFilePath: String,
    val progress: Int, // 0..100
    val status: String, // DOWNLOADING, COMPLETED, FAILED
    val fileSizeBytes: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toNicoVideo(): NicoVideo = NicoVideo(
        contentId = contentId,
        title = title,
        thumbnailUrl = thumbnailUrl,
        uploaderName = uploaderName,
        lengthSeconds = lengthSeconds,
        localFilePath = localFilePath,
        streamUrl = localFilePath
    )
}

@Entity(tableName = "watch_history")
data class HistoryEntity(
    @PrimaryKey val contentId: String,
    val title: String,
    val thumbnailUrl: String,
    val uploaderName: String,
    val viewCounter: Long,
    val commentCounter: Long,
    val lengthSeconds: Int,
    val isLive: Boolean,
    val watchedAt: Long = System.currentTimeMillis(),
    val syncedFromOfficial: Boolean = false
) {
    fun toNicoVideo(): NicoVideo = NicoVideo(
        contentId = contentId,
        title = title,
        thumbnailUrl = thumbnailUrl,
        uploaderName = uploaderName,
        viewCounter = viewCounter,
        commentCounter = commentCounter,
        lengthSeconds = lengthSeconds,
        isLive = isLive
    )
}

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val contentId: String,
    val title: String,
    val thumbnailUrl: String,
    val uploaderName: String,
    val viewCounter: Long,
    val commentCounter: Long,
    val lengthSeconds: Int,
    val isLive: Boolean,
    val mylistName: String = "默认收藏夹",
    val addedAt: Long = System.currentTimeMillis(),
    val syncedFromOfficial: Boolean = false
) {
    fun toNicoVideo(): NicoVideo = NicoVideo(
        contentId = contentId,
        title = title,
        thumbnailUrl = thumbnailUrl,
        uploaderName = uploaderName,
        viewCounter = viewCounter,
        commentCounter = commentCounter,
        lengthSeconds = lengthSeconds,
        isLive = isLive
    )
}

@Entity(tableName = "danmaku_keywords")
data class DanmakuKeywordEntity(
    @PrimaryKey val keyword: String,
    val createdAt: Long = System.currentTimeMillis()
)
