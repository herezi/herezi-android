package com.herezi.feature.nearby

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.herezi.core.domain.Category
import com.herezi.core.domain.ListFilter
import com.herezi.core.domain.SpotQuery
import com.herezi.core.domain.When
import com.herezi.core.ui.component.EmptyIcon
import com.herezi.core.ui.component.ErrorState
import com.herezi.core.ui.component.FilterPill
import com.herezi.core.ui.component.LocationBanner
import com.herezi.core.ui.component.SkeletonBlock
import com.herezi.core.ui.component.SpotCard
import com.herezi.core.ui.component.SpotListSkeleton
import com.herezi.core.ui.component.StatusMessage
import com.herezi.core.ui.label
import com.herezi.core.ui.theme.HereziElevation
import com.herezi.core.ui.theme.HereziRadius
import com.herezi.core.ui.theme.HereziSpace
import com.herezi.core.ui.theme.HereziTheme
import com.herezi.core.ui.theme.color
import java.time.Instant

/** 끝에서 이만큼 남으면 다음 페이지 (S1b). */
private const val PREFETCH_DISTANCE = 5

/**
 * 전체 목록(S1b). [onBack] 이 null 이면 탭 첫 화면(지도 탭)으로 쓰인다.
 * [onSwitchView] 는 목록↔지도 탭 전환에 지금 필터를 넘긴다.
 */
@Composable
fun NearbyRoute(
    onSpotClick: (String) -> Unit,
    onSearch: () -> Unit,
    onSwitchView: (ListFilter) -> Unit,
    onBack: (() -> Unit)?,
    viewModel: NearbyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshLocation()
        onPauseOrDispose { }
    }
    NearbyScreen(
        state = state,
        actions = NearbyActions(
            onBack = onBack,
            onSearch = onSearch,
            onPeriod = viewModel::setPeriod,
            onToggleCategory = viewModel::toggleCategory,
            onAllCategories = viewModel::clearCategories,
            onFreeOnly = viewModel::setFreeOnly,
            onRetry = viewModel::refresh,
            onResetFilters = viewModel::resetFilters,
            onLoadMore = viewModel::loadMore,
            onSpotClick = onSpotClick,
            onSwitchView = { onSwitchView(state.filter) },
            onLocationSettings = {
                context.startActivity(
                    Intent(
                        AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ),
                )
            },
        ),
    )
}

data class NearbyActions(
    val onBack: (() -> Unit)? = null,
    val onSearch: () -> Unit = {},
    val onPeriod: (When) -> Unit = {},
    val onToggleCategory: (Category) -> Unit = {},
    val onAllCategories: () -> Unit = {},
    val onFreeOnly: (Boolean) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onResetFilters: () -> Unit = {},
    val onLoadMore: () -> Unit = {},
    val onSpotClick: (String) -> Unit = {},
    val onSwitchView: () -> Unit = {},
    val onLocationSettings: () -> Unit = {},
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NearbyScreen(state: NearbyUiState, actions: NearbyActions) {
    val list = rememberLazyListState()
    LaunchedEffect(state.filter) { list.scrollToItem(0) }
    LoadMoreEffect(list, state, actions.onLoadMore)
    Box(Modifier.fillMaxSize().background(HereziTheme.colors.bgPrimary)) {
        Column(Modifier.fillMaxSize()) {
            TopBar(state.locationName, actions.onBack, actions.onSearch)
            LazyColumn(Modifier.weight(1f), state = list, contentPadding = PaddingValues(bottom = 88.dp)) {
                item(key = "when") { WhenTabs(state.filter.period, actions.onPeriod) }
                stickyHeader(key = "chips") {
                    Column(Modifier.background(HereziTheme.colors.bgPrimary)) {
                        ChipRow(state.filter, actions.onFreeOnly, actions.onAllCategories, actions.onToggleCategory)
                        if (list.firstVisibleItemIndex >= 1) HorizontalDivider(color = HereziTheme.colors.border)
                    }
                }
                if (state.locationOff) item(key = "banner") { Banner(actions.onLocationSettings) }
                listBody(state, actions)
            }
        }
        FloatingPill(
            text = "지도로 보기",
            icon = Icons.Rounded.Map,
            onClick = actions.onSwitchView,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = HereziSpace.s4),
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.listBody(state: NearbyUiState, actions: NearbyActions) {
    when {
        state.error != null -> item(key = "error") {
            ErrorState(
                onRetry = actions.onRetry,
                description = "주변 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.",
            )
        }
        state.loading -> item(key = "skeleton") { SpotListSkeleton() }
        state.spots.isEmpty() && state.filtered -> item(key = "empty") {
            StatusMessage(
                icon = EmptyIcon,
                title = "조건에 맞는 볼거리가 없어요",
                description = "필터를 바꾸거나 기간을 늘려 보세요.",
                actionLabel = "필터 초기화",
                onAction = actions.onResetFilters,
            )
        }
        state.spots.isEmpty() -> item(key = "empty") {
            StatusMessage(
                icon = EmptyIcon,
                title = "근처에 지금 볼 만한 게 없어요",
                description = "기간을 늘리거나 지도를 움직여 보세요.",
                actionLabel = if (state.filter.period == When.WEEK) "지도로 보기" else "이번 주로 보기",
                onAction = {
                    if (state.filter.period ==
                        When.WEEK
                    ) {
                        actions.onSwitchView()
                    } else {
                        actions.onPeriod(When.WEEK)
                    }
                },
            )
        }
        else -> {
            item(key = "count") { CountLine(state) }
            val now = Instant.now()
            items(state.spots, key = { it.id }) { spot ->
                SpotCard(spot, now = now, onClick = { actions.onSpotClick(spot.id) })
            }
            item(key = "end") { ListEnd(state, actions.onLoadMore) }
        }
    }
}

@Composable
private fun LoadMoreEffect(list: LazyListState, state: NearbyUiState, onLoadMore: () -> Unit) {
    val nearEnd by remember {
        derivedStateOf {
            val last = list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= list.layoutInfo.totalItemsCount - PREFETCH_DISTANCE
        }
    }
    LaunchedEffect(list, state.hasMore, state.moreFailed) {
        snapshotFlow { nearEnd }.collect { if (it && state.hasMore && !state.moreFailed) onLoadMore() }
    }
}

@Composable
internal fun TopBar(locationName: String?, onBack: (() -> Unit)?, onSearch: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = HereziSpace.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val back = onBack
        if (back != null) {
            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "뒤로") }
        } else {
            Spacer(Modifier.width(HereziSpace.s4))
        }
        Box(Modifier.weight(1f)) {
            if (locationName == null) {
                SkeletonBlock(Modifier.width(120.dp).height(20.dp))
            } else {
                Text(
                    locationName,
                    style = HereziTheme.typography.title2,
                    color = HereziTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics {
                        heading()
                        contentDescription = "현재 위치, $locationName"
                    },
                )
            }
        }
        IconButton(onClick = onSearch) {
            Icon(Icons.Rounded.Search, "검색", tint = HereziTheme.colors.iconPrimary)
        }
    }
}

/** 기간 탭 (DESIGN.md §6.3): 큰 글자 탭, 선택 = text.primary Bold, 비선택 = text.tertiary. */
@Composable
internal fun WhenTabs(selected: When, onSelect: (When) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = HereziSpace.s5).heightIn(min = 44.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(HereziSpace.s5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        When.entries.forEach { period ->
            val isSelected = period == selected
            Text(
                period.label,
                style = HereziTheme.typography.title3.let {
                    if (isSelected) it.copy(fontWeight = FontWeight.Bold) else it
                },
                color = if (isSelected) HereziTheme.colors.textPrimary else HereziTheme.colors.textTertiary,
                modifier = Modifier
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(period) })
                    .padding(vertical = HereziSpace.s2),
            )
        }
    }
}

/** 칩 줄 (DESIGN.md §6.2): 무료만 │ 전체 · 카테고리 9종. 홈 카테고리 줄과 같은 [FilterPill]. */
@Composable
internal fun ChipRow(
    filter: ListFilter,
    onFreeOnly: (Boolean) -> Unit,
    onAllCategories: () -> Unit,
    onToggleCategory: (Category) -> Unit,
) {
    LazyRow(
        Modifier.padding(top = HereziSpace.s2, bottom = HereziSpace.s3),
        contentPadding = PaddingValues(horizontal = HereziSpace.s5),
        horizontalArrangement = Arrangement.spacedBy(HereziSpace.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item { FilterPill("무료만", filter.freeOnly, onClick = { onFreeOnly(!filter.freeOnly) }, check = true) }
        item { Box(Modifier.width(1.dp).height(16.dp).background(HereziTheme.colors.border)) }
        item { FilterPill("전체", filter.categories.isEmpty(), onClick = onAllCategories) }
        items(Category.entries) { category ->
            FilterPill(
                category.label,
                category in filter.categories,
                onClick = { onToggleCategory(category) },
                dot = category.color,
            )
        }
    }
}

@Composable
internal fun Banner(onSettings: () -> Unit, modifier: Modifier = Modifier) {
    LocationBanner("설정", onAction = onSettings, modifier = modifier.padding(bottom = HereziSpace.s3))
}

@Composable
private fun CountLine(state: NearbyUiState) {
    val km = SpotQuery.DEFAULT_RADIUS_M / 1000
    val text = "${km}km 안에 ${state.spots.size}개" + if (state.hasMore) " 이상" else ""
    Text(
        text,
        style = HereziTheme.typography.caption,
        color = HereziTheme.colors.textSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .padding(horizontal = HereziSpace.s5, vertical = HereziSpace.s2)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = "${km}km 안에 볼거리 ${state.spots.size}개" + if (state.hasMore) " 이상" else ""
            },
    )
}

@Composable
private fun ListEnd(state: NearbyUiState, onRetry: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = HereziSpace.s6), contentAlignment = Alignment.Center) {
        when {
            state.moreFailed -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("더 불러오지 못했어요", style = HereziTheme.typography.caption, color = HereziTheme.colors.textSecondary)
                TextButton(onClick = onRetry) { Text("다시 시도", style = HereziTheme.typography.caption) }
            }
            state.hasMore -> SkeletonBlock(Modifier.width(120.dp).height(12.dp))
            else -> Text(
                "여기까지예요",
                style = HereziTheme.typography.caption,
                color = HereziTheme.colors.textTertiary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * 플로팅 알약 (DESIGN.md §6.10): 높이 44, 잉크, 흰 caption Bold + 아이콘 16, elevation.1.
 * [inverse] 가 false 면 흰 배경·잉크 글자 변형("이 지역에서 다시 찾기").
 */
@Composable
internal fun FloatingPill(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    inverse: Boolean = true,
    iconModifier: Modifier = Modifier,
) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        shape = RoundedCornerShape(HereziRadius.full),
        color = if (inverse) HereziTheme.colors.bgInverse else HereziTheme.colors.bgPrimary,
        contentColor = if (inverse) HereziTheme.colors.textInverse else HereziTheme.colors.textPrimary,
        shadowElevation = HereziElevation.e1,
        modifier = modifier.heightIn(min = 44.dp),
    ) {
        Row(
            Modifier.padding(horizontal = HereziSpace.s4, vertical = HereziSpace.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, modifier = iconModifier.size(16.dp))
            Spacer(Modifier.width(HereziSpace.s1))
            Text(text, style = HereziTheme.typography.caption.copy(fontWeight = FontWeight.Bold))
        }
    }
}
