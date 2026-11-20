package com.herezi.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import com.herezi.core.ui.theme.HereziColors

private const val EYE_W = 0.36f
private const val EYE_H = 0.5f
private const val EYE_GAP = 0.1f
private const val PUPIL = 0.5f

/**
 * 브랜드 "두리번" 두 눈: 흰 타원 두 개 + 잉크 눈동자(오른쪽 위를 봄). 장식이라 스크린리더에서 숨긴다.
 * 로고 이미지 에셋이 정해지면 바꾼다.
 */
@Composable
fun HereziEyes(modifier: Modifier = Modifier, white: Color = HereziColors.White, pupil: Color = HereziColors.Ink) {
    Canvas(modifier.clearAndSetSemantics { }) {
        val w = size.width * EYE_W
        val h = size.height * EYE_H
        val gap = size.width * EYE_GAP
        val left = (size.width - (2 * w + gap)) / 2
        val top = (size.height - h) / 2
        listOf(left, left + w + gap).forEach { x ->
            drawOval(white, topLeft = Offset(x, top), size = Size(w, h))
            val p = w * PUPIL
            drawOval(pupil, topLeft = Offset(x + w - p - w * 0.08f, top + h * 0.12f), size = Size(p, p * 1.2f))
        }
    }
}

/** 앱 로고 자리: 잉크 둥근 사각형 + 두 눈. */
@Composable
fun HereziLogo(size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).background(HereziColors.Ink, RoundedCornerShape(size * 0.28f))) {
        HereziEyes(Modifier.fillMaxSize().padding(size * 0.18f))
    }
}
