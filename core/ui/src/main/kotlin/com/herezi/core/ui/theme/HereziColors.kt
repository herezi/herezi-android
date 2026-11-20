package com.herezi.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * 팔레트(원색값). DESIGN.md §2.1. 화면 코드는 이것 대신 [HereziColorScheme] 의미 토큰을 쓴다.
 */
object HereziColors {
    val Ink = Color(0xFF191F28)
    val Gray700 = Color(0xFF4E5968)
    val Gray500 = Color(0xFF8B95A1)
    val Gray200 = Color(0xFFE5E8EB)
    val Gray100 = Color(0xFFF2F4F6)
    val White = Color(0xFFFFFFFF)
    val Red = Color(0xFFF04452)
    val Green700 = Color(0xFF08734F)
    val Green50 = Color(0xFFE6F6EF)

    /** 이전 이름. [Red] 와 같다. */
    val Error = Red
}

/** 의미 토큰. DESIGN.md §2.2 (`color.text.primary` → `textPrimary`). M1 은 라이트만. */
@Immutable
data class HereziColorScheme(
    val bgPrimary: Color = HereziColors.White,
    val bgSecondary: Color = HereziColors.Gray100,
    val bgInverse: Color = HereziColors.Ink,
    val textPrimary: Color = HereziColors.Ink,
    val textSecondary: Color = HereziColors.Gray700,
    val textTertiary: Color = HereziColors.Gray500,
    val textInverse: Color = HereziColors.White,
    val border: Color = HereziColors.Gray200,
    val iconPrimary: Color = HereziColors.Ink,
    val iconSecondary: Color = HereziColors.Gray500,
    val statusError: Color = HereziColors.Red,
    val badgeFreeFg: Color = HereziColors.Green700,
    val badgeFreeBg: Color = HereziColors.Green50,
    val badgeEndedFg: Color = HereziColors.Gray700,
    val badgeEndedBg: Color = HereziColors.Gray100,
    val scrim: Color = HereziColors.Ink.copy(alpha = 0.4f),
)
