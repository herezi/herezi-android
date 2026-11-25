package com.herezi.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.herezi.core.domain.HomeFeed
import com.herezi.core.domain.HomeFeedBuilder
import com.herezi.core.domain.LocationSource
import com.herezi.core.domain.ReverseGeocoder
import com.herezi.core.domain.Spot
import com.herezi.core.domain.SpotQuery
import com.herezi.core.domain.SpotsRepository
import com.herezi.core.domain.When
import com.herezi.core.domain.account.SaveOutcome
import com.herezi.core.domain.account.SavedLibrary
import com.herezi.core.domain.origin
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    /** null 이면 위치 확인 중(스켈레톤). */
    val locationName: String? = null,
    val locationKnown: Boolean = true,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val feed: HomeFeed? = null,
    /** 보여줄 이전 데이터가 없는 실패. */
    val error: Boolean = false,
    val savedIds: Set<String> = emptySet(),
    /** 섹션 기준 시각(응답 받은 순간). 카드 기간 표기도 이 값으로. */
    val now: Instant = Instant.EPOCH,
)

/**
 * 홈 (S1). `GET /v1/spots?radius_m=3000&when=week&limit=100` 한 번으로 받아 [HomeFeedBuilder] 로 섹션을 나눈다.
 * 15분 넘게 지나 돌아오면 다시 부른다.
 */
@HiltViewModel
class HomeViewModel
@Inject
constructor(
    private val repository: SpotsRepository,
    private val location: LocationSource,
    private val geocoder: ReverseGeocoder,
    private val library: SavedLibrary,
    private val clock: Clock,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    private var loadJob: Job? = null
    private var loadedAt: Instant? = null

    init {
        viewModelScope.launch {
            library.items.collect { list ->
                _state.update { it.copy(savedIds = list.map { s -> s.spot.id }.toSet()) }
            }
        }
        load()
    }

    /** 당겨서 새로고침: 위치 다시 확인 후 재조회. */
    fun refresh() = load(pull = true)

    /** 화면이 다시 보일 때. 15분 넘었으면 재조회. */
    fun onResume() {
        val last = loadedAt ?: return
        if (Duration.between(last, clock.instant()) > STALE_AFTER) load()
    }

    /** 로그아웃이면 S7 시트만 띄운다(아이콘은 채우지 않음). */
    fun toggleSave(spot: Spot) {
        viewModelScope.launch {
            when (val outcome = library.toggle(spot)) {
                SaveOutcome.NeedsLogin -> Unit
                SaveOutcome.Saved -> _messages.send(MSG_SAVED)
                SaveOutcome.Removed -> _messages.send(MSG_UNSAVED)
                is SaveOutcome.Failed -> _messages.send(outcome.message)
            }
        }
    }

    private fun load(pull: Boolean = false) {
        loadJob?.cancel()
        val hadFeed = _state.value.feed != null
        _state.update { it.copy(loading = !hadFeed, refreshing = pull, error = false) }
        loadJob = viewModelScope.launch {
            val origin = location.origin()
            _state.update { it.copy(locationKnown = origin.known) }
            launch {
                val name = if (origin.known) geocoder.placeName(origin.point) ?: NAME_UNKNOWN else NAME_CITY_HALL
                _state.update { it.copy(locationName = name) }
            }
            try {
                val page = repository.nearby(
                    SpotQuery(
                        center = origin.point,
                        radiusM = HomeFeedBuilder.HOME_RADIUS_M,
                        period = When.WEEK,
                        limit = HomeFeedBuilder.HOME_LIMIT,
                    ),
                )
                val now = clock.instant()
                val feed = HomeFeedBuilder.build(page.items, now, Seoul, hasMore = page.nextCursor != null)
                loadedAt = now
                _state.update { it.copy(feed = feed, now = now, loading = false, refreshing = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
                _state.update { it.copy(loading = false, refreshing = false, error = !hadFeed) }
                if (hadFeed) _messages.send(MSG_STALE)
            }
        }
    }

    companion object {
        val Seoul: ZoneId = ZoneId.of("Asia/Seoul")
        val STALE_AFTER: Duration = Duration.ofMinutes(15)

        const val NAME_UNKNOWN = "내 주변"
        const val NAME_CITY_HALL = "서울시청 주변"
        const val MSG_SAVED = "저장했어요"
        const val MSG_UNSAVED = "저장을 취소했어요"
        const val MSG_STALE = "최신 정보를 불러오지 못했어요"
    }
}
