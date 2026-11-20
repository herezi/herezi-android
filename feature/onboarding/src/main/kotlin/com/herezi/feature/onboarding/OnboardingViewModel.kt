package com.herezi.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.herezi.core.domain.AppPreferences
import com.herezi.core.domain.LocationAccess
import com.herezi.core.domain.LocationSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class OnboardingState {
    /** 저장된 값을 읽는 중 (빈 화면). */
    Checking,

    /** S0 권한 안내를 보여준다. */
    Show,

    /** 홈으로 간다. */
    Done,
}

/**
 * S0. 첫 실행이고 위치 권한을 아직 묻지 않았을 때만 보여준다.
 * 허용·거부·나중에 모두 홈으로 가고, 다시 보여주지 않는다.
 */
@HiltViewModel
class OnboardingViewModel
@Inject
constructor(
    private val preferences: AppPreferences,
    private val location: LocationSource,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingState.Checking)
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val show = !preferences.onboardingDone() && location.access() == LocationAccess.DENIED
            _state.value = if (show) OnboardingState.Show else OnboardingState.Done
        }
    }

    /** 권한 창 결과가 오면, 또는 "나중에 할게요". */
    fun finish() {
        viewModelScope.launch {
            preferences.setOnboardingDone()
            _state.value = OnboardingState.Done
        }
    }
}
