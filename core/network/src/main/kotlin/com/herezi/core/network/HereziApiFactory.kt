package com.herezi.core.network

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object HereziApiFactory {
    /** 서버가 필드를 추가해도 앱이 깨지지 않도록 모르는 키는 무시한다. */
    val json: Json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    fun create(baseUrl: String): HereziApi = create(baseUrl, OkHttpClient())

    /** 타임아웃·인터셉터를 바꿔야 할 때 쓴다. */
    internal fun create(baseUrl: String, client: OkHttpClient): HereziApi = Retrofit
        .Builder()
        .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(HereziApi::class.java)
}
