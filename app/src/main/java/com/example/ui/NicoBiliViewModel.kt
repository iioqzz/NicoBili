package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.NicoApiClient
import com.example.data.local.DownloadEntity
import com.example.data.local.FavoriteEntity
import com.example.data.local.HistoryEntity
import com.example.data.local.NicoDatabase
import com.example.data.local.UserPreferencesRepository
import com.example.data.model.DanmakuComment
import com.example.data.model.DanmakuFilterConfig
import com.example.data.model.DanmakuPosition
import com.example.data.model.DefaultNicoCategories
import com.example.data.model.LatestTimeRange
import com.example.data.model.NicoCategory
import com.example.data.model.NicoUserAccount
import com.example.data.model.NicoVideo
import com.example.data.model.ThemeMode
import com.example.data.model.VideoQuality
import com.example.data.repository.NicoRepository
import com.example.player.BackgroundPlaybackService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FeedUiState(
    val selectedCategory: NicoCategory = DefaultNicoCategories[1], // 推荐
    val videos: List<NicoVideo> = emptyList(),
    val livePrograms: List<NicoVideo> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val latestTimeRange: LatestTimeRange = LatestTimeRange.LAST_7_DAYS
)

data class PlayerUiState(
    val currentVideo: NicoVideo? = null,
    val relatedVideos: List<NicoVideo> = emptyList(),
    val danmakuList: List<DanmakuComment> = emptyList(),
    val selectedQuality: VideoQuality? = null,
    val hlsCookieHeader: String = "",
    val isLoading: Boolean = false,
    val isFullscreen: Boolean = false,
    val isBackgroundPlayEnabled: Boolean = false,
    val isLiked: Boolean = false,
    val isFollowed: Boolean = false,
    val statusMessage: String? = null
)

class NicoBiliViewModel(application: Application) : AndroidViewModel(application) {

    private val api = NicoApiClient()
    private val db = NicoDatabase.getInstance(application)
    private val prefs = UserPreferencesRepository(application)
    val repository = NicoRepository(application, api, db.nicoDao(), prefs)

    val themeMode: StateFlow<ThemeMode> = repository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val downloads: StateFlow<List<DownloadEntity>> = repository.downloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history: StateFlow<List<HistoryEntity>> = repository.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<FavoriteEntity>> = repository.favorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val danmakuFilterConfig: StateFlow<DanmakuFilterConfig> = combine(
        repository.danmakuPrefs,
        repository.blockedKeywords
    ) { config, keywords ->
        config.copy(blockedKeywords = keywords)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DanmakuFilterConfig())

    private val _feedState = MutableStateFlow(FeedUiState())
    val feedState: StateFlow<FeedUiState> = _feedState.asStateFlow()

    private val _playerState = MutableStateFlow(PlayerUiState())
    val playerState: StateFlow<PlayerUiState> = _playerState.asStateFlow()

    private val _account = MutableStateFlow(NicoUserAccount())
    val account: StateFlow<NicoUserAccount> = _account.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    init {
        loadCategory(DefaultNicoCategories[1])
        loadLivePrograms()
        restoreSession()
    }

    private fun restoreSession() {
        viewModelScope.launch {
            val cookie = repository.sessionCookie.first()
            if (cookie.isNotBlank()) {
                val profile = repository.verifyAndSaveSession(cookie)
                if (profile != null) {
                    _account.value = profile
                }
            }
        }
    }

    fun loadCategory(category: NicoCategory) {
        _feedState.value = _feedState.value.copy(
            selectedCategory = category,
            isLoading = true,
            errorMessage = null,
            isSearching = false
        )
        viewModelScope.launch {
            if (category.id == "live") {
                val live = repository.fetchLivePrograms()
                _feedState.value = _feedState.value.copy(
                    livePrograms = live,
                    isLoading = false,
                    errorMessage = if (live.isEmpty()) "暂时无法获取生放送列表，请检查网络" else null
                )
            } else {
                val startTimeIso = if (category.id == "latest") {
                    computeStartTimeIso(_feedState.value.latestTimeRange)
                } else {
                    null
                }
                runCatching {
                    repository.searchVideos(
                        query = category.query,
                        sort = category.sort,
                        startTimeGteIso = startTimeIso
                    )
                }.onSuccess { list ->
                    _feedState.value = _feedState.value.copy(
                        videos = list,
                        isLoading = false,
                        errorMessage = if (list.isEmpty()) "未找到相关视频" else null
                    )
                }.onFailure { e ->
                    _feedState.value = _feedState.value.copy(
                        isLoading = false,
                        errorMessage = "加载失败: ${e.localizedMessage ?: "网络异常"}"
                    )
                }
            }
        }
    }

    fun updateLatestTimeRange(range: LatestTimeRange) {
        _feedState.value = _feedState.value.copy(latestTimeRange = range)
        val latestCategory = DefaultNicoCategories.firstOrNull { it.id == "latest" }
            ?: _feedState.value.selectedCategory
        loadCategory(latestCategory)
    }

    private fun computeStartTimeIso(range: LatestTimeRange): String? {
        val days = range.days ?: return null
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Tokyo"))
        cal.add(java.util.Calendar.DAY_OF_YEAR, -days)
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'+09:00'", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("Asia/Tokyo")
        return sdf.format(cal.time)
    }

    fun loadLivePrograms() {
        viewModelScope.launch {
            val live = repository.fetchLivePrograms()
            _feedState.value = _feedState.value.copy(livePrograms = live)
        }
    }

    fun search(query: String) {
        val trimmed = query.trim()
        _feedState.value = _feedState.value.copy(
            searchQuery = trimmed,
            isSearching = trimmed.isNotBlank(),
            isLoading = true,
            errorMessage = null
        )
        viewModelScope.launch {
            runCatching {
                repository.searchVideos(
                    query = trimmed.ifBlank { _feedState.value.selectedCategory.query },
                    sort = "-viewCounter"
                )
            }.onSuccess { list ->
                _feedState.value = _feedState.value.copy(
                    videos = list,
                    isLoading = false,
                    errorMessage = if (list.isEmpty()) "没有找到关于 \"$trimmed\" 的视频" else null
                )
            }.onFailure { e ->
                _feedState.value = _feedState.value.copy(
                    isLoading = false,
                    errorMessage = "搜索失败: ${e.localizedMessage ?: "网络异常"}"
                )
            }
        }
    }

    fun openVideo(video: NicoVideo) {
        _playerState.value = PlayerUiState(
            currentVideo = video,
            isLoading = true,
            isBackgroundPlayEnabled = _playerState.value.isBackgroundPlayEnabled
        )
        viewModelScope.launch {
            val detail = repository.loadWatchDetail(video)
            val relatedQuery = detail.video.tags.firstOrNull() ?: detail.video.genre.ifBlank { "ニコニコ" }
            val related = runCatching {
                repository.searchVideos(query = relatedQuery, sort = "-viewCounter")
                    .filter { it.contentId != detail.video.contentId }
                    .take(12)
            }.getOrDefault(emptyList())

            _playerState.value = _playerState.value.copy(
                currentVideo = detail.video,
                relatedVideos = related,
                danmakuList = detail.danmaku,
                selectedQuality = detail.video.availableQualities.firstOrNull(),
                hlsCookieHeader = detail.hlsCookieHeader,
                isLoading = false
            )
        }
    }

    fun selectQuality(quality: VideoQuality) {
        _playerState.value = _playerState.value.copy(
            selectedQuality = quality,
            statusMessage = "已切换至 ${quality.label}"
        )
    }

    fun setFullscreen(fullscreen: Boolean) {
        _playerState.value = _playerState.value.copy(isFullscreen = fullscreen)
    }

    fun toggleBackgroundPlay() {
        val current = _playerState.value
        val next = !current.isBackgroundPlayEnabled
        _playerState.value = current.copy(
            isBackgroundPlayEnabled = next,
            statusMessage = if (next) "已开启后台播放" else "已关闭后台播放"
        )
        val app = getApplication<Application>()
        val video = current.currentVideo
        if (next && video != null) {
            BackgroundPlaybackService.start(app, video.title, video.uploaderName)
        } else {
            BackgroundPlaybackService.stop(app)
        }
    }

    fun toggleLike() {
        _playerState.value = _playerState.value.copy(isLiked = !_playerState.value.isLiked)
    }

    fun toggleFollow() {
        _playerState.value = _playerState.value.copy(isFollowed = !_playerState.value.isFollowed)
    }

    fun toggleFavorite(video: NicoVideo, isCurrentlyFav: Boolean) {
        viewModelScope.launch {
            repository.toggleFavorite(video, isCurrentlyFav)
            _playerState.value = _playerState.value.copy(
                statusMessage = if (isCurrentlyFav) "已取消收藏" else "已加入收藏夹"
            )
        }
    }

    fun sendLocalDanmaku(body: String, positionMs: Long, position: DanmakuPosition, colorHex: Long) {
        if (body.isBlank()) return
        val newComment = DanmakuComment(
            id = "local_${System.currentTimeMillis()}",
            vposMs = positionMs,
            body = body.trim(),
            userId = _account.value.userId.ifBlank { "me" },
            colorHex = colorHex,
            position = position,
            postedAt = "刚刚"
        )
        val updated = (_playerState.value.danmakuList + newComment).sortedBy { it.vposMs }
        _playerState.value = _playerState.value.copy(
            danmakuList = updated,
            statusMessage = "弹幕已发送"
        )
    }

    fun downloadCurrentVideo() {
        val state = _playerState.value
        val video = state.currentVideo ?: return
        val quality = state.selectedQuality?.label ?: "720p"
        val url = state.selectedQuality?.streamUrl?.ifBlank { video.streamUrl } ?: video.streamUrl
        _playerState.value = state.copy(statusMessage = "已开始下载: ${video.title}")
        viewModelScope.launch {
            repository.startDownload(video, quality, url, state.hlsCookieHeader)
        }
    }

    fun deleteDownload(download: DownloadEntity) {
        viewModelScope.launch {
            repository.deleteDownload(download)
        }
    }

    fun updateDanmakuFilter(config: DanmakuFilterConfig) {
        viewModelScope.launch {
            repository.updateDanmakuConfig(config)
        }
    }

    fun addBlockedKeyword(keyword: String) {
        viewModelScope.launch {
            repository.addBlockedKeyword(keyword)
        }
    }

    fun removeBlockedKeyword(keyword: String) {
        viewModelScope.launch {
            repository.removeBlockedKeyword(keyword)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            repository.setThemeMode(mode)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun onOfficialLoginCookiesCaptured(cookieString: String) {
        viewModelScope.launch {
            _syncMessage.value = "正在验证官方账号并同步数据..."
            val profile = repository.verifyAndSaveSession(cookieString)
            if (profile != null) {
                _account.value = profile
                _syncMessage.value = "登录成功: ${profile.nickname}，已同步官方收藏夹与历史记录"
            } else {
                _syncMessage.value = "已保存登录凭证，正在同步..."
            }
        }
    }

    fun syncAccountNow() {
        viewModelScope.launch {
            _syncMessage.value = "正在与ニコニコ官网同步..."
            val (favs, hist) = repository.syncOfficialAccountData()
            _syncMessage.value = "同步完成：收藏 $favs 条，历史 $hist 条"
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _account.value = NicoUserAccount()
            _syncMessage.value = "已退出官方账号"
        }
    }

    fun clearStatusMessage() {
        _playerState.value = _playerState.value.copy(statusMessage = null)
        _syncMessage.value = null
    }
}
