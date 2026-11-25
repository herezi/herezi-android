package com.herezi.core.network

import com.herezi.core.domain.RegionData
import com.herezi.core.domain.Spot
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 지역 데이터 파일 `GET /v1/regions/{region}/spots` (ADR 0004). 메모리 → 디스크([dir]) → 네트워크.
 * - 원본 JSON 과 ETag 를 `{dir}/{region}.json`·`{region}.meta.json` 에 둔다. 다음부터 `If-None-Match` 로 묻고 304 면 그대로 쓴다.
 * - gzip 은 OkHttp 가 알아서 받고 푼다(`Accept-Encoding: gzip`).
 * - 확인은 실행 후 처음 한 번 + 그 뒤로는 마지막 확인이 [maxAge](서버 `max-age=3600`) 넘었을 때만. `force` 면 언제나.
 * - 동시 요청은 하나로 합친다(Mutex). 디스크 파일이 깨졌으면 지우고 다시 받는다. 받지 못하면 마지막 데이터로 동작한다.
 */
@Suppress("LongParameterList")
class RegionSpotsRepository(
    baseUrl: String,
    private val dir: File,
    private val region: String = SEOUL,
    private val client: OkHttpClient = OkHttpClient(),
    private val clock: Clock = Clock.systemUTC(),
    private val maxAge: Duration = Duration.ofHours(1),
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : RegionData {
    private val url = "${baseUrl.trimEnd('/')}/v1/regions/$region/spots"
    private val dataFile get() = File(dir, "$region.json")
    private val metaFile get() = File(dir, "$region.meta.json")
    private val lock = Mutex()

    @Volatile private var memory: List<Spot>? = null

    /** 이번 실행에서 마지막으로 서버에 물어본 시각. null 이면 아직 안 물어봤다(실행 후 첫 확인). */
    @Volatile private var checkedAt: Instant? = null

    override suspend fun spots(): List<Spot> {
        memory?.let { return it }
        return lock.withLock {
            memory ?: loadDisk() ?: run {
                fetch()
                memory ?: throw IOException("지역 데이터 없음")
            }
        }
    }

    override suspend fun refresh(force: Boolean): Boolean = lock.withLock {
        val last = checkedAt
        if (!force && last != null && Duration.between(last, clock.instant()) < maxAge) return@withLock false
        if (memory == null) loadDisk()
        fetch()
    }

    /** 디스크 → 메모리. 깨졌으면 지운다. */
    private suspend fun loadDisk(): List<Spot>? = withContext(io) {
        if (!dataFile.exists()) return@withContext null
        val parsed = runCatching { parse(dataFile.readText()) }.getOrNull()
        if (parsed == null) {
            dataFile.delete()
            metaFile.delete()
        }
        parsed?.also { memory = it }
    }

    /** 서버에 묻는다. 새 데이터를 받았으면 true, 304 면 false. 실패하면 던진다. */
    private suspend fun fetch(): Boolean = withContext(io) {
        val etag = if (dataFile.exists() && memory != null) readMeta()?.etag else null
        val request = Request.Builder().url(url).apply { etag?.let { header("If-None-Match", it) } }.build()
        client.newCall(request).execute().use { response ->
            when {
                response.code == HTTP_NOT_MODIFIED -> {
                    checkedAt = clock.instant()
                    false
                }
                response.isSuccessful -> {
                    val body = response.body?.string() ?: throw IOException("빈 응답")
                    val spots = parse(body)
                    dir.mkdirs()
                    writeAtomically(dataFile, body)
                    writeAtomically(metaFile, metaJson(Meta(response.header("ETag"))))
                    memory = spots
                    checkedAt = clock.instant()
                    true
                }
                else -> throw IOException("region ${response.code}")
            }
        }
    }

    private fun parse(raw: String): List<Spot> =
        HereziApiFactory.json.decodeFromString(RegionSpotsDto.serializer(), raw).items.mapNotNull { it.toDomain() }

    private fun readMeta(): Meta? = runCatching {
        HereziApiFactory.json.decodeFromString(Meta.serializer(), metaFile.readText())
    }.getOrNull()

    private fun metaJson(meta: Meta) = HereziApiFactory.json.encodeToString(Meta.serializer(), meta)

    private fun writeAtomically(target: File, text: String) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    @Serializable
    private data class Meta(val etag: String? = null)

    companion object {
        const val SEOUL = "seoul"
        private const val HTTP_NOT_MODIFIED = 304
    }
}
