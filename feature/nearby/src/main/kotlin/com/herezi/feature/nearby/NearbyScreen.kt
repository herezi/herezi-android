package com.herezi.feature.nearby

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.herezi.core.domain.Category
import com.herezi.core.domain.Spot
import com.herezi.core.domain.When
import com.herezi.core.map.SpotMap
import com.herezi.core.ui.component.EmptyIcon
import com.herezi.core.ui.component.ErrorState
import com.herezi.core.ui.component.LocationBanner
import com.herezi.core.ui.component.SpotCard
import com.herezi.core.ui.component.SpotListSkeleton
import com.herezi.core.ui.component.StatusMessage
import com.herezi.core.ui.label
import com.herezi.core.ui.theme.HereziRadius
import com.herezi.core.ui.theme.HereziSpace
import com.herezi.core.ui.theme.HereziTheme
import com.herezi.core.ui.theme.color
import com.herezi.core.ui.theme.icon
import java.time.Instant

@Composable
fun NearbyRoute(
    onSpotClick: (String) -> Unit,
    onSearch: () -> Unit = {},
    onSaved: () -> Unit = {},
    onSettings: () -> Unit = {},
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
        onPeriod = viewModel::setPeriod,
        onToggleCategory = viewModel::toggleCategory,
        onFreeOnly = viewModel::setFreeOnly,
        onToggleMap = viewModel::toggleMap,
        onRetry = viewModel::refresh,
        onResetFilters = viewModel::resetFilters,
        onSpotClick = onSpotClick,
        entries = NearbyEntries(onSearch, onSaved, onSettings) {
            context.startActivity(
                Intent(
                    AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                ),
            )
        },
    )
}

@Composable
fun NearbyScreen(
    state: NearbyUiState,
    onPeriod: (When) -> Unit,
    onToggleCategory: (Category) -> Unit,
    onFreeOnly: (Boolean) -> Unit,
    onToggleMap: () -> Unit,
    onRetry: () -> Unit,
    onResetFilters: () -> Unit,
    onSpotClick: (String) -> Unit,
    entries: NearbyEntries = NearbyEntries(),
) {
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = HereziSpace.s5, vertical = HereziSpace.s3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "내 주변",
                    style = HereziTheme.typography.title1,
                    color = HereziTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                // 다른 화면 입구. 홈·탭 재설계(S1)는 별도 이슈.
                IconButton(onClick = entries.onSearch) { Icon(Icons.Rounded.Search, "검색") }
                IconButton(onClick = entries.onSaved) { Icon(Icons.Rounded.BookmarkBorder, "저장") }
                IconButton(onClick = entries.onSettings) { Icon(Icons.Rounded.Settings, "설정") }
                TextButton(onClick = onToggleMap) { Text(if (state.showMap) "목록" else "지도") }
            }
            TabRow(selectedTabIndex = state.filter.period.ordinal) {
                When.entries.forEach { period ->
                    Tab(
                        selected = state.filter.period == period,
                        onClick = { onPeriod(period) },
                        text = { Text(period.label) },
                    )
                }
            }
            LazyRow(
                Modifier.padding(vertical = HereziSpace.s2),
                contentPadding = PaddingValues(horizontal = HereziSpace.s5),
                horizontalArrangement = Arrangement.spacedBy(HereziSpace.s2),
            ) {
                items(Category.entries) { category ->
                    FilterChip(
                        selected = category in state.filter.categories,
                        onClick = { onToggleCategory(category) },
                        label = { Text(category.label, style = HereziTheme.typography.caption) },
                        leadingIcon = {
                            Icon(
                                category.icon,
                                contentDescription = null,
                                tint = if (category in state.filter.categories) Color.White else category.color,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        shape = RoundedCornerShape(HereziRadius.full),
                        border = null,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = HereziTheme.colors.bgSecondary,
                            labelColor = HereziTheme.colors.textPrimary,
                            selectedContainerColor = HereziTheme.colors.bgInverse,
                            selectedLabelColor = HereziTheme.colors.textInverse,
                        ),
                    )
                }
            }
            Row(Modifier.padding(horizontal = HereziSpace.s5), verticalAlignment = Alignment.CenterVertically) {
                Text("무료만", style = HereziTheme.typography.caption, modifier = Modifier.weight(1f))
                Switch(checked = state.filter.freeOnly, onCheckedChange = onFreeOnly)
            }
            if (state.locationOff) {
                LocationBanner(
                    "설정",
                    onAction = entries.onLocationSettings,
                    modifier = Modifier.padding(vertical = HereziSpace.s2),
                )
            }
            Box(Modifier.fillMaxSize()) {
                val filtered = state.filter != NearbyFilter()
                when {
                    state.error != null ->
                        ErrorState(
                            onRetry = onRetry,
                            description = "주변 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.",
                        )
                    state.showMap -> SpotMap(spots = state.spots, center = state.center, onSpotClick = onSpotClick)
                    state.spots.isEmpty() && state.loading -> SpotListSkeleton()
                    state.spots.isEmpty() && filtered ->
                        StatusMessage(
                            icon = EmptyIcon,
                            title = "조건에 맞는 볼거리가 없어요",
                            description = "필터를 바꾸거나 기간을 늘려 보세요.",
                            actionLabel = "필터 초기화",
                            onAction = onResetFilters,
                        )
                    state.spots.isEmpty() ->
                        StatusMessage(
                            icon = EmptyIcon,
                            title = "근처에 지금 볼 만한 게 없어요",
                            description = "기간을 늘리거나 지도를 움직여 보세요.",
                            actionLabel = "이번 주로 보기",
                            onAction = { onPeriod(When.WEEK) },
                        )
                    else -> SpotList(state.spots, onSpotClick)
                }
            }
        }
    }
}

@Composable
private fun SpotList(spots: List<Spot>, onSpotClick: (String) -> Unit) {
    val now = remember(spots) { Instant.now() }
    LazyColumn(Modifier.fillMaxSize()) {
        items(spots, key = { it.id }) { spot ->
            SpotCard(spot, now = now, onClick = { onSpotClick(spot.id) })
        }
    }
}

/** 홈에서 다른 화면으로 가는 입구들. */
data class NearbyEntries(
    val onSearch: () -> Unit = {},
    val onSaved: () -> Unit = {},
    val onSettings: () -> Unit = {},
    val onLocationSettings: () -> Unit = {},
)
