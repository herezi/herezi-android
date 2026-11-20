package com.herezi.app.di

import android.content.Context
import com.herezi.app.BuildConfig
import com.herezi.app.location.AndroidLocationSource
import com.herezi.app.location.AndroidReverseGeocoder
import com.herezi.core.data.LocalStores
import com.herezi.core.domain.AppPreferences
import com.herezi.core.domain.LocationSource
import com.herezi.core.domain.RecentSearchesRepository
import com.herezi.core.domain.ReverseGeocoder
import com.herezi.core.domain.SavedSpotsRepository
import com.herezi.core.domain.SpotsRepository
import com.herezi.core.domain.fake.FakeSpotsRepository
import com.herezi.core.network.HereziApiFactory
import com.herezi.core.network.LiveSpotsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/**
 * 데이터 저장소 선택. HEREZI_API_BASE_URL 이 비어 있으면 서버 없이 Fake 로 동작한다.
 * 저장 목록·최근 검색·앱 설정은 기기 로컬(:core:data), 위치는 플랫폼 LocationManager.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun spotsRepository(): SpotsRepository = if (BuildConfig.API_BASE_URL.isBlank()) {
        FakeSpotsRepository()
    } else {
        LiveSpotsRepository(HereziApiFactory.create(BuildConfig.API_BASE_URL))
    }

    @Provides
    @Singleton
    fun localStores(@ApplicationContext context: Context): LocalStores = LocalStores.create(context)

    @Provides
    fun savedSpots(stores: LocalStores): SavedSpotsRepository = stores.saved

    @Provides
    fun recentSearches(stores: LocalStores): RecentSearchesRepository = stores.recentSearches

    @Provides
    fun appPreferences(stores: LocalStores): AppPreferences = stores.preferences

    @Provides
    @Singleton
    fun locationSource(@ApplicationContext context: Context): LocationSource = AndroidLocationSource(context)

    @Provides
    @Singleton
    fun reverseGeocoder(@ApplicationContext context: Context): ReverseGeocoder = AndroidReverseGeocoder(context)

    @Provides
    fun clock(): Clock = Clock.systemUTC()
}
