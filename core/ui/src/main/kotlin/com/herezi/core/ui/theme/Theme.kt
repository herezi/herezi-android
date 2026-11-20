package com.herezi.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColors =
    lightColorScheme(
        primary = HereziColors.Ink,
        onPrimary = HereziColors.White,
        secondary = HereziColors.Gray700,
        onSecondary = HereziColors.White,
        background = HereziColors.White,
        onBackground = HereziColors.Ink,
        surface = HereziColors.White,
        onSurface = HereziColors.Ink,
        surfaceVariant = HereziColors.Gray100,
        onSurfaceVariant = HereziColors.Gray700,
        outline = HereziColors.Gray500,
        error = HereziColors.Error,
    )

// Pretendard 대신 시스템 기본 산세리프를 쓴다 (폰트 번들은 추후 결정).
private val Base = FontFamily.Default

private fun style(weight: FontWeight, size: Int, lineHeight: Int) =
    TextStyle(fontFamily = Base, fontWeight = weight, fontSize = size.sp, lineHeight = lineHeight.sp)

private val HereziTypography =
    Typography(
        headlineSmall = style(FontWeight.Bold, size = 22, lineHeight = 30),
        titleMedium = style(FontWeight.SemiBold, size = 17, lineHeight = 24),
        bodyMedium = style(FontWeight.Normal, size = 15, lineHeight = 22),
        labelMedium = style(FontWeight.Medium, size = 13, lineHeight = 18),
    )

@Composable
fun HereziTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, typography = HereziTypography, content = content)
}
