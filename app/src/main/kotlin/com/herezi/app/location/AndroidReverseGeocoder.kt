package com.herezi.app.location

import android.content.Context
import android.location.Geocoder
import com.herezi.core.domain.GeoPoint
import com.herezi.core.domain.ReverseGeocoder
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 기기 역지오코딩으로 "구 동"을 만든다(DESIGN.md §8). 실패하면 null. */
class AndroidReverseGeocoder(context: Context) : ReverseGeocoder {
    private val app = context.applicationContext

    override suspend fun placeName(point: GeoPoint): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        runCatching {
            @Suppress("DEPRECATION") // 동기 API 가 minSdk 26 에서 공통으로 쓸 수 있는 유일한 형태.
            Geocoder(app, Locale.KOREA).getFromLocation(point.lat, point.lng, 1)?.firstOrNull()
        }.getOrNull()?.let { a ->
            listOfNotNull(a.subLocality ?: a.locality, a.thoroughfare ?: a.subThoroughfare)
                .distinct()
                .joinToString(" ")
                .ifBlank { null }
        }
    }
}
