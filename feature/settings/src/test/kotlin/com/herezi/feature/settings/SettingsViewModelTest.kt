package com.herezi.feature.settings

import com.herezi.core.domain.LocationAccess
import com.herezi.core.domain.fake.FakeLocationSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsViewModelTest {
    @Test
    fun `위치 권한 상태를 문구로 보여주고 돌아오면 갱신한다`() {
        val location = FakeLocationSource()
        val vm = SettingsViewModel(location)
        assertEquals("허용 안 됨", vm.state.value.accessLabel)

        location.accessValue = LocationAccess.APPROXIMATE
        vm.refresh()
        assertEquals("대략적 위치만", vm.state.value.accessLabel)

        location.accessValue = LocationAccess.PRECISE
        vm.refresh()
        assertEquals("허용됨", vm.state.value.accessLabel)
    }

    @Test
    fun `출처 목록과 제보 본문`() {
        assertEquals(6, DataSources.size)
        assertTrue(DataSources.all { it.url.startsWith("https://") })
        assertTrue(reportBody("1.0.0 (12)", "15").contains("앱 버전: 1.0.0 (12)\nOS: Android 15"))
    }
}
