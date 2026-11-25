package com.herezi.feature.saved

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.herezi.core.domain.SavedSpot
import com.herezi.core.domain.distanceTo
import com.herezi.core.ui.component.HereziToastHost
import com.herezi.core.ui.component.PrimaryButton
import com.herezi.core.ui.component.SpotCard
import com.herezi.core.ui.component.SpotListSkeleton
import com.herezi.core.ui.component.StatusMessage
import com.herezi.core.ui.theme.HereziSpace
import com.herezi.core.ui.theme.HereziTheme
import java.time.Instant

@Composable
fun SavedRoute(
    onSpotClick: (String) -> Unit,
    onBrowseNearby: () -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: SavedViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toast = remember { SnackbarHostState() }
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { msg ->
            val result = toast.showSnackbar(
                msg.text,
                actionLabel = "되돌리기".takeIf { msg.undoable },
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undo()
        }
    }
    SavedScreen(
        state = state,
        toast = toast,
        onSpotClick = onSpotClick,
        onBrowseNearby = onBrowseNearby,
        onBack = onBack,
        onSignIn = viewModel::signIn,
        onRetry = viewModel::retry,
        onPullRefresh = viewModel::pullRefresh,
        onRemove = viewModel::remove,
        onClearEnded = viewModel::clearEnded,
        onToggleEditing = viewModel::toggleEditing,
        onToggleSelected = viewModel::toggleSelected,
        onDeleteSelected = viewModel::deleteSelected,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList", "LongMethod")
@Composable
fun SavedScreen(
    state: SavedUiState,
    toast: SnackbarHostState,
    onSpotClick: (String) -> Unit,
    onBrowseNearby: () -> Unit,
    onBack: (() -> Unit)?,
    onSignIn: () -> Unit,
    onRetry: () -> Unit,
    onPullRefresh: () -> Unit,
    onRemove: (String) -> Unit,
    onClearEnded: () -> Unit,
    onToggleEditing: () -> Unit,
    onToggleSelected: (String) -> Unit,
    onDeleteSelected: () -> Unit,
) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        containerColor = HereziTheme.colors.bgPrimary,
        snackbarHost = { HereziToastHost(toast) },
        topBar = {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = HereziSpace.s1, vertical = HereziSpace.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "뒤로") }
                } else {
                    Spacer(Modifier.width(HereziSpace.s4))
                }
                Text(
                    "저장",
                    style = HereziTheme.typography.title1,
                    color = HereziTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                if (state.canEdit) {
                    TextButton(onClick = onToggleEditing) {
                        Text(
                            if (state.editing) "완료" else "편집",
                            style = HereziTheme.typography.bodyStrong,
                            color = HereziTheme.colors.textPrimary,
                        )
                    }
                }
            }
        },
        bottomBar = {
            if (state.editing) {
                Box(Modifier.padding(horizontal = HereziSpace.s5, vertical = HereziSpace.s3)) {
                    PrimaryButton(
                        "${state.selected.size}개 지우기",
                        onClick = onDeleteSelected,
                        enabled = state.selected.isNotEmpty(),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state.mode) {
                SavedMode.SIGNED_OUT -> SignInPrompt(state.deviceSavedCount, onSignIn, onBrowseNearby)
                SavedMode.LOADING -> SpotListSkeleton()
                SavedMode.ERROR -> StatusMessage(
                    icon = Icons.Rounded.WifiOff,
                    title = "저장 목록을 불러오지 못했어요",
                    description = "잠시 후 다시 시도해 주세요.",
                    actionLabel = "다시 시도",
                    onAction = onRetry,
                )
                SavedMode.EMPTY -> PullToRefreshBox(state.refreshing, onPullRefresh, Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            StatusMessage(
                                icon = Icons.Rounded.Bookmark,
                                title = "저장한 볼거리가 없어요",
                                description = "마음에 드는 곳을 저장해 두면 여기에 모여요.",
                                actionLabel = "주변 둘러보기",
                                onAction = onBrowseNearby,
                            )
                        }
                    }
                }
                SavedMode.LIST -> PullToRefreshBox(state.refreshing, onPullRefresh, Modifier.fillMaxSize()) {
                    SavedList(state, onSpotClick, onRemove, onToggleSelected, onClearEnded = { confirmClear = true })
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            text = { Text("끝난 볼거리 ${state.ended.size}개를 지울까요?", style = HereziTheme.typography.body) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClearEnded()
                }) { Text("지우기") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun SavedList(
    state: SavedUiState,
    onSpotClick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onToggleSelected: (String) -> Unit,
    onClearEnded: () -> Unit,
) {
    val now = remember(state.active, state.ended) { Instant.now() }
    LazyColumn(Modifier.fillMaxSize()) {
        items(state.active, key = { it.spot.id }) { SavedItem(it, state, now, onSpotClick, onRemove, onToggleSelected) }
        if (state.ended.isNotEmpty()) {
            item(key = "ended-header") {
                Row(
                    Modifier.fillMaxWidth().padding(start = HereziSpace.s5, end = HereziSpace.s2, top = HereziSpace.s6),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "끝난 볼거리 ${state.ended.size}",
                        style = HereziTheme.typography.caption,
                        color = HereziTheme.colors.textSecondary,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    TextButton(onClick = onClearEnded) {
                        Text(
                            "모두 지우기",
                            style = HereziTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                            color = HereziTheme.colors.textSecondary,
                        )
                    }
                }
            }
            items(state.ended, key = {
                it.spot.id
            }) { SavedItem(it, state, now, onSpotClick, onRemove, onToggleSelected) }
            item(key = "ended-notice") {
                Text(
                    "끝난 지 90일이 지나면 자동으로 지워져요.",
                    style = HereziTheme.typography.caption,
                    color = HereziTheme.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = HereziSpace.s5, vertical = HereziSpace.s3),
                )
            }
        }
    }
}

@Composable
private fun SavedItem(
    item: SavedSpot,
    state: SavedUiState,
    now: Instant,
    onSpotClick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onToggleSelected: (String) -> Unit,
) {
    val id = item.spot.id
    var menu by remember { mutableStateOf(false) }
    val selected = id in state.selected
    Box {
        SpotCard(
            spot = item.spot,
            now = now,
            distanceM = state.origin?.point?.distanceTo(item.spot.location),
            showEnded = true,
            onClick = { if (state.editing) onToggleSelected(id) else onSpotClick(id) },
            onLongClick = { if (!state.editing) menu = true },
            modifier = Modifier.semantics {
                customActions = listOf(
                    CustomAccessibilityAction("삭제") {
                        onRemove(id)
                        true
                    },
                )
            },
            leading = if (state.editing) {
                {
                    Icon(
                        if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (selected) HereziTheme.colors.iconPrimary else HereziTheme.colors.iconSecondary,
                        modifier = Modifier.padding(end = HereziSpace.s3).size(24.dp).align(Alignment.CenterVertically),
                    )
                }
            } else {
                null
            },
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("삭제") }, onClick = {
                menu = false
                onRemove(id)
            })
        }
    }
}

/** 로그인 전 본문 (S4 `SignInPrompt`). 브랜드 버튼은 S7 시트에만 둔다. */
@Composable
private fun SignInPrompt(deviceSavedCount: Int, onSignIn: () -> Unit, onBrowseNearby: () -> Unit) {
    StatusMessage(
        icon = Icons.Rounded.Bookmark,
        title = "로그인하면 저장할 수 있어요",
        description = if (deviceSavedCount > 0) {
            "이 기기에 저장한 볼거리 ${deviceSavedCount}개가 있어요. 로그인하면 계정으로 옮겨 드려요."
        } else {
            "저장한 볼거리를 어느 기기에서든 다시 볼 수 있어요."
        },
        actionLabel = "로그인",
        onAction = onSignIn,
        secondaryLabel = "주변 둘러보기",
        onSecondary = onBrowseNearby,
    )
}
