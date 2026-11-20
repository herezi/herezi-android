package com.herezi.feature.nearby

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.herezi.core.domain.Category
import com.herezi.core.domain.Spot
import com.herezi.core.domain.When
import com.herezi.core.map.SpotMap
import com.herezi.core.ui.formatDistance
import com.herezi.core.ui.formatPeriod
import com.herezi.core.ui.label

@Composable
fun NearbyRoute(onSpotClick: (String) -> Unit, viewModel: NearbyViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    NearbyScreen(
        state = state,
        onPeriod = viewModel::setPeriod,
        onToggleCategory = viewModel::toggleCategory,
        onFreeOnly = viewModel::setFreeOnly,
        onToggleMap = viewModel::toggleMap,
        onRetry = viewModel::refresh,
        onSpotClick = onSpotClick,
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
    onSpotClick: (String) -> Unit,
) {
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("내 주변", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
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
                Modifier.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Spacer(Modifier.padding(start = 12.dp)) }
                items(Category.entries) { category ->
                    FilterChip(
                        selected = category in state.filter.categories,
                        onClick = { onToggleCategory(category) },
                        label = { Text(category.label) },
                    )
                }
            }
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("무료만", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                Switch(checked = state.filter.freeOnly, onCheckedChange = onFreeOnly)
            }
            Box(Modifier.fillMaxSize()) {
                when {
                    state.error != null ->
                        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.error)
                            TextButton(onClick = onRetry) { Text("다시 시도") }
                        }
                    state.showMap -> SpotMap(spots = state.spots, center = state.center, onSpotClick = onSpotClick)
                    state.spots.isEmpty() && !state.loading ->
                        Text("조건에 맞는 볼거리가 없어요", Modifier.align(Alignment.Center))
                    else -> SpotList(state.spots, onSpotClick)
                }
                if (state.loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun SpotList(spots: List<Spot>, onSpotClick: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(spots, key = { it.id }) { spot ->
            SpotRow(spot, onClick = { onSpotClick(spot.id) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun SpotRow(spot: Spot, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp)) {
        Text(spot.title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        val meta =
            listOfNotNull(
                spot.category.label,
                spot.distanceM?.let(::formatDistance),
                formatPeriod(spot.startsAt, spot.endsAt),
                "무료".takeIf { spot.isFree },
            )
        Text(
            meta.joinToString(" · "),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
