package com.herezi.core.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * herezi-api REST 계약 (api/openapi.yaml). 계약이 바뀌면 `make sync-api` 후 여기를 맞춘다.
 * 좌표를 받는 경로(`/v1/spots?lat…`, `/v1/spots/map`, `/v1/geo/reverse`)는 부르지 않는다(ADR 0004, `NoLocationSendTest`).
 * 지역 파일은 ETag/304 를 직접 다루려고 [RegionSpotsRepository] 가 OkHttp 로 받는다.
 */
interface HereziApi {
    /** 상세. 끝난 항목도 200 + is_ended=true. 없으면 404. */
    @GET("v1/spots/{id}")
    suspend fun getSpot(@Path("id") id: String): SpotDto

    // ---- 계정 (`/v1/me/…`, Bearer Supabase access token) ----

    @GET("v1/me/saved")
    suspend fun listSaved(
        @Header("Authorization") authorization: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): SpotPageDto

    @PUT("v1/me/saved/{spot_id}")
    suspend fun saveSpot(
        @Header("Authorization") authorization: String,
        @Path("spot_id") spotId: String,
    ): Response<Unit>

    @DELETE("v1/me/saved/{spot_id}")
    suspend fun unsaveSpot(
        @Header("Authorization") authorization: String,
        @Path("spot_id") spotId: String,
    ): Response<Unit>

    @POST("v1/me/saved:import")
    suspend fun importSaved(
        @Header("Authorization") authorization: String,
        @Body body: SavedImportRequestDto,
    ): SavedImportResultDto

    @DELETE("v1/me")
    suspend fun deleteMe(@Header("Authorization") authorization: String): Response<Unit>
}
