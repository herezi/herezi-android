package com.herezi.core.domain

import kotlin.math.roundToInt

/** 두 지점 사이 거리(m, 하버사인, 서버와 같은 지구 반지름). */
fun GeoPoint.distanceTo(other: GeoPoint): Int = SpotQueryEngine.distanceM(this, other).roundToInt()
