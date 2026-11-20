package com.herezi.core.ui.image

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Constraints
import com.herezi.core.ui.theme.HereziTheme

/** 테스트·미리보기에서 로더를 바꿀 때만 제공한다. 없으면 앱 전역 [HereziImageLoader.get]. */
val LocalImageLoader = staticCompositionLocalOf<HereziImageLoader?> { null }

private sealed interface ImageState {
    data object Loading : ImageState

    data object Failed : ImageState

    data class Loaded(val bitmap: ImageBitmap) : ImageState
}

/**
 * 원격 이미지. 불러오는 중에는 `bg.secondary` 만, 주소가 없거나 실패하면 [fallback] (DESIGN §6.16).
 * 크기는 레이아웃 제약에서 정해 그 크기로 줄여 디코딩한다. 화면에서 사라지면 요청을 취소한다.
 */
@Composable
fun RemoteImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallback: @Composable BoxWithConstraintsScope.() -> Unit,
) {
    BoxWithConstraints(modifier) {
        val loader = rememberImageLoader()
        val widthPx = constraints.maxWidth.finiteOrZero()
        val heightPx = constraints.maxHeight.finiteOrZero()
        val initial = remember(url, widthPx, heightPx, loader) { initialState(loader, url, widthPx, heightPx) }
        val state by produceState(initial, url, widthPx, heightPx, loader) {
            value = initial
            if (initial == ImageState.Loading && url != null && loader != null) {
                value = loader.load(url, widthPx, heightPx).toState()
            }
        }
        when (val s = state) {
            ImageState.Loading -> Box(Modifier.matchParentSize().background(HereziTheme.colors.bgSecondary))
            ImageState.Failed -> fallback()
            is ImageState.Loaded -> Image(
                bitmap = s.bitmap,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

private fun Int.finiteOrZero(): Int = if (this == Constraints.Infinity) 0 else this

private fun Bitmap?.toState(): ImageState = this?.let { ImageState.Loaded(it.asImageBitmap()) } ?: ImageState.Failed

private fun initialState(loader: HereziImageLoader?, url: String?, widthPx: Int, heightPx: Int): ImageState = when {
    url.isNullOrBlank() || loader == null -> ImageState.Failed
    else -> loader.cached(url, widthPx, heightPx)?.toState() ?: ImageState.Loading
}

/** 미리보기(레이아웃 편집기)에서는 네트워크를 쓰지 않는다 → 항상 대체 그림. */
@Composable
private fun rememberImageLoader(): HereziImageLoader? {
    val provided = LocalImageLoader.current
    if (provided != null) return provided
    if (LocalInspectionMode.current) return null
    val context = LocalContext.current
    return remember(context) { HereziImageLoader.get(context) }
}
