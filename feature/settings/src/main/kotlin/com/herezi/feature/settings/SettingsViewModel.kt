package com.herezi.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.herezi.core.domain.LocationAccess
import com.herezi.core.domain.LocationSource
import com.herezi.core.domain.account.AuthProvider
import com.herezi.core.domain.account.LoginReason
import com.herezi.core.domain.account.LoginRequest
import com.herezi.core.domain.account.SavedLibrary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val access: LocationAccess = LocationAccess.DENIED,
    /** null 이면 로그인 전. */
    val provider: AuthProvider? = null,
) {
    val accessLabel: String get() = access.label
}

val LocationAccess.label: String
    get() = when (this) {
        LocationAccess.PRECISE -> "허용됨"
        LocationAccess.APPROXIMATE -> "대략적 위치만"
        LocationAccess.DENIED -> "허용 안 됨"
    }

val AuthProvider.signedInLabel: String
    get() = when (this) {
        AuthProvider.KAKAO -> "카카오로 로그인됨"
        AuthProvider.APPLE -> "Apple로 로그인됨"
    }

/** 설정 (S6). 위치 권한 값은 앱이 포그라운드로 돌아올 때 [refresh] 로 갱신한다. 계정 그룹은 세션을 따른다. */
@HiltViewModel
class SettingsViewModel
@Inject
constructor(private val location: LocationSource, private val library: SavedLibrary) :
    ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState(location.access(), library.session.value?.provider))
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            library.session.collect { s -> _state.update { it.copy(provider = s?.provider) } }
        }
    }

    fun refresh() {
        _state.update { it.copy(access = location.access()) }
    }

    fun signIn() = library.requestLogin(LoginRequest(LoginReason.ACCOUNT))

    /** 확인 대화상자 뒤. 토스트 "로그아웃했어요" 는 [SavedLibrary.messages] 로 앱이 띄운다. */
    fun signOut() {
        viewModelScope.launch { library.signOut() }
    }
}

/** 정보 출처 목록 (S6). */
data class DataSource(val name: String, val url: String)

val DataSources = listOf(
    DataSource("한국관광공사 TourAPI", "https://api.visitkorea.or.kr"),
    DataSource("KOPIS 공연예술통합전산망", "https://www.kopis.or.kr"),
    DataSource("서울 열린데이터광장", "https://data.seoul.go.kr"),
    DataSource("문화공공데이터광장", "https://www.culture.go.kr/data"),
    DataSource("산림청", "https://www.forest.go.kr"),
    DataSource("기상청", "https://www.kma.go.kr"),
)

/** 정보 오류 제보 메일 본문 (S6): 앱 버전·OS. */
fun reportBody(appVersion: String, osVersion: String): String = "\n\n---\n앱 버전: $appVersion\nOS: Android $osVersion"

const val REPORT_SUBJECT = "[정보 오류]"
