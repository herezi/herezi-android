package com.herezi.core.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.herezi.core.ui.theme.HereziColors
import com.herezi.core.ui.theme.HereziRadius
import com.herezi.core.ui.theme.HereziSize
import com.herezi.core.ui.theme.HereziTheme

/** 주 버튼 (DESIGN.md §6.10): 높이 56, 잉크, radius.lg, 꽉 채움. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = HereziSize.buttonLg),
        shape = RoundedCornerShape(HereziRadius.lg),
        colors = ButtonDefaults.buttonColors(
            containerColor = HereziTheme.colors.bgInverse,
            contentColor = HereziTheme.colors.textInverse,
            disabledContainerColor = HereziColors.Gray200,
            disabledContentColor = HereziColors.Gray500,
        ),
    ) { Text(text, style = HereziTheme.typography.title3) }
}

/** 보조 버튼: 높이 56, bg.secondary, 잉크 글자. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = HereziSize.buttonLg),
        shape = RoundedCornerShape(HereziRadius.lg),
        colors = ButtonDefaults.buttonColors(
            containerColor = HereziTheme.colors.bgSecondary,
            contentColor = HereziTheme.colors.textPrimary,
        ),
    ) { Text(text, style = HereziTheme.typography.title3) }
}

/** 중간 버튼: 높이 44, 잉크, radius.sm (빈 상태·배너). */
@Composable
fun MediumButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = HereziSize.buttonMd),
        shape = RoundedCornerShape(HereziRadius.sm),
        colors = ButtonDefaults.buttonColors(
            containerColor = HereziTheme.colors.bgInverse,
            contentColor = HereziTheme.colors.textInverse,
        ),
    ) { Text(text, style = HereziTheme.typography.bodyStrong) }
}

/** 글자 버튼: 높이 44, text.secondary. */
@Composable
fun HereziTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = HereziSize.buttonMd)) {
        Text(text, style = HereziTheme.typography.body, color = HereziTheme.colors.textSecondary)
    }
}
