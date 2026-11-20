package com.herezi.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.herezi.core.domain.LocationSource
import com.herezi.core.domain.RecentSearchesRepository
import com.herezi.core.domain.Spot
import com.herezi.core.domain.SpotQuery
import com.herezi.core.domain.SpotsRepository
import com.herezi.core.domain.origin
import dagger.hilt.android.lifecycle.HiltViewModel
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

sealed interface SearchResult {
    /** 검색 전: 최근 검색을 보여준다. */
    data object Idle : SearchResult

    data class Loading(val q: String) : SearchResult

    data class Found(val q: String, val items: List<Spot>) : SearchResult

    data class Empty(val q: String) : SearchResult

    data class Failed(val q: String) : SearchResult
}

data class SearchUiState(
    val input: String = "",
    val recent: List<String> = emptyList(),
    val result: SearchResult = SearchResult.Idle,
)

/**
 * 검색 (S5). 키보드 검색을 누를 때만 조회한다(M1: 입력마다 조회하지 않음).
 * 결과는 "지금" 진행 중인 것만, 현재 위치(모르면 서울시청) 기준 반경 20km 거리순.
 */
@HiltViewModel
class SearchViewModel
@Inject
constructor(
    private val repository: SpotsRepository,
    private val recentSearches: RecentSearchesRepository,
    private val location: LocationSource,
) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    private var job: Job? = null

    init {
        viewModelScope.launch { recentSearches.recent.collect { r -> _state.update { it.copy(recent = r) } } }
    }

    fun onInput(text: String) = _state.update { it.copy(input = text) }

    /** 입력 지우기 → 검색 전 화면(최근 검색)으로. */
    fun clearInput() {
        job?.cancel()
        _state.update { it.copy(input = "", result = SearchResult.Idle) }
    }

    fun search() = search(_state.value.input)

    fun search(text: String) {
        val q = text.trim()
        if (q.length < MIN_LENGTH) {
            viewModelScope.launch { _messages.send(MSG_TOO_SHORT) }
            return
        }
        _state.update { it.copy(input = q) }
        viewModelScope.launch { recentSearches.add(q) }
        load(q)
    }

    fun retry() {
        val q = (_state.value.result as? SearchResult.Failed)?.q ?: return
        load(q)
    }

    fun removeRecent(q: String) {
        viewModelScope.launch { recentSearches.remove(q) }
    }

    fun clearRecent() {
        viewModelScope.launch { recentSearches.clear() }
    }

    private fun load(q: String) {
        job?.cancel()
        _state.update { it.copy(result = SearchResult.Loading(q)) }
        job = viewModelScope.launch {
            val result = try {
                val center = location.origin().point
                val items = repository
                    .nearby(SpotQuery(center = center, radiusM = SpotQuery.SEARCH_RADIUS_M, keyword = q))
                    .items
                if (items.isEmpty()) SearchResult.Empty(q) else SearchResult.Found(q, items)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
                SearchResult.Failed(q)
            }
            _state.update { it.copy(result = result) }
        }
    }

    companion object {
        const val MIN_LENGTH = 2
        const val MSG_TOO_SHORT = "두 글자 이상 입력해 주세요"
    }
}
