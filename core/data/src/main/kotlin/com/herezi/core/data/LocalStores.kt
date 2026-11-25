package com.herezi.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.herezi.core.domain.AppPreferences
import com.herezi.core.domain.RecentSearchesRepository
import com.herezi.core.domain.SavedSpot
import com.herezi.core.domain.SavedSpotsRepository
import com.herezi.core.domain.Spot
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.hereziStore: DataStore<Preferences> by preferencesDataStore(name = "herezi")

/** 앱 하나에 DataStore 하나. :app 이 [create] 로 만든다. */
class LocalStores private constructor(store: DataStore<Preferences>) {
    val saved: SavedSpotsRepository = DataStoreSavedSpots(store)
    val recentSearches: RecentSearchesRepository = DataStoreRecentSearches(store)
    val preferences: AppPreferences = DataStoreAppPreferences(store)
    val savedCache: com.herezi.core.domain.account.SavedCache = DataStoreSavedCache(store)

    companion object {
        fun create(context: Context): LocalStores = LocalStores(context.applicationContext.hereziStore)
    }
}

private val SavedKey = stringPreferencesKey("saved_spots")
private val RecentKey = stringPreferencesKey("recent_searches")
private val OnboardingKey = booleanPreferencesKey("onboarding_done")

private class DataStoreSavedSpots(private val store: DataStore<Preferences>) : SavedSpotsRepository {
    private val all: Flow<List<SavedSpot>> = store.data.map { SavedCodec.decode(it[SavedKey]) }

    override val saved: Flow<List<SavedSpot>> = all.map { list -> list.sortedByDescending { it.savedAt } }

    override fun isSaved(id: String): Flow<Boolean> =
        all.map { list -> list.any { it.spot.id == id } }.distinctUntilChanged()

    override suspend fun get(id: String): SavedSpot? = all.first().firstOrNull { it.spot.id == id }

    override suspend fun save(spot: Spot, savedAt: Instant) = change { list ->
        val old = list.firstOrNull { it.spot.id == spot.id }
        list.filterNot { it.spot.id == spot.id } + SavedSpot(spot.copy(distanceM = null), old?.savedAt ?: savedAt)
    }

    override suspend fun restore(item: SavedSpot) = change { list ->
        list.filterNot { it.spot.id == item.spot.id } +
            item
    }

    override suspend fun updateSnapshot(spot: Spot) = change { list ->
        list.map { if (it.spot.id == spot.id) it.copy(spot = spot.copy(distanceM = null)) else it }
    }

    override suspend fun remove(ids: Collection<String>) = change { list -> list.filterNot { it.spot.id in ids } }

    private suspend fun change(block: (List<SavedSpot>) -> List<SavedSpot>) {
        store.edit { prefs -> prefs[SavedKey] = SavedCodec.encode(block(SavedCodec.decode(prefs[SavedKey]))) }
    }
}

private class DataStoreRecentSearches(private val store: DataStore<Preferences>) : RecentSearchesRepository {
    override val recent: Flow<List<String>> = store.data.map { StringListCodec.decode(it[RecentKey]) }

    override suspend fun add(query: String) = change { RecentSearchesRepository.push(it, query) }

    override suspend fun remove(query: String) = change { it - query }

    override suspend fun clear() = change { emptyList() }

    private suspend fun change(block: (List<String>) -> List<String>) {
        store.edit { prefs ->
            prefs[RecentKey] = StringListCodec.encode(block(StringListCodec.decode(prefs[RecentKey])))
        }
    }
}

private class DataStoreAppPreferences(private val store: DataStore<Preferences>) : AppPreferences {
    override suspend fun onboardingDone(): Boolean = store.data.first()[OnboardingKey] ?: false

    override suspend fun setOnboardingDone() {
        store.edit { it[OnboardingKey] = true }
    }
}
