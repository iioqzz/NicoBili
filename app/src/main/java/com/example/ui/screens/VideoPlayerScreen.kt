package com.example.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.SubtitlesOff
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.data.model.DanmakuCategoryFilter
import com.example.data.model.DanmakuFilterConfig
import com.example.data.model.DanmakuPosition
import com.example.data.model.NicoVideo
import com.example.data.model.VideoQuality
import com.example.data.model.formatCount
import com.example.data.model.formatDuration
import com.example.player.DanmakuOverlay
import com.example.ui.PlayerUiState
import com.example.ui.components.VideoRowCard
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    playerState: PlayerUiState,
    filterConfig: DanmakuFilterConfig,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onSelectVideo: (NicoVideo) -> Unit,
    onSelectQuality: (VideoQuality) -> Unit,
    onToggleFullscreen: (Boolean) -> Unit,
    onToggleBackgroundPlay: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleFollow: () -> Unit,
    onToggleFavorite: (NicoVideo, Boolean) -> Unit,
    onUploaderClick: (String, String, String) -> Unit,
    onDownloadClick: () -> Unit,
    onSendDanmaku: (String, Long, DanmakuPosition, Long) -> Unit,
    onUpdateFilterConfig: (DanmakuFilterConfig) -> Unit,
    onAddBlockedKeyword: (String) -> Unit,
    onRemoveBlockedKeyword: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val video = playerState.currentVideo ?: return

    var isPlaying by remember { mutableStateOf(true) }
    var currentPosMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf((video.lengthSeconds * 1000L).coerceAtLeast(1L)) }
    var showControls by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var danmakuCategory by remember { mutableStateOf(DanmakuCategoryFilter.ALL) }

    var showFilterSheet by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showSendDanmakuDialog by remember { mutableStateOf(false) }

    // Handle fullscreen orientation & system bars
    DisposableEffect(playerState.isFullscreen) {
        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (playerState.isFullscreen) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            if (playerState.isFullscreen && activity != null) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BackHandler {
        if (playerState.isFullscreen) {
            onToggleFullscreen(false)
        } else {
            onBack()
        }
    }

    // Build ExoPlayer with NicoNico HLS cookie headers
    val exoPlayer = remember(video.contentId, playerState.hlsCookieHeader) {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("NicoBiliAndroid/1.0")
            .apply {
                if (playerState.hlsCookieHeader.isNotBlank()) {
                    setDefaultRequestProperties(mapOf("Cookie" to playerState.hlsCookieHeader))
                }
            }
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .build()
    }

    val playableUrl = remember(video.localFilePath, playerState.selectedQuality, video.streamUrl) {
        video.localFilePath?.takeIf { it.isNotBlank() }
            ?: playerState.selectedQuality?.streamUrl?.takeIf { it.isNotBlank() }
            ?: video.streamUrl
    }

    LaunchedEffect(playableUrl) {
        if (playableUrl.isNotBlank()) {
            val uri = if (playableUrl.startsWith("/")) Uri.parse("file://$playableUrl") else Uri.parse(playableUrl)
            exoPlayer.setMediaItem(MediaItem.fromUri(uri))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }

    // Poll playback position for Danmaku sync
    LaunchedEffect(exoPlayer, playableUrl) {
        while (true) {
            if (playableUrl.isNotBlank()) {
                currentPosMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                if (exoPlayer.duration > 0) {
                    durationMs = exoPlayer.duration
                }
                isPlaying = exoPlayer.isPlaying
            } else if (isPlaying) {
                currentPosMs += 50L
                if (currentPosMs > durationMs) currentPosMs = 0L
            }
            delay(50L)
        }
    }

    DisposableEffect(exoPlayer, playerState.isBackgroundPlayEnabled) {
        onDispose {
            if (!playerState.isBackgroundPlayEnabled) {
                exoPlayer.release()
            }
        }
    }

    val shareVideo: () -> Unit = {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, video.title)
            putExtra(Intent.EXTRA_TEXT, "${video.title}\n${video.shareUrl}")
        }
        context.startActivity(Intent.createChooser(sendIntent, "分享视频"))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (playerState.isFullscreen) Color.Black else MaterialTheme.colorScheme.background)
            .then(if (!playerState.isFullscreen) Modifier.statusBarsPadding() else Modifier)
    ) {
        // Video Player Surface + Danmaku + Controls
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (playerState.isFullscreen) Modifier.fillMaxSize()
                    else Modifier.aspectRatio(16f / 9f)
                )
                .background(Color.Black)
                .clickable { showControls = !showControls }
        ) {
            if (playableUrl.isNotBlank()) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    update = { it.player = exoPlayer },
                    modifier = Modifier.fillMaxSize()
                )
            } else if (video.isLive) {
                // Live WebView Player for NicoNico 生放送
                NicoLiveWebPlayer(
                    liveUrl = video.shareUrl,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Thumbnail preview while resolving stream
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Real-time Danmaku Overlay
            DanmakuOverlay(
                comments = playerState.danmakuList,
                currentPositionMs = currentPosMs,
                filterConfig = filterConfig,
                modifier = Modifier.fillMaxSize()
            )

            // Controls Overlay
            if (showControls) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                ) {
                    // Top Controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            if (playerState.isFullscreen) onToggleFullscreen(false) else onBack()
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "返回",
                                tint = Color.White
                            )
                        }
                        Text(
                            text = video.title,
                            color = Color.White,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        // Quality Badge
                        Surface(
                            color = Color.Black.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .testTag("quality_selector_button")
                                .clickable { showQualityDialog = true }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = playerState.selectedQuality?.resolution ?: "720p",
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                        IconButton(
                            onClick = { showFilterSheet = true },
                            modifier = Modifier.testTag("danmaku_filter_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FilterList,
                                contentDescription = "弹幕过滤",
                                tint = Color.White
                            )
                        }
                        IconButton(
                            onClick = onToggleBackgroundPlay,
                            modifier = Modifier.testTag("background_play_button")
                        ) {
                            Icon(
                                imageVector = if (playerState.isBackgroundPlayEnabled) {
                                    Icons.Rounded.Headphones
                                } else {
                                    Icons.Outlined.Headphones
                                },
                                contentDescription = "后台播放",
                                tint = if (playerState.isBackgroundPlayEnabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.White
                                }
                            )
                        }
                    }

                    // Center Play/Pause
                    IconButton(
                        onClick = {
                            if (playableUrl.isNotBlank()) {
                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                            } else {
                                isPlaying = !isPlaying
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(56.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "暂停" else "播放",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    // Bottom Controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatDuration((currentPosMs / 1000).toInt()),
                            color = Color.White,
                            fontSize = 11.sp
                        )
                        Slider(
                            value = (currentPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f),
                            onValueChange = { frac ->
                                val target = (frac * durationMs).toLong()
                                currentPosMs = target
                                if (playableUrl.isNotBlank()) exoPlayer.seekTo(target)
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 6.dp)
                        )
                        Text(
                            text = formatDuration((durationMs / 1000).toInt()),
                            color = Color.White,
                            fontSize = 11.sp
                        )
                        IconButton(
                            onClick = {
                                onUpdateFilterConfig(filterConfig.copy(enabled = !filterConfig.enabled))
                            },
                            modifier = Modifier.testTag("danmaku_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (filterConfig.enabled) {
                                    Icons.Rounded.Subtitles
                                } else {
                                    Icons.Rounded.SubtitlesOff
                                },
                                contentDescription = "弹幕开关",
                                tint = if (filterConfig.enabled) MaterialTheme.colorScheme.primary else Color.White
                            )
                        }
                        IconButton(
                            onClick = { onToggleFullscreen(!playerState.isFullscreen) },
                            modifier = Modifier.testTag("fullscreen_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (playerState.isFullscreen) {
                                    Icons.Rounded.FullscreenExit
                                } else {
                                    Icons.Rounded.Fullscreen
                                },
                                contentDescription = "全屏切换",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Below Player Content (Hidden in Fullscreen)
        if (!playerState.isFullscreen) {
            // Bilibili-style Tab Row + "点我发弹幕" Pill
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        divider = {}
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("简介", fontWeight = FontWeight.SemiBold) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = {
                                Text(
                                    "弹幕 ${playerState.danmakuList.size}",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    // "点我发弹幕" pill matching Screenshot 1
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .testTag("send_danmaku_pill")
                            .clickable { showSendDanmakuDialog = true }
                    ) {
                        Text(
                            text = "点我发弹幕",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

            if (selectedTab == 0) {
                // Intro + Actions + Related Videos
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(14.dp)
                        ) {
                            // Uploader Row (Tap avatar or name to open UP主主页)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .testTag("player_uploader_entry")
                                        .clickable {
                                            onUploaderClick(
                                                video.uploaderId,
                                                video.uploaderName,
                                                video.uploaderIconUrl
                                            )
                                        }
                                        .padding(vertical = 2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (video.uploaderIconUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = video.uploaderIconUrl,
                                                contentDescription = video.uploaderName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Rounded.Person,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = video.uploaderName,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "ID: ${video.contentId}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Button(
                                    onClick = onToggleFollow,
                                    shape = RoundedCornerShape(20.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (playerState.isFollowed) {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        }
                                    ),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = if (playerState.isFollowed) "已关注" else "+ 关注",
                                        color = if (playerState.isFollowed) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            Color.White
                                        },
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            // Title & Stats
                            Text(
                                text = video.title,
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = formatCount(video.viewCounter),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(12.dp))
                                Icon(
                                    imageVector = Icons.Outlined.ChatBubbleOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = formatCount(video.commentCounter),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (video.startTime.isNotBlank()) {
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = video.startTime,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (video.description.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = video.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(Modifier.height(16.dp))

                            // 5-Icon Action Bar (Like, Favorite, Download, Background Play, Share)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                PlayerActionItem(
                                    icon = if (playerState.isLiked) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp,
                                    label = formatCount(video.likeCounter + if (playerState.isLiked) 1 else 0),
                                    active = playerState.isLiked,
                                    onClick = onToggleLike
                                )
                                PlayerActionItem(
                                    icon = if (isFavorite) Icons.Rounded.Star else Icons.Outlined.StarBorder,
                                    label = if (isFavorite) "已收藏" else formatCount(video.mylistCounter),
                                    active = isFavorite,
                                    onClick = { onToggleFavorite(video, isFavorite) }
                                )
                                PlayerActionItem(
                                    icon = Icons.Outlined.Download,
                                    label = "下载视频",
                                    active = false,
                                    onClick = onDownloadClick
                                )
                                PlayerActionItem(
                                    icon = if (playerState.isBackgroundPlayEnabled) {
                                        Icons.Rounded.Headphones
                                    } else {
                                        Icons.Outlined.Headphones
                                    },
                                    label = "后台播放",
                                    active = playerState.isBackgroundPlayEnabled,
                                    onClick = onToggleBackgroundPlay
                                )
                                PlayerActionItem(
                                    icon = Icons.Outlined.Share,
                                    label = "分享",
                                    active = false,
                                    onClick = shareVideo
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    }

                    // Related Videos
                    items(playerState.relatedVideos, key = { it.contentId }) { related ->
                        VideoRowCard(
                            video = related,
                            onClick = { onSelectVideo(related) }
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }
            } else {
                // Categorized Danmaku Viewer & Filter Tab
                Column(modifier = Modifier.fillMaxSize()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(DanmakuCategoryFilter.entries) { cat ->
                            FilterChip(
                                selected = danmakuCategory == cat,
                                onClick = { danmakuCategory = cat },
                                label = { Text(cat.label) }
                            )
                        }
                    }

                    val filteredDanmaku = remember(playerState.danmakuList, danmakuCategory, filterConfig) {
                        playerState.danmakuList.filter { c ->
                            filterConfig.matches(c) && when (danmakuCategory) {
                                DanmakuCategoryFilter.ALL -> true
                                DanmakuCategoryFilter.SCROLL -> c.position == DanmakuPosition.SCROLL
                                DanmakuCategoryFilter.TOP -> c.position == DanmakuPosition.TOP
                                DanmakuCategoryFilter.BOTTOM -> c.position == DanmakuPosition.BOTTOM
                                DanmakuCategoryFilter.COLORED -> c.isColored
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        items(filteredDanmaku, key = { it.id }) { comment ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        currentPosMs = comment.vposMs
                                        if (playableUrl.isNotBlank()) exoPlayer.seekTo(comment.vposMs)
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = formatDuration((comment.vposMs / 1000).toInt()),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(44.dp)
                                )
                                Text(
                                    text = comment.body,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = comment.position.label.take(2),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                        }
                    }
                }
            }
        }
    }

    // Quality Selector Dialog
    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = { Text("切换清晰度") },
            text = {
                Column {
                    video.availableQualities.forEach { quality ->
                        val selected = quality.id == playerState.selectedQuality?.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectQuality(quality)
                                    showQualityDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = quality.label,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            if (selected) {
                                Text("当前", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualityDialog = false }) { Text("关闭") }
            }
        )
    }

    // Send Danmaku Dialog
    if (showSendDanmakuDialog) {
        var inputText by remember { mutableStateOf("") }
        var selectedPos by remember { mutableStateOf(DanmakuPosition.SCROLL) }
        var selectedColor by remember { mutableLongStateOf(0xFFFFFFFFL) }
        val colors = listOf(0xFFFFFFFFL, 0xFFFB7299L, 0xFF23ADE5L, 0xFFFFCC00L, 0xFF34C759L, 0xFFFF3B30L)

        AlertDialog(
            onDismissRequest = { showSendDanmakuDialog = false },
            title = { Text("发送实时弹幕") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("发一条友善的弹幕见证当下...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DanmakuPosition.entries.forEach { pos ->
                            FilterChip(
                                selected = selectedPos == pos,
                                onClick = { selectedPos = pos },
                                label = { Text(pos.label.take(2)) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        colors.forEach { c ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(c))
                                    .clickable { selectedColor = c }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (inputText.isNotBlank()) {
                        onSendDanmaku(inputText, currentPosMs, selectedPos, selectedColor)
                        showSendDanmakuDialog = false
                    }
                }) { Text("发送") }
            },
            dismissButton = {
                TextButton(onClick = { showSendDanmakuDialog = false }) { Text("取消") }
            }
        )
    }

    // Danmaku Filter & Settings Bottom Sheet
    if (showFilterSheet) {
        var newKeyword by remember { mutableStateOf("") }
        ModalBottomSheet(onDismissRequest = { showFilterSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("弹幕过滤与显示设置", style = MaterialTheme.typography.titleLarge)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("显示滚动弹幕")
                    Switch(
                        checked = filterConfig.showScroll,
                        onCheckedChange = { onUpdateFilterConfig(filterConfig.copy(showScroll = it)) }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("显示顶部 / 底部弹幕")
                    Switch(
                        checked = filterConfig.showTop && filterConfig.showBottom,
                        onCheckedChange = {
                            onUpdateFilterConfig(filterConfig.copy(showTop = it, showBottom = it))
                        }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("显示彩色弹幕")
                    Switch(
                        checked = filterConfig.showColored,
                        onCheckedChange = { onUpdateFilterConfig(filterConfig.copy(showColored = it)) }
                    )
                }

                Text("弹幕透明度: ${(filterConfig.opacity * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = filterConfig.opacity,
                    onValueChange = { onUpdateFilterConfig(filterConfig.copy(opacity = it)) },
                    valueRange = 0.2f..1.0f
                )

                Text("弹幕速度: ${String.format("%.1fx", filterConfig.speedMultiplier)}", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = filterConfig.speedMultiplier,
                    onValueChange = { onUpdateFilterConfig(filterConfig.copy(speedMultiplier = it)) },
                    valueRange = 0.5f..2.0f
                )

                Text("关键词屏蔽", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newKeyword,
                        onValueChange = { newKeyword = it },
                        placeholder = { Text("输入要屏蔽的关键词") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = {
                        if (newKeyword.isNotBlank()) {
                            onAddBlockedKeyword(newKeyword)
                            newKeyword = ""
                        }
                    }) {
                        Icon(Icons.Rounded.Add, contentDescription = "添加屏蔽词")
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filterConfig.blockedKeywords) { kw ->
                        FilterChip(
                            selected = true,
                            onClick = { onRemoveBlockedKeyword(kw) },
                            label = { Text(kw) },
                            trailingIcon = {
                                Icon(Icons.Rounded.Close, contentDescription = "移除", modifier = Modifier.size(14.dp))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerActionItem(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun NicoLiveWebPlayer(
    liveUrl: String,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                webChromeClient = WebChromeClient()
                webViewClient = createAdBlockingWebViewClient()
                loadUrl(liveUrl)
            }
        },
        modifier = modifier
    )
}
