package com.herezi.feature.spotdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.herezi.core.domain.Spot
import com.herezi.core.domain.SpotsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SpotDetailUiState {
    data object Loading : SpotDetailUiState

    data class Loaded(val spot: Spot) : SpotDetailUiState

    data object NotFound : SpotDetailUiState

    data class Failed(val message: String) : SpotDetailUiState
}

@HiltViewModel
class SpotDetailViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SpotsRepository,
) : ViewModel() {
    private val spotId: String = checkNotNull(savedStateHandle[ARG_SPOT_ID]) { "$ARG_SPOT_ID 인자가 없다" }

    private val _state = MutableStateFlow<SpotDetailUiState>(SpotDetailUiState.Loading)
    val state: StateFlow<SpotDetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = SpotDetailUiState.Loading
        viewModelScope.launch {
            _state.value =
                try {
                    repository.spot(spotId)?.let { SpotDetailUiState.Loaded(it) } ?: SpotDetailUiState.NotFound
                } catch (e: CancellationException) {
                    throw e
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    SpotDetailUiState.Failed(e.message ?: "불러오지 못했습니다")
                }
        }
    }

    companion object {
        /** 내비게이션 인자 이름. :app 의 라우트와 같아야 한다. */
        const val ARG_SPOT_ID = "spotId"
    }
}
