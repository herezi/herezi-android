package com.herezi.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.herezi.core.ui.R

/** 글자 토큰. DESIGN.md §3 (`type.title2` → `title2`). 크기는 sp 라 글꼴 크기 설정을 따른다. */
@Immutable
data class HereziTypography(
    val display: TextStyle = style(FontWeight.Bold, size = 26, lineHeight = 36),
    val title1: TextStyle = style(FontWeight.Bold, size = 22, lineHeight = 30),
    val title2: TextStyle = style(FontWeight.Bold, size = 19, lineHeight = 26),
    val title3: TextStyle = style(FontWeight.SemiBold, size = 17, lineHeight = 24),
    val body: TextStyle = style(FontWeight.Normal, size = 15, lineHeight = 22),
    val bodyStrong: TextStyle = style(FontWeight.SemiBold, size = 15, lineHeight = 22),
    val caption: TextStyle = style(FontWeight.Medium, size = 13, lineHeight = 18),
    val micro: TextStyle = style(FontWeight.SemiBold, size = 11, lineHeight = 14),
)

/**
 * Pretendard v1.3.9 (SIL OFL 1.1, `res/raw/license_pretendard.txt`). DESIGN.md §3 의 네 굵기만 번들한다.
 * 포스터 ExtraBold(800)는 가장 가까운 Bold 로 그린다.
 */
val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
)

private val Base = Pretendard

private fun style(weight: FontWeight, size: Int, lineHeight: Int) =
    TextStyle(fontFamily = Base, fontWeight = weight, fontSize = size.sp, lineHeight = lineHeight.sp)

/** 숫자(거리·시간)용 고정폭 숫자. */
fun TextStyle.tabularNumbers(): TextStyle = copy(fontFeatureSettings = "tnum")

private val LocalHereziColors = staticCompositionLocalOf { HereziColorScheme() }
private val LocalHereziTypography = staticCompositionLocalOf { HereziTypography() }

/** Material 3 컴포넌트가 토큰과 같은 색·글자를 쓰도록 맞춘다. */
private fun materialColors(c: HereziColorScheme) = lightColorScheme(
    primary = c.bgInverse,
    onPrimary = c.textInverse,
    secondary = c.textSecondary,
    onSecondary = c.textInverse,
    secondaryContainer = c.bgSecondary,
    onSecondaryContainer = c.textPrimary,
    background = c.bgPrimary,
    onBackground = c.textPrimary,
    surface = c.bgPrimary,
    onSurface = c.textPrimary,
    surfaceVariant = c.bgSecondary,
    onSurfaceVariant = c.textSecondary,
    surfaceContainer = c.bgPrimary,
    surfaceContainerLow = c.bgPrimary,
    surfaceContainerHigh = c.bgPrimary,
    outline = c.border,
    outlineVariant = c.border,
    error = c.statusError,
    scrim = c.scrim,
)

private fun materialTypography(t: HereziTypography) = Typography(
    displayLarge = t.display,
    displayMedium = t.display,
    displaySmall = t.display,
    headlineLarge = t.title1,
    headlineMedium = t.display,
    headlineSmall = t.title1,
    titleLarge = t.title2,
    titleMedium = t.title3,
    bodyLarge = t.body,
    bodyMedium = t.body,
    bodySmall = t.caption,
    titleSmall = t.bodyStrong,
    labelLarge = t.title3,
    labelMedium = t.caption,
    labelSmall = t.micro,
)

@Composable
fun HereziTheme(content: @Composable () -> Unit) {
    // M1 은 라이트 모드만 (DESIGN.md §2.2).
    val colors = HereziColorScheme()
    val typography = HereziTypography()
    CompositionLocalProvider(LocalHereziColors provides colors, LocalHereziTypography provides typography) {
        MaterialTheme(
            colorScheme = materialColors(colors),
            typography = materialTypography(typography),
            content = content,
        )
    }
}

/** `HereziTheme.colors.textPrimary`, `HereziTheme.typography.title2` 처럼 쓴다. */
object HereziTheme {
    val colors: HereziColorScheme
        @Composable @ReadOnlyComposable
        get() = LocalHereziColors.current

    val typography: HereziTypography
        @Composable @ReadOnlyComposable
        get() = LocalHereziTypography.current
}
