package com.herezi.app

import androidx.lifecycle.ViewModel
import com.herezi.core.domain.account.LoginRequest
import com.herezi.core.domain.account.SavedLibrary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** 앱 뼈대가 보는 계정 상태: S7 시트 요청과 앱 전체 토스트. */
@HiltViewModel
class AppViewModel
@Inject
constructor(private val library: SavedLibrary) : ViewModel() {
    val loginRequest: StateFlow<LoginRequest?> = library.loginRequest
    val messages: Flow<String> = library.messages

    fun dismissLogin() = library.dismissLogin()
}
