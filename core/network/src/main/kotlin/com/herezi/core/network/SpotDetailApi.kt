package com.herezi.core.network

import com.herezi.core.domain.Spot
import retrofit2.HttpException

/** 상세 `GET /v1/spots/{id}` (좌표 없음). 지역 파일에 없는 항목(저장 목록의 오래된 항목 등)만 여기로 온다. 404 → null. */
class SpotDetailApi(private val api: HereziApi) {
    suspend fun spot(id: String): Spot? = try {
        api.getSpot(id).toDomain()
    } catch (e: HttpException) {
        if (e.code() == HTTP_NOT_FOUND) null else throw e
    }

    private companion object {
        const val HTTP_NOT_FOUND = 404
    }
}
