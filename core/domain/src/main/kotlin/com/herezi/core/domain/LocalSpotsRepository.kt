package com.herezi.core.domain

import java.time.Clock

/**
 * 지역 데이터 파일 포트(`GET /v1/regions/seoul/spots`). 구현: :core:network `RegionSpotsRepository`.
 * 메모리 → 디스크 → 네트워크 순으로 읽고, 오프라인이면 마지막 데이터를 쓴다.
 */
interface RegionData {
    /** 지금 쓸 데이터. 기기에 아무것도 없고 받지도 못하면 던진다. */
    suspend fun spots(): List<Spot>

    /**
     * 서버에 바뀌었는지 묻는다(ETag/304). [force] 가 false 면 마지막 확인이 1시간 안이면 묻지 않는다.
     * 바뀌어서 새 데이터를 받았으면 true. 실패하면 던진다(기존 데이터는 그대로).
     */
    suspend fun refresh(force: Boolean): Boolean
}

/**
 * 좌표를 서버로 보내지 않는 [SpotsRepository]. 근처·지도·검색은 [RegionData] 위에서 [SpotQueryEngine] 으로 계산한다.
 * 상세는 지역 데이터에 있으면 그것(끝남은 기기 시각으로 다시 계산), 없으면 [remoteDetail](`GET /v1/spots/{id}`, 좌표 없음).
 */
class LocalSpotsRepository(
    private val region: RegionData,
    private val remoteDetail: suspend (String) -> Spot?,
    private val clock: Clock,
) : SpotsRepository {
    override suspend fun nearby(query: SpotQuery): SpotPage =
        SpotQueryEngine.nearby(region.spots(), query, clock.instant())

    override suspend fun inBounds(query: MapQuery): MapSpots =
        SpotQueryEngine.inBounds(region.spots(), query, clock.instant())

    override suspend fun spot(id: String): Spot? {
        val local = runCatchingData { region.spots() }?.firstOrNull { it.id == id }
        return local?.copy(isEnded = SpotQueryEngine.isEnded(local, clock.instant()), distanceM = null)
            ?: remoteDetail(id)
    }

    override suspend fun refreshData(force: Boolean): Boolean = region.refresh(force)

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun <T> runCatchingData(block: suspend () -> T): T? = try {
        block()
    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
