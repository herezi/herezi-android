package com.herezi.core.ui

import androidx.annotation.RawRes

/** 앱에 번들한 서드파티 에셋의 라이선스 (S6 '오픈소스 라이선스'). 라이선스 원문은 `res/raw` 에 둔다. */
data class OpenSourceLicense(
    val name: String,
    val version: String,
    val license: String,
    val url: String,
    @RawRes val textRes: Int,
)

val BundledLicenses: List<OpenSourceLicense> = listOf(
    OpenSourceLicense(
        name = "Pretendard",
        version = "1.3.9",
        license = "SIL Open Font License 1.1",
        url = "https://github.com/orioncactus/pretendard",
        textRes = R.raw.license_pretendard,
    ),
)
