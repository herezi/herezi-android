import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

val localProps =
    Properties().apply {
        rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }

// local.properties → 환경 변수 → 빈 값 순서로 읽는다. 키가 없어도 빌드는 항상 된다.
fun config(name: String): String = localProps.getProperty(name) ?: System.getenv(name).orEmpty()

// 네이버 클라우드 플랫폼 Maps Client ID. 없으면 지도 대신 안내 문구를 보여준다. 절대 커밋하지 않는다.
val naverMapClientId: String = config("NAVER_MAP_CLIENT_ID")

// 비어 있으면 서버 없이 FakeSpotsRepository 로 동작한다. 예: http://10.0.2.2:8080/
val apiBaseUrl: String = config("HEREZI_API_BASE_URL")

// 실서버 API (Railway, herezi-api docs/ops/railway.md). 도메인 구매 후 api.herezi.com 으로 교체.
val prodApiBaseUrl = "https://api-production-4ebcf3e2.up.railway.app"

android {
    namespace = "com.herezi.app"
    buildFeatures {
        compose = true
        buildConfig = true
    }
    defaultConfig {
        applicationId = "com.herezi.app"
        versionCode = 1
        versionName = "0.1.0"
        manifestPlaceholders["naverMapClientId"] = naverMapClientId
        buildConfigField("boolean", "MAP_ENABLED", naverMapClientId.isNotBlank().toString())
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }
    buildTypes {
        release {
            // 릴리스는 값이 없으면 실서버(Railway)로. 디버그는 비어 있으면 Fake 데이터.
            buildConfigField("String", "API_BASE_URL", "\"${apiBaseUrl.ifBlank { prodApiBaseUrl }}\"")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:domain"))
    implementation(project(":core:network"))
    implementation(project(":core:ui"))
    implementation(project(":core:map"))
    implementation(project(":feature:home"))
    implementation(project(":feature:nearby"))
    implementation(project(":feature:spotdetail"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:saved"))
    implementation(project(":feature:search"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
}
