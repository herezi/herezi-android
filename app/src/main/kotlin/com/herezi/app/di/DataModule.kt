package com.herezi.app.di

import android.content.Context
import com.herezi.app.BuildConfig
import com.herezi.app.auth.ActivityTracker
import com.herezi.app.auth.AndroidSocialLogin
import com.herezi.app.location.AndroidLocationSource
import com.herezi.app.location.AndroidReverseGeocoder
import com.herezi.core.data.EncryptedSessionStore
import com.herezi.core.data.LocalStores
import com.herezi.core.domain.AppPreferences
import com.herezi.core.domain.LocalSpotsRepository
import com.herezi.core.domain.LocationSource
import com.herezi.core.domain.RecentSearchesRepository
import com.herezi.core.domain.ReverseGeocoder
import com.herezi.core.domain.SavedSpotsRepository
import com.herezi.core.domain.SpotsRepository
import com.herezi.core.domain.account.AccountApi
import com.herezi.core.domain.account.AuthService
import com.herezi.core.domain.account.SavedLibrary
import com.herezi.core.domain.account.SocialLogin
import com.herezi.core.domain.fake.FakeAccountApi
import com.herezi.core.domain.fake.FakeSpotsRepository
import com.herezi.core.network.HereziApiFactory
import com.herezi.core.network.LiveAccountApi
import com.herezi.core.network.RegionSpotsRepository
import com.herezi.core.network.SpotDetailApi
import com.herezi.core.network.SupabaseAuthBackend
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 데이터 저장소 선택. HEREZI_API_BASE_URL 이 비어 있으면 서버 없이 Fake 로 동작한다.
 * 저장 목록·최근 검색·앱 설정은 기기 로컬(:core:data), 위치는 플랫폼 LocationManager.
 * 볼거리는 지역 파일 + 기기 계산, 위치명은 OS Geocoder 만 쓴다(좌표를 서버로 보내지 않는다, herezi-api ADR 0004).
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun spotsRepository(@ApplicationContext context: Context, clock: Clock): SpotsRepository =
        if (BuildConfig.API_BASE_URL.isBlank()) {
            FakeSpotsRepository()
        } else {
            // 좌표를 서버로 보내지 않는다(herezi-api ADR 0004): 지역 파일을 받아 두고 기기에서 계산한다.
            val detail = SpotDetailApi(HereziApiFactory.create(BuildConfig.API_BASE_URL))
            LocalSpotsRepository(
                RegionSpotsRepository(BuildConfig.API_BASE_URL, File(context.cacheDir, "herezi-region")),
                remoteDetail = detail::spot,
                clock = clock,
            )
        }

    @Provides
    @Singleton
    fun localStores(@ApplicationContext context: Context): LocalStores = LocalStores.create(context)

    /** 기기 로컬 저장(v1.3 이전). 첫 로그인 때 계정으로 옮길 원본. */
    @Provides
    fun deviceSavedSpots(stores: LocalStores): SavedSpotsRepository = stores.saved

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

/** 계정 (S4·S6·S7): Supabase 세션, `/v1/me/…`, 로그인 화면, 저장 목록. */
@Module
@InstallIn(SingletonComponent::class)
object AccountModule {
    /** 앱 전체 범위(화면이 사라져도 로그인 후 처리·저장 요청이 끝까지 간다). */
    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Provides
    @Singleton
    fun activityTracker(): ActivityTracker = ActivityTracker()

    @Provides
    @Singleton
    fun socialLogin(activities: ActivityTracker): SocialLogin =
        AndroidSocialLogin(activities, kakaoConfigured = BuildConfig.KAKAO_NATIVE_APP_KEY.isNotBlank())

    @Provides
    @Singleton
    fun authService(@ApplicationContext context: Context, clock: Clock): AuthService = AuthService(
        SupabaseAuthBackend(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY, clock = clock),
        EncryptedSessionStore(context),
        clock,
    )

    /** 서버 없이(Fake) 돌 때는 계정 API 도 메모리 Fake. */
    @Provides
    @Singleton
    fun accountApi(): AccountApi = if (BuildConfig.API_BASE_URL.isBlank()) {
        FakeAccountApi(FakeSpotsRepository.seedSpots(java.time.Instant.now()).associateBy { it.id })
    } else {
        LiveAccountApi(HereziApiFactory.create(BuildConfig.API_BASE_URL))
    }

    @Suppress("LongParameterList")
    @Provides
    @Singleton
    fun savedLibrary(
        auth: AuthService,
        api: AccountApi,
        stores: LocalStores,
        social: SocialLogin,
        clock: Clock,
        @ApplicationScope scope: CoroutineScope,
    ): SavedLibrary = SavedLibrary(auth, api, stores.savedCache, stores.saved, social, clock, scope)
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
