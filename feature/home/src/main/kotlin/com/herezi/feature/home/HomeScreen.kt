package com.herezi.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.herezi.core.domain.Category
import com.herezi.core.domain.FeedSection
import com.herezi.core.domain.FeedSectionKind
import com.herezi.core.domain.HomeFeed
import com.herezi.core.domain.ListFilter
import com.herezi.core.domain.Spot
import com.herezi.core.domain.When
import com.herezi.core.ui.component.CategoryDot
import com.herezi.core.ui.component.ErrorState
import com.herezi.core.ui.component.FilterPill
import com.herezi.core.ui.component.FreeBadge
import com.herezi.core.ui.component.HereziBadge
import com.herezi.core.ui.component.HereziToastHost
import com.herezi.core.ui.component.LocationBanner
import com.herezi.core.ui.component.PosterStyle
import com.herezi.core.ui.component.SecondaryButton
import com.herezi.core.ui.component.SkeletonBlock
import com.herezi.core.ui.component.SpotImage
import com.herezi.core.ui.component.StatusMessage
import com.herezi.core.ui.component.spokenCard
import com.herezi.core.ui.formatDistance
import com.herezi.core.ui.formatEndsToday
import com.herezi.core.ui.formatHeroBadge
import com.herezi.core.ui.formatHeroPeriod
import com.herezi.core.ui.label
import com.herezi.core.ui.spokenDistance
import com.herezi.core.ui.theme.HereziRadius
import com.herezi.core.ui.theme.HereziSpace
import com.herezi.core.ui.theme.HereziTheme
import com.herezi.core.ui.theme.color
import com.herezi.core.ui.theme.tabularNumbers
import java.time.Instant

@Composable
fun HomeRoute(
    onSpotClick: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenList: (ListFilter) -> Unit,
    onOpenMap: () -> Unit,
    onLocationSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toast = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.messages.collect { toast.showSnackbar(it) } }
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose { }
    }
    HomeScreen(
        state = state,
        toast = toast,
        actions = HomeActions(
            onSpotClick = onSpotClick,
            onSearch = onSearch,
            onOpenList = onOpenList,
            onOpenMap = onOpenMap,
            onLocationSettings = onLocationSettings,
            onRetry = viewModel::refresh,
            onRefresh = viewModel::refresh,
            onToggleSave = viewModel::toggleSave,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(state: HomeUiState, toast: SnackbarHostState, actions: HomeActions) {
    val list = rememberLazyListState()
    Scaffold(containerColor = HereziTheme.colors.bgPrimary, snackbarHost = { HereziToastHost(toast) }) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = actions.onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(Modifier.fillMaxSize(), state = list) {
                item(key = "top") { TopBar(state.locationName, actions.onSearch) }
                val feed = state.feed
                when {
                    state.error -> item(key = "error") {
                        ErrorState(
                            onRetry = actions.onRetry,
                            description = "주변 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.",
                        )
                    }
                    feed == null -> item(key = "loading") { LoadingFeed(actions) }
                    feed.isEmpty -> item(key = "empty") {
                        StatusMessage(
                            icon = Icons.Rounded.SearchOff,
                            title = "근처에 이번 주 볼거리가 없어요",
                            description = "지도에서 다른 동네를 둘러보세요.",
                            actionLabel = "지도로 보기",
                            onAction = actions.onOpenMap,
                        )
                    }
                    else -> feedItems(feed, state, actions)
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.feedItems(
    feed: HomeFeed,
    state: HomeUiState,
    actions: HomeActions,
) {
    item(key = "headline") { Headline(feed, state.locationKnown) }
    if (!state.locationKnown) {
        item(key = "banner") {
            LocationBanner(
                "설정",
                onAction = actions.onLocationSettings,
                modifier = Modifier.padding(top = HereziSpace.s4),
            )
        }
    }
    item(key = "categories") { CategoryRow(actions.onOpenList) }
    feed.hero?.let { hero ->
        item(key = "hero") {
            Column(Modifier.padding(top = HereziSpace.s8)) {
                SectionHeader(hero.title) { actions.onOpenList(hero.listFilter) }
                Spacer(Modifier.height(HereziSpace.s3))
                HeroCard(hero.spot, state.now, saved = hero.spot.id in state.savedIds, actions)
            }
        }
    }
    items(feed.sections, key = { it.kind.name }) { section ->
        Column(Modifier.padding(top = HereziSpace.s8)) {
            SectionHeader(section.kind.title) { actions.onOpenList(section.listFilter) }
            Spacer(Modifier.height(HereziSpace.s3))
            TileRow(section, state, actions)
        }
    }
    item(key = "all") {
        Column(
            Modifier.fillMaxWidth().padding(
                horizontal = HereziSpace.s5,
            ).padding(top = HereziSpace.s8, bottom = HereziSpace.s8),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SecondaryButton("전체 목록 보기", onClick = { actions.onOpenList(ListFilter(period = When.WEEK)) })
            Spacer(Modifier.height(HereziSpace.s2))
            Text(
                "3km 안 볼거리를 거리순으로 모두 볼 수 있어요",
                style = HereziTheme.typography.caption,
                color = HereziTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun TopBar(locationName: String?, onSearch: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = HereziSpace.s5, end = HereziSpace.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.LocationOn, null, tint = HereziTheme.colors.textSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(HereziSpace.s1))
        Box(Modifier.weight(1f)) {
            if (locationName == null) {
                SkeletonBlock(Modifier.width(120.dp).height(16.dp))
            } else {
                Text(
                    locationName,
                    style = HereziTheme.typography.bodyStrong,
                    color = HereziTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { contentDescription = "현재 위치, $locationName" },
                )
            }
        }
        IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "검색", tint = HereziTheme.colors.iconPrimary) }
    }
}

@Composable
private fun Headline(feed: HomeFeed, locationKnown: Boolean) {
    val (first, second) = homeHeadline(feed, locationKnown)
    Text(
        "$first\n$second",
        style = HereziTheme.typography.display.tabularNumbers(),
        color = HereziTheme.colors.textPrimary,
        modifier = Modifier
            .padding(horizontal = HereziSpace.s5)
            .padding(top = HereziSpace.s2)
            .semantics {
                heading()
                contentDescription = "$first $second"
                liveRegion = LiveRegionMode.Polite
            },
    )
}

/** 홈 카테고리 줄: 글자 알약(S1b 칩과 같은 [FilterPill]). `전체`만 선택 모양. 누르면 S1b 로 이동. */
@Composable
private fun CategoryRow(onOpenList: (ListFilter) -> Unit) {
    LazyRow(
        Modifier.padding(top = HereziSpace.s5),
        contentPadding = PaddingValues(horizontal = HereziSpace.s5),
        horizontalArrangement = Arrangement.spacedBy(HereziSpace.s2),
    ) {
        item { FilterPill("전체", selected = true, onClick = { onOpenList(ListFilter()) }) }
        items(Category.entries) { category ->
            FilterPill(
                category.label,
                selected = false,
                onClick = { onOpenList(ListFilter(categories = setOf(category))) },
                dot = category.color,
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, onSeeAll: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(start = HereziSpace.s5, end = HereziSpace.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = HereziTheme.typography.title2,
            color = HereziTheme.colors.textPrimary,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        TextButton(
            onClick = onSeeAll,
            modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "$title, 전체 보기" },
        ) {
            Text("전체 보기", style = HereziTheme.typography.caption, color = HereziTheme.colors.textSecondary)
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                null,
                tint = HereziTheme.colors.textSecondary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

private const val HERO_RATIO = 4f / 7f
private const val HERO_META_ALPHA = 0.92f
private val HeroGradient = Brush.verticalGradient(
    0f to Color.Transparent,
    0.3f to Color.Transparent,
    0.6f to Color(0x99191F28),
    1f to Color(0xD9191F28),
)

/** 히어로 카드 (DESIGN.md §6.13). */
@Composable
private fun HeroCard(spot: Spot, now: Instant, saved: Boolean, actions: HomeActions) {
    val meta = listOfNotNull(
        spot.category.label,
        spot.distanceM?.let(::formatDistance),
        formatHeroPeriod(spot.startsAt, spot.endsAt, now),
        "무료".takeIf { spot.isFree },
    ).joinToString(" · ")
    val badge = formatHeroBadge(spot.startsAt, spot.endsAt, now)
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = HereziSpace.s5)) {
        val height = (maxWidth * HERO_RATIO).coerceAtLeast(200.dp)
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = height)
                .clip(RoundedCornerShape(HereziRadius.lg)),
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .combinedClickable(onClick = { actions.onSpotClick(spot.id) })
                    .clearAndSetSemantics {
                        contentDescription = listOfNotNull(
                            spot.title,
                            badge,
                            spot.category.label,
                            spot.distanceM?.let(::spokenDistance),
                            "무료".takeIf { spot.isFree },
                        ).joinToString(", ")
                        onClick {
                            actions.onSpotClick(spot.id)
                            true
                        }
                    },
            ) {
                SpotImage(spot, Modifier.matchParentSize(), style = PosterStyle.Backdrop)
                Box(Modifier.matchParentSize().background(HeroGradient))
            }
            Column(
                Modifier.align(Alignment.BottomStart).padding(HereziSpace.s4).padding(top = height * 0.5f),
            ) {
                Text(
                    spot.title,
                    style = HereziTheme.typography.title1,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(HereziSpace.s1))
                Text(
                    meta,
                    style = HereziTheme.typography.caption.tabularNumbers(),
                    color = Color.White.copy(alpha = HERO_META_ALPHA),
                )
            }
            if (badge != null) {
                Text(
                    badge,
                    style = HereziTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                    color = HereziTheme.colors.textPrimary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .background(Color.White, RoundedCornerShape(HereziRadius.full))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .clearAndSetSemantics { },
                )
            }
            IconButton(
                onClick = { actions.onToggleSave(spot) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .semantics { stateDescription = if (saved) "저장됨" else "" },
            ) {
                Box(
                    Modifier.size(36.dp).background(Color.White.copy(alpha = 0.9f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        contentDescription = if (saved) "저장 취소" else "저장",
                        tint = HereziTheme.colors.iconPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

private val TileWidth = 148.dp
private val TileImageHeight = 112.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TileRow(section: FeedSection, state: HomeUiState, actions: HomeActions) {
    val rowState = rememberLazyListState()
    LazyRow(
        state = rowState,
        flingBehavior = rememberSnapFlingBehavior(rowState),
        contentPadding = PaddingValues(horizontal = HereziSpace.s5),
        horizontalArrangement = Arrangement.spacedBy(HereziSpace.s3),
        modifier = Modifier.semantics {
            collectionInfo = CollectionInfo(rowCount = 1, columnCount = section.items.size)
        },
    ) {
        items(section.items, key = { it.id }) { spot ->
            SpotTile(spot, section.kind, saved = spot.id in state.savedIds, actions)
        }
    }
}

/** 가로 카드 (DESIGN.md §6.14). 저장은 길게 누르기 메뉴로. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpotTile(spot: Spot, kind: FeedSectionKind, saved: Boolean, actions: HomeActions) {
    var menu by remember { mutableStateOf(false) }
    val saveLabel = if (saved) "저장 취소" else "저장"
    val endsBadge = spot.endsAt?.takeIf { kind == FeedSectionKind.ENDING_TODAY }?.let { formatEndsToday(it) }
    val freeBadge = kind != FeedSectionKind.FREE && spot.isFree && endsBadge == null
    Box {
        Column(
            Modifier
                .width(TileWidth)
                .combinedClickable(onClick = { actions.onSpotClick(spot.id) }, onLongClick = { menu = true })
                .clearAndSetSemantics {
                    contentDescription = spokenCard(spot, spot.distanceM, ended = false)
                    onClick {
                        actions.onSpotClick(spot.id)
                        true
                    }
                    onLongClick(label = saveLabel) {
                        actions.onToggleSave(spot)
                        true
                    }
                },
        ) {
            Box(Modifier.width(TileWidth).height(TileImageHeight).clip(RoundedCornerShape(HereziRadius.md))) {
                val hasBadge = endsBadge != null || freeBadge
                SpotImage(
                    spot,
                    Modifier.matchParentSize(),
                    style = if (hasBadge) PosterStyle.NoLabel else PosterStyle.Full,
                )
                Box(Modifier.align(Alignment.TopStart).padding(HereziSpace.s2)) {
                    when {
                        endsBadge != null -> HereziBadge(endsBadge, HereziTheme.colors.textPrimary, Color.White)
                        freeBadge -> FreeBadge()
                    }
                }
            }
            Spacer(Modifier.height(HereziSpace.s2))
            Text(
                spot.title,
                style = HereziTheme.typography.bodyStrong,
                color = HereziTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryDot(spot.category)
                Spacer(Modifier.width(HereziSpace.s1))
                Text(
                    listOfNotNull(spot.category.label, spot.distanceM?.let(::formatDistance)).joinToString(" · "),
                    style = HereziTheme.typography.caption.tabularNumbers(),
                    color = HereziTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(saveLabel) }, onClick = {
                menu = false
                actions.onToggleSave(spot)
            })
        }
    }
}

/** 로딩 (S1): 헤드라인 스켈레톤, 카테고리 줄은 바로, 섹션 제목·히어로·타일 자리. */
@Composable
private fun LoadingFeed(actions: HomeActions) {
    Column {
        Column(Modifier.padding(horizontal = HereziSpace.s5, vertical = HereziSpace.s2).clearAndSetSemantics { }) {
            SkeletonBlock(Modifier.width(170.dp).height(26.dp))
            Spacer(Modifier.height(HereziSpace.s2))
            SkeletonBlock(Modifier.width(230.dp).height(26.dp))
        }
        CategoryRow(actions.onOpenList)
        Column(Modifier.padding(HereziSpace.s5).clearAndSetSemantics { }) {
            Spacer(Modifier.height(HereziSpace.s4))
            SkeletonBlock(Modifier.width(150.dp).height(20.dp))
            Spacer(Modifier.height(HereziSpace.s3))
            SkeletonBlock(Modifier.fillMaxWidth().height(200.dp), RoundedCornerShape(HereziRadius.lg))
            Spacer(Modifier.height(HereziSpace.s8))
            SkeletonBlock(Modifier.width(150.dp).height(20.dp))
            Spacer(Modifier.height(HereziSpace.s3))
            Row(horizontalArrangement = Arrangement.spacedBy(HereziSpace.s3)) {
                repeat(3) {
                    SkeletonBlock(
                        Modifier.width(TileWidth).height(TileImageHeight),
                        RoundedCornerShape(HereziRadius.md),
                    )
                }
            }
        }
    }
}
