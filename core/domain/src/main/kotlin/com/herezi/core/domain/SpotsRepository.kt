package com.herezi.core.domain

/**
 * 볼거리 데이터 포트. 화면(feature)은 이 인터페이스만 안다.
 * 구현: [com.herezi.core.domain.fake.FakeSpotsRepository] (서버 없이), :core:network 의 LiveSpotsRepository.
 */
interface SpotsRepository {
    /** 조건에 맞는 근처 항목을 거리순으로 돌려준다. */
    suspend fun nearby(query: SpotQuery): SpotPage

    /** 상세. 없으면 null. */
    suspend fun spot(id: String): Spot?
}
