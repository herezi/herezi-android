package com.herezi.feature.nearby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.herezi.core.domain.Category
import com.herezi.core.domain.GeoPoint
import com.herezi.core.domain.LocationSource
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

data class NearbyFilter(
    val categories: Set<Category> = emptySet(),
    val period: When = When.NOW,
    val freeOnly: Boolean = false,
)

data class NearbyUiState(
    val center: GeoPoint = GeoPoint.SeoulCityHall,
    /** 위치를 몰라 서울시청 기준이면 true → 위치 꺼짐 배너. */
    val locationOff: Boolean = true,
    val filter: NearbyFilter = NearbyFilter(),
    val spots: List<Spot> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val showMap: Boolean = false,
)

/**
 * 근처 목록 화면 상태. 필터가 바뀔 때마다 이전 요청을 취소하고 다시 불러온다.
 * 위치를 모르면 서울시청 기준. 화면에 돌아올 때 [refreshLocation] 으로 위치가 바뀌었는지 본다.
 */
@HiltViewModel
class NearbyViewModel
@Inject
constructor(
    private val repository: SpotsRepository,
    private val location: LocationSource,
) : ViewModel() {
    private val _state = MutableStateFlow(NearbyUiState())
    val state: StateFlow<NearbyUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var loadedOnce = false

    init {
        refreshLocation()
    }

    /** 위치를 다시 읽고, 바뀌었거나 처음이면 다시 불러온다. */
    fun refreshLocation() {
        viewModelScope.launch {
            val origin = location.origin()
            val before = _state.value
            if (loadedOnce && before.center == origin.point && before.locationOff == !origin.known) return@launch
            loadedOnce = true
            _state.update { it.copy(center = origin.point, locationOff = !origin.known) }
            load()
        }
    }

    fun toggleCategory(category: Category) = updateFilter {
        val categories = it.categories
        it.copy(categories = if (category in categories) categories - category else categories + category)
    }

    /** 빈 상태 "필터 초기화": 카테고리·무료를 끄고 기간은 기본(지금)으로. */
    fun resetFilters() = updateFilter { NearbyFilter() }

    fun clearCategories() = updateFilter { it.copy(categories = emptySet()) }

    fun setPeriod(period: When) = updateFilter { it.copy(period = period) }

    fun setFreeOnly(freeOnly: Boolean) = updateFilter { it.copy(freeOnly = freeOnly) }

    fun toggleMap() = _state.update { it.copy(showMap = !it.showMap) }

    fun refresh() = load()

    private fun updateFilter(change: (NearbyFilter) -> NearbyFilter) {
        val before = _state.value.filter
        val after = change(before)
        if (after == before) return
        _state.update { it.copy(filter = after) }
        load()
    }

    private fun load() {
        loadJob?.cancel()
        val snapshot = _state.value
        _state.update { it.copy(loading = true, error = null) }
        loadJob =
            viewModelScope.launch {
                val query =
                    SpotQuery(
                        center = snapshot.center,
                        categories = snapshot.filter.categories,
                        period = snapshot.filter.period,
                        freeOnly = snapshot.filter.freeOnly,
                    )
                try {
                    val page = repository.nearby(query)
                    _state.update { it.copy(spots = page.items, loading = false) }
                } catch (e: CancellationException) {
                    throw e
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    _state.update { it.copy(loading = false, error = e.message ?: "불러오지 못했습니다") }
                }
            }
    }
}
