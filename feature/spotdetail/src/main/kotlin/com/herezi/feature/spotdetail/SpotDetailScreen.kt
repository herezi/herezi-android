package com.herezi.feature.spotdetail

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.herezi.core.domain.Spot
import com.herezi.core.ui.HereziLinks
import com.herezi.core.ui.component.CategoryDot
import com.herezi.core.ui.component.EndedBadge
import com.herezi.core.ui.component.ErrorState
import com.herezi.core.ui.component.FreeBadge
import com.herezi.core.ui.component.HereziToastHost
import com.herezi.core.ui.component.PrimaryButton
import com.herezi.core.ui.component.SecondaryButton
import com.herezi.core.ui.component.SkeletonBlock
import com.herezi.core.ui.component.SpotImage
import com.herezi.core.ui.component.StatusMessage
import com.herezi.core.ui.formatDetailPeriod
import com.herezi.core.ui.formatDetailPeriodNote
import com.herezi.core.ui.formatDetailTime
import com.herezi.core.ui.formatDistance
import com.herezi.core.ui.formatEndedNotice
import com.herezi.core.ui.formatFetchedAt
import com.herezi.core.ui.label
import com.herezi.core.ui.link.openInAppBrowser
import com.herezi.core.ui.sourceLabel
import com.herezi.core.ui.spokenDateTime
import com.herezi.core.ui.spokenPeriod
import com.herezi.core.ui.theme.HereziRadius
import com.herezi.core.ui.theme.HereziSpace
import com.herezi.core.ui.theme.HereziTheme
import com.herezi.core.ui.theme.tabularNumbers
import java.time.Instant

/** 상세 화면 밖으로 나가는 동작. :app 이 내비게이션을, 이 모듈이 인텐트를 맡는다. */
@Composable
fun SpotDetailRoute(onBack: () -> Unit, onBrowseNearby: () -> Unit, viewModel: SpotDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val toast = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.messages.collect { toast.showSnackbar(it) } }
    SpotDetailScreen(
        state = state,
        saved = saved,
        toast = toast,
        actions = SpotDetailActions(
            onBack = onBack,
            onRetry = viewModel::load,
            onToggleSave = viewModel::toggleSave,
            onShare = { share(context, it) },
            onDirections = { spot, distance -> openNaver(context, spot, distance, place = false) },
            onShowPlace = { spot -> openNaver(context, spot, null, place = true) },
            onOpenSource = { url -> openInAppBrowser(context, url) },
            onReport = { spot -> sendReport(context, spot) },
            onCopyAddress = { address ->
                copy(context, address)
                viewModel.addressCopied()
            },
            onBrowseNearby = onBrowseNearby,
            onRemoveSaved = viewModel::removeFromSaved,
        ),
    )
}

data class SpotDetailActions(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onToggleSave: () -> Unit = {},
    val onShare: (Spot) -> Unit = {},
    val onDirections: (Spot, Int?) -> Unit = { _, _ -> },
    val onShowPlace: (Spot) -> Unit = {},
    val onOpenSource: (String) -> Unit = {},
    val onReport: (Spot) -> Unit = {},
    val onCopyAddress: (String) -> Unit = {},
    val onBrowseNearby: () -> Unit = {},
    val onRemoveSaved: () -> Unit = {},
)

@Composable
fun SpotDetailScreen(state: SpotDetailUiState, saved: Boolean, toast: SnackbarHostState, actions: SpotDetailActions) {
    val loaded = state as? SpotDetailUiState.Loaded
    Scaffold(
        containerColor = HereziTheme.colors.bgPrimary,
        snackbarHost = { HereziToastHost(toast) },
        bottomBar = { if (loaded != null) BottomBar(loaded, actions) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val scroll = rememberScrollState()
            var imageHeight by remember { mutableIntStateOf(0) }
            when (state) {
                SpotDetailUiState.Loading -> DetailSkeleton()
                is SpotDetailUiState.NotFound ->
                    StatusMessage(
                        icon = Icons.Rounded.SearchOff,
                        title = "끝났거나 없는 볼거리예요",
                        description = "목록에서 다른 볼거리를 찾아보세요.",
                        actionLabel = "주변 보기",
                        onAction = actions.onBrowseNearby,
                        secondaryLabel = "저장에서 지우기".takeIf { state.saved },
                        onSecondary = actions.onRemoveSaved,
                        modifier = Modifier.padding(top = 56.dp),
                    )
                SpotDetailUiState.Failed -> ErrorState(
                    onRetry = actions.onRetry,
                    modifier = Modifier.padding(top = 56.dp),
                )
                is SpotDetailUiState.Loaded ->
                    DetailContent(state, actions, scroll, onImageHeight = { imageHeight = it })
            }
            TopBar(
                spot = loaded?.spot,
                saved = saved,
                solid = loaded == null || (imageHeight > 0 && scroll.value >= imageHeight),
                actions = actions,
            )
        }
    }
}

@Composable
private fun TopBar(spot: Spot?, saved: Boolean, solid: Boolean, actions: SpotDetailActions) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (solid) Modifier.background(HereziTheme.colors.bgPrimary) else Modifier)
            .padding(horizontal = HereziSpace.s1, vertical = HereziSpace.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarIcon(Icons.AutoMirrored.Rounded.ArrowBack, "뒤로", solid, onClick = actions.onBack)
        Text(
            if (solid && spot != null) spot.title else "",
            style = HereziTheme.typography.title3,
            color = HereziTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = HereziSpace.s2),
        )
        if (spot != null) {
            BarIcon(Icons.Rounded.Share, "공유", solid) { actions.onShare(spot) }
            BarIcon(
                if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                if (saved) "저장 취소" else "저장",
                solid,
                state = if (saved) "저장됨" else null,
                onClick = actions.onToggleSave,
            )
        }
    }
}

@Composable
private fun BarIcon(icon: ImageVector, label: String, solid: Boolean, state: String? = null, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.semantics { if (state != null) stateDescription = state },
    ) {
        Box(
            Modifier
                .size(36.dp)
                .then(
                    if (solid) {
                        Modifier
                    } else {
                        Modifier.background(
                            HereziTheme.colors.bgPrimary.copy(alpha = 0.9f),
                            CircleShape,
                        )
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = HereziTheme.colors.iconPrimary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun DetailContent(
    state: SpotDetailUiState.Loaded,
    actions: SpotDetailActions,
    scroll: ScrollState,
    onImageHeight: (Int) -> Unit,
) {
    val spot = state.spot
    val now = remember(spot) { Instant.now() }
    Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
        SpotImage(
            spot,
            modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).onSizeChanged { onImageHeight(it.height) },
        )
        Column(Modifier.padding(horizontal = HereziSpace.s5).padding(top = HereziSpace.s5)) {
            if (spot.isEnded || spot.isFree) {
                Row(horizontalArrangement = Arrangement.spacedBy(HereziSpace.s2)) {
                    if (spot.isEnded) EndedBadge()
                    if (spot.isFree) FreeBadge()
                }
                Spacer(Modifier.height(HereziSpace.s2))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryDot(spot.category)
                Spacer(Modifier.width(HereziSpace.s1))
                Text(
                    spot.category.label,
                    style = HereziTheme.typography.caption,
                    color = HereziTheme.colors.textSecondary,
                )
            }
            Spacer(Modifier.height(HereziSpace.s1))
            Text(
                spot.title,
                style = HereziTheme.typography.title1,
                color = HereziTheme.colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            if (spot.isEnded) {
                Spacer(Modifier.height(HereziSpace.s4))
                Text(
                    formatEndedNotice(spot.endsAt),
                    style = HereziTheme.typography.caption,
                    color = HereziTheme.colors.textPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(HereziTheme.colors.bgSecondary, RoundedCornerShape(HereziRadius.md))
                        .padding(HereziSpace.s3),
                )
            }
            Spacer(Modifier.height(HereziSpace.s5))
            InfoRows(state, now, actions)
        }
        Spacer(Modifier.height(HereziSpace.s6))
        Box(Modifier.fillMaxWidth().height(8.dp).background(HereziTheme.colors.bgSecondary))
        spot.summary?.let {
            Text(
                it,
                style = HereziTheme.typography.body,
                color = HereziTheme.colors.textPrimary,
                modifier = Modifier.padding(horizontal = HereziSpace.s5, vertical = HereziSpace.s6),
            )
        } ?: Spacer(Modifier.height(HereziSpace.s6))
        SourceCard(spot, now, actions.onOpenSource)
        Spacer(Modifier.height(HereziSpace.s3))
        ReportRow(onClick = { actions.onReport(spot) })
        Spacer(Modifier.height(HereziSpace.s6))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InfoRows(state: SpotDetailUiState.Loaded, now: Instant, actions: SpotDetailActions) {
    val spot = state.spot
    Column(verticalArrangement = Arrangement.spacedBy(HereziSpace.s3)) {
        InfoRow(
            Icons.Rounded.CalendarToday,
            formatDetailPeriod(spot.startsAt, spot.endsAt),
            formatDetailPeriodNote(spot.endsAt, now),
            spoken = "기간, ${spokenPeriod(spot.startsAt, spot.endsAt)}",
        )
        formatDetailTime(spot.startsAt, spot.endsAt)?.let {
            InfoRow(Icons.Rounded.Schedule, it, null, spoken = "시간, $it")
        }
        val place = spot.placeName ?: spot.address
        if (place != null) {
            val address = spot.address?.takeIf { spot.placeName != null }
            InfoRow(
                Icons.Rounded.LocationOn,
                place,
                address,
                spoken = listOfNotNull("장소", place, address).joinToString(", "),
                modifier = spot.address?.let { a ->
                    Modifier.combinedClickable(onClick = {}, onLongClick = { actions.onCopyAddress(a) })
                } ?: Modifier,
            )
        }
        val distance = state.distanceM
        val origin = state.origin
        if (distance != null && origin != null) {
            val from = if (origin.known) "내 위치에서" else "서울시청에서"
            val text = "$from ${formatDistance(distance)}"
            InfoRow(Icons.AutoMirrored.Rounded.DirectionsWalk, text, null, spoken = "거리, $text")
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, value: String, note: String?, spoken: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken }) {
        Icon(icon, null, tint = HereziTheme.colors.iconSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(HereziSpace.s3))
        Column {
            Text(
                value,
                style = HereziTheme.typography.bodyStrong.tabularNumbers(),
                color = HereziTheme.colors.textPrimary,
            )
            if (note != null) {
                Text(note, style = HereziTheme.typography.caption, color = HereziTheme.colors.textSecondary)
            }
        }
    }
}

@Composable
private fun SourceCard(spot: Spot, now: Instant, onOpen: (String) -> Unit) {
    Row(
        Modifier
            .padding(horizontal = HereziSpace.s5)
            .fillMaxWidth()
            .background(HereziTheme.colors.bgSecondary, RoundedCornerShape(HereziRadius.md))
            .padding(HereziSpace.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "출처 · ${sourceLabel(spot.source)}",
                style = HereziTheme.typography.caption,
                color = HereziTheme.colors.textSecondary,
            )
            val fetched = formatFetchedAt(spot.fetchedAt, now)
            Text(
                fetched,
                style = HereziTheme.typography.caption,
                color = HereziTheme.colors.textSecondary,
                modifier = Modifier.semantics { contentDescription = "$fetched, ${spokenDateTime(spot.fetchedAt)}" },
            )
        }
        spot.sourceUrl?.let { url ->
            TextButton(onClick = { onOpen(url) }) {
                Text("원문 보기", style = HereziTheme.typography.caption, color = HereziTheme.colors.textPrimary)
                Spacer(Modifier.width(HereziSpace.s1))
                Icon(
                    Icons.AutoMirrored.Rounded.OpenInNew,
                    null,
                    Modifier.size(16.dp),
                    tint = HereziTheme.colors.iconPrimary,
                )
            }
        }
    }
}

@Composable
private fun BottomBar(state: SpotDetailUiState.Loaded, actions: SpotDetailActions) {
    Column(Modifier.background(HereziTheme.colors.bgPrimary)) {
        HorizontalDivider(color = HereziTheme.colors.border, thickness = 1.dp)
        Box(Modifier.padding(horizontal = HereziSpace.s5, vertical = HereziSpace.s3)) {
            if (state.spot.isEnded) {
                SecondaryButton("장소 보기", onClick = { actions.onShowPlace(state.spot) })
            } else {
                PrimaryButton("네이버지도로 길찾기", onClick = { actions.onDirections(state.spot, state.distanceM) })
            }
        }
    }
}

@Composable
private fun DetailSkeleton() {
    Column(Modifier.fillMaxSize().clearAndSetSemantics { }) {
        SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(4f / 3f), RoundedCornerShape(0.dp))
        Column(Modifier.padding(HereziSpace.s5), verticalArrangement = Arrangement.spacedBy(HereziSpace.s3)) {
            SkeletonBlock(Modifier.fillMaxWidth(0.8f).height(20.dp))
            SkeletonBlock(Modifier.fillMaxWidth(0.5f).height(20.dp))
            Spacer(Modifier.height(HereziSpace.s2))
            repeat(3) { SkeletonBlock(Modifier.fillMaxWidth(0.6f).height(14.dp)) }
        }
    }
}

private fun share(context: Context, spot: Spot) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareText(spot))
    }
    context.startActivity(Intent.createChooser(send, null))
}

/** 네이버지도 앱으로 길찾기(또는 장소)를 열고, 앱이 없으면 웹으로 연다. */
private fun openNaver(context: Context, spot: Spot, distanceM: Int?, place: Boolean) {
    val app = if (place) {
        NaverMapDirections.placeAppUri(spot, context.packageName)
    } else {
        NaverMapDirections.appUri(spot, context.packageName, distanceM)
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(app)))
    } catch (_: ActivityNotFoundException) {
        val web = if (place) NaverMapDirections.placeWebUri(spot) else NaverMapDirections.webUri(spot, distanceM)
        openInAppBrowser(context, web)
    }
}

private fun sendReport(context: Context, spot: Spot) {
    val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(HereziLinks.SUPPORT_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, reportSubject(spot))
    }
    try {
        context.startActivity(mail)
    } catch (_: ActivityNotFoundException) {
        copy(context, HereziLinks.SUPPORT_EMAIL)
    }
}

private fun copy(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(text, text))
}

/** 오류 제보 행. 글자 버튼만으로는 신고 기능으로 보이지 않아서 아이콘·설명·화살표가 있는 테두리 행으로 둔다. */
@Composable
private fun ReportRow(onClick: () -> Unit) {
    val shape = RoundedCornerShape(HereziRadius.md)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = HereziSpace.s5)
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, HereziTheme.colors.border, shape)
            .clickable(onClickLabel = "잘못된 정보 신고", role = Role.Button, onClick = onClick)
            .padding(HereziSpace.s4),
    ) {
        Icon(
            Icons.Rounded.Feedback,
            contentDescription = null,
            tint = HereziTheme.colors.textSecondary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(HereziSpace.s3))
        Column(Modifier.weight(1f)) {
            Text("정보가 달라요", style = HereziTheme.typography.bodyStrong, color = HereziTheme.colors.textPrimary)
            Text(
                "기간·장소·요금이 틀렸다면 알려 주세요",
                style = HereziTheme.typography.caption,
                color = HereziTheme.colors.textSecondary,
            )
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = HereziTheme.colors.textSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}
