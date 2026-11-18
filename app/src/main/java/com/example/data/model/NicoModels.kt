package com.example.data.model

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

enum class DanmakuPosition(val label: String) {
    SCROLL("滚动弹幕"),
    TOP("顶部弹幕"),
    BOTTOM("底部弹幕")
}

enum class DanmakuSize(val scale: Float) {
    SMALL(0.8f),
    MEDIUM(1.0f),
    BIG(1.3f)
}

enum class DanmakuCategoryFilter(val label: String) {
    ALL("全部"),
    SCROLL("滚动"),
    TOP("顶部"),
    BOTTOM("底部"),
    COLORED("彩色")
}

data class VideoQuality(
    val id: String,
    val label: String,
    val resolution: String,
    val streamUrl: String
)

data class NicoVideo(
    val contentId: String,
    val title: String,
    val description: String = "",
    val thumbnailUrl: String = "",
    val viewCounter: Long = 0L,
    val commentCounter: Long = 0L,
    val mylistCounter: Long = 0L,
    val likeCounter: Long = 0L,
    val lengthSeconds: Int = 0,
    val startTime: String = "",
    val uploaderId: String = "",
    val uploaderName: String = "ニコニコ投稿者",
    val uploaderIconUrl: String = "",
    val tags: List<String> = emptyList(),
    val genre: String = "",
    val isLive: Boolean = false,
    val liveWatchers: Int = 0,
    val streamUrl: String = "",
    val availableQualities: List<VideoQuality> = emptyList(),
    val localFilePath: String? = null
) {
    val shareUrl: String
        get() = if (isLive) {
            "https://sp.live.nicovideo.jp/watch/$contentId"
        } else {
            "https://www.nicovideo.jp/watch/$contentId"
        }
}

data class DanmakuComment(
    val id: String,
    val vposMs: Long,
    val body: String,
    val userId: String = "",
    val colorHex: Long = 0xFFFFFFFF,
    val position: DanmakuPosition = DanmakuPosition.SCROLL,
    val size: DanmakuSize = DanmakuSize.MEDIUM,
    val isOwner: Boolean = false,
    val postedAt: String = ""
) {
    val isColored: Boolean
        get() = (colorHex and 0x00FFFFFF) != 0x00FFFFFFL
}

data class DanmakuFilterConfig(
    val enabled: Boolean = true,
    val showScroll: Boolean = true,
    val showTop: Boolean = true,
    val showBottom: Boolean = true,
    val showColored: Boolean = true,
    val opacity: Float = 0.9f,
    val speedMultiplier: Float = 1.0f,
    val fontSizeScale: Float = 1.0f,
    val blockedKeywords: List<String> = emptyList()
) {
    fun matches(comment: DanmakuComment): Boolean {
        if (!enabled) return false
        when (comment.position) {
            DanmakuPosition.SCROLL -> if (!showScroll) return false
            DanmakuPosition.TOP -> if (!showTop) return false
            DanmakuPosition.BOTTOM -> if (!showBottom) return false
        }
        if (!showColored && comment.isColored) return false
        if (blockedKeywords.any { kw ->
                kw.isNotBlank() && comment.body.contains(kw, ignoreCase = true)
            }
        ) {
            return false
        }
        return true
    }
}

data class NicoUserAccount(
    val isLoggedIn: Boolean = false,
    val userId: String = "",
    val nickname: String = "未登录",
    val iconUrl: String = "",
    val isPremium: Boolean = false,
    val sessionCookie: String = ""
)

data class UploaderProfile(
    val userId: String,
    val nickname: String,
    val iconUrl: String = "",
    val description: String = "",
    val followerCount: Long = 0L,
    val followingCount: Long = 0L,
    val videoCount: Int = 0,
    val level: Int = 1,
    val isPremium: Boolean = false
)

enum class LatestTimeRange(val label: String, val days: Int?) {
    LAST_24_HOURS("24小时内", 1),
    LAST_3_DAYS("3天内", 3),
    LAST_7_DAYS("7天内", 7),
    LAST_30_DAYS("30天内", 30),
    LAST_3_MONTHS("3个月内", 90),
    CUSTOM("手动输入", null),
    ALL("全部时间", null)
}

data class NicoCategory(
    val id: String,
    val label: String,
    val query: String,
    val sort: String = "-viewCounter"
)

val DefaultNicoCategories = listOf(
    NicoCategory("live", "直播", "", "-viewCounter"),
    NicoCategory("recommend", "推荐", "ニコニコ", "-viewCounter"),
    NicoCategory("latest", "最新", "ニコニコ", "-viewCounter"),
    NicoCategory("anime", "动画", "アニメ", "-viewCounter"),
    NicoCategory("music", "音乐", "音楽", "-viewCounter"),
    NicoCategory("game", "游戏", "ゲーム", "-viewCounter"),
    NicoCategory("vocaloid", "VOCALOID", "VOCALOID", "-viewCounter"),
    NicoCategory("ranking", "排行榜", "MMD", "-mylistCounter")
)

fun formatCount(count: Long): String {
    return when {
        count >= 100_000_000 -> String.format("%.1f亿", count / 100_000_000.0)
        count >= 10_000 -> String.format("%.1f万", count / 10_000.0)
        else -> count.toString()
    }
}

fun formatDuration(seconds: Int): String {
    if (seconds <= 0) return "LIVE"
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hrs > 0) {
        String.format("%d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format("%02d:%02d", mins, secs)
    }
}
