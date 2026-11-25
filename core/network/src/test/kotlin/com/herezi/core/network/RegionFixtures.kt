package com.herezi.core.network

import okio.Buffer
import okio.GzipSink
import okio.buffer

/** 지역 파일 응답 본문. [items] 는 (id, lat, lng, startsAt, endsAt?) 묶음. */
internal fun regionJson(vararg items: RegionItem, generatedAt: String = "2026-11-25T00:00:00.123456789Z"): String =
    items.joinToString(
        separator = ",",
        prefix = """{"region":"seoul","generated_at":"$generatedAt","count":${items.size},"items":[""",
        postfix = "]}",
    ) { it.json() }

internal data class RegionItem(
    val id: String,
    val lat: Double = 37.5663,
    val lng: Double = 126.9779,
    val startsAt: String = "2026-11-01T00:00:00Z",
    val endsAt: String? = "2026-12-31T14:59:59Z",
    val title: String = id,
    val category: String = "festival",
) {
    fun json(): String {
        val end = endsAt?.let { ""","ends_at":"$it"""" }.orEmpty()
        return """{"id":"$id","title":"$title","category":"$category","lat":$lat,"lng":$lng,""" +
            """"starts_at":"$startsAt"$end,"source":"tourapi","fetched_at":"2026-11-24T00:00:00.5Z","is_ended":false}"""
    }
}

internal fun gzip(text: String): Buffer {
    val out = Buffer()
    GzipSink(out).buffer().use { it.writeUtf8(text) }
    return out
}
