package com.herezi.app.di

import com.herezi.app.BuildConfig
import com.herezi.core.domain.SpotsRepository
import com.herezi.core.domain.fake.FakeSpotsRepository
import com.herezi.core.network.HereziApiFactory
import com.herezi.core.network.LiveSpotsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 데이터 저장소 선택. HEREZI_API_BASE_URL 이 비어 있으면 서버 없이 Fake 로 동작한다.
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
}
