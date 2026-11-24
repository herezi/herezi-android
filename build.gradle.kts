import com.android.build.api.dsl.CommonExtension
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless)
}

val ktlintVersion: String = libs.versions.ktlint.get()

// ---------------------------------------------------------------------------
// 포맷(ktlint via Spotless) + 정적 분석(detekt)을 모든 모듈에 적용한다.
// 포맷 자동 수정: ./gradlew spotlessApply  (또는 `make fmt`)
// ---------------------------------------------------------------------------
spotless {
    kotlinGradle {
        target("*.gradle.kts")
        ktlint(ktlintVersion)
    }
}

subprojects {
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "io.gitlab.arturbosch.detekt")

    extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        kotlin {
            target("src/**/*.kt")
            ktlint(ktlintVersion)
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint(ktlintVersion)
        }
    }

    // 공통 Android 설정은 여기 한 곳에만 둔다. 모듈 빌드 파일에는 다른 점만 적는다.
    val configureAndroid: CommonExtension<*, *, *, *, *, *>.() -> Unit = {
        compileSdk = 36
        defaultConfig {
            minSdk = 26
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        testOptions {
            // JVM 단위 테스트에서 android.* 스텁 호출이 예외 대신 기본값을 돌려준다.
            unitTests.isReturnDefaultValues = true
        }
        lint {
            abortOnError = true
            warningsAsErrors = false
            checkDependencies = false
            disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
        }
    }
    plugins.withId("com.android.application") {
        extensions.configure<com.android.build.api.dsl.ApplicationExtension> {
            configureAndroid()
            defaultConfig.targetSdk = 36
        }
    }
    plugins.withId("com.android.library") {
        extensions.configure<com.android.build.api.dsl.LibraryExtension> { configureAndroid() }
    }
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        // 순수 JVM 모듈도 `./gradlew testDebugUnitTest` 한 번으로 테스트되도록 별칭을 둔다.
        tasks.register("testDebugUnitTest") {
            group = "verification"
            description = "JVM 모듈의 test 별칭 (make check 일관성용)"
            dependsOn("test")
        }
    }
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }

    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        allRules = false
        parallel = true
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        source.setFrom(files("src/main/kotlin", "src/main/java", "src/test/kotlin", "src/test/java"))
    }
}

// ---------------------------------------------------------------------------
// 모듈 의존 규칙(레이어링). AGENTS.md "모듈 규칙" 참고.
// 모듈별로 허용된 *프로젝트* 의존만 적는다. 여기 없는 의존은 위반이다.
// 새 모듈을 추가하면 settings.gradle.kts 와 이 맵(또는 아래 접두사 기본값),
// 그리고 AGENTS.md 모듈 지도를 함께 고친다.
// ---------------------------------------------------------------------------
val allowedProjectDeps: Map<String, Set<String>> =
    mapOf(
        ":core:data" to setOf(":core:domain"),
        ":core:domain" to emptySet(),
        ":core:map" to setOf(":core:domain"),
        ":core:network" to setOf(":core:domain"),
        ":core:ui" to setOf(":core:domain"),
        ":feature:nearby" to setOf(":core:domain", ":core:ui", ":core:map"),
        ":feature:spotdetail" to setOf(":core:domain", ":core:ui"),
    )

fun allowedFor(path: String): Set<String>? = allowedProjectDeps[path] ?: when {
    path == ":app" -> null // 조립 루트: 자기 자신 외 모두 의존 가능
    path.startsWith(":feature:") -> setOf(":core:domain", ":core:ui")
    path.startsWith(":core:") -> setOf(":core:domain")
    else -> emptySet()
}

// 네이버 지도 SDK는 :core:map 안에만 둔다. docs/adr/0001-module-structure.md 참고.
val mapSdkGroups = listOf("com.naver.maps", "io.github.fornewid")
val mapSdkOwner = ":core:map"

val forbiddenDomainGroups = listOf("androidx.", "com.android.", "com.google.android.", "com.squareup.")

val checkModuleRules by tasks.registering {
    group = "verification"
    description = "Fails if a module violates the layering rules in AGENTS.md."
    doLast {
        val violations = mutableListOf<String>()
        subprojects.forEach { p ->
            val deps =
                p.configurations
                    .flatMap { it.dependencies.withType<ProjectDependency>() }
                    .map { it.path }
                    .filter { it != p.path }
                    .toSortedSet()

            if (":app" in deps) {
                violations += "${p.path} depends on :app. FIX: :app is the composition root; " +
                    "move shared code into a :core module."
            }
            val allowed = allowedFor(p.path)
            if (allowed != null) {
                (deps - allowed).forEach { bad ->
                    val hint =
                        when {
                            p.path.startsWith(":feature:") && bad.startsWith(":feature:") ->
                                "Features must not depend on each other. Navigate via callbacks wired in :app, " +
                                    "or move the shared type into :core:domain / shared UI into :core:ui."
                            p.path.startsWith(":feature:") && bad == ":core:network" ->
                                "Features talk to data only through interfaces in :core:domain " +
                                    "(e.g. SpotsRepository). " +
                                    "Inject the :core:network implementation from :app."
                            p.path == ":core:domain" ->
                                ":core:domain is the innermost layer and must have zero project dependencies."
                            else ->
                                "If this dependency is intentional, " +
                                    "update allowedProjectDeps in build.gradle.kts AND AGENTS.md."
                        }
                    val allowedText = allowed.ifEmpty { setOf("none") }
                    violations += "${p.path} -> $bad is not allowed (allowed: $allowedText). FIX: $hint"
                }
            }

            if (p.path != mapSdkOwner) {
                p.configurations
                    .flatMap { it.dependencies.withType<ExternalModuleDependency>() }
                    .filter { d -> mapSdkGroups.any { (d.group ?: "") == it } }
                    .map { "${it.group}:${it.name}" }
                    .toSortedSet()
                    .forEach { coord ->
                        violations += "${p.path} depends on $coord. " +
                            "FIX: the Naver Map SDK may only be used inside $mapSdkOwner. " +
                            "Expose what you need as a composable/API in $mapSdkOwner (e.g. SpotMap) " +
                            "and depend on that module instead."
                    }
            }

            if (p.path == ":core:domain") {
                if (p.plugins.hasPlugin("com.android.library") || p.plugins.hasPlugin("com.android.application")) {
                    violations += ":core:domain applies an Android plugin. " +
                        "FIX: it must stay a pure Kotlin/JVM module (plugin kotlin-jvm only)."
                }
                p.configurations
                    .filter { !it.name.startsWith("test", ignoreCase = true) && !it.name.contains("Test") }
                    .flatMap { it.dependencies.withType<ExternalModuleDependency>() }
                    .filter { d -> forbiddenDomainGroups.any { (d.group ?: "").startsWith(it.trimEnd('.')) } }
                    .forEach { d ->
                        violations += ":core:domain depends on ${d.group}:${d.name}. " +
                            "FIX: domain must be platform-free; " +
                            "put Android/network code in :core:network, :core:ui or :app behind a domain interface."
                    }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Module rule violations (see AGENTS.md#모듈-규칙):\n" +
                    violations.joinToString("\n") { " - $it" },
            )
        }
        logger.lifecycle("checkModuleRules: OK (${subprojects.size} modules)")
    }
}

tasks.named("check") { dependsOn(checkModuleRules) }
