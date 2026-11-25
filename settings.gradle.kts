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
        // Kakao SDK (로그인) 도 자체 저장소에만 있다. 같은 이유로 그룹을 제한한다.
        maven("https://devrepo.kakao.com/nexus/content/groups/public/") {
            content { includeGroup("com.kakao.sdk") }
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
include(":feature:auth")
include(":feature:home")
include(":feature:nearby")
include(":feature:onboarding")
include(":feature:saved")
include(":feature:search")
include(":feature:settings")
include(":feature:spotdetail")
