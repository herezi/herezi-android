package com.herezi.core.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.herezi.core.domain.Spot
import com.herezi.core.ui.formatCardPeriod
import com.herezi.core.ui.formatDistance
import com.herezi.core.ui.label
import com.herezi.core.ui.spokenDistance
import com.herezi.core.ui.spokenPeriod
import com.herezi.core.ui.theme.HereziSize
import com.herezi.core.ui.theme.HereziSpace
import com.herezi.core.ui.theme.HereziTheme
import com.herezi.core.ui.theme.tabularNumbers
import java.time.Instant

/** 끝난 카드(저장 목록)의 썸네일·글자 불투명도. */
const val ENDED_CARD_ALPHA = 0.4f

/**
 * 스팟 카드 (DESIGN.md §6.1). 목록·검색·저장에서 같은 카드를 쓴다.
 * 메타 첫 줄은 `카테고리 · 거리`, 거리를 모르면 `카테고리 · 장소명`.
 * [showEnded] 면 끝난 항목을 40% 로 흐리게 하고 `종료됨` 배지를 붙인다(저장 목록에서만).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpotCard(
    spot: Spot,
    now: Instant,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    distanceM: Int? = spot.distanceM,
    showEnded: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    leading: (@Composable RowScope.() -> Unit)? = null,
) {
    val ended = showEnded && spot.isEnded
    val dim = if (ended) Modifier.alpha(ENDED_CARD_ALPHA) else Modifier
    val period = formatCardPeriod(spot.startsAt, spot.endsAt, now)
    val description = spokenCard(spot, distanceM, ended)
    Row(
        modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick {
                    onClick()
                    true
                }
                if (onLongClick != null) {
                    onLongClick {
                        onLongClick()
                        true
                    }
                }
            }
            .padding(horizontal = HereziSpace.s5, vertical = HereziSpace.s4),
        verticalAlignment = Alignment.Top,
    ) {
        leading?.invoke(this)
        CategoryThumbnail(spot.category, iconSize = 28.dp, modifier = dim.size(HereziSize.thumb))
        Spacer(Modifier.width(HereziSpace.s3))
        Column(Modifier.weight(1f)) {
            Column(dim) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryDot(spot.category)
                    Spacer(Modifier.width(HereziSpace.s1))
                    val second = distanceM?.let(::formatDistance) ?: spot.placeName
                    Text(
                        listOfNotNull(spot.category.label, second).joinToString(" · "),
                        style = HereziTheme.typography.caption.tabularNumbers(),
                        color = HereziTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    spot.title,
                    style = HereziTheme.typography.title3,
                    color = HereziTheme.colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    period.text,
                    style = HereziTheme.typography.caption.tabularNumbers().let {
                        if (period.emphasized) it.copy(fontWeight = FontWeight.SemiBold) else it
                    },
                    color = if (period.emphasized) HereziTheme.colors.textPrimary else HereziTheme.colors.textSecondary,
                )
            }
            if (ended || spot.isFree) {
                Spacer(Modifier.height(HereziSpace.s1))
                Row(horizontalArrangement = Arrangement.spacedBy(HereziSpace.s2)) {
                    if (ended) EndedBadge()
                    if (spot.isFree) FreeBadge(dim)
                }
            }
        }
    }
}

/** "서울빛초롱축제, 축제·행사, 350미터, 11월 20일부터 12월 14일까지, 무료". 종료 시 앞에 "종료됨,". */
fun spokenCard(spot: Spot, distanceM: Int?, ended: Boolean): String = listOfNotNull(
    "종료됨".takeIf { ended },
    spot.title,
    spot.category.label,
    distanceM?.let(::spokenDistance) ?: spot.placeName,
    spokenPeriod(spot.startsAt, spot.endsAt),
    "무료".takeIf { spot.isFree },
).joinToString(", ")
