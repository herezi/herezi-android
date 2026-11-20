plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.herezi.core.network"
    testOptions {
        unitTests.all {
            // 계약 드리프트 테스트가 복사해 둔 스펙(api/openapi.yaml)을 읽는다.
            it.systemProperty("herezi.openapi", rootProject.file("api/openapi.yaml").absolutePath)
        }
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
