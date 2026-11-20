package com.herezi.feature.onboarding

import com.herezi.core.domain.GeoPoint
import com.herezi.core.domain.fake.FakeLocationSource
import com.herezi.core.domain.fake.InMemoryAppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `첫 실행이고 권한이 없으면 보여주고 끝내면 다시 보여주지 않는다`() = runTest(dispatcher) {
        val prefs = InMemoryAppPreferences()
        val vm = OnboardingViewModel(prefs, FakeLocationSource())
        assertEquals(OnboardingState.Checking, vm.state.value)
        advanceUntilIdle()
        assertEquals(OnboardingState.Show, vm.state.value)

        vm.finish()
        advanceUntilIdle()
        assertEquals(OnboardingState.Done, vm.state.value)
        assertTrue(prefs.onboardingDone())

        val again = OnboardingViewModel(prefs, FakeLocationSource())
        advanceUntilIdle()
        assertEquals(OnboardingState.Done, again.state.value)
    }

    @Test
    fun `이미 권한이 있으면 건너뛴다`() = runTest(dispatcher) {
        val vm = OnboardingViewModel(InMemoryAppPreferences(), FakeLocationSource(GeoPoint(37.5, 127.0)))
        advanceUntilIdle()
        assertEquals(OnboardingState.Done, vm.state.value)
    }
}
