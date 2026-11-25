package com.herezi.core.domain.account

import com.herezi.core.domain.SavedSpot
import com.herezi.core.domain.SavedSpotsRepository
import com.herezi.core.domain.Spot
import java.time.Clock
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** S7 시트를 연 곳. 제목과 로그인 후 할 일이 다르다. */
enum class LoginReason {
    /** 로그아웃 상태에서 저장 → 로그인 후 그 볼거리를 저장. */
    SAVE,

    /** S4 로그인 버튼·S6 로그인 행. */
    ACCOUNT,
}

data class LoginRequest(val reason: LoginReason, val pendingSave: Spot? = null)

/** 저장·삭제 결과. [Failed.message] 는 토스트 문구. */
sealed interface SaveOutcome {
    data object NeedsLogin : SaveOutcome

    data object Saved : SaveOutcome

    data object Removed : SaveOutcome

    data class Failed(val message: String) : SaveOutcome
}

/** 계정 저장 목록 동기화 상태 (S4 상태 표). */
enum class SavedSync { IDLE, LOADING, LOADED, FAILED }

/**
 * 계정 저장(찜) 목록 — S3·S4·S6·S7 이 함께 쓴다. 앱에 하나(싱글턴).
 * - 목록은 서버(`/v1/me/saved`)가 기준, 기기에는 계정별 캐시([SavedCache])만 둔다.
 * - 저장·삭제는 낙관적 갱신, 실패하면 되돌린다.
 * - 로그아웃 상태에서 저장하면 [loginRequest] 를 세워 S7 시트를 띄운다(아이콘은 채우지 않음).
 * - 로그인 후([onSignedIn]): 기기 로컬 저장 옮기기(`saved:import` 1회) → 목록 받기 → 대기 중 저장 → 토스트 차례로.
 * [deviceSaved] 는 v1.3 이전 기기 로컬 저장(옮기기 원본).
 */
@Suppress("TooManyFunctions", "LongParameterList")
class SavedLibrary(
    private val auth: AuthService,
    private val api: AccountApi,
    private val cache: SavedCache,
    private val deviceSaved: SavedSpotsRepository,
    private val social: SocialLogin,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    private val _items = MutableStateFlow<List<SavedSpot>>(emptyList())

    /** 저장 시각 최신순. */
    val items: StateFlow<List<SavedSpot>> = _items.asStateFlow()

    private val _sync = MutableStateFlow(SavedSync.IDLE)
    val sync: StateFlow<SavedSync> = _sync.asStateFlow()

    val session: StateFlow<AuthSession?> get() = auth.session

    val signedIn: Boolean get() = auth.session.value != null

    /** 로그인 전 S4 안내용: 이 기기에 남은 로컬 저장 수. */
    val deviceSavedCount: Flow<Int> = deviceSaved.saved.map { it.size }.distinctUntilChanged()

    private val _loginRequest = MutableStateFlow<LoginRequest?>(null)

    /** null 이 아니면 :app 이 S7 시트를 띄운다. */
    val loginRequest: StateFlow<LoginRequest?> = _loginRequest.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)

    /** 화면을 가리지 않는 앱 전체 토스트(로그인 후 차례 토스트, 세션 만료, 로그아웃). :app 이 하나만 모은다. */
    val messages: Flow<String> = _messages.receiveAsFlow()

    private val importLock = Mutex()

    /** 앱 시작 때 한 번. 캐시로 먼저 그리고, 남은 기기 저장을 조용히 옮긴 뒤 다시 받는다. */
    fun start() {
        val current = auth.session.value ?: return
        scope.launch {
            _items.value = cache.load(current.userId)
            if (_items.value.isNotEmpty()) _sync.value = SavedSync.LOADED
            runCatchingAuth { importDeviceSaved() }
            refresh()
        }
    }

    fun requestLogin(request: LoginRequest) {
        _loginRequest.value = request
    }

    fun dismissLogin() {
        _loginRequest.value = null
    }

    fun isSaved(id: String): Flow<Boolean> = _items.map { list -> list.any { it.spot.id == id } }.distinctUntilChanged()

    fun get(id: String): SavedSpot? = _items.value.firstOrNull { it.spot.id == id }

    /** 저장 버튼. 로그아웃이면 시트를 띄우고 [SaveOutcome.NeedsLogin]. */
    suspend fun toggle(spot: Spot): SaveOutcome {
        if (!signedIn) {
            requestLogin(LoginRequest(LoginReason.SAVE, spot))
            return SaveOutcome.NeedsLogin
        }
        return if (get(spot.id) != null) {
            if (remove(listOf(spot.id))) SaveOutcome.Removed else SaveOutcome.Failed(MSG_SAVE_FAILED)
        } else {
            save(spot)
        }
    }

    suspend fun save(spot: Spot): SaveOutcome {
        if (!signedIn) {
            requestLogin(LoginRequest(LoginReason.SAVE, spot))
            return SaveOutcome.NeedsLogin
        }
        if (get(spot.id) != null) return SaveOutcome.Saved
        val item = SavedSpot(spot.copy(distanceM = null), clock.instant())
        _items.update { list -> listOf(item) + list.filterNot { it.spot.id == spot.id } }
        return try {
            auth.authorized { api.save(it, spot.id) }
            persist()
            SaveOutcome.Saved
        } catch (e: CancellationException) {
            throw e
        } catch (e: AuthException) {
            _items.update { list -> list.filterNot { it.spot.id == spot.id } }
            SaveOutcome.Failed(failureMessage(e))
        }
    }

    /** 낙관적 삭제. 하나라도 실패하면 실패한 항목을 되돌리고 false. */
    suspend fun remove(ids: Collection<String>): Boolean {
        if (ids.isEmpty() || !signedIn) return ids.isEmpty()
        val removed = _items.value.filter { it.spot.id in ids }
        _items.update { list -> list.filterNot { it.spot.id in ids } }
        val failed = scopedAll(removed) { item -> auth.authorized { api.unsave(it, item.spot.id) } }
        if (failed.isNotEmpty()) _items.update { list -> (list + failed).sortedByDescending { it.savedAt } }
        persist()
        return failed.isEmpty()
    }

    /** 되돌리기: 다시 `PUT` 하고 저장 시각을 그대로 둔다(서버 시각은 다시 받을 때 맞춰진다). */
    suspend fun restore(items: List<SavedSpot>): Boolean {
        if (items.isEmpty() || !signedIn) return items.isEmpty()
        val ids = items.map { it.spot.id }.toSet()
        _items.update { list -> (list.filterNot { it.spot.id in ids } + items).sortedByDescending { it.savedAt } }
        val failed = scopedAll(items) { item -> auth.authorized { api.save(it, item.spot.id) } }
        if (failed.isNotEmpty()) {
            val bad = failed.map { it.spot.id }.toSet()
            _items.update { list -> list.filterNot { it.spot.id in bad } }
        }
        persist()
        return failed.isEmpty()
    }

    /** 서버 목록을 끝까지 받는다. 실패하면 false(캐시는 그대로). */
    suspend fun refresh(): Boolean {
        val current = auth.session.value ?: return false
        if (_items.value.isEmpty() && _sync.value != SavedSync.LOADED) _sync.value = SavedSync.LOADING
        return try {
            val all = mutableListOf<SavedSpot>()
            var cursor: String? = null
            do {
                val page = auth.authorized { api.listSaved(it, cursor) }
                all += page.items
                cursor = page.nextCursor
            } while (cursor != null && all.size < MAX_PAGES_ITEMS)
            if (auth.session.value?.userId != current.userId) return false
            _items.value = all.sortedByDescending { it.savedAt }
            _sync.value = SavedSync.LOADED
            cache.save(current.userId, _items.value)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: AuthException.SessionExpired) {
            onSessionExpired()
            false
        } catch (_: AuthException) {
            if (_items.value.isEmpty() && _sync.value != SavedSync.LOADED) _sync.value = SavedSync.FAILED
            false
        }
    }

    /**
     * 로그인 성공 직후(세션 저장 뒤). 시트는 이미 닫혔고, 나머지는 앱 범위에서 한다(화면이 사라져도 끝까지).
     * 순서: 기기 저장 옮기기 → 목록 받기 → 대기 중 저장 → 토스트 차례로.
     */
    fun onSignedIn(request: LoginRequest?) {
        _loginRequest.value = null
        scope.launch { completeSignIn(request) }
    }

    internal suspend fun completeSignIn(request: LoginRequest?) {
        _items.value = emptyList()
        _sync.value = SavedSync.LOADING
        val imported = runCatchingAuth { importDeviceSaved() } ?: 0
        refresh()
        val pending = request?.pendingSave
        val first = if (request?.reason == LoginReason.SAVE && pending != null) {
            when (val outcome = save(pending)) {
                is SaveOutcome.Failed -> outcome.message
                else -> MSG_SAVED
            }
        } else {
            MSG_SIGNED_IN
        }
        _messages.send(first)
        if (imported > 0) _messages.send(importedMessage(imported))
    }

    /** S6 로그아웃: 세션·캐시 삭제, 카카오 SDK 로그아웃. 서버 호출이 실패해도 기기에서는 로그아웃. */
    suspend fun signOut() {
        val provider = auth.session.value?.provider
        if (provider == AuthProvider.KAKAO) quietly { social.kakaoLogout() }
        auth.signOut()
        clearLocalAccountData()
        _messages.send(MSG_SIGNED_OUT)
    }

    /**
     * 회원 탈퇴. 카카오는 `unlink` 를 먼저(실패해도 진행), 그다음 `DELETE /v1/me`.
     * 성공하면 세션·캐시·기기 로컬 저장을 모두 지운다. 실패하면 [AuthException] 을 그대로 던진다(화면이 문구를 고른다).
     * TODO(herezi-api#72): Apple 사용자는 서버가 Apple 토큰을 철회해야 한다. 앱은 아직 따로 하는 일이 없다.
     */
    suspend fun deleteAccount() {
        val provider = auth.session.value?.provider ?: throw AuthException.SessionExpired()
        if (provider == AuthProvider.KAKAO) quietly { social.kakaoUnlink() }
        try {
            auth.authorized { api.deleteMe(it) }
        } catch (e: AuthException.SessionExpired) {
            onSessionExpired(announce = false)
            throw e
        }
        auth.clear()
        clearLocalAccountData()
        deviceSaved.remove(deviceSaved.saved.first().map { it.spot.id })
    }

    /** 기기 로컬 저장을 200개씩 옮기고, 성공한 묶음은 로컬에서 지운다. 옮긴(새로 저장된) 개수. */
    private suspend fun importDeviceSaved(): Int = importLock.withLock {
        val local = deviceSaved.saved.first()
        if (local.isEmpty() || !signedIn) return@withLock 0
        var imported = 0
        local.map { it.spot.id }.distinct().chunked(AccountApi.IMPORT_MAX).forEach { chunk ->
            val result = auth.authorized { api.importSaved(it, chunk) }
            imported += result.imported
            deviceSaved.remove(chunk)
        }
        imported
    }

    private suspend fun onSessionExpired(announce: Boolean = true) {
        auth.clear()
        clearLocalAccountData()
        if (announce) _messages.send(MSG_SESSION_EXPIRED)
    }

    private suspend fun clearLocalAccountData() {
        cache.clear()
        _items.value = emptyList()
        _sync.value = SavedSync.IDLE
    }

    private suspend fun persist() {
        val userId = auth.session.value?.userId ?: return
        cache.save(userId, _items.value)
    }

    private suspend fun failureMessage(e: AuthException): String = when (e) {
        is AuthException.SavedLimit -> MSG_LIMIT
        is AuthException.SessionExpired -> {
            onSessionExpired(announce = false)
            MSG_SESSION_EXPIRED
        }
        else -> MSG_SAVE_FAILED
    }

    /** 동시 [MAX_CONCURRENT]개로 돌리고 실패한 항목을 돌려준다. */
    private suspend fun scopedAll(items: List<SavedSpot>, call: suspend (SavedSpot) -> Unit): List<SavedSpot> {
        val gate = kotlinx.coroutines.sync.Semaphore(MAX_CONCURRENT)
        return kotlinx.coroutines.coroutineScope {
            items.map { item ->
                async {
                    gate.acquire()
                    try {
                        call(item)
                        null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: AuthException.SessionExpired) {
                        onSessionExpired()
                        item
                    } catch (_: AuthException) {
                        item
                    } finally {
                        gate.release()
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    private suspend fun <T> runCatchingAuth(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: AuthException.SessionExpired) {
        onSessionExpired()
        null
    } catch (_: AuthException) {
        null
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun quietly(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 제공자 SDK 실패는 무시한다(서버·기기 처리가 우선).
        }
    }

    companion object {
        const val MAX_CONCURRENT = 4
        private const val MAX_PAGES_ITEMS = 1000

        const val MSG_SAVED = "저장했어요"
        const val MSG_UNSAVED = "저장을 취소했어요"
        const val MSG_SIGNED_IN = "로그인했어요"
        const val MSG_SIGNED_OUT = "로그아웃했어요"
        const val MSG_SESSION_EXPIRED = "다시 로그인해 주세요"
        const val MSG_SAVE_FAILED = "저장하지 못했어요. 다시 눌러 주세요."
        const val MSG_LIMIT = "저장은 500개까지 할 수 있어요. 안 보는 볼거리를 지워 주세요."
        const val MSG_REMOVE_FAILED = "지우지 못했어요. 잠시 후 다시 시도해 주세요."
        const val MSG_OFFLINE = "인터넷 연결을 확인하고 다시 시도해 주세요."

        fun importedMessage(count: Int) = "기기에 저장했던 ${count}개를 계정으로 옮겼어요"
    }
}
