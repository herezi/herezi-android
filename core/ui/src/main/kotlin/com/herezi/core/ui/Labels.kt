package com.herezi.core.ui

import com.herezi.core.domain.Category
import com.herezi.core.domain.When
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** 앱의 날짜 기준 시간대 (DESIGN.md §6.1: KST). */
val Seoul: ZoneId = ZoneId.of("Asia/Seoul")

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
private const val ROUND_M = 10
private const val WHOLE_KM_FROM = 10_000

/** DESIGN.md §6.1: 1km 미만 10m 단위 "350m", 10km 미만 "1.2km", 그 이상 "12km". */
fun formatDistance(meters: Int): String = when {
    meters < METERS_PER_KM -> {
        val rounded = (meters / ROUND_M.toDouble()).roundToInt() * ROUND_M
        if (rounded >= METERS_PER_KM) "1.0km" else "${rounded}m"
    }
    meters < WHOLE_KM_FROM -> "%.1fkm".format(Locale.ROOT, meters / METERS_PER_KM.toDouble())
    else -> "${(meters / METERS_PER_KM.toDouble()).roundToInt()}km"
}

/** 스크린리더용 거리. "350미터", "1.2킬로미터". */
fun spokenDistance(meters: Int): String = formatDistance(meters).replace("km", "킬로미터").replace("m", "미터")

private fun ZonedDateTime.weekday(): String = dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)

private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")

private fun ZonedDateTime.shortDate() = "$monthValue.$dayOfMonth(${weekday()})"

private fun ZonedDateTime.longDate() = "${monthValue}월 ${dayOfMonth}일(${weekday()})"

private fun ZonedDateTime.yearDate() = "%d.%02d.%02d".format(Locale.ROOT, year, monthValue, dayOfMonth)

/** 카드 기간 줄. [emphasized] 면 text.primary SemiBold 로 그린다("오늘까지"). */
data class PeriodText(val text: String, val emphasized: Boolean = false)

/**
 * 카드 기간 줄 (DESIGN.md §6.1, KST).
 * 오늘 시작·끝 "오늘 18:00 – 22:00", 진행 중 오늘 끝 "오늘까지", 여러 날 "11.20(금) – 12.14(일)",
 * 해가 다르면 "2026.12.20 – 2027.01.04", 끝 없음 "11.20(금)부터".
 */
fun formatCardPeriod(start: Instant, end: Instant?, now: Instant, zone: ZoneId = Seoul): PeriodText {
    val s = start.atZone(zone)
    val today = now.atZone(zone).toLocalDate()
    if (end == null) return PeriodText("${s.shortDate()}부터")
    val e = end.atZone(zone)
    return when {
        s.toLocalDate() == today && e.toLocalDate() == today ->
            PeriodText("오늘 ${TimeFormat.format(s)} – ${TimeFormat.format(e)}")
        e.toLocalDate() == today && !start.isAfter(now) -> PeriodText("오늘까지", emphasized = true)
        s.year != e.year -> PeriodText("${s.yearDate()} – ${e.yearDate()}")
        else -> PeriodText("${s.shortDate()} – ${e.shortDate()}")
    }
}

/** 상세 기간 값 (S3): "11월 20일(금) – 12월 14일(일)", 끝 없음 "11월 20일(금)부터". */
fun formatDetailPeriod(start: Instant, end: Instant?, zone: ZoneId = Seoul): String {
    val s = start.atZone(zone)
    return if (end == null) "${s.longDate()}부터" else "${s.longDate()} – ${end.atZone(zone).longDate()}"
}

/** 스크린리더용 기간: "11월 20일부터 12월 14일까지". */
fun spokenPeriod(start: Instant, end: Instant?, zone: ZoneId = Seoul): String {
    val s = start.atZone(zone)
    val from = "${s.monthValue}월 ${s.dayOfMonth}일부터"
    if (end == null) return from
    val e = end.atZone(zone)
    return "$from ${e.monthValue}월 ${e.dayOfMonth}일까지"
}

/** 상세 기간 보조: 오늘 끝나면 "오늘까지". */
fun formatDetailPeriodNote(end: Instant?, now: Instant, zone: ZoneId = Seoul): String? =
    "오늘까지".takeIf { end != null && end.atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate() }

/** 상세 시간 행 (S3). 시작·끝 시각이 00:00 이 아닐 때만: 하루 "18:00 – 22:00", 여러 날 "매일 17:00 – 22:00". */
fun formatDetailTime(start: Instant, end: Instant?, zone: ZoneId = Seoul): String? {
    if (end == null) return null
    val s = start.atZone(zone)
    val e = end.atZone(zone)
    if (s.toLocalTime() == LocalTime.MIDNIGHT || e.toLocalTime() == LocalTime.MIDNIGHT) return null
    val range = "${TimeFormat.format(s)} – ${TimeFormat.format(e)}"
    return if (s.toLocalDate() == e.toLocalDate()) range else "매일 $range"
}

private const val MINUTES_PER_HOUR = 60L
private const val HOURS_PER_DAY = 24L

/** 갱신 시각 (DESIGN.md §8): 1시간 안 "N분 전 갱신", 24시간 안 "N시간 전 갱신", 그 이상 "11월 18일 갱신". */
fun formatFetchedAt(fetched: Instant, now: Instant, zone: ZoneId = Seoul): String {
    val minutes = Duration.between(fetched, now).toMinutes().coerceAtLeast(0)
    return when {
        minutes < 1 -> "방금 갱신"
        minutes < MINUTES_PER_HOUR -> "${minutes}분 전 갱신"
        minutes < MINUTES_PER_HOUR * HOURS_PER_DAY -> "${minutes / MINUTES_PER_HOUR}시간 전 갱신"
        else -> fetched.atZone(zone).let { "${it.monthValue}월 ${it.dayOfMonth}일 갱신" }
    }
}

private const val NOON = 12

/** 스크린리더용 절대 시각: "11월 20일 오전 9시 12분". */
fun spokenDateTime(time: Instant, zone: ZoneId = Seoul): String {
    val t = time.atZone(zone)
    val amPm = if (t.hour < NOON) "오전" else "오후"
    val hour = (t.hour % NOON).let { if (it == 0) NOON else it }
    return "${t.monthValue}월 ${t.dayOfMonth}일 $amPm ${hour}시 ${t.minute}분"
}

/** 상세 종료 안내 (S3): "11월 17일에 끝난 볼거리예요." 끝 시각을 모르면 날짜 없이. */
fun formatEndedNotice(end: Instant?, zone: ZoneId = Seoul): String =
    end?.atZone(zone)?.let { "${it.monthValue}월 ${it.dayOfMonth}일에 끝난 볼거리예요." } ?: "끝난 볼거리예요."

/** 출처 코드 → 이름 (DESIGN.md §8). 모르는 값은 그대로. */
fun sourceLabel(source: String): String = when (source) {
    "tourapi" -> "한국관광공사"
    "kopis" -> "KOPIS 공연예술통합전산망"
    "seoul_culture" -> "서울 열린데이터광장"
    else -> source
}
