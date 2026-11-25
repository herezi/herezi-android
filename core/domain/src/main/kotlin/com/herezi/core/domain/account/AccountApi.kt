package com.herezi.core.domain.account

import com.herezi.core.domain.SavedSpot

/** `GET /v1/me/saved` 한 페이지. */
data class SavedPage(val items: List<SavedSpot>, val nextCursor: String? = null)

/** `POST /v1/me/saved:import` 결과. */
data class ImportResult(val imported: Int, val skippedUnknown: Int)

/**
 * 계정 API 포트 (`/v1/me/…`). 모두 Bearer [accessToken] 이 필요하다.
 * 오류는 [AuthException] 으로 던진다(401 → Unauthorized, 503 → Unavailable, 409 → SavedLimit, 404 → NotFound).
 * 구현: :core:network `LiveAccountApi`, 테스트는 [com.herezi.core.domain.fake.FakeAccountApi].
 */
interface AccountApi {
    suspend fun listSaved(accessToken: String, cursor: String?): SavedPage

    suspend fun save(accessToken: String, spotId: String)

    suspend fun unsave(accessToken: String, spotId: String)

    suspend fun importSaved(accessToken: String, spotIds: List<String>): ImportResult

    suspend fun deleteMe(accessToken: String)

    companion object {
        /** `saved:import` 한 번에 보낼 수 있는 최대 개수. */
        const val IMPORT_MAX = 200
    }
}

/** 계정 저장 목록 캐시(마지막 응답, 계정별). 구현: :core:data. 로그아웃·탈퇴 때 지운다. */
interface SavedCache {
    suspend fun load(userId: String): List<SavedSpot>

    suspend fun save(userId: String, items: List<SavedSpot>)

    suspend fun clear()
}
