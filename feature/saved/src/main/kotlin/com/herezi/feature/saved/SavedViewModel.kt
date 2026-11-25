package com.herezi.feature.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.herezi.core.domain.LocationSource
import com.herezi.core.domain.Origin
import com.herezi.core.domain.SavedSpot
import com.herezi.core.domain.account.LoginReason
import com.herezi.core.domain.account.LoginRequest
import com.herezi.core.domain.account.SavedLibrary
import com.herezi.core.domain.account.SavedSync
import com.herezi.core.domain.origin
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** S4 본문 상태. */
enum class SavedMode { SIGNED_OUT, LOADING, ERROR, EMPTY, LIST }

data class SavedUiState(
    val signedIn: Boolean = false,
    val sync: SavedSync = SavedSync.IDLE,
    /** 로그인 전 안내: 이 기기에 남은 로컬 저장 수. */
    val deviceSavedCount: Int = 0,
    /** 진행 중, 저장 시각 최신순. */
    val active: List<SavedSpot> = emptyList(),
    /** 끝난 볼거리 (흐리게). */
    val ended: List<SavedSpot> = emptyList(),
    /** 위치를 알 때만 카드에 거리를 보여준다. */
    val origin: Origin? = null,
    val editing: Boolean = false,
    val selected: Set<String> = emptySet(),
    val refreshing: Boolean = false,
) {
    private val hasItems: Boolean get() = active.isNotEmpty() || ended.isNotEmpty()

    val mode: SavedMode get() = when {
        !signedIn -> SavedMode.SIGNED_OUT
        hasItems -> SavedMode.LIST
        sync == SavedSync.FAILED -> SavedMode.ERROR
        sync == SavedSync.LOADED -> SavedMode.EMPTY
        else -> SavedMode.LOADING
    }

    /** 편집 버튼은 로그인 상태이고 항목이 있을 때만. */
    val canEdit: Boolean get() = signedIn && hasItems
}

/** 토스트. [undoable] 이면 "되돌리기" 를 붙인다. */
data class SavedMessage(val text: String, val undoable: Boolean = false)

/**
 * 저장 목록 (S4). 계정 목록([SavedLibrary])을 그대로 보여준다 — 캐시로 먼저 그리고 화면 진입마다 서버에서 다시 받는다.
 * 각 항목을 `GET /v1/spots/{id}` 로 다시 받던 방식은 없앴다(서버 응답에 `is_ended` 가 있다).
 */
@HiltViewModel
class SavedViewModel
@Inject
constructor(private val library: SavedLibrary, private val location: LocationSource) :
    ViewModel() {
    private val _state = MutableStateFlow(SavedUiState())
    val state: StateFlow<SavedUiState> = _state.asStateFlow()

    private val _messages = Channel<SavedMessage>(Channel.BUFFERED)
    val messages: Flow<SavedMessage> = _messages.receiveAsFlow()

    private var lastRemoved: List<SavedSpot> = emptyList()

    init {
        viewModelScope.launch {
            combine(library.items, library.sync, library.session, library.deviceSavedCount) { items, sync, session, n ->
                Snapshot(items, sync, session != null, n)
            }.collect { snap ->
                val (ended, active) = snap.items.partition { it.spot.isEnded }
                _state.update { s ->
                    val ids = snap.items.map { it.spot.id }.toSet()
                    s.copy(
                        signedIn = snap.signedIn,
                        sync = snap.sync,
                        deviceSavedCount = snap.deviceCount,
                        active = active,
                        ended = ended,
                        selected = s.selected intersect ids,
                        editing = s.editing && snap.signedIn && ids.isNotEmpty(),
                    )
                }
            }
        }
    }

    private data class Snapshot(
        val items: List<SavedSpot>,
        val sync: SavedSync,
        val signedIn: Boolean,
        val deviceCount: Int,
    )

    /** 화면 진입마다 부른다. 실패는 조용히(캐시 유지). */
    fun refresh() {
        viewModelScope.launch {
            val origin = location.origin()
            _state.update { it.copy(origin = origin.takeIf { o -> o.known }) }
            library.refresh()
        }
    }

    /** 당겨서 새로고침. 캐시가 있을 때 실패하면 토스트. */
    fun pullRefresh() {
        if (_state.value.refreshing) return
        _state.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            val hadItems = _state.value.active.isNotEmpty() || _state.value.ended.isNotEmpty()
            val ok = library.refresh()
            _state.update { it.copy(refreshing = false) }
            if (!ok && hadItems && library.signedIn) _messages.send(SavedMessage(MSG_REFRESH_FAILED))
        }
    }

    fun retry() {
        viewModelScope.launch { library.refresh() }
    }

    fun signIn() = library.requestLogin(LoginRequest(LoginReason.ACCOUNT))

    fun remove(id: String) = removeAll(listOf(id))

    fun undo() {
        val items = lastRemoved
        lastRemoved = emptyList()
        viewModelScope.launch {
            if (!library.restore(items)) _messages.send(SavedMessage(SavedLibrary.MSG_SAVE_FAILED))
        }
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
        val removed = (s.active + s.ended).filter { it.spot.id in ids }
        viewModelScope.launch {
            if (library.remove(ids)) {
                lastRemoved = removed
                _messages.send(SavedMessage(MSG_REMOVED, undoable = true))
            } else {
                _messages.send(SavedMessage(SavedLibrary.MSG_REMOVE_FAILED))
            }
        }
    }

    companion object {
        const val MSG_REMOVED = "저장을 취소했어요"
        const val MSG_REFRESH_FAILED = "최신 목록을 불러오지 못했어요"
    }
}
