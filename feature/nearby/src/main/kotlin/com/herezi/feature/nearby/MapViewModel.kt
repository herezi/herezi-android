package com.herezi.feature.nearby

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.herezi.core.domain.Category
import com.herezi.core.domain.GeoBounds
import com.herezi.core.domain.GeoPoint
import com.herezi.core.domain.ListFilter
import com.herezi.core.domain.LocationSource
import com.herezi.core.domain.MapQuery
import com.herezi.core.domain.ReverseGeocoder
import com.herezi.core.domain.Spot
import com.herezi.core.domain.SpotsRepository
import com.herezi.core.domain.When
import com.herezi.core.domain.distanceTo
import com.herezi.core.domain.origin
import com.herezi.core.map.MapCameraRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 지도 탭(S2) 상태. */
data class MapUiState(
    /** 내 위치(모르면 서울시청). 첫 카메라 중심·거리 기준. */
    val origin: GeoPoint = GeoPoint.SeoulCityHall,
    val locationOff: Boolean = true,
    /** 위치를 아직 읽지 않았으면 false → 지도를 그리지 않는다(엉뚱한 곳에서 시작하지 않게). */
    val ready: Boolean = false,
    val locationName: String? = null,
    val filter: ListFilter = ListFilter(),
    /** 지도 중심에서 가까운 순(서버 순서). 거리는 [origin] 기준. */
    val spots: List<Spot> = emptyList(),
    val loading: Boolean = false,
    val error: Boolean = false,
    val searchedOnce: Boolean = false,
    val selectedId: String? = null,
    /** 사용자가 지도를 옮겼고 아직 그 영역을 찾지 않았다 → '이 지역에서 다시 찾기'. */
    val showResearch: Boolean = false,
    /** '다시 찾기'를 눌러 찾는 중 → 알약을 남겨 두고 아이콘을 돌린다. */
    val researching: Boolean = false,
    /** 한 번씩 소비하는 카메라 이동 요청(내 위치). */
    val cameraRequest: MapCameraRequest? = null,
    /** 결과가 잘렸다는 토스트를 띄울 차례. 띄우면 [MapViewModel.truncatedShown]. */
    val truncatedNotice: Boolean = false,
    /** 내 위치 버튼을 눌렀는데 권한이 없다 → 배너의 '설정'과 같은 동작. */
    val openLocationSettings: Boolean = false,
    /** 다른 동네로 옮겨 다시 찾은 지도 중심. '목록으로 보기'가 이 기준으로 연다. null 이면 내 위치. */
    val listCenter: GeoPoint? = null,
) {
    val selected: Spot? get() = selectedId?.let { id -> spots.firstOrNull { it.id == id } }

    /** 시트 접힘 줄 문구 (S2 상태 표). */
    val headline: String
        get() = when {
            loading -> "찾는 중…"
            error -> "불러오지 못했어요"
            searchedOnce && spots.isEmpty() -> "이 지역엔 지금 볼거리가 없어요"
            searchedOnce -> "이 지역 볼거리 ${spots.size}개"
            else -> "찾는 중…"
        }

    /** 접힘 줄 글자 버튼. 오류 → 다시 시도, 빈(이번 주가 아닐 때) → 이번 주로 보기. */
    val headlineAction: HeadlineAction?
        get() = when {
            loading -> null
            error -> HeadlineAction.RETRY
            searchedOnce && spots.isEmpty() && filter.period != When.WEEK -> HeadlineAction.WEEK
            else -> null
        }
}

enum class HeadlineAction(val label: String) { RETRY("다시 시도"), WEEK("이번 주로 보기") }

/**
 * S2 지도. 지도를 움직여도 자동으로 다시 찾지 않는다 — 카메라가 멈추면 영역만 기억하고
 * 사용자 제스처였으면 '이 지역에서 다시 찾기'를 띄운다. 첫 영역·필터 변경·내 위치 이동은 바로 찾는다.
 */
@HiltViewModel
class MapViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SpotsRepository,
    private val location: LocationSource,
    private val geocoder: ReverseGeocoder,
) : ViewModel() {
    private val _state = MutableStateFlow(MapUiState(filter = NearbyViewModel.filterFrom(savedStateHandle)))
    val state: StateFlow<MapUiState> = _state.asStateFlow()

    /** 마지막으로 카메라가 멈춘 영역(지금 보이는 곳). */
    private var visible: GeoBounds? = null

    /** 마지막으로 찾은 영역(다시 시도·필터 변경용). */
    private var searched: GeoBounds? = null

    /** 다음 카메라 멈춤에서 바로 찾는다(내 위치 이동). */
    private var searchOnNextIdle = false
    private var truncatedNoticeFor: GeoBounds? = null
    private var searchJob: Job? = null
    private var nameJob: Job? = null
    private var cameraSeq = 0L

    init {
        viewModelScope.launch {
            val origin = location.origin()
            _state.update { it.copy(origin = origin.point, locationOff = !origin.known, ready = true) }
            updateName(origin.point, origin.known)
        }
    }

    /** 지도가 멈췄다. 첫 영역이면 바로 찾고, 사용자가 옮긴 거면 '다시 찾기'를 띄운다. */
    fun onCameraIdle(bounds: GeoBounds, byUser: Boolean) {
        visible = bounds
        when {
            searched == null || searchOnNextIdle -> {
                searchOnNextIdle = false
                search(bounds)
            }
            byUser && bounds != searched -> _state.update { it.copy(showResearch = true) }
        }
    }

    /** '이 지역에서 다시 찾기'. 다른 동네면 위치명·목록 기준도 그 중심으로 바꾼다. */
    fun researchHere() {
        val bounds = visible ?: return
        _state.update { it.copy(showResearch = false, researching = true, listCenter = bounds.center) }
        search(bounds)
        updateName(bounds.center, known = true)
    }

    /** 내 위치 버튼. 권한이 없으면 설정으로, 있으면 내 위치로 옮긴 뒤 그 지역을 찾는다. */
    fun moveToMyLocation() {
        viewModelScope.launch {
            val origin = location.origin()
            if (!origin.known) {
                _state.update { it.copy(locationOff = true, openLocationSettings = true) }
                return@launch
            }
            searchOnNextIdle = true
            _state.update {
                it.copy(
                    origin = origin.point,
                    locationOff = false,
                    listCenter = null,
                    showResearch = false,
                    cameraRequest = MapCameraRequest(origin.point, ++cameraSeq),
                )
            }
            updateName(origin.point, known = true)
        }
    }

    /** 화면이 돌아왔을 때 권한이 바뀌었으면 배너만 갱신한다. 지역 파일이 바뀌었으면 같은 영역을 다시 계산한다. */
    fun refreshLocation() {
        viewModelScope.launch {
            val changed = try {
                repository.refreshData(force = false)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
                false
            }
            searched?.let { if (changed) search(it) }
            val origin = location.origin()
            if (_state.value.locationOff != !origin.known) {
                _state.update { it.copy(origin = origin.point, locationOff = !origin.known) }
            }
        }
    }

    fun select(id: String) = _state.update { it.copy(selectedId = id) }

    fun clearSelection() = _state.update { it.copy(selectedId = null) }

    fun toggleCategory(category: Category) = updateFilter {
        it.copy(categories = if (category in it.categories) it.categories - category else it.categories + category)
    }

    fun clearCategories() = updateFilter { it.copy(categories = emptySet()) }

    fun setPeriod(period: When) = updateFilter { it.copy(period = period) }

    fun setFreeOnly(freeOnly: Boolean) = updateFilter { it.copy(freeOnly = freeOnly) }

    fun retry() {
        (searched ?: visible)?.let(::search)
    }

    fun truncatedShown() = _state.update { it.copy(truncatedNotice = false) }

    fun locationSettingsOpened() = _state.update { it.copy(openLocationSettings = false) }

    /** 필터가 바뀌면 지금 보이는 영역으로 바로 다시 찾는다. */
    private fun updateFilter(change: (ListFilter) -> ListFilter) {
        val before = _state.value.filter
        val after = change(before)
        if (after == before) return
        _state.update { it.copy(filter = after, showResearch = false, researching = false) }
        (visible ?: searched)?.let(::search)
    }

    private fun search(bounds: GeoBounds) {
        searchJob?.cancel()
        searched = bounds
        // 로딩 중에도 기존 핀은 둔다(S2 상태 표).
        _state.update { it.copy(loading = true, error = false) }
        val filter = _state.value.filter
        searchJob = viewModelScope.launch {
            try {
                val result = repository.inBounds(
                    MapQuery(
                        bounds,
                        categories = filter.categories,
                        period = filter.period,
                        freeOnly = filter.freeOnly,
                    ),
                )
                val origin = _state.value.origin
                val items = result.items.map { it.copy(distanceM = origin.distanceTo(it.location)) }
                val notice = result.truncated && truncatedNoticeFor != bounds
                if (notice) truncatedNoticeFor = bounds
                _state.update { s ->
                    s.copy(
                        spots = items,
                        loading = false,
                        researching = false,
                        searchedOnce = true,
                        selectedId = s.selectedId?.takeIf { id -> items.any { it.id == id } },
                        truncatedNotice = s.truncatedNotice || notice,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
                _state.update { it.copy(loading = false, error = true, researching = false) }
            }
        }
    }

    private fun updateName(point: GeoPoint, known: Boolean) {
        nameJob?.cancel()
        nameJob = viewModelScope.launch {
            val name = if (known) {
                geocoder.placeName(
                    point,
                ) ?: NearbyViewModel.NAME_UNKNOWN
            } else {
                NearbyViewModel.NAME_CITY_HALL
            }
            _state.update { it.copy(locationName = name) }
        }
    }
}
