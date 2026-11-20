pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Naver Map SDK is only published here. Restricted to its group so nothing else resolves from it.
        maven("https://repository.map.naver.com/archive/maven") {
            content { includeGroup("com.naver.maps") }
        }
    }
}

rootProject.name = "herezi-android"

include(":app")
include(":core:data")
include(":core:domain")
include(":core:map")
include(":core:network")
include(":core:ui")
include(":feature:home")
include(":feature:nearby")
include(":feature:onboarding")
include(":feature:saved")
include(":feature:search")
include(":feature:settings")
include(":feature:spotdetail")
