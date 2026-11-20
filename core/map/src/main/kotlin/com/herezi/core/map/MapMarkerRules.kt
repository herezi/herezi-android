package com.herezi.core.map

/**
 * 지도 핀·클러스터 규칙 (DESIGN.md §6.6). 안드로이드 없이 계산만 → JVM 단위 테스트.
 * 크기는 모두 dp.
 */
object MapMarkerRules {
    /** 핀 지름 `size.pin`. */
    const val PIN_DP = 36

    /** 선택된 핀 지름. */
    const val SELECTED_PIN_DP = 44
    const val PIN_BORDER_DP = 2
    const val SELECTED_BORDER_DP = 3
    const val PIN_ICON_DP = 18

    /** 선택 핀 아래 라벨까지 간격. */
    const val LABEL_GAP_DP = 6

    /** 라벨 최대 글자 수(넘치면 말줄임). */
    const val LABEL_MAX_CHARS = 12

    /** 같은 화면 반경 이 안의 핀을 묶는다. */
    const val CLUSTER_SCREEN_DISTANCE_DP = 48.0

    /** 이 줌보다 크면 묶지 않는다(건물 단위). */
    const val CLUSTER_MAX_ZOOM = 16

    const val CLUSTER_BORDER_DP = 2
    const val CLUSTER_TEXT_SP = 13

    private const val MID_CLUSTER = 10
    private const val BIG_CLUSTER = 50
    private const val MAX_SHOWN = 99
    private const val SMALL_DP = 32
    private const val MID_DP = 40
    private const val BIG_DP = 48

    /** 클러스터 지름: 2–9 → 32, 10–49 → 40, 50+ → 48. */
    fun clusterDiameterDp(count: Int): Int = when {
        count >= BIG_CLUSTER -> BIG_DP
        count >= MID_CLUSTER -> MID_DP
        else -> SMALL_DP
    }

    /** 클러스터 숫자: 99 넘으면 `99+`. */
    fun clusterLabel(count: Int): String = if (count > MAX_SHOWN) "$MAX_SHOWN+" else count.toString()

    /** 선택 핀 라벨: 최대 [LABEL_MAX_CHARS] 자, 넘치면 끝을 `…` 로. */
    fun pinLabel(title: String): String {
        val trimmed = title.trim()
        return if (trimmed.length <= LABEL_MAX_CHARS) trimmed else trimmed.take(LABEL_MAX_CHARS - 1) + "…"
    }

    /** 스크린리더: 핀 = "공연, 세종 체임버 콘서트". */
    fun spokenPin(categoryLabel: String, title: String): String = "$categoryLabel, $title"

    /** 스크린리더: 클러스터 = "볼거리 12개, 두 번 탭하면 확대". */
    fun spokenCluster(count: Int): String = "볼거리 ${count}개, 두 번 탭하면 확대"
}
