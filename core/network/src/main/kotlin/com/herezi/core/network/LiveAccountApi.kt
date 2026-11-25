package com.herezi.core.network

import com.herezi.core.domain.SavedSpot
import com.herezi.core.domain.account.AccountApi
import com.herezi.core.domain.account.AuthException
import com.herezi.core.domain.account.ImportResult
import com.herezi.core.domain.account.SavedPage
import java.io.IOException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import kotlin.coroutines.cancellation.CancellationException
import retrofit2.HttpException
import retrofit2.Response

/** `/v1/me/…` 계약 구현. HTTP 상태를 [AuthException] 으로 바꾼다. */
class LiveAccountApi(private val api: HereziApi) : AccountApi {
    override suspend fun listSaved(accessToken: String, cursor: String?): SavedPage = call {
        val page = api.listSaved(bearer(accessToken), cursor = cursor, limit = PAGE_LIMIT)
        SavedPage(
            items = page.items.mapNotNull { dto ->
                val spot = dto.toDomain() ?: return@mapNotNull null
                val savedAt = dto.savedAt?.let(::parseInstant) ?: spot.fetchedAt
                SavedSpot(spot.copy(distanceM = null), savedAt)
            },
            nextCursor = page.nextCursor,
        )
    }

    override suspend fun save(accessToken: String, spotId: String) = call {
        api.saveSpot(bearer(accessToken), spotId).orThrow()
    }

    override suspend fun unsave(accessToken: String, spotId: String) = call {
        api.unsaveSpot(bearer(accessToken), spotId).orThrow()
    }

    override suspend fun importSaved(accessToken: String, spotIds: List<String>): ImportResult = call {
        val r = api.importSaved(bearer(accessToken), SavedImportRequestDto(spotIds))
        ImportResult(imported = r.imported, skippedUnknown = r.skippedUnknown)
    }

    override suspend fun deleteMe(accessToken: String) = call { api.deleteMe(bearer(accessToken)).orThrow() }

    private fun bearer(token: String) = "Bearer $token"

    private fun Response<Unit>.orThrow() {
        if (!isSuccessful) throw HttpException(this)
    }

    private suspend fun <T> call(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        throw e.toAuthException()
    } catch (e: IOException) {
        throw AuthException.Offline(e)
    }

    private fun parseInstant(value: String): Instant? = try {
        OffsetDateTime.parse(value).toInstant()
    } catch (_: DateTimeParseException) {
        null
    }

    private companion object {
        const val PAGE_LIMIT = 100
    }
}

/** 계약의 오류 응답 → 도메인 오류. */
internal fun HttpException.toAuthException(): AuthException {
    val code = runCatching {
        response()?.errorBody()?.string()?.let {
            HereziApiFactory.json.decodeFromString(ErrorDto.serializer(), it).code
        }
    }.getOrNull()
    return when (code()) {
        HTTP_UNAUTHORIZED -> AuthException.Unauthorized()
        HTTP_NOT_FOUND -> AuthException.NotFound()
        HTTP_CONFLICT -> if (code == null || code == "saved_limit_exceeded") {
            AuthException.SavedLimit()
        } else {
            AuthException.Failed("409 $code")
        }
        HTTP_UNAVAILABLE -> AuthException.Unavailable()
        else -> AuthException.Failed("http ${code()} ${code.orEmpty()}")
    }
}

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_NOT_FOUND = 404
private const val HTTP_CONFLICT = 409
private const val HTTP_UNAVAILABLE = 503
