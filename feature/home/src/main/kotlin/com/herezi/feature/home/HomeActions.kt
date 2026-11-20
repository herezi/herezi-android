package com.herezi.feature.home

import com.herezi.core.domain.ListFilter
import com.herezi.core.domain.Spot

/** 홈에서 나가는 길. :app 이 내비게이션을 맡는다. */
data class HomeActions(
    val onSpotClick: (String) -> Unit = {},
    val onSearch: () -> Unit = {},
    val onOpenList: (ListFilter) -> Unit = {},
    val onOpenMap: () -> Unit = {},
    val onLocationSettings: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onToggleSave: (Spot) -> Unit = {},
)
