package com.herezi.app

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.herezi.feature.nearby.NearbyRoute
import com.herezi.feature.spotdetail.SpotDetailRoute
import com.herezi.feature.spotdetail.SpotDetailViewModel
import java.net.URLEncoder

private const val NEARBY = "nearby"
private const val SPOT = "spot"

/** 화면 연결은 여기서만 한다. feature 끼리는 서로 모른다. */
@Composable
fun HereziNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = NEARBY) {
        composable(NEARBY) {
            NearbyRoute(onSpotClick = { id -> nav.navigate("$SPOT/${URLEncoder.encode(id, "UTF-8")}") })
        }
        composable(
            route = "$SPOT/{${SpotDetailViewModel.ARG_SPOT_ID}}",
            arguments = listOf(navArgument(SpotDetailViewModel.ARG_SPOT_ID) { type = NavType.StringType }),
        ) {
            SpotDetailRoute(onBack = { nav.popBackStack() })
        }
    }
}
