package com.herezi.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.herezi.core.ui.component.HereziToastHost
import com.herezi.core.ui.theme.HereziColors
import com.herezi.core.ui.theme.HereziTheme
import com.herezi.feature.auth.LoginSheetRoute
import com.herezi.feature.home.HomeRoute
import com.herezi.feature.nearby.MapRoute
import com.herezi.feature.nearby.NearbyRoute
import com.herezi.feature.nearby.NearbyViewModel
import com.herezi.feature.onboarding.OnboardingRoute
import com.herezi.feature.saved.SavedRoute
import com.herezi.feature.search.SearchRoute
import com.herezi.feature.settings.DeleteAccountRoute
import com.herezi.feature.settings.LicensesScreen
import com.herezi.feature.settings.SettingsRoute
import com.herezi.feature.settings.SourcesScreen
import com.herezi.feature.spotdetail.SpotDetailRoute
import com.herezi.feature.spotdetail.SpotDetailViewModel
import java.net.URLEncoder

/** 탭 (DESIGN.md §6.12): 주변(S1 홈 → S1b) · 지도(S2) · 저장(S4) · MY(S6 설정이 탭 루트). */
private enum class Tab(val graph: String, val label: String, val icon: ImageVector) {
    NEARBY("tab/nearby", "주변", Icons.Rounded.Explore),
    MAP("tab/map", "지도", Icons.Rounded.Map),
    SAVED("tab/saved", "저장", Icons.Rounded.Bookmark),
    MY("tab/my", "MY", Icons.Rounded.Person),
}

private const val HOME = "home"
private const val SEARCH = "search"
private const val SAVED = "saved"
private const val SETTINGS = "settings"
private const val SOURCES = "settings/sources"
private const val LICENSES = "settings/licenses"
private const val DELETE_ACCOUNT = "settings/delete-account"
private val FilterArgs = "${NearbyViewModel.ARG_WHEN}={${NearbyViewModel.ARG_WHEN}}" +
    "&${NearbyViewModel.ARG_CATEGORY}={${NearbyViewModel.ARG_CATEGORY}}" +
    "&${NearbyViewModel.ARG_FREE}={${NearbyViewModel.ARG_FREE}}"
private val LIST = "list?$FilterArgs" +
    "&${NearbyViewModel.ARG_LAT}={${NearbyViewModel.ARG_LAT}}&${NearbyViewModel.ARG_LNG}={${NearbyViewModel.ARG_LNG}}"
private val MAP = "map?$FilterArgs"

private fun ListFilter.query(): String = "${NearbyViewModel.ARG_WHEN}=${period.code}" +
    "&${NearbyViewModel.ARG_CATEGORY}=${categories.joinToString(",") { it.code }}" +
    "&${NearbyViewModel.ARG_FREE}=$freeOnly"

/** 앱 뼈대. S0(필요할 때만) 다음에 탭 화면. */
@Composable
fun HereziApp(app: AppViewModel = hiltViewModel()) {
    var onboarded by rememberSaveable { mutableStateOf(false) }
    if (!onboarded) {
        OnboardingRoute(onDone = { onboarded = true })
        return
    }
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination
    val context = LocalContext.current
    val toast = remember { SnackbarHostState() }
    LaunchedEffect(app) { app.messages.collect { toast.showSnackbar(it) } }
    // 선택된 MY 탭을 다시 누를 때마다 올려서 S6 를 맨 위로 스크롤한다.
    var myReselect by remember { mutableIntStateOf(0) }
    val loginRequest by app.loginRequest.collectAsStateWithLifecycle()
    loginRequest?.let { request ->
        LoginSheetRoute(request = request, onDismiss = app::dismissLogin, onOpenUrl = { openInApp(context, it) })
    }
    Scaffold(
        containerColor = HereziTheme.colors.bgPrimary,
        snackbarHost = { HereziToastHost(toast) },
        bottomBar = {
            NavigationBar(containerColor = HereziTheme.colors.bgPrimary) {
                Tab.entries.forEach { tab ->
                    val selected = current?.hierarchy?.any { it.route == tab.graph } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (selected) {
                                nav.popToStart(tab)
                                if (tab == Tab.MY) myReselect++
                            } else {
                                nav.switchTab(tab)
                            }
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
        HereziNavHost(nav, myReselect, Modifier.padding(padding))
    }
}

private fun NavHostController.switchTab(tab: Tab, restore: Boolean = true) = navigate(tab.graph) {
    popUpTo(Tab.NEARBY.graph) { saveState = true }
    launchSingleTop = true
    restoreState = restore
}

/** 탭 첫 화면 route (navigation 그래프의 startDestination). */
private val Tab.start: String
    get() = when (this) {
        Tab.NEARBY -> HOME
        Tab.MAP -> MAP
        Tab.SAVED -> SAVED
        Tab.MY -> SETTINGS
    }

/**
 * 선택된 탭을 다시 눌렀을 때: 그 탭의 첫 화면만 남긴다(§6.12).
 * 그래프 route(tab.graph)로 pop 하면 첫 화면까지 빠져 다른 탭 화면이 보였다.
 */
private fun NavHostController.popToStart(tab: Tab) {
    if (!popBackStack(tab.start, inclusive = false)) switchTab(tab)
}

/** 화면 연결은 여기서만 한다. feature 끼리는 서로 모른다. */
@Composable
private fun HereziNavHost(nav: NavHostController, myReselect: Int, modifier: Modifier) {
    val context = LocalContext.current
    NavHost(navController = nav, startDestination = Tab.NEARBY.graph, modifier = modifier) {
        navigation(route = Tab.NEARBY.graph, startDestination = Tab.NEARBY.start) {
            composable(HOME) {
                HomeRoute(
                    onSpotClick = { nav.openSpot(Tab.NEARBY, it) },
                    onSearch = { nav.navigate(SEARCH) },
                    onOpenList = { nav.navigate("list?${it.query()}") },
                    onOpenMap = { nav.switchTab(Tab.MAP) },
                    onLocationSettings = { openAppSettings(context) },
                )
            }
            composable(LIST, arguments = filterArguments() + centerArguments()) {
                NearbyRoute(
                    onSpotClick = { nav.openSpot(Tab.NEARBY, it) },
                    onSearch = { nav.navigate(SEARCH) },
                    onSwitchView = { filter ->
                        // 지도 탭을 이 필터로 새로 연다.
                        nav.navigate("map?${filter.query()}") {
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
        navigation(route = Tab.MAP.graph, startDestination = Tab.MAP.start) {
            composable(MAP, arguments = filterArguments()) {
                MapRoute(
                    onSpotClick = { nav.openSpot(Tab.MAP, it) },
                    onSearch = { nav.navigate(SEARCH) },
                    onOpenList = { filter, center ->
                        // 주변 탭으로 바꾸고 S1b 를 지금 필터(와 옮겨 찾은 지도 중심)로 연다.
                        nav.switchTab(Tab.NEARBY)
                        val at = center?.let {
                            "&${NearbyViewModel.ARG_LAT}=${it.lat}&${NearbyViewModel.ARG_LNG}=${it.lng}"
                        }.orEmpty()
                        nav.navigate("list?${filter.query()}$at")
                    },
                )
            }
            spotDetail(nav, Tab.MAP)
        }
        navigation(route = Tab.SAVED.graph, startDestination = Tab.SAVED.start) {
            composable(SAVED) {
                SavedRoute(
                    onSpotClick = { nav.openSpot(Tab.SAVED, it) },
                    onBrowseNearby = { nav.switchTab(Tab.NEARBY) },
                )
            }
            spotDetail(nav, Tab.SAVED)
        }
        navigation(route = Tab.MY.graph, startDestination = Tab.MY.start) {
            composable(SETTINGS) {
                SettingsRoute(
                    appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    scrollToTop = myReselect,
                    onOpenSources = { nav.navigate(SOURCES) },
                    onOpenLicenses = { nav.navigate(LICENSES) },
                    onDeleteAccount = { nav.navigate(DELETE_ACCOUNT) },
                )
            }
            composable(DELETE_ACCOUNT) {
                DeleteAccountRoute(
                    onBack = { nav.popBackStack() },
                    onFinished = {
                        // 탭 바를 처음 상태로 되돌리고 주변 탭 홈(S1)으로.
                        nav.navigate(Tab.NEARBY.graph) {
                            popUpTo(nav.graph.id) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(SOURCES) { SourcesScreen(onBack = { nav.popBackStack() }) }
            composable(LICENSES) { LicensesScreen(onBack = { nav.popBackStack() }) }
        }
    }
}

private fun filterArguments() = listOf(
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
)

/** 지도에서 옮겨 찾은 중심(S2 → S1b). 비어 있으면 내 위치. */
private fun centerArguments() = listOf(NearbyViewModel.ARG_LAT, NearbyViewModel.ARG_LNG).map { name ->
    navArgument(name) {
        type = NavType.StringType
        defaultValue = ""
    }
}

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

/** 약관 등: Custom Tabs(인앱 브라우저). 없으면 일반 브라우저, 그것도 없으면 무시. */
private fun openInApp(context: Context, url: String) {
    try {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, Uri.parse(url))
    } catch (_: ActivityNotFoundException) {
        // 브라우저 없음.
    }
}

private fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
    )
}
