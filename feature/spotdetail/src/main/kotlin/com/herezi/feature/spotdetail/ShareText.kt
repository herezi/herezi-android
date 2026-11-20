package com.herezi.feature.spotdetail

import com.herezi.core.domain.Spot
import com.herezi.core.ui.HereziLinks
import com.herezi.core.ui.formatDetailPeriod

/** 공유 문구 (S3): `{title}\n{기간} · {place_name}\n{링크}`. 장소명이 없으면 기간만. */
fun shareText(spot: Spot): String {
    val second = listOfNotNull(formatDetailPeriod(spot.startsAt, spot.endsAt), spot.placeName).joinToString(" · ")
    return "${spot.title}\n$second\n${HereziLinks.share(spot.id)}"
}

/** 정보 오류 제보 메일 제목 (S3). */
fun reportSubject(spot: Spot): String = "[정보 오류] ${spot.id} ${spot.title}"
