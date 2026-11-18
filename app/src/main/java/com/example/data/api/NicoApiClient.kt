package com.example.data.api

import com.example.data.model.DanmakuComment
import com.example.data.model.DanmakuPosition
import com.example.data.model.DanmakuSize
import com.example.data.model.NicoUserAccount
import com.example.data.model.NicoVideo
import com.example.data.model.VideoQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class WatchDetailResult(
    val video: NicoVideo,
    val danmaku: List<DanmakuComment>,
    val hlsCookieHeader: String = ""
)

class NicoApiClient {

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val userAgent = "NicoBiliAndroid/1.0 (https://www.nicovideo.jp)"

    suspend fun searchVideos(
        query: String,
        targets: String = "title,description,tags",
        sort: String = "-viewCounter",
        limit: Int = 30,
        startTimeGteIso: String? = null
    ): List<NicoVideo> = withContext(Dispatchers.IO) {
        val encodedQuery = URLEncoder.encode(query.ifBlank { "ニコニコ" }, "UTF-8")
        val fields = "contentId,title,description,viewCounter,mylistCounter,likeCounter,lengthSeconds,thumbnailUrl,startTime,commentCounter,tags,genre"
        val filterParam = if (!startTimeGteIso.isNullOrBlank()) {
            "&filters[startTime][gte]=${URLEncoder.encode(startTimeGteIso, "UTF-8")}"
        } else {
            ""
        }
        val url = "https://snapshot.search.nicovideo.jp/api/v2/snapshot/video/contents/search" +
            "?q=$encodedQuery&targets=$targets&fields=$fields&_sort=$sort&_limit=$limit&_context=nicobili$filterParam"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            val body = response.body?.string() ?: return@withContext emptyList()
            val root = JSONObject(body)
            val data = root.optJSONArray("data") ?: return@withContext emptyList()
            val list = mutableListOf<NicoVideo>()
            for (i in 0 until data.length()) {
                val obj = data.optJSONObject(i) ?: continue
                val contentId = obj.optString("contentId")
                if (contentId.isBlank()) continue
                val rawTags = obj.optString("tags", "")
                list.add(
                    NicoVideo(
                        contentId = contentId,
                        title = obj.optString("title", "Untitled"),
                        description = obj.optString("description", "").replace(Regex("<[^>]*>"), ""),
                        thumbnailUrl = obj.optString("thumbnailUrl", ""),
                        viewCounter = obj.optLong("viewCounter", 0L),
                        commentCounter = obj.optLong("commentCounter", 0L),
                        mylistCounter = obj.optLong("mylistCounter", 0L),
                        likeCounter = obj.optLong("likeCounter", 0L),
                        lengthSeconds = obj.optInt("lengthSeconds", 0),
                        startTime = obj.optString("startTime", "").take(10),
                        genre = obj.optString("genre", ""),
                        tags = rawTags.split(" ").filter { it.isNotBlank() }
                    )
                )
            }
            list
        }
    }

    suspend fun fetchLivePrograms(limit: Int = 24): List<NicoVideo> = withContext(Dispatchers.IO) {
        val url = "https://live.nicovideo.jp/front/api/pages/ranking/v1/programs?type=onair"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("X-Frontend-Id", "6")
            .get()
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val body = response.body?.string() ?: return@use emptyList()
                val root = JSONObject(body)
                val data = root.optJSONObject("data") ?: return@use emptyList()
                val programs = data.optJSONArray("programs") ?: return@use emptyList()
                val list = mutableListOf<NicoVideo>()
                val count = minOf(programs.length(), limit)
                for (i in 0 until count) {
                    val p = programs.optJSONObject(i) ?: continue
                    val id = p.optString("id")
                    if (id.isBlank()) continue
                    val thumbObj = p.optJSONObject("thumbnail")
                    val thumb = thumbObj?.optString("huge")
                        ?.ifBlank { thumbObj.optString("large") }
                        ?.ifBlank { thumbObj.optString("small") }
                        ?: p.optString("thumbnailUrl", "")
                    val provider = p.optJSONObject("programProvider")
                    val stats = p.optJSONObject("statistics")
                    val watchers = stats?.optInt("watchCount", 0) ?: p.optInt("viewCount", 0)
                    val comments = stats?.optLong("commentCount", 0L) ?: 0L
                    list.add(
                        NicoVideo(
                            contentId = id,
                            title = p.optString("title", "ニコニコ生放送"),
                            description = p.optString("description", "").replace(Regex("<[^>]*>"), ""),
                            thumbnailUrl = thumb,
                            viewCounter = watchers.toLong(),
                            commentCounter = comments,
                            uploaderName = provider?.optString("name", "生放送主") ?: "生放送主",
                            uploaderIconUrl = provider?.optString("icon", "") ?: "",
                            isLive = true,
                            liveWatchers = watchers,
                            availableQualities = listOf(
                                VideoQuality("abr", "自动 (ABR)", "Auto", ""),
                                VideoQuality("high", "超清 1080p", "1080p", ""),
                                VideoQuality("normal", "高清 720p", "720p", ""),
                                VideoQuality("low", "流畅 360p", "360p", "")
                            )
                        )
                    )
                }
                list
            }
        }.getOrDefault(emptyList())
    }

    suspend fun fetchWatchDetail(
        baseVideo: NicoVideo,
        sessionCookie: String
    ): WatchDetailResult = withContext(Dispatchers.IO) {
        if (baseVideo.isLive) {
            return@withContext WatchDetailResult(
                video = baseVideo,
                danmaku = emptyList()
            )
        }

        val trackId = "nicobili_${System.currentTimeMillis()}"
        val endpoint = if (sessionCookie.isNotBlank()) {
            "https://www.nicovideo.jp/api/watch/v3/${baseVideo.contentId}?actionTrackId=$trackId"
        } else {
            "https://www.nicovideo.jp/api/watch/v3_guest/${baseVideo.contentId}?actionTrackId=$trackId"
        }

        val reqBuilder = Request.Builder()
            .url(endpoint)
            .header("User-Agent", userAgent)
            .header("X-Frontend-Id", "6")
            .header("X-Frontend-Version", "0")
            .header("Referer", "https://www.nicovideo.jp/watch/${baseVideo.contentId}")

        if (sessionCookie.isNotBlank()) {
            reqBuilder.header("Cookie", sessionCookie)
        }

        runCatching {
            httpClient.newCall(reqBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use WatchDetailResult(baseVideo, emptyList())
                }
                val body = response.body?.string() ?: return@use WatchDetailResult(baseVideo, emptyList())
                val root = JSONObject(body)
                val data = root.optJSONObject("data") ?: return@use WatchDetailResult(baseVideo, emptyList())

                val videoObj = data.optJSONObject("video")
                val ownerObj = data.optJSONObject("owner")
                val countObj = videoObj?.optJSONObject("count")

                val qualities = mutableListOf<VideoQuality>()
                var resolvedStreamUrl = baseVideo.streamUrl
                var domandCookie = ""

                // Parse Domand (HLS) qualities if available
                val mediaObj = data.optJSONObject("media")
                val domandObj = mediaObj?.optJSONObject("domand")
                if (domandObj != null) {
                    val accessRightKey = domandObj.optString("accessRightKey", "")
                    val videosArr = domandObj.optJSONArray("videos")
                    val audiosArr = domandObj.optJSONArray("audios")
                    var bestAudioId = ""
                    if (audiosArr != null) {
                        for (i in 0 until audiosArr.length()) {
                            val a = audiosArr.optJSONObject(i) ?: continue
                            if (a.optBoolean("isAvailable", false)) {
                                bestAudioId = a.optString("id")
                                break
                            }
                        }
                    }

                    if (videosArr != null && accessRightKey.isNotBlank()) {
                        val outputs = JSONArray()
                        for (i in 0 until videosArr.length()) {
                            val v = videosArr.optJSONObject(i) ?: continue
                            if (!v.optBoolean("isAvailable", false)) continue
                            val qId = v.optString("id")
                            val label = v.optString("label", qId)
                            val height = v.optInt("height", 720)
                            val pair = JSONArray().put(qId)
                            if (bestAudioId.isNotBlank()) pair.put(bestAudioId)
                            outputs.put(pair)
                            qualities.add(
                                VideoQuality(
                                    id = qId,
                                    label = "${height}p $label",
                                    resolution = "${height}p",
                                    streamUrl = ""
                                )
                            )
                        }

                        // Request HLS playlist URL from nvapi domand access-rights
                        if (outputs.length() > 0) {
                            val hlsResult = requestDomandHlsUrl(
                                videoId = baseVideo.contentId,
                                trackId = trackId,
                                accessRightKey = accessRightKey,
                                outputs = outputs,
                                sessionCookie = sessionCookie
                            )
                            if (hlsResult != null) {
                                resolvedStreamUrl = hlsResult.first
                                domandCookie = hlsResult.second
                            }
                        }
                    }
                }

                val finalQualities = if (qualities.isNotEmpty()) {
                    qualities.map { it.copy(streamUrl = resolvedStreamUrl) }
                } else {
                    listOf(
                        VideoQuality("720p", "720p 高清", "720p", resolvedStreamUrl),
                        VideoQuality("480p", "480p 清晰", "480p", resolvedStreamUrl),
                        VideoQuality("360p", "360p 流畅", "360p", resolvedStreamUrl)
                    )
                }

                // Fetch real Danmaku comments via nvcomment
                val commentObj = data.optJSONObject("comment")
                val nvCommentObj = commentObj?.optJSONObject("nvComment")
                val danmakuList = if (nvCommentObj != null) {
                    fetchNvComments(nvCommentObj)
                } else {
                    emptyList()
                }

                val updatedVideo = baseVideo.copy(
                    title = videoObj?.optString("title")?.ifBlank { baseVideo.title } ?: baseVideo.title,
                    description = videoObj?.optString("description", baseVideo.description)
                        ?.replace(Regex("<[^>]*>"), "") ?: baseVideo.description,
                    viewCounter = countObj?.optLong("view", baseVideo.viewCounter) ?: baseVideo.viewCounter,
                    commentCounter = countObj?.optLong("comment", baseVideo.commentCounter) ?: baseVideo.commentCounter,
                    mylistCounter = countObj?.optLong("mylist", baseVideo.mylistCounter) ?: baseVideo.mylistCounter,
                    likeCounter = countObj?.optLong("like", baseVideo.likeCounter) ?: baseVideo.likeCounter,
                    lengthSeconds = videoObj?.optInt("duration", baseVideo.lengthSeconds) ?: baseVideo.lengthSeconds,
                    uploaderName = ownerObj?.optString("nickname")?.ifBlank { baseVideo.uploaderName }
                        ?: baseVideo.uploaderName,
                    uploaderIconUrl = ownerObj?.optString("iconUrl", "") ?: "",
                    streamUrl = resolvedStreamUrl,
                    availableQualities = finalQualities
                )

                WatchDetailResult(
                    video = updatedVideo,
                    danmaku = danmakuList,
                    hlsCookieHeader = domandCookie
                )
            }
        }.getOrElse {
            WatchDetailResult(baseVideo, emptyList())
        }
    }

    private fun requestDomandHlsUrl(
        videoId: String,
        trackId: String,
        accessRightKey: String,
        outputs: JSONArray,
        sessionCookie: String
    ): Pair<String, String>? {
        val url = "https://nvapi.nicovideo.jp/v1/watch/$videoId/access-rights/hls?actionTrackId=$trackId"
        val payload = JSONObject().put("outputs", outputs).toString()
        val reqBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("X-Frontend-Id", "6")
            .header("X-Frontend-Version", "0")
            .header("X-Request-With", "https://www.nicovideo.jp")
            .header("X-Access-Right-Key", accessRightKey)
            .post(payload.toRequestBody("application/json".toMediaType()))

        if (sessionCookie.isNotBlank()) {
            reqBuilder.header("Cookie", sessionCookie)
        }

        return runCatching {
            httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val setCookies = resp.headers("Set-Cookie")
                    .map { it.substringBefore(";") }
                    .joinToString("; ")
                val json = JSONObject(resp.body?.string() ?: return@use null)
                val contentUrl = json.optJSONObject("data")?.optString("contentUrl") ?: ""
                if (contentUrl.isNotBlank()) contentUrl to setCookies else null
            }
        }.getOrNull()
    }

    private fun fetchNvComments(nvCommentObj: JSONObject): List<DanmakuComment> {
        val server = nvCommentObj.optString("server", "https://nvcomment.nicovideo.jp")
        val threadKey = nvCommentObj.optString("threadKey", "")
        val params = nvCommentObj.optJSONObject("params") ?: return emptyList()

        val payload = JSONObject()
            .put("params", params)
            .put("threadKey", threadKey)
            .put("additionals", JSONObject())
            .toString()

        val request = Request.Builder()
            .url("$server/v1/threads")
            .header("User-Agent", userAgent)
            .header("X-Frontend-Id", "6")
            .header("X-Frontend-Version", "0")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        return runCatching {
            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val root = JSONObject(resp.body?.string() ?: return@use emptyList())
                val threads = root.optJSONObject("data")?.optJSONArray("threads") ?: return@use emptyList()
                val comments = mutableListOf<DanmakuComment>()
                for (t in 0 until threads.length()) {
                    val thread = threads.optJSONObject(t) ?: continue
                    val fork = thread.optString("fork", "main")
                    val arr = thread.optJSONArray("comments") ?: continue
                    for (i in 0 until arr.length()) {
                        val c = arr.optJSONObject(i) ?: continue
                        val body = c.optString("body", "")
                        if (body.isBlank()) continue
                        val cmds = c.optJSONArray("commands")
                        var position = DanmakuPosition.SCROLL
                        var size = DanmakuSize.MEDIUM
                        var color = 0xFFFFFFFFL
                        if (cmds != null) {
                            for (k in 0 until cmds.length()) {
                                val cmd = cmds.optString(k).lowercase()
                                when (cmd) {
                                    "ue" -> position = DanmakuPosition.TOP
                                    "shita" -> position = DanmakuPosition.BOTTOM
                                    "big" -> size = DanmakuSize.BIG
                                    "small" -> size = DanmakuSize.SMALL
                                    else -> parseNicoColor(cmd)?.let { color = it }
                                }
                            }
                        }
                        comments.add(
                            DanmakuComment(
                                id = c.optString("id", "${t}_$i"),
                                vposMs = c.optLong("vposMs", 0L),
                                body = body,
                                userId = c.optString("userId", ""),
                                colorHex = color,
                                position = position,
                                size = size,
                                isOwner = fork == "owner",
                                postedAt = c.optString("postedAt", "").take(16).replace("T", " ")
                            )
                        )
                    }
                }
                comments.sortedBy { it.vposMs }
            }
        }.getOrDefault(emptyList())
    }

    private fun parseNicoColor(cmd: String): Long? {
        if (cmd.startsWith("#") && cmd.length == 7) {
            return cmd.substring(1).toLongOrNull(16)?.or(0xFF000000L)
        }
        return when (cmd) {
            "red" -> 0xFFFF3B30L
            "pink" -> 0xFFFB7299L
            "orange" -> 0xFFFF9500L
            "yellow" -> 0xFFFFCC00L
            "green" -> 0xFF34C759L
            "cyan" -> 0xFF32ADE6L
            "blue" -> 0xFF007AFFL
            "purple" -> 0xFFAF52DEL
            "black" -> 0xFF1C1C1EL
            else -> null
        }
    }

    suspend fun fetchAccountProfile(sessionCookie: String): NicoUserAccount? = withContext(Dispatchers.IO) {
        if (sessionCookie.isBlank()) return@withContext null
        val request = Request.Builder()
            .url("https://nvapi.nicovideo.jp/v1/users/me")
            .header("User-Agent", userAgent)
            .header("X-Frontend-Id", "6")
            .header("X-Frontend-Version", "0")
            .header("Cookie", sessionCookie)
            .get()
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val root = JSONObject(resp.body?.string() ?: return@use null)
                val user = root.optJSONObject("data")?.optJSONObject("user") ?: return@use null
                val icons = user.optJSONObject("icons")
                NicoUserAccount(
                    isLoggedIn = true,
                    userId = user.optString("id", ""),
                    nickname = user.optString("nickname", "ニコニコ用户"),
                    iconUrl = icons?.optString("large")?.ifBlank { icons.optString("small") } ?: "",
                    isPremium = user.optBoolean("isPremium", false),
                    sessionCookie = sessionCookie
                )
            }
        }.getOrNull()
    }

    suspend fun fetchOfficialWatchHistory(sessionCookie: String): List<NicoVideo> = withContext(Dispatchers.IO) {
        if (sessionCookie.isBlank()) return@withContext emptyList()
        val request = Request.Builder()
            .url("https://nvapi.nicovideo.jp/v1/users/me/watch/history?pageSize=30&page=1")
            .header("User-Agent", userAgent)
            .header("X-Frontend-Id", "6")
            .header("X-Frontend-Version", "0")
            .header("Cookie", sessionCookie)
            .get()
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val root = JSONObject(resp.body?.string() ?: return@use emptyList())
                val items = root.optJSONObject("data")?.optJSONArray("items") ?: return@use emptyList()
                val list = mutableListOf<NicoVideo>()
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val video = item.optJSONObject("video") ?: continue
                    val count = video.optJSONObject("count")
                    val owner = video.optJSONObject("owner")
                    val thumb = video.optJSONObject("thumbnail")
                    list.add(
                        NicoVideo(
                            contentId = video.optString("id"),
                            title = video.optString("title", "Untitled"),
                            thumbnailUrl = thumb?.optString("largeUrl")?.ifBlank { thumb.optString("url") } ?: "",
                            viewCounter = count?.optLong("view", 0L) ?: 0L,
                            commentCounter = count?.optLong("comment", 0L) ?: 0L,
                            lengthSeconds = video.optInt("duration", 0),
                            uploaderName = owner?.optString("name", "投稿者") ?: "投稿者"
                        )
                    )
                }
                list
            }
        }.getOrDefault(emptyList())
    }

    suspend fun fetchOfficialFavorites(sessionCookie: String): List<NicoVideo> = withContext(Dispatchers.IO) {
        if (sessionCookie.isBlank()) return@withContext emptyList()
        val request = Request.Builder()
            .url("https://nvapi.nicovideo.jp/v1/users/me/watch-later?sortKey=addedAt&sortOrder=desc&pageSize=30&page=1")
            .header("User-Agent", userAgent)
            .header("X-Frontend-Id", "6")
            .header("X-Frontend-Version", "0")
            .header("Cookie", sessionCookie)
            .get()
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val root = JSONObject(resp.body?.string() ?: return@use emptyList())
                val items = root.optJSONObject("data")?.optJSONObject("watchLater")?.optJSONArray("items")
                    ?: return@use emptyList()
                val list = mutableListOf<NicoVideo>()
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val video = item.optJSONObject("video") ?: continue
                    val count = video.optJSONObject("count")
                    val owner = video.optJSONObject("owner")
                    val thumb = video.optJSONObject("thumbnail")
                    list.add(
                        NicoVideo(
                            contentId = video.optString("id"),
                            title = video.optString("title", "Untitled"),
                            thumbnailUrl = thumb?.optString("largeUrl")?.ifBlank { thumb.optString("url") } ?: "",
                            viewCounter = count?.optLong("view", 0L) ?: 0L,
                            commentCounter = count?.optLong("comment", 0L) ?: 0L,
                            lengthSeconds = video.optInt("duration", 0),
                            uploaderName = owner?.optString("name", "投稿者") ?: "投稿者"
                        )
                    )
                }
                list
            }
        }.getOrDefault(emptyList())
    }
}
