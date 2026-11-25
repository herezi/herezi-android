package com.herezi.feature.nearby

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.herezi.core.domain.Category
import com.herezi.core.domain.GeoPoint
import com.herezi.core.domain.ListFilter
import com.herezi.core.domain.LocationSource
import com.herezi.core.domain.ReverseGeocoder
import com.herezi.core.domain.Spot
import com.herezi.core.domain.SpotQuery
import com.herezi.core.domain.SpotsRepository
import com.herezi.core.domain.When
import com.herezi.core.domain.origin
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

typealias NearbyFilter = ListFilter

data class NearbyUiState(
    val center: GeoPoint = GeoPoint.SeoulCityHall,
    /** 위치를 몰라 서울시청 기준이면 true → 위치 꺼짐 배너. */
    val locationOff: Boolean = true,
    /** 상단 위치명. null 이면 확인 중(스켈레톤). */
    val locationName: String? = null,
    val filter: NearbyFilter = NearbyFilter(),
    val spots: List<Spot> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val nextCursor: String? = null,
    val loadingMore: Boolean = false,
    val moreFailed: Boolean = false,
) {
    val hasMore: Boolean get() = nextCursor != null

    /** 필터가 기본값(지금·전체·무료 끔)이 아니면 true → "조건에 맞는 볼거리가 없어요". */
    val filtered: Boolean get() = filter != NearbyFilter()
}

/**
 * 전체 목록(S1b)·지도 탭(S2) 상태. 시작 필터는 내비게이션 인자로 받는다(홈의 타일·전체 보기).
 * 필터가 바뀔 때마다 이전 요청을 취소하고 다시 불러온다. 위치를 모르면 서울시청 기준.
 */
@HiltViewModel
class NearbyViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SpotsRepository,
    private val location: LocationSource,
    private val geocoder: ReverseGeocoder,
) : ViewModel() {
    private val _state = MutableStateFlow(NearbyUiState(filter = filterFrom(savedStateHandle)))

    /** 지도에서 다른 동네로 옮겨 다시 찾은 뒤 '목록으로 보기'로 왔으면 그 지도 중심(S2). */
    private val pinnedCenter: GeoPoint? = centerFrom(savedStateHandle)
    val state: StateFlow<NearbyUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var loadedOnce = false

    init {
        refreshLocation()
    }

    /** 위치를 다시 읽고, 바뀌었거나 처음이면 다시 불러온다. 지도 중심을 받았으면 그 기준. */
    fun refreshLocation() {
        viewModelScope.launch {
            val origin = location.origin()
            val center = pinnedCenter ?: origin.point
            val before = _state.value
            if (loadedOnce && before.center == center && before.locationOff == !origin.known) return@launch
            loadedOnce = true
            _state.update { it.copy(center = center, locationOff = !origin.known) }
            load()
            val named = pinnedCenter != null || origin.known
            val name = if (named) geocoder.placeName(center) ?: NAME_UNKNOWN else NAME_CITY_HALL
            _state.update { it.copy(locationName = name) }
        }
    }

    fun toggleCategory(category: Category) = updateFilter {
        val categories = it.categories
        it.copy(categories = if (category in categories) categories - category else categories + category)
    }

    /** `전체` 칩: 카테고리 선택을 모두 해제. */
    fun clearCategories() = updateFilter { it.copy(categories = emptySet()) }

    fun setPeriod(period: When) = updateFilter { it.copy(period = period) }

    fun setFreeOnly(freeOnly: Boolean) = updateFilter { it.copy(freeOnly = freeOnly) }

    /** 빈 상태 "필터 초기화": 지금·전체·무료 끔. */
    fun resetFilters() = updateFilter { NearbyFilter() }

    /** 당겨서 새로고침: 지역 파일을 강제로 확인한 뒤 다시 계산(확인이 실패해도 가진 데이터로). */
    fun refresh() {
        viewModelScope.launch {
            try {
                repository.refreshData(force = true)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
                // 오프라인: 마지막 데이터로 계산.
            }
            load()
        }
    }

    /** 끝에서 몇 개 남았을 때 부른다. 다음 페이지가 없거나 불러오는 중이면 무시. */
    fun loadMore() {
        val s = _state.value
        val cursor = s.nextCursor ?: return
        if (s.loadingMore || s.loading) return
        _state.update { it.copy(loadingMore = true, moreFailed = false) }
        loadJob = viewModelScope.launch {
            try {
                val page = repository.nearby(query(s).copy(cursor = cursor))
                _state.update {
                    it.copy(spots = it.spots + page.items, nextCursor = page.nextCursor, loadingMore = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
                _state.update { it.copy(loadingMore = false, moreFailed = true) }
            }
        }
    }

    private fun updateFilter(change: (NearbyFilter) -> NearbyFilter) {
        val before = _state.value.filter
        val after = change(before)
        if (after == before) return
        _state.update { it.copy(filter = after) }
        load()
    }

    private fun query(s: NearbyUiState) = SpotQuery(
        center = s.center,
        categories = s.filter.categories,
        period = s.filter.period,
        freeOnly = s.filter.freeOnly,
    )

    private fun load() {
        loadJob?.cancel()
        // 필터 변경 시 이전 목록 위에 덮지 않고 교체(스켈레톤).
        _state.update {
            it.copy(loading = true, error = null, spots = emptyList(), nextCursor = null, moreFailed = false)
        }
        val snapshot = _state.value
        loadJob =
            viewModelScope.launch {
                try {
                    val page = repository.nearby(query(snapshot))
                    _state.update { it.copy(spots = page.items, nextCursor = page.nextCursor, loading = false) }
                } catch (e: CancellationException) {
                    throw e
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    _state.update { it.copy(loading = false, error = e.message ?: "불러오지 못했습니다") }
                }
            }
    }

    companion object {
        const val ARG_WHEN = "when"
        const val ARG_CATEGORY = "category"
        const val ARG_FREE = "free"
        const val ARG_LAT = "lat"
        const val ARG_LNG = "lng"

        const val NAME_UNKNOWN = "내 주변"
        const val NAME_CITY_HALL = "서울시청 주변"

        /** 내비게이션 인자 → 시작 필터. 모르는 값은 기본값. */
        fun filterFrom(handle: SavedStateHandle): NearbyFilter = NearbyFilter(
            period = handle.get<String>(ARG_WHEN)?.let { c -> When.entries.firstOrNull { it.code == c } } ?: When.NOW,
            categories = handle.get<String>(ARG_CATEGORY).orEmpty().split(",").mapNotNull(Category::fromCode).toSet(),
            freeOnly = handle.get<Boolean>(ARG_FREE) == true,
        )

        /** 내비게이션 인자 → 목록 기준 위치. 없거나 숫자가 아니면 null(내 위치). */
        fun centerFrom(handle: SavedStateHandle): GeoPoint? {
            val lat = handle.get<String>(ARG_LAT)?.toDoubleOrNull() ?: return null
            val lng = handle.get<String>(ARG_LNG)?.toDoubleOrNull() ?: return null
            return GeoPoint(lat, lng)
        }
    }
}
