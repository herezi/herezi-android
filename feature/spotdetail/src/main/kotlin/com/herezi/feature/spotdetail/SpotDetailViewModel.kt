package com.herezi.feature.spotdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.herezi.core.domain.LocationSource
import com.herezi.core.domain.Origin
import com.herezi.core.domain.Spot
import com.herezi.core.domain.SpotsRepository
import com.herezi.core.domain.account.SaveOutcome
import com.herezi.core.domain.account.SavedLibrary
import com.herezi.core.domain.distanceTo
import com.herezi.core.domain.origin
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SpotDetailUiState {
    data object Loading : SpotDetailUiState

    /** [distanceM] 은 [origin] 기준. [origin] 이 모르는 위치면 "서울시청에서". */
    data class Loaded(val spot: Spot, val distanceM: Int? = null, val origin: Origin? = null) : SpotDetailUiState

    /** 404. [saved] 면 "저장에서 지우기" 를 보여준다. */
    data class NotFound(val saved: Boolean) : SpotDetailUiState

    data object Failed : SpotDetailUiState
}

@HiltViewModel
class SpotDetailViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SpotsRepository,
    private val library: SavedLibrary,
    private val location: LocationSource,
) : ViewModel() {
    private val spotId: String = checkNotNull(savedStateHandle[ARG_SPOT_ID]) { "$ARG_SPOT_ID 인자가 없다" }

    private val _state = MutableStateFlow<SpotDetailUiState>(SpotDetailUiState.Loading)
    val state: StateFlow<SpotDetailUiState> = _state.asStateFlow()

    val saved: StateFlow<Boolean> =
        library.isSaved(spotId).stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _messages = Channel<String>(Channel.BUFFERED)

    /** 토스트 문구. */
    val messages: Flow<String> = _messages.receiveAsFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            // 저장 목록에서 들어오면 스냅샷으로 먼저 그린다(빈 화면 없이).
            val shown = (_state.value as? SpotDetailUiState.Loaded)?.spot ?: library.get(spotId)?.spot
            _state.value = shown?.let { withDistance(it) } ?: SpotDetailUiState.Loading
            try {
                val fresh = repository.spot(spotId)
                _state.value = if (fresh == null) {
                    SpotDetailUiState.NotFound(saved = library.get(spotId) != null)
                } else {
                    withDistance(fresh)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
                if (shown != null) {
                    _messages.send(MSG_STALE)
                } else {
                    _state.value = SpotDetailUiState.Failed
                }
            }
        }
    }

    /** 로그아웃이면 S7 시트가 뜨고(아이콘은 그대로), 로그인 후 저장은 [SavedLibrary] 가 마친다. */
    fun toggleSave() {
        val spot = (_state.value as? SpotDetailUiState.Loaded)?.spot ?: return
        viewModelScope.launch { outcomeMessage(library.toggle(spot))?.let { _messages.send(it) } }
    }

    /** 404 화면의 "저장에서 지우기". */
    fun removeFromSaved() {
        viewModelScope.launch {
            if (library.remove(listOf(spotId))) {
                _state.value = SpotDetailUiState.NotFound(saved = false)
                _messages.send(MSG_UNSAVED)
            } else {
                _messages.send(SavedLibrary.MSG_REMOVE_FAILED)
            }
        }
    }

    fun addressCopied() {
        viewModelScope.launch { _messages.send(MSG_ADDRESS_COPIED) }
    }

    private suspend fun withDistance(spot: Spot): SpotDetailUiState.Loaded {
        val origin = location.origin()
        return SpotDetailUiState.Loaded(spot, origin.point.distanceTo(spot.location), origin)
    }

    companion object {
        /** 저장 버튼 결과 → 토스트. 로그인 시트가 뜬 경우는 문구 없음. */
        fun outcomeMessage(outcome: SaveOutcome): String? = when (outcome) {
            SaveOutcome.NeedsLogin -> null
            SaveOutcome.Saved -> MSG_SAVED
            SaveOutcome.Removed -> MSG_UNSAVED
            is SaveOutcome.Failed -> outcome.message
        }

        /** 내비게이션 인자 이름. :app 의 라우트와 같아야 한다. */
        const val ARG_SPOT_ID = "spotId"

        const val MSG_SAVED = "저장했어요"
        const val MSG_UNSAVED = "저장을 취소했어요"
        const val MSG_STALE = "최신 정보를 불러오지 못했어요"
        const val MSG_ADDRESS_COPIED = "주소를 복사했어요"
    }
}
