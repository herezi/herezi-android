package com.herezi.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.herezi.core.ui.component.ErrorState
import com.herezi.core.ui.component.HereziToastHost
import com.herezi.core.ui.component.SpotCard
import com.herezi.core.ui.component.SpotListSkeleton
import com.herezi.core.ui.component.StatusMessage
import com.herezi.core.ui.theme.HereziRadius
import com.herezi.core.ui.theme.HereziSize
import com.herezi.core.ui.theme.HereziSpace
import com.herezi.core.ui.theme.HereziTheme
import java.time.Instant

@Composable
fun SearchRoute(onBack: () -> Unit, onSpotClick: (String) -> Unit, viewModel: SearchViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toast = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.messages.collect { toast.showSnackbar(it) } }
    SearchScreen(
        state = state,
        toast = toast,
        onBack = onBack,
        onInput = viewModel::onInput,
        onClear = viewModel::clearInput,
        onSearch = viewModel::search,
        onRecent = viewModel::search,
        onRemoveRecent = viewModel::removeRecent,
        onClearRecent = viewModel::clearRecent,
        onRetry = viewModel::retry,
        onSpotClick = onSpotClick,
    )
}

@Composable
fun SearchScreen(
    state: SearchUiState,
    toast: SnackbarHostState,
    onBack: () -> Unit,
    onInput: (String) -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
    onRecent: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearRecent: () -> Unit,
    onRetry: () -> Unit,
    onSpotClick: (String) -> Unit,
) {
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { focus.requestFocus() }
    Scaffold(
        containerColor = HereziTheme.colors.bgPrimary,
        snackbarHost = { HereziToastHost(toast) },
        topBar = {
            Row(
                Modifier.fillMaxWidth().padding(start = HereziSpace.s1, end = HereziSpace.s5, top = HereziSpace.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "뒤로") }
                SearchBar(
                    value = state.input,
                    onValueChange = onInput,
                    onClear = onClear,
                    onSearch = {
                        focusManager.clearFocus()
                        onSearch()
                    },
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val r = state.result) {
                SearchResult.Idle -> RecentList(state.recent, onRecent, onRemoveRecent, onClearRecent)
                is SearchResult.Loading -> SpotListSkeleton(Modifier.padding(top = HereziSpace.s2))
                is SearchResult.Empty ->
                    StatusMessage(
                        icon = Icons.Rounded.Search,
                        title = "'${r.q}'에 맞는 볼거리가 없어요",
                        description = "지금 진행 중인 것만 찾아요. 다른 이름으로 찾아보세요.",
                    )
                is SearchResult.Failed -> ErrorState(onRetry = onRetry)
                is SearchResult.Found -> {
                    val now = remember(r) { Instant.now() }
                    LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            Text(
                                "'${r.q}' 검색 결과 ${r.items.size}개",
                                style = HereziTheme.typography.caption,
                                color = HereziTheme.colors.textSecondary,
                                modifier = Modifier
                                    .padding(horizontal = HereziSpace.s5, vertical = HereziSpace.s3)
                                    .semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                        items(r.items, key = { it.id }) { spot ->
                            SpotCard(spot, now = now, onClick = { onSpotClick(spot.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .heightIn(min = HereziSize.buttonMd)
            .background(HereziTheme.colors.bgSecondary, RoundedCornerShape(HereziRadius.md))
            .padding(start = HereziSpace.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = HereziTheme.colors.iconSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(HereziSpace.s2))
        Box(Modifier.weight(1f).padding(vertical = HereziSpace.s3)) {
            if (value.isEmpty()) {
                Text("축제, 전시, 장소 이름으로 찾기", style = HereziTheme.typography.body, color = HereziTheme.colors.textTertiary)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = HereziTheme.typography.body.copy(color = HereziTheme.colors.textPrimary),
                cursorBrush = SolidColor(HereziTheme.colors.textPrimary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "볼거리 검색" },
            )
        }
        if (value.isNotEmpty()) {
            IconButton(onClick = onClear) {
                Icon(
                    Icons.Rounded.Cancel,
                    "검색어 지우기",
                    tint = HereziTheme.colors.iconSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            Spacer(Modifier.width(HereziSpace.s3))
        }
    }
}

@Composable
private fun RecentList(
    recent: List<String>,
    onRecent: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    if (recent.isEmpty()) {
        Text(
            "최근 검색이 없어요",
            style = HereziTheme.typography.body,
            color = HereziTheme.colors.textTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = HereziSpace.s10),
        )
        return
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = HereziSpace.s5, end = HereziSpace.s2, top = HereziSpace.s4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "최근 검색",
                style = HereziTheme.typography.caption,
                color = HereziTheme.colors.textSecondary,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            TextButton(onClick = onClearAll) {
                Text(
                    "전체 삭제",
                    style = HereziTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                    color = HereziTheme.colors.textSecondary,
                )
            }
        }
        LazyColumn {
            items(recent, key = { it }) { q ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        onRecent(q)
                    }.padding(start = HereziSpace.s5, end = HereziSpace.s2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Schedule,
                        null,
                        tint = HereziTheme.colors.iconSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(HereziSpace.s3))
                    Text(
                        q,
                        style = HereziTheme.typography.body,
                        color = HereziTheme.colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onRemove(q) }) {
                        Icon(
                            Icons.Rounded.Close,
                            "$q 지우기",
                            tint = HereziTheme.colors.iconSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}
