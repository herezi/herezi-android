package com.herezi.app.auth

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.herezi.core.domain.account.AuthException
import com.herezi.core.domain.account.SocialLogin
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.user.UserApiClient
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * 카카오 SDK·Custom Tabs 로그인 화면. SDK 는 :app 에서만 쓴다(feature 는 도메인 포트 [SocialLogin] 만 안다).
 * 카카오: 카카오톡이 있으면 카카오톡, 없거나 쓸 수 없으면 카카오 계정(웹). OIDC id_token 을 받는다(콘솔에서 OIDC 켜야 함).
 */
class AndroidSocialLogin(
    private val activities: ActivityTracker,
    private val web: WebAuthFlow,
    private val kakaoConfigured: Boolean,
) : SocialLogin {
    override suspend fun kakaoIdToken(hashedNonce: String): String = withContext(Dispatchers.Main) {
        if (!kakaoConfigured) throw AuthException.NotConfigured()
        val activity = activities.activity ?: throw AuthException.Failed("no activity")
        val client = UserApiClient.instance
        val token = if (client.isKakaoTalkLoginAvailable(activity)) {
            try {
                kakaoCall { cb -> client.loginWithKakaoTalk(activity, nonce = hashedNonce, callback = cb) }
            } catch (e: AuthException.Cancelled) {
                throw e
            } catch (_: AuthException) {
                // 카카오톡에 연결된 계정이 없는 등 → 카카오 계정(웹)으로.
                kakaoCall { cb -> client.loginWithKakaoAccount(activity, nonce = hashedNonce, callback = cb) }
            }
        } else {
            kakaoCall { cb -> client.loginWithKakaoAccount(activity, nonce = hashedNonce, callback = cb) }
        }
        token.idToken ?: throw AuthException.Failed("kakao id_token 없음 (OIDC 설정 확인)")
    }

    override suspend fun webAuthCode(authorizeUrl: String): String = withContext(Dispatchers.Main) {
        val activity = activities.activity ?: throw AuthException.Failed("no activity")
        val uri = web.await {
            CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(activity, Uri.parse(authorizeUrl))
        }
        WebAuthFlow.codeFrom(uri)
    }

    override suspend fun kakaoLogout() {
        if (!kakaoConfigured) return
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont -> UserApiClient.instance.logout { cont.resume(Unit) } }
        }
    }

    override suspend fun kakaoUnlink() {
        if (!kakaoConfigured) return
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont -> UserApiClient.instance.unlink { cont.resume(Unit) } }
        }
    }

    private suspend fun kakaoCall(start: ((OAuthToken?, Throwable?) -> Unit) -> Unit): OAuthToken =
        suspendCancellableCoroutine { cont ->
            start { token, error ->
                when {
                    token != null -> cont.resume(token)
                    error is ClientError && error.reason == ClientErrorCause.Cancelled ->
                        cont.resumeWithException(AuthException.Cancelled())
                    error is java.io.IOException -> cont.resumeWithException(AuthException.Offline(error))
                    else -> cont.resumeWithException(AuthException.Failed("kakao ${error?.javaClass?.simpleName}"))
                }
            }
        }
}
