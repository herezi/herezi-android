package com.herezi.core.domain

/**
 * 볼거리 데이터 포트. 화면(feature)은 이 인터페이스만 안다.
 * 구현: [com.herezi.core.domain.fake.FakeSpotsRepository] (서버 없이), [LocalSpotsRepository] (지역 파일 + 기기 계산).
 * 좌표는 서버로 보내지 않는다(herezi-api ADR 0004).
 */
interface SpotsRepository {
    /** 조건에 맞는 근처 항목을 거리순으로 돌려준다. */
    suspend fun nearby(query: SpotQuery): SpotPage

    /** 지도 영역(bbox) 안 항목을 영역 중심에서 가까운 순으로 돌려준다. 클러스터링은 앱에서 한다. */
    suspend fun inBounds(query: MapQuery): MapSpots

    /** 상세. 없으면 null. 끝난 항목도 돌려주고 [Spot.isEnded] 로 표시한다. */
    suspend fun spot(id: String): Spot?

    /**
     * 기기 데이터(지역 파일)를 서버에서 다시 확인한다. [force] 면 1시간 규칙을 무시한다.
     * 바뀌었으면 true. 서버 없이 도는 구현은 아무것도 하지 않는다.
     */
    suspend fun refreshData(force: Boolean): Boolean = false
}
