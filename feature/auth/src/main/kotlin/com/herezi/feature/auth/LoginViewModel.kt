package com.herezi.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.herezi.core.domain.account.AuthCrypto
import com.herezi.core.domain.account.AuthException
import com.herezi.core.domain.account.AuthProvider
import com.herezi.core.domain.account.AuthService
import com.herezi.core.domain.account.LoginRequest
import com.herezi.core.domain.account.SavedLibrary
import com.herezi.core.domain.account.SocialLogin
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** S7 상태. [inProgress] 가 있으면 다른 버튼·닫기·끌어내리기를 막는다. [error] 는 오류 줄 문구. */
data class LoginUiState(val inProgress: AuthProvider? = null, val error: String? = null)

/**
 * S7 로그인 시트.
 * - 카카오: 원문 nonce 를 만들고 SHA-256(hex)은 카카오에, 원문은 Supabase 에 보낸다.
 * - Apple: Supabase OAuth(PKCE) 웹 흐름. verifier 는 이 VM 이 들고 있다가 딥링크로 받은 code 와 교환한다.
 * 성공하면 [done] 을 보내 시트를 닫고, 로그인 후 할 일은 [SavedLibrary.onSignedIn] 이 앱 범위에서 한다.
 */
@HiltViewModel
class LoginViewModel
@Inject
constructor(
    private val auth: AuthService,
    private val social: SocialLogin,
    private val library: SavedLibrary,
) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private val _done = Channel<Unit>(Channel.CONFLATED)
    val done: Flow<Unit> = _done.receiveAsFlow()

    fun signIn(provider: AuthProvider, request: LoginRequest?) {
        if (_state.value.inProgress != null) return // 연타 무시
        _state.value = LoginUiState(inProgress = provider)
        viewModelScope.launch {
            try {
                when (provider) {
                    AuthProvider.KAKAO -> kakao()
                    AuthProvider.APPLE -> apple()
                }
                _state.value = LoginUiState()
                library.onSignedIn(request)
                _done.send(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                _state.value = LoginUiState(error = errorMessage(e))
            }
        }
    }

    private suspend fun kakao() {
        if (!auth.isConfigured) throw AuthException.NotConfigured()
        val nonce = AuthCrypto.randomToken()
        val idToken = social.kakaoIdToken(AuthCrypto.sha256Hex(nonce))
        auth.signInWithIdToken(AuthProvider.KAKAO, idToken, nonce)
    }

    private suspend fun apple() {
        val verifier = AuthCrypto.randomToken() + AuthCrypto.randomToken()
        val url = auth.authorizeUrl(AuthProvider.APPLE, AuthCrypto.codeChallenge(verifier))
        val code = social.webAuthCode(url)
        auth.exchangeCode(code, verifier, AuthProvider.APPLE)
    }

    companion object {
        const val MSG_FAILED = "로그인하지 못했어요. 다시 시도해 주세요."
        const val MSG_OFFLINE = "인터넷 연결을 확인하고 다시 시도해 주세요."

        /** 취소는 문구 없이 기본 상태(null). 원인은 사용자에게 보여주지 않는다. */
        fun errorMessage(e: AuthException): String? = when (e) {
            is AuthException.Cancelled -> null
            is AuthException.Offline -> MSG_OFFLINE
            else -> MSG_FAILED
        }
    }
}
