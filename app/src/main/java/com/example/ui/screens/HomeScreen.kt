package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.DefaultNicoCategories
import com.example.data.model.LatestTimeRange
import com.example.data.model.NicoCategory
import com.example.data.model.NicoUserAccount
import com.example.data.model.NicoVideo
import com.example.ui.FeedUiState
import com.example.ui.components.VideoGridCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    feedState: FeedUiState,
    account: NicoUserAccount,
    isDarkTheme: Boolean,
    onSelectCategory: (NicoCategory) -> Unit,
    onSelectLatestTimeRange: (LatestTimeRange) -> Unit,
    onSelectCustomLatestDays: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onSearch: (String) -> Unit,
    onToggleDarkMode: () -> Unit,
    onAvatarClick: () -> Unit,
    onVideoClick: (NicoVideo) -> Unit
) {
    var searchText by remember { mutableStateOf(feedState.searchQuery) }
    var showTimeRangeSheet by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()

    val selectedIndex = DefaultNicoCategories.indexOfFirst {
        it.id == feedState.selectedCategory.id
    }.coerceAtLeast(0)

    val pagerState = rememberPagerState(
        initialPage = selectedIndex,
        pageCount = { DefaultNicoCategories.size }
    )

    // Sync swipe -> ViewModel category load
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val targetCategory = DefaultNicoCategories.getOrNull(page)
            if (targetCategory != null && targetCategory.id != feedState.selectedCategory.id) {
                onSelectCategory(targetCategory)
            }
        }
    }

    // Sync external category change -> Pager
    LaunchedEffect(selectedIndex) {
        if (pagerState.currentPage != selectedIndex) {
            pagerState.animateScrollToPage(selectedIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Bilibili-style Top Search Header
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // User Avatar
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .testTag("home_avatar_button")
                            .clickable(onClick = onAvatarClick),
                        contentAlignment = Alignment.Center
                    ) {
                        if (account.iconUrl.isNotBlank()) {
                            AsyncImage(
                                model = account.iconUrl,
                                contentDescription = account.nickname,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = "我的账号",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(10.dp))

                    // Pill Search Bar
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "搜索",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            BasicTextField(
                                value = searchText,
                                onValueChange = { searchText = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        keyboard?.hide()
                                        onSearch(searchText)
                                    }
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("home_search_input"),
                                decorationBox = { innerTextField ->
                                    if (searchText.isEmpty()) {
                                        Text(
                                            text = "搜索视频、生放送、标签或UP主",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                            if (searchText.isNotEmpty()) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "清除",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable {
                                            searchText = ""
                                            onSelectCategory(feedState.selectedCategory)
                                        }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(4.dp))

                    // Quick Night Mode Toggle
                    IconButton(
                        onClick = onToggleDarkMode,
                        modifier = Modifier.testTag("home_theme_toggle")
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                            contentDescription = "切换夜间模式",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Category Tabs (直播 | 推荐 | 最新 | 动画 | 音乐 | 游戏 | VOCALOID | 排行榜)
                ScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    edgePadding = 12.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    indicator = { tabPositions ->
                        if (pagerState.currentPage < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                                height = 3.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    divider = {}
                ) {
                    DefaultNicoCategories.forEachIndexed { index, category ->
                        val selected = index == pagerState.currentPage
                        val isLatest = category.id == "latest"
                        Tab(
                            selected = selected,
                            onClick = {
                                if (isLatest && selected) {
                                    showTimeRangeSheet = true
                                } else {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                    onSelectCategory(category)
                                }
                            },
                            modifier = Modifier.testTag("category_tab_${category.id}"),
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val rangeLabel = if (feedState.latestTimeRange == LatestTimeRange.CUSTOM) {
                                        "${feedState.customLatestDays}天内"
                                    } else {
                                        feedState.latestTimeRange.label
                                    }
                                    Text(
                                        text = if (isLatest && selected) {
                                            "${category.label}·$rangeLabel"
                                        } else {
                                            category.label
                                        },
                                        fontSize = if (selected) 16.sp else 14.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                    if (isLatest && selected) {
                                        Icon(
                                            imageVector = Icons.Rounded.ArrowDropDown,
                                            contentDescription = "调整发布时间范围",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        // Swipeable HorizontalPager for all category tabs
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("home_category_pager")
        ) { page ->
            val pageCategory = DefaultNicoCategories[page]
            val isCurrentPage = page == pagerState.currentPage
            val isLivePage = pageCategory.id == "live" && !feedState.isSearching
            val itemsToShow = if (isLivePage) {
                feedState.livePrograms
            } else if (isCurrentPage) {
                feedState.videos
            } else {
                emptyList()
            }

            val pageGridState = rememberLazyGridState()
            val shouldLoadMore by remember(itemsToShow.size, isCurrentPage) {
                derivedStateOf {
                    val lastVisible = pageGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    isCurrentPage && itemsToShow.isNotEmpty() && lastVisible >= itemsToShow.size - 4
                }
            }
            LaunchedEffect(shouldLoadMore) {
                if (shouldLoadMore && !isLivePage) {
                    onLoadMore()
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    isLivePage && itemsToShow.isEmpty() && !feedState.isLoading -> {
                        if (isCurrentPage) {
                            NicoLivePortalWebView(modifier = Modifier.fillMaxSize())
                        }
                    }
                    (feedState.isLoading || !isCurrentPage) && itemsToShow.isEmpty() -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    feedState.errorMessage != null && itemsToShow.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = feedState.errorMessage,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            IconButton(onClick = { onSelectCategory(pageCategory) }) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "重试",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    else -> {
                        LazyVerticalGrid(
                            state = pageGridState,
                            columns = GridCells.Adaptive(minSize = 165.dp),
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(itemsToShow, key = { it.contentId }) { video ->
                                VideoGridCard(
                                    video = video,
                                    onClick = { onVideoClick(video) }
                                )
                            }
                            if (feedState.isLoadingMore && isCurrentPage) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(26.dp),
                                            strokeWidth = 2.5.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTimeRangeSheet) {
        var customDaysText by remember { mutableStateOf(feedState.customLatestDays.toString()) }
        var showCustomInput by remember { mutableStateOf(feedState.latestTimeRange == LatestTimeRange.CUSTOM) }

        ModalBottomSheet(onDismissRequest = { showTimeRangeSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 28.dp)
            ) {
                Text(
                    text = "调整视频发布时间范围",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(12.dp))
                LatestTimeRange.entries.forEach { range ->
                    val isSelected = range == feedState.latestTimeRange
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .testTag("time_range_${range.name}")
                            .clickable {
                                if (range == LatestTimeRange.CUSTOM) {
                                    showCustomInput = true
                                } else {
                                    onSelectLatestTimeRange(range)
                                    showTimeRangeSheet = false
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (range == LatestTimeRange.CUSTOM && isSelected) {
                                "手动输入（当前：${feedState.customLatestDays}天内）"
                            } else {
                                range.label
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "已选中",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (showCustomInput) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customDaysText,
                            onValueChange = { value ->
                                customDaysText = value.filter { it.isDigit() }.take(4)
                            },
                            label = { Text("输入天数（如 14、180）") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("custom_days_input")
                        )
                        Button(
                            onClick = {
                                val days = customDaysText.toIntOrNull()
                                if (days != null && days > 0) {
                                    onSelectCustomLatestDays(days)
                                    showTimeRangeSheet = false
                                }
                            },
                            modifier = Modifier.testTag("custom_days_confirm")
                        ) {
                            Text("确定")
                        }
                    }
                }
            }
        }
    }
}
