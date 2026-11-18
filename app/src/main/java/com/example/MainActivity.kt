package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.ThemeMode
import com.example.ui.NicoBiliViewModel
import com.example.ui.screens.DownloadsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LiveScreen
import com.example.ui.screens.OfficialLoginScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.VideoPlayerScreen
import com.example.ui.theme.NicoBiliTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: NicoBiliViewModel = viewModel()
            NicoBiliApp(viewModel = viewModel)
        }
    }
}

private data class BottomNavTab(
    val id: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val MainTabs = listOf(
    BottomNavTab("home", "首页", Icons.Rounded.Home, Icons.Outlined.Home),
    BottomNavTab("live", "生放送", Icons.Rounded.LiveTv, Icons.Outlined.LiveTv),
    BottomNavTab("downloads", "下载", Icons.Rounded.Download, Icons.Outlined.Download),
    BottomNavTab("profile", "我的", Icons.Rounded.Person, Icons.Outlined.Person)
)

@Composable
fun NicoBiliApp(viewModel: NicoBiliViewModel) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val feedState by viewModel.feedState.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val filterConfig by viewModel.danmakuFilterConfig.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    val syncMessage by viewModel.syncMessage.collectAsStateWithLifecycle()

    val isDarkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var isPlayerOpen by remember { mutableStateOf(false) }
    var isLoginOpen by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(playerState.statusMessage) {
        playerState.statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    NicoBiliTheme(darkTheme = isDarkTheme) {
        when {
            isLoginOpen -> {
                OfficialLoginScreen(
                    onBack = { isLoginOpen = false },
                    onLoginCaptured = { cookie -> viewModel.onOfficialLoginCookiesCaptured(cookie) }
                )
            }
            isPlayerOpen && playerState.currentVideo != null -> {
                val currentId = playerState.currentVideo!!.contentId
                val isFav by viewModel.repository.observeIsFavorite(currentId)
                    .collectAsStateWithLifecycle(initialValue = false)

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    contentWindowInsets = WindowInsets(0, 0, 0, 0)
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        VideoPlayerScreen(
                            playerState = playerState,
                            filterConfig = filterConfig,
                            isFavorite = isFav,
                            onBack = { isPlayerOpen = false },
                            onSelectVideo = { viewModel.openVideo(it) },
                            onSelectQuality = { viewModel.selectQuality(it) },
                            onToggleFullscreen = { viewModel.setFullscreen(it) },
                            onToggleBackgroundPlay = { viewModel.toggleBackgroundPlay() },
                            onToggleLike = { viewModel.toggleLike() },
                            onToggleFollow = { viewModel.toggleFollow() },
                            onToggleFavorite = { v, fav -> viewModel.toggleFavorite(v, fav) },
                            onDownloadClick = { viewModel.downloadCurrentVideo() },
                            onSendDanmaku = { body, posMs, pos, color ->
                                viewModel.sendLocalDanmaku(body, posMs, pos, color)
                            },
                            onUpdateFilterConfig = { viewModel.updateDanmakuFilter(it) },
                            onAddBlockedKeyword = { viewModel.addBlockedKeyword(it) },
                            onRemoveBlockedKeyword = { viewModel.removeBlockedKeyword(it) }
                        )
                    }
                }
            }
            else -> {
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            windowInsets = WindowInsets.navigationBars
                        ) {
                            MainTabs.forEachIndexed { index, tab ->
                                val selected = selectedTabIndex == index
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { selectedTabIndex = index },
                                    modifier = Modifier.testTag("bottom_nav_${tab.id}"),
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label) }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (selectedTabIndex) {
                            0 -> HomeScreen(
                                feedState = feedState,
                                account = account,
                                isDarkTheme = isDarkTheme,
                                onSelectCategory = { viewModel.loadCategory(it) },
                                onSelectLatestTimeRange = { viewModel.updateLatestTimeRange(it) },
                                onSearch = { viewModel.search(it) },
                                onToggleDarkMode = {
                                    viewModel.setThemeMode(if (isDarkTheme) ThemeMode.LIGHT else ThemeMode.DARK)
                                },
                                onAvatarClick = { selectedTabIndex = 3 },
                                onVideoClick = { video ->
                                    viewModel.openVideo(video)
                                    isPlayerOpen = true
                                }
                            )
                            1 -> LiveScreen(
                                livePrograms = feedState.livePrograms,
                                isLoading = feedState.isLoading,
                                onRefresh = { viewModel.loadLivePrograms() },
                                onLiveClick = { live ->
                                    viewModel.openVideo(live)
                                    isPlayerOpen = true
                                }
                            )
                            2 -> DownloadsScreen(
                                downloads = downloads,
                                onPlayOffline = { video ->
                                    viewModel.openVideo(video)
                                    isPlayerOpen = true
                                },
                                onDeleteDownload = { viewModel.deleteDownload(it) }
                            )
                            3 -> ProfileScreen(
                                account = account,
                                themeMode = themeMode,
                                favorites = favorites,
                                history = history,
                                syncMessage = syncMessage,
                                onLoginClick = { isLoginOpen = true },
                                onSyncClick = { viewModel.syncAccountNow() },
                                onLogoutClick = { viewModel.logout() },
                                onSelectThemeMode = { viewModel.setThemeMode(it) },
                                onClearHistory = { viewModel.clearHistory() },
                                onVideoClick = { video ->
                                    viewModel.openVideo(video)
                                    isPlayerOpen = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
