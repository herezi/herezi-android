package com.herezi.core.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.dp

/** 간격 (4pt 격자). DESIGN.md §4. 화면 좌우 여백은 [s5]. */
object HereziSpace {
    val s1 = 4.dp
    val s2 = 8.dp
    val s3 = 12.dp
    val s4 = 16.dp
    val s5 = 20.dp
    val s6 = 24.dp
    val s8 = 32.dp
    val s10 = 40.dp
}

/** 크기. DESIGN.md §4. */
object HereziSize {
    val touch = 48.dp
    val chip = 36.dp
    val buttonLg = 56.dp
    val buttonMd = 44.dp
    val thumb = 72.dp
    val pin = 36.dp
}

/** 모서리. DESIGN.md §5. */
object HereziRadius {
    val xs = 6.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val full = 999.dp
}

/** 그림자. 지도 위에 뜨는 것에만. DESIGN.md §5. */
object HereziElevation {
    val e0 = 0.dp
    val e1 = 3.dp
    val e2 = 6.dp
}

/** 모션. DESIGN.md §5. */
object HereziMotion {
    const val FAST_MS = 150
    const val BASE_MS = 250
    const val SLOW_MS = 350
    private const val SLOW_DAMPING = 0.85f

    private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)
    private val EaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

    /** 칩·토글 선택, 저장 아이콘. */
    fun <T> fast(): FiniteAnimationSpec<T> = tween(FAST_MS, easing = EaseOut)

    /** 목록↔지도 교차 페이드, 배너 등장. */
    fun <T> base(): FiniteAnimationSpec<T> = tween(BASE_MS, easing = EaseInOut)

    /** 시트, 선택 핀 확대. */
    fun <T> slow(): FiniteAnimationSpec<T> = spring(dampingRatio = SLOW_DAMPING)
}
