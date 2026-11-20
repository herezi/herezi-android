package com.herezi.core.network

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** herezi-api REST 계약 (api/openapi.yaml). 계약이 바뀌면 `make sync-api` 후 여기를 맞춘다. */
interface HereziApi {
    @Suppress("LongParameterList")
    @GET("v1/spots")
    suspend fun listSpots(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("radius_m") radiusM: Int? = null,
        @Query("category") category: String? = null,
        @Query("when") period: String? = null,
        @Query("free") free: Boolean? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): SpotPageDto

    @GET("v1/spots/{id}")
    suspend fun getSpot(@Path("id") id: String): SpotDto
}
