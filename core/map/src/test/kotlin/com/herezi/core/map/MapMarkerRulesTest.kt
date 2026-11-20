package com.herezi.core.map

import org.junit.Assert.assertEquals
import org.junit.Test

class MapMarkerRulesTest {
    @Test
    fun `클러스터 지름은 개수 구간별 32 40 48`() {
        assertEquals(32, MapMarkerRules.clusterDiameterDp(2))
        assertEquals(32, MapMarkerRules.clusterDiameterDp(9))
        assertEquals(40, MapMarkerRules.clusterDiameterDp(10))
        assertEquals(40, MapMarkerRules.clusterDiameterDp(49))
        assertEquals(48, MapMarkerRules.clusterDiameterDp(50))
        assertEquals(48, MapMarkerRules.clusterDiameterDp(500))
    }

    @Test
    fun `클러스터 숫자는 99 넘으면 99+`() {
        assertEquals("12", MapMarkerRules.clusterLabel(12))
        assertEquals("99", MapMarkerRules.clusterLabel(99))
        assertEquals("99+", MapMarkerRules.clusterLabel(100))
    }

    @Test
    fun `선택 핀 라벨은 12자까지, 넘치면 말줄임`() {
        assertEquals("서울빛초롱축제", MapMarkerRules.pinLabel("서울빛초롱축제"))
        assertEquals("가나다라마바사아자차카타", MapMarkerRules.pinLabel(" 가나다라마바사아자차카타 "))
        assertEquals("가나다라마바사아자차카…", MapMarkerRules.pinLabel("가나다라마바사아자차카타파"))
        assertEquals(12, MapMarkerRules.pinLabel("아주 아주 긴 행사 이름입니다 정말로").length)
    }

    @Test
    fun `스크린리더 문구`() {
        assertEquals("공연, 세종 체임버 콘서트", MapMarkerRules.spokenPin("공연", "세종 체임버 콘서트"))
        assertEquals("볼거리 12개, 두 번 탭하면 확대", MapMarkerRules.spokenCluster(12))
    }
}
