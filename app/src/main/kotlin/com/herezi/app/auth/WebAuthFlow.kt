package com.herezi.app.auth

import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.herezi.core.domain.account.AuthException
import kotlinx.coroutines.CompletableDeferred

/**
 * 브라우저(Custom Tabs) OAuth 한 번. 딥링크(`herezi://auth-callback`)가 오면 [deliver],
 * 딥링크 없이 앱으로 돌아오면(사용자가 탭을 닫음) 잠깐 기다렸다가 취소로 끝낸다.
 */
class WebAuthFlow {
    private var pending: CompletableDeferred<Uri>? = null
    private var opened = false
    private val main = Handler(Looper.getMainLooper())

    suspend fun await(open: () -> Unit): Uri {
        pending?.completeExceptionally(AuthException.Cancelled())
        val deferred = CompletableDeferred<Uri>()
        pending = deferred
        opened = false
        open()
        opened = true
        return try {
            deferred.await()
        } finally {
            if (pending === deferred) pending = null
        }
    }

    /** MainActivity 가 딥링크를 받았을 때. 이 흐름의 주소가 아니면 false. */
    fun deliver(uri: Uri): Boolean {
        if (uri.scheme != CALLBACK_SCHEME || uri.host != CALLBACK_HOST) return false
        pending?.complete(uri)
        return true
    }

    /** 앱이 다시 보일 때. 딥링크가 같이 왔으면 [deliver] 가 먼저 불린다. */
    fun onResume() {
        val deferred = pending ?: return
        if (!opened) return
        main.postDelayed({
            if (pending ===
                deferred
            ) {
                deferred.completeExceptionally(AuthException.Cancelled())
            }
        }, CANCEL_GRACE_MS)
    }

    companion object {
        const val CALLBACK_SCHEME = "herezi"
        const val CALLBACK_HOST = "auth-callback"
        private const val CANCEL_GRACE_MS = 700L

        /** `?code=…` 또는 오류(`error`, `error_description`). 프래그먼트(#)도 본다. */
        fun codeFrom(uri: Uri): String {
            uri.getQueryParameter("code")?.takeIf { it.isNotBlank() }?.let { return it }
            val fragment = uri.fragment.orEmpty()
            val params = fragment.split('&').mapNotNull { part ->
                part.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to Uri.decode(it[1]) }
            }.toMap()
            params["code"]?.let { return it }
            val error = uri.getQueryParameter("error") ?: params["error"]
            if (error == "access_denied") throw AuthException.Cancelled()
            throw AuthException.Failed("oauth ${error ?: "no code"}")
        }
    }
}
