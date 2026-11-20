package com.herezi.feature.nearby

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.provider.Settings as AndroidSettings
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.herezi.core.domain.Category
import com.herezi.core.domain.GeoBounds
import com.herezi.core.domain.GeoPoint
import com.herezi.core.domain.ListFilter
import com.herezi.core.domain.When
import com.herezi.core.map.LocalMapConfig
import com.herezi.core.map.PinStyle
import com.herezi.core.map.SpotMap
import com.herezi.core.ui.R as UiR
import com.herezi.core.ui.component.HereziToastHost
import com.herezi.core.ui.component.SpotCard
import com.herezi.core.ui.theme.HereziColors
import com.herezi.core.ui.theme.HereziElevation
import com.herezi.core.ui.theme.HereziRadius
import com.herezi.core.ui.theme.HereziSpace
import com.herezi.core.ui.theme.HereziTheme
import com.herezi.core.ui.theme.color
import com.herezi.core.ui.theme.icon
import java.time.Instant

/** 시트 손잡이 영역 높이(위 8 + 손잡이 4 + 아래 8). */
private val HandleArea = 20.dp

/** 시트 접힘 줄 최소 높이(손잡이 포함 약 88, DESIGN §6.5). */
private val HeadlineMinHeight = 68.dp

/** 펼침 높이: 화면의 60%. */
private const val SHEET_MAX_FRACTION = 0.6f
private const val SPIN_MS = 900
private const val FULL_TURN = 360f

/** 지도 탭(S2). [onOpenList] 는 지금 필터와, 다른 동네로 옮겨 찾았으면 그 중심을 넘긴다. */
@Composable
fun MapRoute(
    onSpotClick: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenList: (ListFilter, GeoPoint?) -> Unit,
    viewModel: MapViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val toast = remember { SnackbarHostState() }
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshLocation()
        onPauseOrDispose { }
    }
    LaunchedEffect(state.openLocationSettings) {
        if (state.openLocationSettings) {
            openAppSettings(context)
            viewModel.locationSettingsOpened()
        }
    }
    LaunchedEffect(state.truncatedNotice) {
        if (state.truncatedNotice) {
            viewModel.truncatedShown()
            toast.showSnackbar("볼거리가 많아요. 지도를 확대하면 더 보여요")
        }
    }
    MapScreen(
        state = state,
        toast = toast,
        actions = MapActions(
            onSearch = onSearch,
            onPeriod = viewModel::setPeriod,
            onToggleCategory = viewModel::toggleCategory,
            onAllCategories = viewModel::clearCategories,
            onFreeOnly = viewModel::setFreeOnly,
            onCameraIdle = viewModel::onCameraIdle,
            onSelect = viewModel::select,
            onClearSelection = viewModel::clearSelection,
            onResearch = viewModel::researchHere,
            onRetry = viewModel::retry,
            onMyLocation = viewModel::moveToMyLocation,
            onSpotClick = onSpotClick,
            onOpenList = { onOpenList(state.filter, state.listCenter) },
            onLocationSettings = { openAppSettings(context) },
        ),
    )
}

data class MapActions(
    val onSearch: () -> Unit = {},
    val onPeriod: (When) -> Unit = {},
    val onToggleCategory: (Category) -> Unit = {},
    val onAllCategories: () -> Unit = {},
    val onFreeOnly: (Boolean) -> Unit = {},
    val onCameraIdle: (GeoBounds, Boolean) -> Unit = { _, _ -> },
    val onSelect: (String) -> Unit = {},
    val onClearSelection: () -> Unit = {},
    val onResearch: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onMyLocation: () -> Unit = {},
    val onSpotClick: (String) -> Unit = {},
    val onOpenList: () -> Unit = {},
    val onLocationSettings: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(state: MapUiState, toast: SnackbarHostState, actions: MapActions) {
    val context = LocalContext.current
    val density = LocalDensity.current
    // 스크린리더가 켜져 있으면 목록(시트 펼침)으로 시작한다(S2 접근성).
    val talkBack = remember { context.isTouchExplorationOn() }
    val sheetState = rememberStandardBottomSheetState(
        initialValue = if (talkBack) SheetValue.Expanded else SheetValue.PartiallyExpanded,
    )
    val scaffold = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)
    var headerPx by remember { mutableIntStateOf(0) }
    val peek = HandleArea + with(density) { headerPx.toDp() }.coerceAtLeast(HeadlineMinHeight)
    // 핀을 고르면 시트가 카드 높이로 올라온다.
    LaunchedEffect(state.selectedId) {
        if (state.selectedId != null && sheetState.currentValue == SheetValue.Expanded) sheetState.partialExpand()
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sheetMax = maxHeight * SHEET_MAX_FRACTION
        BottomSheetScaffold(
            scaffoldState = scaffold,
            sheetPeekHeight = peek,
            sheetShape = RoundedCornerShape(topStart = HereziRadius.xl, topEnd = HereziRadius.xl),
            sheetShadowElevation = HereziElevation.e2,
            sheetContainerColor = HereziTheme.colors.bgPrimary,
            sheetDragHandle = { SheetHandle() },
            containerColor = HereziTheme.colors.bgPrimary,
            snackbarHost = { HereziToastHost(toast, Modifier.padding(bottom = peek)) },
            topBar = { FilterArea(state, actions) },
            sheetContent = {
                SheetContent(state, actions, sheetMax, onHeaderHeight = { headerPx = it })
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
                MapLayer(state, actions, peek)
                Overlays(state, actions, peek)
            }
        }
    }
}

/** 상단 필터 영역: 위치명·검색, 기간 탭, 칩 줄, 아래 1px 선. 뒤로 버튼 없음(탭 첫 화면). */
@Composable
private fun FilterArea(state: MapUiState, actions: MapActions) {
    Column(Modifier.background(HereziTheme.colors.bgPrimary)) {
        TopBar(state.locationName, onBack = null, onSearch = actions.onSearch)
        WhenTabs(state.filter.period, actions.onPeriod)
        ChipRow(state.filter, actions.onFreeOnly, actions.onAllCategories, actions.onToggleCategory)
        HorizontalDivider(color = HereziTheme.colors.border)
    }
}

@Composable
private fun MapLayer(state: MapUiState, actions: MapActions, peek: androidx.compose.ui.unit.Dp) {
    if (!state.ready) {
        Box(Modifier.fillMaxSize().background(HereziTheme.colors.bgSecondary))
        return
    }
    SpotMap(
        spots = state.spots,
        selectedId = state.selectedId,
        initialCenter = state.origin,
        myLocation = state.origin.takeUnless { state.locationOff },
        cameraRequest = state.cameraRequest,
        pinStyle = rememberPinStyle(),
        onCameraIdle = actions.onCameraIdle,
        onSpotClick = actions.onSelect,
        onMapClick = actions.onClearSelection,
        contentPadding = PaddingValues(bottom = peek),
    )
}

/** 지도 위에 뜨는 것들: 위치 배너, 다시 찾기, 내 위치, 목록으로 보기. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.Overlays(
    state: MapUiState,
    actions: MapActions,
    peek: androidx.compose.ui.unit.Dp,
) {
    val mapOn = LocalMapConfig.current.enabled
    Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(top = HereziSpace.s3)) {
        if (state.locationOff) Banner(actions.onLocationSettings)
        AnimatedVisibility(
            visible = mapOn && (state.showResearch || state.researching),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            FloatingPill(
                text = "이 지역에서 다시 찾기",
                icon = Icons.Rounded.Refresh,
                onClick = actions.onResearch,
                inverse = false,
                iconModifier = Modifier.rotate(spinAngle(state.researching)),
            )
        }
    }
    val bottom = peek + HereziSpace.s4
    if (mapOn) {
        MyLocationButton(
            onClick = actions.onMyLocation,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = HereziSpace.s4, bottom = bottom),
        )
    }
    FloatingPill(
        text = "목록으로 보기",
        icon = Icons.AutoMirrored.Rounded.FormatListBulleted,
        onClick = actions.onOpenList,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottom),
    )
}

/** 내 위치 버튼: 흰 원 44, elevation.1. */
@Composable
private fun MyLocationButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = HereziTheme.colors.bgPrimary,
        contentColor = HereziTheme.colors.iconPrimary,
        shadowElevation = HereziElevation.e1,
        modifier = modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.NearMe, contentDescription = "내 위치로 이동", modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun SheetHandle() {
    Box(Modifier.fillMaxWidth().height(HandleArea), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .width(36.dp)
                .height(4.dp)
                .background(HereziColors.Gray200, RoundedCornerShape(HereziRadius.full)),
        )
    }
}

/** 시트: 위에는 접힘 줄 또는 고른 카드, 펼치면 결과 목록(지도 중심에서 가까운 순). */
@Composable
private fun SheetContent(
    state: MapUiState,
    actions: MapActions,
    maxHeight: androidx.compose.ui.unit.Dp,
    onHeaderHeight: (Int) -> Unit,
) {
    val now = remember(state.spots) { Instant.now() }
    Column(Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
        Box(Modifier.fillMaxWidth().onSizeChanged { onHeaderHeight(it.height) }) {
            val selected = state.selected
            if (selected != null) {
                SpotCard(selected, now = now, onClick = { actions.onSpotClick(selected.id) })
            } else {
                Headline(state, actions)
            }
        }
        if (state.spots.isNotEmpty()) {
            HorizontalDivider(color = HereziTheme.colors.border)
            LazyColumn(contentPadding = PaddingValues(bottom = HereziSpace.s6)) {
                items(state.spots, key = { it.id }) { spot ->
                    SpotCard(spot, now = now, onClick = { actions.onSpotClick(spot.id) })
                }
            }
        }
    }
}

/** 접힘 줄: "이 지역 볼거리 24개" / 찾는 중… / 오류·빈 + 글자 버튼. 결과가 바뀌면 스크린리더에 알린다. */
@Composable
private fun Headline(state: MapUiState, actions: MapActions) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = HeadlineMinHeight)
            .padding(horizontal = HereziSpace.s5)
            .padding(bottom = HereziSpace.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            state.headline,
            style = HereziTheme.typography.title3,
            color = HereziTheme.colors.textPrimary,
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
        )
        state.headlineAction?.let { action ->
            TextButton(
                onClick = {
                    when (action) {
                        HeadlineAction.RETRY -> actions.onRetry()
                        HeadlineAction.WEEK -> actions.onPeriod(When.WEEK)
                    }
                },
            ) {
                Text(
                    action.label,
                    style = HereziTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                    color = HereziTheme.colors.textSecondary,
                )
            }
        }
    }
}

/** 찾는 중이면 아이콘을 돌린다. 시스템 애니메이션이 꺼져 있으면 멈춘다. */
@Composable
private fun spinAngle(spinning: Boolean): Float {
    val context = LocalContext.current
    val reduced = remember { context.animationsOff() }
    if (!spinning || reduced) return 0f
    val transition = rememberInfiniteTransition(label = "research")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = FULL_TURN,
        animationSpec = infiniteRepeatable(tween(SPIN_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "research-angle",
    )
    return angle
}

/** 핀 토큰: 카테고리 색·아이콘(DESIGN §2.3), 잉크, Pretendard. */
@Composable
private fun rememberPinStyle(): PinStyle {
    val context = LocalContext.current
    return remember(context) {
        PinStyle(
            color = { it.color },
            icon = { it.icon },
            ink = HereziColors.Ink,
            boldTypeface = context.font(UiR.font.pretendard_bold),
            semiBoldTypeface = context.font(UiR.font.pretendard_semibold),
        )
    }
}

private fun Context.font(id: Int): Typeface? = runCatching { ResourcesCompat.getFont(this, id) }.getOrNull()

private fun Context.isTouchExplorationOn(): Boolean =
    (getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager)?.isTouchExplorationEnabled == true

private fun Context.animationsOff(): Boolean =
    AndroidSettings.Global.getFloat(contentResolver, AndroidSettings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

private fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(
            AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ),
    )
}
