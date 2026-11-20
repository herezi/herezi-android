package com.herezi.core.domain

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_M = 6_371_000.0

/** 두 지점 사이 거리(m, 하버사인). */
fun GeoPoint.distanceTo(other: GeoPoint): Int {
    val dLat = Math.toRadians(other.lat - lat)
    val dLng = Math.toRadians(other.lng - lng)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat)) * cos(Math.toRadians(other.lat)) * sin(dLng / 2).pow(2)
    return (2 * EARTH_RADIUS_M * asin(sqrt(a))).roundToInt()
}
