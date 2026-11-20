package com.herezi.feature.spotdetail

import androidx.lifecycle.SavedStateHandle
import com.herezi.core.domain.fake.FakeSpotsRepository
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
class SpotDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(id: String) =
        SpotDetailViewModel(SavedStateHandle(mapOf(SpotDetailViewModel.ARG_SPOT_ID to id)), FakeSpotsRepository())

    @Test
    fun `id로 상세를 불러온다`() = runTest(dispatcher) {
        val vm = viewModel("fake:yeouido-cherry")
        assertEquals(SpotDetailUiState.Loading, vm.state.value)
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state is SpotDetailUiState.Loaded)
        assertEquals("여의도 봄꽃 축제", (state as SpotDetailUiState.Loaded).spot.title)
    }

    @Test
    fun `없는 id는 NotFound`() = runTest(dispatcher) {
        val vm = viewModel("nope")
        advanceUntilIdle()
        assertEquals(SpotDetailUiState.NotFound, vm.state.value)
    }
}
