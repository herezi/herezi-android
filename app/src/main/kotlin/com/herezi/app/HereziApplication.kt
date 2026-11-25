package com.herezi.app

import android.app.Application
import com.herezi.app.auth.ActivityTracker
import com.herezi.core.domain.account.SavedLibrary
import com.kakao.sdk.common.KakaoSdk
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class HereziApplication : Application() {
    @Inject lateinit var activities: ActivityTracker

    @Inject lateinit var library: SavedLibrary

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(activities)
        // 키가 없는 빌드(CI)는 카카오 SDK 를 초기화하지 않는다 → 카카오 로그인은 "설정 없음"으로 실패.
        if (BuildConfig.KAKAO_NATIVE_APP_KEY.isNotBlank()) KakaoSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
        // 로그인 상태면 캐시로 먼저 그리고, 남은 기기 저장을 조용히 옮긴 뒤 목록을 다시 받는다.
        library.start()
    }
}
