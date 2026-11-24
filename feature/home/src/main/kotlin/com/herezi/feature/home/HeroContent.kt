package com.herezi.feature.home

import com.herezi.core.domain.Spot
import com.herezi.core.ui.component.PosterStyle
import com.herezi.core.ui.formatDistance
import com.herezi.core.ui.formatHeroBadge
import com.herezi.core.ui.formatHeroPeriod
import com.herezi.core.ui.label
import com.herezi.core.ui.spokenDistance
import java.time.Instant

/** 히어로 카드 바탕: 사진이 있으면 사진(실패하면 [PosterStyle.Backdrop] 포스터), 없으면 바로 포스터. */
internal sealed interface HeroMedia {
    val posterStyle: PosterStyle

    data class Photo(val url: String) : HeroMedia {
        override val posterStyle: PosterStyle = PosterStyle.Backdrop
    }

    data object Poster : HeroMedia {
        override val posterStyle: PosterStyle = PosterStyle.Backdrop
    }
}

/**
 * 히어로 카드(DESIGN.md §6.13)에 무엇을 그릴지. 바탕이 사진이든 포스터든
 * 아래 그라데이션 + 흰 제목 + 메타 줄은 카드가 늘 직접 그린다(포스터는 제목을 빼고 그린다).
 */
internal data class HeroContent(
    val title: String,
    val meta: String,
    val badge: String?,
    val media: HeroMedia,
    val showsGradient: Boolean,
    val showsTitle: Boolean,
    val spoken: String,
)

internal fun heroContent(spot: Spot, now: Instant): HeroContent {
    val badge = formatHeroBadge(spot.startsAt, spot.endsAt, now)
    return HeroContent(
        title = spot.title,
        meta = listOfNotNull(
            spot.category.label,
            spot.distanceM?.let(::formatDistance),
            formatHeroPeriod(spot.startsAt, spot.endsAt, now),
            "무료".takeIf { spot.isFree },
        ).joinToString(" · "),
        badge = badge,
        media = spot.imageUrl?.takeIf { it.isNotBlank() }?.let { HeroMedia.Photo(it) } ?: HeroMedia.Poster,
        showsGradient = true,
        showsTitle = true,
        spoken = listOfNotNull(
            spot.title,
            badge,
            spot.category.label,
            spot.distanceM?.let(::spokenDistance),
            "무료".takeIf { spot.isFree },
        ).joinToString(", "),
    )
}
