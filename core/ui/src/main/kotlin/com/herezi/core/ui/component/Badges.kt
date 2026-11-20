package com.herezi.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.herezi.core.domain.Category
import com.herezi.core.ui.theme.CATEGORY_TINT_ALPHA
import com.herezi.core.ui.theme.HereziRadius
import com.herezi.core.ui.theme.HereziTheme
import com.herezi.core.ui.theme.color
import com.herezi.core.ui.theme.icon

/** 배지 (DESIGN.md §6.9): type.micro, 안쪽 6·3, radius.xs. */
@Composable
fun HereziBadge(text: String, fg: Color, bg: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = HereziTheme.typography.micro,
        color = fg,
        modifier = modifier
            .background(bg, RoundedCornerShape(HereziRadius.xs))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

@Composable
fun FreeBadge(modifier: Modifier = Modifier) =
    HereziBadge("무료", HereziTheme.colors.badgeFreeFg, HereziTheme.colors.badgeFreeBg, modifier)

@Composable
fun EndedBadge(modifier: Modifier = Modifier) =
    HereziBadge("종료됨", HereziTheme.colors.badgeEndedFg, HereziTheme.colors.badgeEndedBg, modifier)

/** 카테고리 점 6. */
@Composable
fun CategoryDot(category: Category, modifier: Modifier = Modifier) {
    Box(modifier.size(6.dp).background(category.color, CircleShape))
}

/**
 * 썸네일 자리: 카테고리 색 12% + 카테고리 아이콘.
 * TODO(#9 후속): `image_url` 이미지 로딩(이미지 로더 의존 추가 후). 지금은 항상 대체 그림.
 */
@Composable
fun CategoryThumbnail(
    category: Category,
    iconSize: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(HereziRadius.md),
) {
    Box(
        modifier.clip(shape).background(category.color.copy(alpha = CATEGORY_TINT_ALPHA)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(category.icon, contentDescription = null, tint = category.color, modifier = Modifier.size(iconSize))
    }
}
