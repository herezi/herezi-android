package com.herezi.core.ui

import com.herezi.core.domain.Category
import com.herezi.core.domain.When
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 화면에 보이는 한국어 이름. 기획서 §7 카테고리 표 기준. */
val Category.label: String
    get() =
        when (this) {
            Category.NATURE -> "자연·계절"
            Category.FESTIVAL -> "축제·행사"
            Category.PERFORMANCE -> "공연"
            Category.EXHIBITION -> "전시"
            Category.POPUP -> "팝업"
            Category.FOOD -> "먹거리"
            Category.FAMILY -> "아이·가족"
            Category.PET -> "반려동물"
            Category.SPORTS -> "스포츠"
        }

val When.label: String
    get() =
        when (this) {
            When.NOW -> "지금"
            When.TODAY -> "오늘"
            When.WEEKEND -> "이번 주말"
            When.WEEK -> "이번 주"
        }

private const val METERS_PER_KM = 1000

/** 420 → "420m", 1530 → "1.5km". */
fun formatDistance(meters: Int): String =
    if (meters < METERS_PER_KM) "${meters}m" else "%.1fkm".format(meters / METERS_PER_KM.toDouble())

private val dateFormat = DateTimeFormatter.ofPattern("M.d")

/** 기간 표시. 예: "4.1 ~ 4.7", 끝이 없으면 "4.1 ~". */
fun formatPeriod(start: Instant, end: Instant?, zone: ZoneId = ZoneId.of("Asia/Seoul")): String {
    val s = dateFormat.format(start.atZone(zone))
    return if (end == null) "$s ~" else "$s ~ ${dateFormat.format(end.atZone(zone))}"
}

/**
 * 지난 시각을 상대 시간으로. 1분 미만 "방금", 이후 "N분 전" / "N시간 전" / "N일 전".
 * 미래 시각(시계 차이)은 "방금"으로 본다.
 */
fun formatRelativeTime(time: Instant, now: Instant): String {
    val elapsed = Duration.between(time, now)
    return when {
        elapsed.toMinutes() < 1 -> "방금"
        elapsed.toHours() < 1 -> "${elapsed.toMinutes()}분 전"
        elapsed.toDays() < 1 -> "${elapsed.toHours()}시간 전"
        else -> "${elapsed.toDays()}일 전"
    }
}
