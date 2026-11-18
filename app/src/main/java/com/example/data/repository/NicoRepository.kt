package com.example.data.repository

import android.content.Context
import android.os.Environment
import com.example.data.api.NicoApiClient
import com.example.data.api.WatchDetailResult
import com.example.data.local.DanmakuKeywordEntity
import com.example.data.local.DownloadEntity
import com.example.data.local.FavoriteEntity
import com.example.data.local.HistoryEntity
import com.example.data.local.NicoDao
import com.example.data.local.UserPreferencesRepository
import com.example.data.model.DanmakuFilterConfig
import com.example.data.model.NicoUserAccount
import com.example.data.model.NicoVideo
import com.example.data.model.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class NicoRepository(
    private val context: Context,
    private val api: NicoApiClient,
    private val dao: NicoDao,
    private val prefs: UserPreferencesRepository
) {
    val themeMode: Flow<ThemeMode> = prefs.themeModeFlow
    val sessionCookie: Flow<String> = prefs.sessionCookieFlow
    val danmakuPrefs: Flow<DanmakuFilterConfig> = prefs.danmakuPrefsFlow
    val downloads: Flow<List<DownloadEntity>> = dao.observeDownloads()
    val history: Flow<List<HistoryEntity>> = dao.observeHistory()
    val favorites: Flow<List<FavoriteEntity>> = dao.observeFavorites()
    val blockedKeywords: Flow<List<String>> = dao.observeBlockedKeywords().map { list ->
        list.map { it.keyword }
    }

    fun observeIsFavorite(contentId: String): Flow<Boolean> = dao.observeIsFavorite(contentId)

    suspend fun searchVideos(
        query: String,
        sort: String = "-viewCounter",
        startTimeGteIso: String? = null
    ): List<NicoVideo> {
        return api.searchVideos(query = query, sort = sort, startTimeGteIso = startTimeGteIso)
    }

    suspend fun fetchLivePrograms(): List<NicoVideo> {
        return api.fetchLivePrograms()
    }

    suspend fun loadWatchDetail(video: NicoVideo): WatchDetailResult {
        val cookie = prefs.sessionCookieFlow.first()
        // Check if downloaded locally first
        val local = dao.getDownload(video.contentId)
        val result = api.fetchWatchDetail(video, cookie)
        val finalVideo = if (local != null && local.status == "COMPLETED" && File(local.localFilePath).exists()) {
            result.video.copy(localFilePath = local.localFilePath)
        } else {
            result.video
        }

        // Record to watch history
        dao.upsertHistory(
            HistoryEntity(
                contentId = finalVideo.contentId,
                title = finalVideo.title,
                thumbnailUrl = finalVideo.thumbnailUrl,
                uploaderName = finalVideo.uploaderName,
                viewCounter = finalVideo.viewCounter,
                commentCounter = finalVideo.commentCounter,
                lengthSeconds = finalVideo.lengthSeconds,
                isLive = finalVideo.isLive
            )
        )
        return result.copy(video = finalVideo)
    }

    suspend fun toggleFavorite(video: NicoVideo, currentlyFavorite: Boolean) {
        if (currentlyFavorite) {
            dao.deleteFavorite(video.contentId)
        } else {
            dao.upsertFavorite(
                FavoriteEntity(
                    contentId = video.contentId,
                    title = video.title,
                    thumbnailUrl = video.thumbnailUrl,
                    uploaderName = video.uploaderName,
                    viewCounter = video.viewCounter,
                    commentCounter = video.commentCounter,
                    lengthSeconds = video.lengthSeconds,
                    isLive = video.isLive
                )
            )
        }
    }

    suspend fun clearHistory() = dao.clearHistory()

    suspend fun addBlockedKeyword(keyword: String) {
        if (keyword.isNotBlank()) {
            dao.insertBlockedKeyword(DanmakuKeywordEntity(keyword.trim()))
        }
    }

    suspend fun removeBlockedKeyword(keyword: String) {
        dao.deleteBlockedKeyword(keyword)
    }

    suspend fun setThemeMode(mode: ThemeMode) = prefs.setThemeMode(mode)

    suspend fun updateDanmakuConfig(config: DanmakuFilterConfig) = prefs.updateDanmakuConfig(config)

    suspend fun verifyAndSaveSession(cookie: String): NicoUserAccount? {
        val profile = api.fetchAccountProfile(cookie)
        if (profile != null) {
            prefs.saveSession(
                cookie = cookie,
                userId = profile.userId,
                nickname = profile.nickname,
                iconUrl = profile.iconUrl
            )
            syncOfficialAccountData(cookie)
        }
        return profile
    }

    suspend fun syncOfficialAccountData(cookieOverride: String? = null): Pair<Int, Int> {
        val cookie = cookieOverride ?: prefs.sessionCookieFlow.first()
        if (cookie.isBlank()) return 0 to 0

        val officialFavs = api.fetchOfficialFavorites(cookie)
        if (officialFavs.isNotEmpty()) {
            dao.upsertFavoritesAll(
                officialFavs.map { v ->
                    FavoriteEntity(
                        contentId = v.contentId,
                        title = v.title,
                        thumbnailUrl = v.thumbnailUrl,
                        uploaderName = v.uploaderName,
                        viewCounter = v.viewCounter,
                        commentCounter = v.commentCounter,
                        lengthSeconds = v.lengthSeconds,
                        isLive = false,
                        mylistName = "官方稍后再看",
                        syncedFromOfficial = true
                    )
                }
            )
        }

        val officialHistory = api.fetchOfficialWatchHistory(cookie)
        if (officialHistory.isNotEmpty()) {
            dao.upsertHistoryAll(
                officialHistory.map { v ->
                    HistoryEntity(
                        contentId = v.contentId,
                        title = v.title,
                        thumbnailUrl = v.thumbnailUrl,
                        uploaderName = v.uploaderName,
                        viewCounter = v.viewCounter,
                        commentCounter = v.commentCounter,
                        lengthSeconds = v.lengthSeconds,
                        isLive = false,
                        syncedFromOfficial = true
                    )
                }
            )
        }
        return officialFavs.size to officialHistory.size
    }

    suspend fun logout() {
        prefs.clearSession()
    }

    suspend fun startDownload(
        video: NicoVideo,
        qualityLabel: String,
        streamUrl: String,
        cookieHeader: String
    ) = withContext(Dispatchers.IO) {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
        if (!dir.exists()) dir.mkdirs()
        val ext = if (streamUrl.contains(".m3u8")) "m3u8" else "mp4"
        val outFile = File(dir, "${video.contentId}.$ext")

        dao.upsertDownload(
            DownloadEntity(
                contentId = video.contentId,
                title = video.title,
                thumbnailUrl = video.thumbnailUrl,
                uploaderName = video.uploaderName,
                lengthSeconds = video.lengthSeconds,
                qualityLabel = qualityLabel,
                localFilePath = outFile.absolutePath,
                progress = 5,
                status = "DOWNLOADING"
            )
        )

        if (streamUrl.isBlank()) {
            dao.updateDownloadProgress(video.contentId, 0, "FAILED", 0L)
            return@withContext
        }

        runCatching {
            val reqBuilder = Request.Builder().url(streamUrl)
            if (cookieHeader.isNotBlank()) {
                reqBuilder.header("Cookie", cookieHeader)
            }
            api.httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    dao.updateDownloadProgress(video.contentId, 0, "FAILED", 0L)
                    return@use
                }
                val body = resp.body ?: run {
                    dao.updateDownloadProgress(video.contentId, 0, "FAILED", 0L)
                    return@use
                }
                val totalBytes = body.contentLength()
                var downloaded = 0L
                body.byteStream().use { input ->
                    FileOutputStream(outFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var read: Int
                        var lastProgress = 5
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            val progress = if (totalBytes > 0) {
                                ((downloaded * 100) / totalBytes).toInt().coerceIn(5, 99)
                            } else {
                                (lastProgress + 10).coerceAtMost(95)
                            }
                            if (progress - lastProgress >= 5) {
                                lastProgress = progress
                                dao.updateDownloadProgress(video.contentId, progress, "DOWNLOADING", downloaded)
                            }
                        }
                    }
                }
                dao.updateDownloadProgress(video.contentId, 100, "COMPLETED", outFile.length())
            }
        }.onFailure {
            dao.updateDownloadProgress(video.contentId, 0, "FAILED", 0L)
        }
    }

    suspend fun deleteDownload(download: DownloadEntity) = withContext(Dispatchers.IO) {
        runCatching { File(download.localFilePath).delete() }
        dao.deleteDownload(download.contentId)
    }
}
