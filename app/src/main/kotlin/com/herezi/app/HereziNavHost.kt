package com.herezi.app

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.herezi.feature.nearby.NearbyRoute
import com.herezi.feature.onboarding.OnboardingRoute
import com.herezi.feature.saved.SavedRoute
import com.herezi.feature.search.SearchRoute
import com.herezi.feature.settings.SettingsRoute
import com.herezi.feature.settings.SourcesScreen
import com.herezi.feature.spotdetail.SpotDetailRoute
import com.herezi.feature.spotdetail.SpotDetailViewModel
import java.net.URLEncoder

private const val ONBOARDING = "onboarding"
private const val NEARBY = "nearby"
private const val SPOT = "spot"
private const val SEARCH = "search"
private const val SAVED = "saved"
private const val SETTINGS = "settings"
private const val SOURCES = "settings/sources"

/** 화면 연결은 여기서만 한다. feature 끼리는 서로 모른다. 탭 바(S1 재설계)는 별도 이슈. */
@Composable
fun HereziNavHost() {
    val nav = rememberNavController()
    val openSpot = { id: String -> nav.navigate("$SPOT/${URLEncoder.encode(id, "UTF-8")}") }
    val toNearby = { nav.navigate(NEARBY) { popUpTo(NEARBY) { inclusive = false } } }
    NavHost(navController = nav, startDestination = ONBOARDING) {
        composable(ONBOARDING) {
            OnboardingRoute(onDone = { nav.navigate(NEARBY) { popUpTo(ONBOARDING) { inclusive = true } } })
        }
        composable(NEARBY) {
            NearbyRoute(
                onSpotClick = openSpot,
                onSearch = { nav.navigate(SEARCH) },
                onSaved = { nav.navigate(SAVED) },
                onSettings = { nav.navigate(SETTINGS) },
            )
        }
        composable(
            route = "$SPOT/{${SpotDetailViewModel.ARG_SPOT_ID}}",
            arguments = listOf(navArgument(SpotDetailViewModel.ARG_SPOT_ID) { type = NavType.StringType }),
        ) {
            SpotDetailRoute(onBack = { nav.popBackStack() }, onBrowseNearby = toNearby)
        }
        composable(SEARCH) { SearchRoute(onBack = { nav.popBackStack() }, onSpotClick = openSpot) }
        composable(SAVED) {
            SavedRoute(
                onSpotClick = openSpot,
                onBrowseNearby = toNearby,
                onSettings = { nav.navigate(SETTINGS) },
                onBack = { nav.popBackStack() },
            )
        }
        composable(SETTINGS) {
            SettingsRoute(
                appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                onBack = { nav.popBackStack() },
                onOpenSources = { nav.navigate(SOURCES) },
            )
        }
        composable(SOURCES) { SourcesScreen(onBack = { nav.popBackStack() }) }
    }
}
