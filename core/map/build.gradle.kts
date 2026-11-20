// 네이버 지도 SDK를 쓸 수 있는 유일한 모듈 (`checkModuleRules`가 강제).
// docs/adr/0001-module-structure.md 참고.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.herezi.core.map"
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.naver.map.sdk)
    implementation(libs.naver.map.compose)

    testImplementation(libs.junit)
}
