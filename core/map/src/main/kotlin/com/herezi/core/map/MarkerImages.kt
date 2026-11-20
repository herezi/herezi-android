package com.herezi.core.map

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.herezi.core.domain.Category
import com.naver.maps.map.overlay.OverlayImage

/**
 * 핀 모양 토큰. :core:map 은 :core:ui 를 모르므로 화면(feature)이 카테고리 색·아이콘·글꼴을 넘긴다.
 * [boldTypeface]·[semiBoldTypeface] 가 null 이면 시스템 굵은 글꼴.
 */
@Immutable
data class PinStyle(
    val color: (Category) -> Color,
    val icon: (Category) -> ImageVector,
    val ink: Color,
    val boldTypeface: Typeface? = null,
    val semiBoldTypeface: Typeface? = null,
)

/** 선택 핀 그림과 그 안의 원 중심 위치(마커 anchor). */
internal data class AnchoredImage(val image: OverlayImage, val anchor: Offset)

/**
 * 핀·클러스터·내 위치 점을 비트맵으로 그려 캐시한다(같은 모양은 한 번만 그린다).
 * 그림자는 `elevation.1`(검정 12%, y 2, blur 8)을 Paint 그림자로 흉내 낸다.
 */
internal class MarkerImages(
    private val density: Density,
    private val style: PinStyle,
    private val painters: Map<Category, VectorPainter>,
) {
    private val pins = HashMap<Category, OverlayImage>()
    private val clusters = HashMap<String, OverlayImage>()
    private val selected = HashMap<Pair<Category, String>, AnchoredImage>()
    private val px = density.density
    private val shadowPad = SHADOW_PAD_DP * px

    fun pin(category: Category): OverlayImage = pins.getOrPut(category) {
        OverlayImage.fromBitmap(drawPin(category, MapMarkerRules.PIN_DP, MapMarkerRules.PIN_BORDER_DP, White, null))
    }

    /** 선택 핀: 지름 44, 잉크 테두리 3, 아래 6 에 흰 알약 제목 라벨. */
    fun selected(category: Category, title: String): AnchoredImage = selected.getOrPut(category to title) {
        val label = MapMarkerRules.pinLabel(title)
        val bitmap =
            drawPin(category, MapMarkerRules.SELECTED_PIN_DP, MapMarkerRules.SELECTED_BORDER_DP, style.ink, label)
        val centerY = shadowPad + MapMarkerRules.SELECTED_PIN_DP * px / 2
        AnchoredImage(OverlayImage.fromBitmap(bitmap), Offset(HALF, centerY / bitmap.height))
    }

    /** 클러스터: 잉크 원 + 흰 숫자(13 Bold, 고정폭), 흰 테두리 2. */
    fun cluster(count: Int): OverlayImage {
        val label = MapMarkerRules.clusterLabel(count)
        val diameter = MapMarkerRules.clusterDiameterDp(count)
        return clusters.getOrPut("$diameter:$label") {
            val size = (diameter * px + shadowPad * 2).toInt()
            val bitmap = newBitmap(size, size)
            val canvas = android.graphics.Canvas(bitmap)
            val c = size / 2f
            val r = diameter * px / 2
            canvas.drawCircle(c, c, r, fill(White, shadow = true))
            canvas.drawCircle(c, c, r - MapMarkerRules.CLUSTER_BORDER_DP * px, fill(style.ink))
            val text = textPaint(MapMarkerRules.CLUSTER_TEXT_SP * density.fontScale * px, style.boldTypeface, White)
            canvas.drawText(label, c, c - (text.descent() + text.ascent()) / 2, text)
            OverlayImage.fromBitmap(bitmap)
        }
    }

    /** 내 위치: 잉크 점 지름 14, 흰 테두리 3. 정확도 원은 LocationOverlay 의 원으로 그린다. */
    val myLocation: OverlayImage by lazy {
        val size = (MY_LOCATION_DP * px + shadowPad * 2).toInt()
        val bitmap = newBitmap(size, size)
        val canvas = android.graphics.Canvas(bitmap)
        val c = size / 2f
        val r = MY_LOCATION_DP * px / 2
        canvas.drawCircle(c, c, r, fill(White, shadow = true))
        canvas.drawCircle(c, c, r - MY_LOCATION_BORDER_DP * px, fill(style.ink))
        OverlayImage.fromBitmap(bitmap)
    }

    private fun drawPin(category: Category, diameterDp: Int, borderDp: Int, border: Color, label: String?): Bitmap {
        val diameter = diameterDp * px
        val labelPaint = label?.let {
            textPaint(LABEL_TEXT_SP * density.fontScale * px, style.semiBoldTypeface, style.ink)
        }
        val labelBox = if (label != null && labelPaint != null) labelBox(label, labelPaint) else null
        val width = maxOf(diameter, labelBox?.width() ?: 0f) + shadowPad * 2
        val labelHeight = labelBox?.let { MapMarkerRules.LABEL_GAP_DP * px + it.height() } ?: 0f
        val bitmap = newBitmap(width.toInt(), (diameter + labelHeight + shadowPad * 2).toInt())
        val canvas = android.graphics.Canvas(bitmap)
        val cx = bitmap.width / 2f
        val cy = shadowPad + diameter / 2
        canvas.drawCircle(cx, cy, diameter / 2, fill(border, shadow = true))
        canvas.drawCircle(cx, cy, diameter / 2 - borderDp * px, fill(style.color(category)))
        drawIcon(canvas, category, cx, cy, MapMarkerRules.PIN_ICON_DP * px * diameterDp / MapMarkerRules.PIN_DP)
        if (label != null && labelPaint != null && labelBox != null) {
            val top = shadowPad + diameter + MapMarkerRules.LABEL_GAP_DP * px
            val rect = RectF(cx - labelBox.width() / 2, top, cx + labelBox.width() / 2, top + labelBox.height())
            canvas.drawRoundRect(rect, rect.height() / 2, rect.height() / 2, fill(White, shadow = true))
            val baseline = rect.centerY() - (labelPaint.descent() + labelPaint.ascent()) / 2
            canvas.drawText(label, cx, baseline, labelPaint)
        }
        return bitmap
    }

    private fun drawIcon(canvas: android.graphics.Canvas, category: Category, cx: Float, cy: Float, iconPx: Float) {
        val painter = painters[category] ?: return
        CanvasDrawScope().draw(
            density,
            LayoutDirection.Ltr,
            Canvas(canvas),
            Size(canvas.width.toFloat(), canvas.height.toFloat()),
        ) {
            translate(cx - iconPx / 2, cy - iconPx / 2) {
                with(painter) { draw(Size(iconPx, iconPx), colorFilter = ColorFilter.tint(White)) }
            }
        }
    }

    private fun labelBox(label: String, paint: Paint): RectF {
        val height = LABEL_HEIGHT_DP * px * density.fontScale
        return RectF(0f, 0f, paint.measureText(label) + LABEL_PAD_DP * px * 2, height)
    }

    private fun newBitmap(width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(maxOf(1, width), maxOf(1, height), Bitmap.Config.ARGB_8888).apply {
            density = (px * BASE_DPI).toInt()
        }

    private fun fill(color: Color, shadow: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        if (shadow) setShadowLayer(SHADOW_BLUR_DP * px, 0f, SHADOW_Y_DP * px, ShadowColor.toArgb())
    }

    private fun textPaint(sizePx: Float, typeface: Typeface?, color: Color) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        textSize = sizePx
        textAlign = Paint.Align.CENTER
        this.typeface = typeface ?: Typeface.DEFAULT_BOLD
        fontFeatureSettings = "tnum"
    }

    private companion object {
        val White = Color.White
        val ShadowColor = Color.Black.copy(alpha = 0.12f)
        const val SHADOW_PAD_DP = 6f
        const val SHADOW_BLUR_DP = 4f
        const val SHADOW_Y_DP = 2f
        const val MY_LOCATION_DP = 14f
        const val MY_LOCATION_BORDER_DP = 3f
        const val LABEL_TEXT_SP = 13f
        const val LABEL_HEIGHT_DP = 26f
        const val LABEL_PAD_DP = 10f
        const val BASE_DPI = 160f
        const val HALF = 0.5f
    }
}
