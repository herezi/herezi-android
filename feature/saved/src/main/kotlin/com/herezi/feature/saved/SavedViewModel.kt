package com.herezi.feature.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.herezi.core.domain.LocationSource
import com.herezi.core.domain.Origin
import com.herezi.core.domain.SavedSpot
import com.herezi.core.domain.SavedSpotsRepository
import com.herezi.core.domain.SpotsRepository
import com.herezi.core.domain.origin
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class SavedUiState(
    val loaded: Boolean = false,
    /** 진행 중, 저장 시각 최신순. */
    val active: List<SavedSpot> = emptyList(),
    /** 끝난 볼거리 (흐리게). */
    val ended: List<SavedSpot> = emptyList(),
    /** 위치를 알 때만 카드에 거리를 보여준다. */
    val origin: Origin? = null,
    val editing: Boolean = false,
    val selected: Set<String> = emptySet(),
) {
    val isEmpty: Boolean get() = loaded && active.isEmpty() && ended.isEmpty()
}

/** 토스트. [undoable] 이면 "되돌리기" 를 붙인다. */
data class SavedMessage(val text: String, val undoable: Boolean = false)

/**
 * 저장 목록 (S4). 화면에 들어올 때마다 [refresh] 로 각 항목을 다시 받아(동시 4개) 종료 여부·기간을 갱신한다.
 * 404 는 종료로 보고 스냅샷을 유지한다. 실패는 조용히 넘긴다.
 */
@HiltViewModel
class SavedViewModel
@Inject
constructor(
    private val savedSpots: SavedSpotsRepository,
    private val repository: SpotsRepository,
    private val location: LocationSource,
) : ViewModel() {
    private val _state = MutableStateFlow(SavedUiState())
    val state: StateFlow<SavedUiState> = _state.asStateFlow()

    private val _messages = Channel<SavedMessage>(Channel.BUFFERED)
    val messages: Flow<SavedMessage> = _messages.receiveAsFlow()

    private var lastRemoved: List<SavedSpot> = emptyList()

    init {
        viewModelScope.launch {
            savedSpots.saved.collect { list ->
                val (ended, active) = list.partition { it.spot.isEnded }
                _state.update { s ->
                    val ids = list.map { it.spot.id }.toSet()
                    s.copy(loaded = true, active = active, ended = ended, selected = s.selected intersect ids)
                }
            }
        }
    }

    /** 화면 진입마다 부른다. */
    fun refresh() {
        viewModelScope.launch {
            val origin = location.origin()
            _state.update { it.copy(origin = origin.takeIf { o -> o.known }) }
            val gate = Semaphore(MAX_CONCURRENT)
            savedSpots.saved.first().map { item ->
                async { gate.withPermit { refreshOne(item) } }
            }.awaitAll()
        }
    }

    private suspend fun refreshOne(item: SavedSpot) {
        try {
            val fresh = repository.spot(item.spot.id)
            savedSpots.updateSnapshot(fresh ?: item.spot.copy(isEnded = true))
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
            // 조용히 실패: 스냅샷 유지, 다음 진입 때 재시도.
        }
    }

    fun remove(id: String) = removeAll(listOf(id))

    fun undo() {
        val items = lastRemoved
        lastRemoved = emptyList()
        viewModelScope.launch { items.forEach { savedSpots.restore(it) } }
    }

    fun clearEnded() = removeAll(_state.value.ended.map { it.spot.id })

    fun toggleEditing() = _state.update { it.copy(editing = !it.editing, selected = emptySet()) }

    fun toggleSelected(id: String) = _state.update {
        it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id)
    }

    fun deleteSelected() {
        val ids = _state.value.selected.toList()
        _state.update { it.copy(editing = false, selected = emptySet()) }
        removeAll(ids)
    }

    private fun removeAll(ids: List<String>) {
        if (ids.isEmpty()) return
        val s = _state.value
        lastRemoved = (s.active + s.ended).filter { it.spot.id in ids }
        viewModelScope.launch {
            savedSpots.remove(ids)
            _messages.send(SavedMessage(MSG_REMOVED, undoable = true))
        }
    }

    companion object {
        const val MAX_CONCURRENT = 4
        const val MSG_REMOVED = "저장을 취소했어요"
    }
}
