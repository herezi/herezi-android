package com.herezi.core.ui

/** 외부 주소·연락처. 도메인·운영 메일은 미확정이라 한 곳에 모아 둔다. */
object HereziLinks {
    /** 운영 메일 (미확정, #9). */
    const val SUPPORT_EMAIL = "help@herezi.com"

    /** 공유 링크. 도메인 미구매라 텍스트 공유에만 쓴다. */
    fun share(spotId: String): String = "https://herezi.com/s/$spotId"

    const val TERMS = "https://herezi.com/terms"
    const val PRIVACY = "https://herezi.com/privacy"
}
