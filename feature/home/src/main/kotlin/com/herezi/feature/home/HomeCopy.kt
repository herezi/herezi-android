package com.herezi.feature.home

import com.herezi.core.domain.FeedSectionKind
import com.herezi.core.domain.HeroSection
import com.herezi.core.domain.HomeFeed

/** 헤드라인 두 줄 (S1). 진행 중이 0개면 이번 주 기준, 위치 거부면 "서울시청 주변에". */
fun homeHeadline(feed: HomeFeed, locationKnown: Boolean): Pair<String, String> {
    val ongoing = feed.ongoingCount > 0
    val count = if (ongoing) feed.ongoingCount else feed.weekCount
    val more = if (feed.hasMore) " 이상" else ""
    val first = when {
        !locationKnown -> "서울시청 주변에"
        ongoing -> "지금 근처에"
        else -> "이번 주 근처에"
    }
    return first to "볼거리 ${count}개$more 있어요"
}

val HeroSection.title: String get() = if (startsLaterToday) "오늘 가장 가까운 곳" else "지금 가장 가까운 곳"

val FeedSectionKind.title: String
    get() = when (this) {
        FeedSectionKind.ENDING_TODAY -> "오늘 끝나요"
        FeedSectionKind.WEEKEND -> "이번 주말 놓치면 아쉬운"
        FeedSectionKind.FREE -> "무료로 즐기기"
        FeedSectionKind.SEASONAL -> "계절 한정"
    }
