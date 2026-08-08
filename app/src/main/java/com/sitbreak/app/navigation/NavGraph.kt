package com.sitbreak.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sitbreak.app.R
import com.sitbreak.app.ui.help.HelpScreen
import com.sitbreak.app.ui.help.HelpDetailScreen
import com.sitbreak.app.ui.activity.HealthCenterScreen
import com.sitbreak.app.ui.activity.ActivityDetailScreen
import com.sitbreak.app.ui.home.HomeScreen
import com.sitbreak.app.ui.splash.SplashScreen
import com.sitbreak.app.ui.stats.StatsScreen
import com.sitbreak.app.ui.settings.SettingsScreen
import com.sitbreak.app.ui.theme.BlueLight
import com.sitbreak.app.ui.theme.BluePrimary
import com.sitbreak.app.ui.theme.CardBackground
import com.sitbreak.app.ui.theme.TextTertiary

object Routes {
    const val SPLASH = "splash"
    const val HOME = "home"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val HELP = "help"
    const val HELP_DETAIL = "help_detail/{helpId}"
    fun helpDetail(id: String) = "help_detail/$id"
    const val ACTIVITY = "activity"
    const val ACTIVITY_DETAIL = "activity_detail/{activityId}"
    fun activityDetail(id: String) = "activity_detail/$id"
}

sealed class BottomNavItem(
    val route: String,
    val titleResId: Int,
    val icon: ImageVector
) {
    data object Home : BottomNavItem(
        route = Routes.HOME,
        titleResId = R.string.nav_home,
        icon = Icons.Outlined.Home,
    )

    data object Stats : BottomNavItem(
        route = Routes.STATS,
        titleResId = R.string.nav_stats,
        icon = Icons.Outlined.BarChart,
    )

    data object Activity : BottomNavItem(
        route = Routes.ACTIVITY,
        titleResId = R.string.nav_activity,
        icon = Icons.Outlined.FitnessCenter,
    )

    data object Settings : BottomNavItem(
        route = Routes.SETTINGS,
        titleResId = R.string.nav_settings,
        icon = Icons.Outlined.Settings,
    )

    @Suppress("DEPRECATION")
    data object Help : BottomNavItem(
        route = Routes.HELP,
        titleResId = R.string.nav_help,
        icon = Icons.Outlined.HelpOutline,
    )
}

val bottomNavItems = listOf(
    BottomNavItem.Home,
    BottomNavItem.Stats,
    BottomNavItem.Activity,
    BottomNavItem.Settings,
    BottomNavItem.Help,
)

@Composable
fun MainNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val isSplash = currentDestination?.route == Routes.SPLASH

    Scaffold(
        bottomBar = {
            if (!isSplash) {
                NavigationBar(
                    containerColor = CardBackground,
                    tonalElevation = 0.dp,
                    modifier = Modifier.shadow(
                        elevation = 8.dp,
                        ambientColor = Color.Black.copy(alpha = 0.05f),
                        spotColor = Color.Black.copy(alpha = 0.05f),
                    ),
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == item.route
                        } == true

                        val tint = if (selected) BluePrimary else TextTertiary

                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                            navController.navigate(item.route) {
                                popUpTo(Routes.HOME) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                            },
                            icon = {
                                if (selected) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(BlueLight, CircleShape),
                                        contentAlignment = androidx.compose.ui.Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = item.icon,
                                            contentDescription = stringResource(item.titleResId),
                                            tint = BluePrimary,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = stringResource(item.titleResId),
                                        tint = tint,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            },
                            label = {
                                Text(
                                    stringResource(item.titleResId),
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.W600 else FontWeight.W400,
                                    color = tint,
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Color.Transparent,
                            ),
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.SPLASH,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.SPLASH) {
                SplashScreen(
                    onFinish = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.SPLASH) {
                                inclusive = true
                            }
                        }
                    }
                )
            }
            composable(Routes.HOME) {
                HomeScreen()
            }
            composable(Routes.STATS) {
                StatsScreen()
            }
            composable(Routes.SETTINGS) {
                SettingsScreen()
            }
            composable(Routes.HELP) {
                HelpScreen(
                    onNavigateToDetail = { helpId ->
                        navController.navigate(Routes.helpDetail(helpId))
                    }
                )
            }
            composable(Routes.HELP_DETAIL) { backStackEntry ->
                val helpId = backStackEntry.arguments?.getString("helpId") ?: "usage"
                HelpDetailScreen(
                    helpId = helpId,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.ACTIVITY) {
                HealthCenterScreen(
                    onNavigateToDetail = { activityId ->
                        navController.navigate(Routes.activityDetail(activityId))
                    }
                )
            }
            composable(Routes.ACTIVITY_DETAIL) { backStackEntry ->
                val activityId = backStackEntry.arguments?.getString("activityId") ?: "neck"
                ActivityDetailScreen(
                    activityId = activityId,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}