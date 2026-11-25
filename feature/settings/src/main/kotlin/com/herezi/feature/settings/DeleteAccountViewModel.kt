package com.herezi.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.herezi.core.domain.account.AuthException
import com.herezi.core.domain.account.AuthProvider
import com.herezi.core.domain.account.LoginReason
import com.herezi.core.domain.account.LoginRequest
import com.herezi.core.domain.account.SavedLibrary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DeleteAccountUiState(
    val savedCount: Int = 0,
    val provider: AuthProvider? = null,
    val confirmed: Boolean = false,
    val deleting: Boolean = false,
    val done: Boolean = false,
) {
    val savedLine: String
        get() = if (savedCount > 0) "저장한 볼거리 ${savedCount}개가 모두 지워져요" else "저장한 볼거리가 모두 지워져요"

    val unlinkLine: String
        get() = if (provider == AuthProvider.APPLE) "Apple 계정 연결이 끊어져요" else "카카오 계정 연결이 끊어져요"
}

/** S6 회원 탈퇴: 확인 화면 → 체크 → 시스템 확인 → 진행 → 완료. 실패하면 화면·체크 유지 + 토스트. */
@HiltViewModel
class DeleteAccountViewModel
@Inject
constructor(private val library: SavedLibrary) : ViewModel() {
    private val _state = MutableStateFlow(
        DeleteAccountUiState(savedCount = library.items.value.size, provider = library.session.value?.provider),
    )
    val state: StateFlow<DeleteAccountUiState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    init {
        viewModelScope.launch {
            library.items.collect { items -> _state.update { if (it.done) it else it.copy(savedCount = items.size) } }
        }
    }

    fun toggleConfirmed() = _state.update { if (it.deleting) it else it.copy(confirmed = !it.confirmed) }

    fun delete() {
        if (!_state.value.confirmed || _state.value.deleting) return
        _state.update { it.copy(deleting = true) }
        viewModelScope.launch {
            try {
                library.deleteAccount()
                _state.update { it.copy(deleting = false, done = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                _state.update { it.copy(deleting = false) }
                when (e) {
                    is AuthException.SessionExpired -> library.requestLogin(LoginRequest(LoginReason.ACCOUNT))
                    is AuthException.Offline -> _messages.send(MSG_OFFLINE)
                    else -> _messages.send(MSG_FAILED)
                }
            }
        }
    }

    companion object {
        const val MSG_FAILED = "탈퇴하지 못했어요. 잠시 후 다시 시도해 주세요."
        const val MSG_OFFLINE = "인터넷 연결을 확인하고 다시 시도해 주세요."
    }
}
