package com.herezi.core.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.herezi.core.ui.theme.HereziRadius
import com.herezi.core.ui.theme.HereziSpace
import com.herezi.core.ui.theme.HereziTheme

/** 토스트 (DESIGN.md §6.11): 잉크 배경 radius.sm, 흰 caption. Scaffold 의 snackbarHost 에 넣는다. */
@Composable
fun HereziToastHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data ->
        Snackbar(
            shape = RoundedCornerShape(HereziRadius.sm),
            containerColor = HereziTheme.colors.bgInverse,
            contentColor = HereziTheme.colors.textInverse,
            modifier = Modifier.padding(HereziSpace.s4),
            action = data.visuals.actionLabel?.let { label ->
                {
                    TextButton(onClick = data::performAction) {
                        Text(
                            label,
                            style = HereziTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                            color = HereziTheme.colors.textInverse,
                        )
                    }
                }
            },
        ) { Text(data.visuals.message, style = HereziTheme.typography.caption) }
    }
}
