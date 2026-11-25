package com.herezi.core.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.herezi.core.domain.SavedSpot
import com.herezi.core.domain.account.AuthProvider
import com.herezi.core.domain.account.AuthSession
import com.herezi.core.domain.account.SavedCache
import com.herezi.core.domain.account.SessionStore
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable

/**
 * Supabase 세션 보관. EncryptedSharedPreferences(키는 Android Keystore 의 AES256-GCM 마스터 키).
 * 앱을 지우면 함께 지워진다(allowBackup=false). 파일이 깨졌으면 지우고 로그아웃 상태로 시작한다.
 */
class EncryptedSessionStore(context: Context) : SessionStore {
    private val app = context.applicationContext
    private val prefs: SharedPreferences? by lazy {
        open() ?: run {
            reset()
            open()
        }
    }

    private fun open(): SharedPreferences? = runCatching {
        EncryptedSharedPreferences.create(
            FILE,
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            app,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrNull()

    private fun reset() {
        app.deleteSharedPreferences(FILE)
    }

    override fun load(): AuthSession? = prefs?.getString(KEY, null)?.let { raw ->
        runCatching { StoreJson.decodeFromString(SessionRecord.serializer(), raw).toDomain() }.getOrNull()
    }

    override fun save(session: AuthSession?) {
        val editor = prefs?.edit() ?: return
        if (session == null) {
            editor.remove(KEY)
        } else {
            editor.putString(KEY, StoreJson.encodeToString(SessionRecord.serializer(), SessionRecord.of(session)))
        }
        editor.apply()
    }

    private companion object {
        const val FILE = "herezi_session"
        const val KEY = "session"
    }
}

@Serializable
internal data class SessionRecord(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val userId: String,
    val provider: String,
) {
    fun toDomain(): AuthSession? = AuthProvider.fromCode(provider)?.let {
        AuthSession(accessToken, refreshToken, Instant.ofEpochSecond(expiresAt), userId, it)
    }

    companion object {
        fun of(s: AuthSession) =
            SessionRecord(s.accessToken, s.refreshToken, s.expiresAt.epochSecond, s.userId, s.provider.code)
    }
}

private val AccountCacheKey = stringPreferencesKey("account_saved_cache")

@Serializable
internal data class AccountCacheRecord(val userId: String, val items: List<SavedSpotRecord>)

/** 계정 저장 목록 캐시(마지막 응답). 계정 하나만 둔다 — 다른 계정이면 비어 있다. */
internal class DataStoreSavedCache(private val store: DataStore<Preferences>) : SavedCache {
    override suspend fun load(userId: String): List<SavedSpot> {
        val raw = store.data.first()[AccountCacheKey] ?: return emptyList()
        val record = runCatching { StoreJson.decodeFromString(AccountCacheRecord.serializer(), raw) }.getOrNull()
        return if (record?.userId == userId) record.items.mapNotNull { it.toDomain() } else emptyList()
    }

    override suspend fun save(userId: String, items: List<SavedSpot>) {
        val record = AccountCacheRecord(userId, items.map { it.toRecord() })
        store.edit { it[AccountCacheKey] = StoreJson.encodeToString(AccountCacheRecord.serializer(), record) }
    }

    override suspend fun clear() {
        store.edit { it.remove(AccountCacheKey) }
    }
}
