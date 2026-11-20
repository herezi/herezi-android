// 순수 Kotlin/JVM. Android·네트워크 의존 금지 (`checkModuleRules`가 강제).
plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
