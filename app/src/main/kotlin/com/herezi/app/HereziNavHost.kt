package com.herezi.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.herezi.core.domain.ListFilter
import com.herezi.core.ui.theme.HereziColors
import com.herezi.core.ui.theme.HereziTheme
import com.herezi.feature.home.HomeRoute
import com.herezi.feature.nearby.NearbyRoute
import com.herezi.feature.nearby.NearbyViewModel
import com.herezi.feature.onboarding.OnboardingRoute
import com.herezi.feature.saved.SavedRoute
import com.herezi.feature.search.SearchRoute
import com.herezi.feature.settings.LicensesScreen
import com.herezi.feature.settings.SettingsRoute
import com.herezi.feature.settings.SourcesScreen
import com.herezi.feature.spotdetail.SpotDetailRoute
import com.herezi.feature.spotdetail.SpotDetailViewModel
import java.net.URLEncoder

/** 탭 (DESIGN.md §6.12): 주변(S1 홈 → S1b) · 지도(S2) · 저장(S4, 우상단 톱니 → S6). */
private enum class Tab(val graph: String, val label: String, val icon: ImageVector) {
    NEARBY("tab/nearby", "주변", Icons.Rounded.Explore),
    MAP("tab/map", "지도", Icons.Rounded.Map),
    SAVED("tab/saved", "저장", Icons.Rounded.Bookmark),
}

private const val HOME = "home"
private const val SEARCH = "search"
private const val SAVED = "saved"
private const val SETTINGS = "settings"
private const val SOURCES = "settings/sources"
private const val LICENSES = "settings/licenses"
private val FilterArgs = "${NearbyViewModel.ARG_WHEN}={${NearbyViewModel.ARG_WHEN}}" +
    "&${NearbyViewModel.ARG_CATEGORY}={${NearbyViewModel.ARG_CATEGORY}}" +
    "&${NearbyViewModel.ARG_FREE}={${NearbyViewModel.ARG_FREE}}"
private val LIST = "list?$FilterArgs"
private val MAP = "map?$FilterArgs&${NearbyViewModel.ARG_MAP}={${NearbyViewModel.ARG_MAP}}"

private fun ListFilter.query(): String = "${NearbyViewModel.ARG_WHEN}=${period.code}" +
    "&${NearbyViewModel.ARG_CATEGORY}=${categories.joinToString(",") { it.code }}" +
    "&${NearbyViewModel.ARG_FREE}=$freeOnly"

/** 앱 뼈대. S0(필요할 때만) 다음에 탭 화면. */
@Composable
fun HereziApp() {
    var onboarded by rememberSaveable { mutableStateOf(false) }
    if (!onboarded) {
        OnboardingRoute(onDone = { onboarded = true })
        return
    }
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination
    Scaffold(
        containerColor = HereziTheme.colors.bgPrimary,
        bottomBar = {
            NavigationBar(containerColor = HereziTheme.colors.bgPrimary) {
                Tab.entries.forEach { tab ->
                    val selected = current?.hierarchy?.any { it.route == tab.graph } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (selected) nav.popBackStack(tab.graph, inclusive = false) else nav.switchTab(tab)
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label, style = HereziTheme.typography.micro) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = HereziTheme.colors.iconPrimary,
                            selectedTextColor = HereziTheme.colors.textPrimary,
                            unselectedIconColor = HereziColors.Gray500,
                            unselectedTextColor = HereziColors.Gray500,
                            indicatorColor = HereziColors.Gray100,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        HereziNavHost(nav, Modifier.padding(padding))
    }
}

private fun NavHostController.switchTab(tab: Tab, restore: Boolean = true) = navigate(tab.graph) {
    popUpTo(Tab.NEARBY.graph) { saveState = true }
    launchSingleTop = true
    restoreState = restore
}

/** 화면 연결은 여기서만 한다. feature 끼리는 서로 모른다. */
@Composable
private fun HereziNavHost(nav: NavHostController, modifier: Modifier) {
    val context = LocalContext.current
    NavHost(navController = nav, startDestination = Tab.NEARBY.graph, modifier = modifier) {
        navigation(route = Tab.NEARBY.graph, startDestination = HOME) {
            composable(HOME) {
                HomeRoute(
                    onSpotClick = { nav.openSpot(Tab.NEARBY, it) },
                    onSearch = { nav.navigate(SEARCH) },
                    onOpenList = { nav.navigate("list?${it.query()}") },
                    onOpenMap = { nav.switchTab(Tab.MAP) },
                    onLocationSettings = { openAppSettings(context) },
                )
            }
            composable(LIST, arguments = filterArguments()) {
                NearbyRoute(
                    onSpotClick = { nav.openSpot(Tab.NEARBY, it) },
                    onSearch = { nav.navigate(SEARCH) },
                    onSwitchView = { filter ->
                        // 지도 탭을 이 필터로 새로 연다.
                        nav.navigate("map?${filter.query()}&${NearbyViewModel.ARG_MAP}=true") {
                            popUpTo(Tab.NEARBY.graph) { saveState = true }
                            launchSingleTop = true
                        }
                    },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(SEARCH) {
                SearchRoute(onBack = { nav.popBackStack() }, onSpotClick = { nav.openSpot(Tab.NEARBY, it) })
            }
            spotDetail(nav, Tab.NEARBY)
        }
        navigation(route = Tab.MAP.graph, startDestination = MAP) {
            composable(MAP, arguments = filterArguments(map = true)) {
                NearbyRoute(
                    onSpotClick = { nav.openSpot(Tab.MAP, it) },
                    onSearch = { nav.navigate(SEARCH) },
                    onSwitchView = { filter ->
                        nav.switchTab(Tab.NEARBY)
                        nav.navigate("list?${filter.query()}")
                    },
                    onBack = null,
                )
            }
            spotDetail(nav, Tab.MAP)
        }
        navigation(route = Tab.SAVED.graph, startDestination = SAVED) {
            composable(SAVED) {
                SavedRoute(
                    onSpotClick = { nav.openSpot(Tab.SAVED, it) },
                    onBrowseNearby = { nav.switchTab(Tab.NEARBY) },
                    onSettings = { nav.navigate(SETTINGS) },
                )
            }
            composable(SETTINGS) {
                SettingsRoute(
                    appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    onBack = { nav.popBackStack() },
                    onOpenSources = { nav.navigate(SOURCES) },
                    onOpenLicenses = { nav.navigate(LICENSES) },
                )
            }
            composable(SOURCES) { SourcesScreen(onBack = { nav.popBackStack() }) }
            composable(LICENSES) { LicensesScreen(onBack = { nav.popBackStack() }) }
            spotDetail(nav, Tab.SAVED)
        }
    }
}

private fun filterArguments(map: Boolean = false) = listOf(
    navArgument(NearbyViewModel.ARG_WHEN) {
        type = NavType.StringType
        defaultValue = "now"
    },
    navArgument(NearbyViewModel.ARG_CATEGORY) {
        type = NavType.StringType
        defaultValue = ""
    },
    navArgument(NearbyViewModel.ARG_FREE) {
        type = NavType.BoolType
        defaultValue = false
    },
    navArgument(NearbyViewModel.ARG_MAP) {
        type = NavType.BoolType
        defaultValue = map
    },
)

/** 상세는 탭마다 같은 탭 안에 쌓는다(탭을 바꿔도 각 탭 백스택 유지). */
private fun NavGraphBuilder.spotDetail(nav: NavHostController, tab: Tab) {
    composable(
        route = "${tab.name.lowercase()}/spot/{${SpotDetailViewModel.ARG_SPOT_ID}}",
        arguments = listOf(navArgument(SpotDetailViewModel.ARG_SPOT_ID) { type = NavType.StringType }),
    ) {
        SpotDetailRoute(onBack = { nav.popBackStack() }, onBrowseNearby = { nav.switchTab(Tab.NEARBY) })
    }
}

private fun NavHostController.openSpot(tab: Tab, id: String) =
    navigate("${tab.name.lowercase()}/spot/${URLEncoder.encode(id, "UTF-8")}")

private fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
    )
}
