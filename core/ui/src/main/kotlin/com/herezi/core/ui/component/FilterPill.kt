package com.herezi.core.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.herezi.core.ui.theme.HereziMotion
import com.herezi.core.ui.theme.HereziRadius
import com.herezi.core.ui.theme.HereziSize
import com.herezi.core.ui.theme.HereziTheme

/**
 * 글자 알약 (홈 카테고리 줄·S1b 칩 공용). 선택 = 잉크 배경·흰 글자, 비선택 = bg.secondary·잉크 글자.
 * [dot] 이 있으면 라벨 앞에 8 원(카테고리 색), [check] 면 선택 시 체크(무료만 토글). 아이콘은 쓰지 않는다.
 * [onClick] 은 토글(필터)이든 이동(홈)이든 같은 모양으로 쓴다.
 */
@Composable
fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dot: Color? = null,
    check: Boolean = false,
    role: Role = Role.Checkbox,
) {
    val bg by animateColorAsState(
        if (selected) HereziTheme.colors.bgInverse else HereziTheme.colors.bgSecondary,
        HereziMotion.fast(),
        label = "pillBg",
    )
    val fg = if (selected) HereziTheme.colors.textInverse else HereziTheme.colors.textPrimary
    Row(
        modifier
            .heightIn(min = HereziSize.chip)
            .clip(RoundedCornerShape(HereziRadius.full))
            .background(bg)
            .toggleable(value = selected, role = role, onValueChange = { onClick() })
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Spacer(Modifier.size(8.dp).background(dot, CircleShape))
            Spacer(Modifier.width(6.dp))
        }
        if (check && selected) {
            Icon(Icons.Rounded.Check, null, tint = fg, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(label, style = HereziTheme.typography.caption, color = fg, maxLines = 1)
    }
}
