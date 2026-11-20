package com.herezi.core.domain.fake

import com.herezi.core.domain.AppPreferences
import com.herezi.core.domain.GeoPoint
import com.herezi.core.domain.LocationAccess
import com.herezi.core.domain.LocationSource
import com.herezi.core.domain.RecentSearchesRepository
import com.herezi.core.domain.SavedSpot
import com.herezi.core.domain.SavedSpotsRepository
import com.herezi.core.domain.Spot
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** 메모리 저장 목록. 테스트와 미리보기용. */
class InMemorySavedSpotsRepository(initial: List<SavedSpot> = emptyList()) : SavedSpotsRepository {
    private val items = MutableStateFlow(initial)

    override val saved: Flow<List<SavedSpot>> = items.map { list -> list.sortedByDescending { it.savedAt } }

    override fun isSaved(id: String): Flow<Boolean> = items.map { list -> list.any { it.spot.id == id } }

    override suspend fun get(id: String): SavedSpot? = items.value.firstOrNull { it.spot.id == id }

    override suspend fun save(spot: Spot, savedAt: Instant) = items.update { list ->
        val old = list.firstOrNull { it.spot.id == spot.id }
        list.filterNot { it.spot.id == spot.id } + SavedSpot(spot, old?.savedAt ?: savedAt)
    }

    override suspend fun restore(item: SavedSpot) = items.update { list ->
        list.filterNot { it.spot.id == item.spot.id } + item
    }

    override suspend fun updateSnapshot(spot: Spot) = items.update { list ->
        list.map { if (it.spot.id == spot.id) it.copy(spot = spot) else it }
    }

    override suspend fun remove(ids: Collection<String>) = items.update { list -> list.filterNot { it.spot.id in ids } }
}

class InMemoryRecentSearchesRepository(initial: List<String> = emptyList()) : RecentSearchesRepository {
    private val items = MutableStateFlow(initial)
    override val recent: Flow<List<String>> = items

    override suspend fun add(query: String) = items.update { RecentSearchesRepository.push(it, query) }

    override suspend fun remove(query: String) = items.update { it - query }

    override suspend fun clear() = items.update { emptyList() }
}

class InMemoryAppPreferences(private var done: Boolean = false) : AppPreferences {
    override suspend fun onboardingDone(): Boolean = done

    override suspend fun setOnboardingDone() {
        done = true
    }
}

/** 고정 위치. [location] 이 null 이면 권한 없음과 같다. */
class FakeLocationSource(
    var location: GeoPoint? = null,
    var accessValue: LocationAccess = if (location == null) LocationAccess.DENIED else LocationAccess.PRECISE,
) : LocationSource {
    override fun access(): LocationAccess = accessValue

    override suspend fun current(): GeoPoint? = location
}
