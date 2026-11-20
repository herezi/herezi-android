package com.herezi.core.domain

import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** 저장(찜)한 볼거리. 가입 없이 기기에만 두는 스냅샷과 저장 시각. */
data class SavedSpot(val spot: Spot, val savedAt: Instant)

/** 저장 목록 포트 (S3 저장 토글, S4 저장 목록). 구현: :core:data, 테스트는 fake. */
interface SavedSpotsRepository {
    /** 저장 시각 최신순. */
    val saved: Flow<List<SavedSpot>>

    fun isSaved(id: String): Flow<Boolean>

    suspend fun get(id: String): SavedSpot?

    /** 저장. 이미 있으면 스냅샷만 바꾸고 저장 시각은 둔다. */
    suspend fun save(spot: Spot, savedAt: Instant)

    /** 되돌리기용: 저장 시각을 그대로 되살린다. */
    suspend fun restore(item: SavedSpot)

    /** 저장돼 있을 때만 스냅샷을 새로 고친다(재조회 결과). */
    suspend fun updateSnapshot(spot: Spot)

    suspend fun remove(ids: Collection<String>)
}

/** 최근 검색어 포트 (S5). 최신순, 최대 [MAX]개, 같은 말은 맨 위로. */
interface RecentSearchesRepository {
    val recent: Flow<List<String>>

    suspend fun add(query: String)

    suspend fun remove(query: String)

    suspend fun clear()

    companion object {
        const val MAX = 10

        /** 저장 규칙(중복 제거·최신순·개수 제한). 구현들이 같이 쓴다. */
        fun push(list: List<String>, query: String): List<String> =
            (listOf(query) + list.filterNot { it == query }).take(MAX)
    }
}

/** 앱 설정 값 (S0 노출 여부). */
interface AppPreferences {
    suspend fun onboardingDone(): Boolean

    suspend fun setOnboardingDone()
}
