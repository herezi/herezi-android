package com.herezi.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.herezi.core.domain.Category
import com.herezi.core.domain.Spot
import com.herezi.core.ui.image.RemoteImage
import com.herezi.core.ui.label
import com.herezi.core.ui.theme.HereziColors
import com.herezi.core.ui.theme.HereziRadius
import com.herezi.core.ui.theme.HereziTheme
import com.herezi.core.ui.theme.Pretendard
import com.herezi.core.ui.theme.color

private const val LABEL_PILL_ALPHA = 0.2f

/** 썸네일 72 에서 아이콘 28 (DESIGN §6.1). */
private const val THUMB_ICON_RATIO = 28f / 72f

/** 포스터 제목 글자 크기: 그림 폭의 이 비율(최소·최대 사이). */
private const val TITLE_WIDTH_RATIO = 0.1f
private const val TITLE_MIN_SP = 14f
private const val TITLE_MAX_SP = 30f
private const val TITLE_LINE_HEIGHT = 1.12f

/** 이 폭보다 좁으면 포스터 글자가 읽히지 않아 아이콘 썸네일을 쓴다 (DESIGN §6.16 크기 규칙). */
val PosterMinWidth = 120.dp

/**
 * 볼거리 이미지 자리(히어로·가로 카드·상세 대표 이미지·썸네일). DESIGN.md §6.16.
 * `image_url` 을 불러오고, 없거나 실패하면 폭 120 이상은 [SpotImagePlaceholder], 그보다 작으면 [CategoryThumbnail].
 * 불러오는 중에는 `bg.secondary` 만. 그림은 장식이라 스크린리더에서 숨긴다(카드가 제목을 읽는다).
 */
@Composable
fun SpotImage(spot: Spot, modifier: Modifier = Modifier, style: PosterStyle = PosterStyle.Full) {
    RemoteImage(url = spot.imageUrl, contentDescription = null, modifier = modifier.clearAndSetSemantics { }) {
        if (maxWidth < PosterMinWidth) {
            CategoryThumbnail(
                spot.category,
                iconSize = (maxWidth * THUMB_ICON_RATIO).coerceIn(16.dp, 28.dp),
                modifier = Modifier.matchParentSize(),
                shape = RectangleShape,
            )
        } else {
            SpotImagePlaceholder(spot.title, spot.category, Modifier.matchParentSize(), style)
        }
    }
}

/** 포스터에서 무엇을 그릴지. 카드가 같은 자리에 제목·배지를 따로 그리면 겹치지 않게 뺀다. */
enum class PosterStyle(val title: Boolean, val label: Boolean, val eyes: Boolean) {
    /** 제목·카테고리 알약·두 눈 모두 (상세). */
    Full(title = true, label = true, eyes = true),

    /** 왼쪽 위에 카드 배지가 있을 때: 카테고리 알약만 뺀다(가로 카드). */
    NoLabel(title = true, label = false, eyes = true),

    /** 카드가 제목·배지·저장 버튼을 직접 그릴 때: 색과 두 눈만 (히어로). */
    Backdrop(title = false, label = false, eyes = true),
}

/**
 * 이미지 없음 "타이포 포스터": 카테고리 색 배경, 왼쪽 아래 흰 ExtraBold 제목(최대 3줄, 좁은 줄 간격),
 * 왼쪽 위 작은 카테고리 알약(흰 20%), 오른쪽 위 브랜드 두 눈. 장식이라 스크린리더에서 숨긴다.
 * 디자인이 바뀌면 이 함수만 바꾼다.
 */
@Composable
fun SpotImagePlaceholder(
    title: String,
    category: Category,
    modifier: Modifier = Modifier,
    style: PosterStyle = PosterStyle.Full,
) {
    BoxWithConstraints(modifier.background(category.color).clearAndSetSemantics { }) {
        val pad = (maxWidth * 0.07f).coerceIn(8.dp, 16.dp)
        val titleSp = (maxWidth.value * TITLE_WIDTH_RATIO).coerceIn(TITLE_MIN_SP, TITLE_MAX_SP)
        if (style.label) {
            Text(
                category.label,
                style = HereziTheme.typography.micro,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(pad)
                    .background(Color.White.copy(alpha = LABEL_PILL_ALPHA), RoundedCornerShape(HereziRadius.full))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        if (style.eyes) {
            val eyes = (maxWidth * 0.14f).coerceIn(20.dp, 40.dp)
            HereziEyes(
                Modifier.align(Alignment.TopEnd).padding(pad).size(eyes, eyes * 0.7f),
                pupil = HereziColors.Ink,
            )
        }
        if (style.title) {
            Text(
                title,
                style = TextStyle(
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = titleSp.sp,
                    lineHeight = TITLE_LINE_HEIGHT.em,
                    letterSpacing = (-0.02).em,
                ),
                color = Color.White,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(pad),
            )
        }
    }
}
