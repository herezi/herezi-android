package com.herezi.core.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.MicExternalOn
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.herezi.core.domain.Category

/** 카테고리 색 (`color.category.*`, DESIGN.md §2.3). 핀·점·썸네일 배경에만 쓰고 글자에는 쓰지 않는다. */
val Category.color: Color
    get() = when (this) {
        Category.NATURE -> Color(0xFF2E9E5B)
        Category.FESTIVAL -> Color(0xFFE5484D)
        Category.PERFORMANCE -> Color(0xFF8E4FD8)
        Category.EXHIBITION -> Color(0xFF0E8F89)
        Category.POPUP -> Color(0xFFDB3F78)
        Category.FOOD -> Color(0xFFD8661F)
        Category.FAMILY -> Color(0xFF5E9414)
        Category.PET -> Color(0xFF9A6436)
        Category.SPORTS -> Color(0xFF5A5FD6)
    }

/** 카테고리 아이콘 (Material Symbols Rounded, DESIGN.md §2.3). */
val Category.icon: ImageVector
    get() = when (this) {
        Category.NATURE -> Icons.Rounded.Eco
        Category.FESTIVAL -> Icons.Rounded.Celebration
        Category.PERFORMANCE -> Icons.Rounded.MicExternalOn
        Category.EXHIBITION -> Icons.Rounded.Palette
        Category.POPUP -> Icons.Rounded.Storefront
        Category.FOOD -> Icons.Rounded.Restaurant
        Category.FAMILY -> Icons.Rounded.FamilyRestroom
        Category.PET -> Icons.Rounded.Pets
        Category.SPORTS -> Icons.AutoMirrored.Rounded.DirectionsRun
    }

/** 알 수 없는 카테고리("기타")용. 도메인은 모르는 코드를 버리지만 UI 토큰은 남겨 둔다. */
object OtherCategoryStyle {
    const val LABEL = "기타"
    val color: Color = HereziColors.Gray700
    val icon: ImageVector = Icons.Rounded.AutoAwesome
}

/** 썸네일 대체 배경 (카테고리 색 12%). */
const val CATEGORY_TINT_ALPHA = 0.12f
