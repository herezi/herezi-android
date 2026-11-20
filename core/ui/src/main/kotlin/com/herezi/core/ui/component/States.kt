package com.herezi.core.ui.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.herezi.core.ui.theme.HereziColors
import com.herezi.core.ui.theme.HereziRadius
import com.herezi.core.ui.theme.HereziSize
import com.herezi.core.ui.theme.HereziSpace
import com.herezi.core.ui.theme.HereziTheme

/** 상태 화면 공통 (DESIGN.md §6.7): 가운데, 위 space.10, 아이콘 48 gray500, 제목 title2, 설명 body. */
@Composable
fun StatusMessage(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    secondaryLabel: String? = null,
    onSecondary: () -> Unit = {},
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = HereziSpace.s5).padding(top = HereziSpace.s10),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = HereziColors.Gray500, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(HereziSpace.s4))
        Text(
            title,
            style = HereziTheme.typography.title2,
            color = HereziTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        if (description != null) {
            Spacer(Modifier.height(HereziSpace.s2))
            Text(
                description,
                style = HereziTheme.typography.body,
                color = HereziTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null) {
            Spacer(Modifier.height(HereziSpace.s5))
            MediumButton(actionLabel, onAction)
        }
        if (secondaryLabel != null) {
            Spacer(Modifier.height(HereziSpace.s2))
            HereziTextButton(secondaryLabel, onSecondary)
        }
    }
}

/** 네트워크·서버 오류 (DESIGN.md §6.7). */
@Composable
fun ErrorState(onRetry: () -> Unit, modifier: Modifier = Modifier, description: String = "잠시 후 다시 시도해 주세요.") =
    StatusMessage(
        icon = Icons.Rounded.WifiOff,
        title = "불러오지 못했어요",
        description = description,
        actionLabel = "다시 시도",
        onAction = onRetry,
        modifier = modifier,
    )

/** 결과 없음 아이콘. */
val EmptyIcon: ImageVector = Icons.Rounded.SearchOff

private const val SKELETON_PERIOD_MS = 1200
private const val SKELETON_MIN_ALPHA = 0.6f
private val SkeletonLineWidths = listOf(0.4f, 0.8f, 0.55f)

/** 로딩 스켈레톤 (DESIGN.md §6.7): 카드 자리 5장, 1.2초 주기 100%↔60%. 스피너는 쓰지 않는다. */
@Composable
fun SpotListSkeleton(modifier: Modifier = Modifier, count: Int = 5) {
    val alpha by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 1f,
        targetValue = SKELETON_MIN_ALPHA,
        animationSpec = infiniteRepeatable(tween(SKELETON_PERIOD_MS / 2), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    Column(modifier.alpha(alpha).clearAndSetSemantics { }) {
        repeat(count) {
            Row(Modifier.fillMaxWidth().padding(horizontal = HereziSpace.s5, vertical = HereziSpace.s4)) {
                SkeletonBlock(Modifier.size(HereziSize.thumb), RoundedCornerShape(HereziRadius.md))
                Spacer(Modifier.width(HereziSpace.s3))
                Column(Modifier.weight(1f)) {
                    SkeletonLineWidths.forEach { fraction ->
                        SkeletonBlock(Modifier.fillMaxWidth(fraction).height(12.dp))
                        Spacer(Modifier.height(HereziSpace.s2))
                    }
                }
            }
        }
    }
}

@Composable
fun SkeletonBlock(modifier: Modifier, shape: RoundedCornerShape = RoundedCornerShape(HereziRadius.xs)) {
    Box(modifier.background(HereziTheme.colors.bgSecondary, shape))
}

/** 위치 꺼짐 배너 (DESIGN.md §6.8). */
@Composable
fun LocationBanner(actionLabel: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = HereziSpace.s5)
            .background(HereziTheme.colors.bgSecondary, RoundedCornerShape(HereziRadius.md))
            .padding(start = HereziSpace.s4, end = HereziSpace.s1, top = HereziSpace.s1, bottom = HereziSpace.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.LocationOff,
            contentDescription = null,
            tint = HereziTheme.colors.textSecondary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(HereziSpace.s2))
        Text(
            LOCATION_BANNER_TEXT,
            style = HereziTheme.typography.caption,
            color = HereziTheme.colors.textPrimary,
            modifier = Modifier.weight(1f).padding(vertical = HereziSpace.s2),
        )
        TextButton(onClick = onAction) {
            Text(
                actionLabel,
                style = HereziTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                color = HereziTheme.colors.textPrimary,
            )
        }
    }
}

const val LOCATION_BANNER_TEXT = "위치를 확인할 수 없어 서울시청 기준으로 보여드려요"
