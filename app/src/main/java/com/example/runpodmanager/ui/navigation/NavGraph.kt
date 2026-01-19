package com.example.runpodmanager.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.runpodmanager.ui.screens.create.CreatePodScreen
import com.example.runpodmanager.ui.screens.pods.PodDetailScreen
import com.example.runpodmanager.ui.screens.pods.PodListScreen
import com.example.runpodmanager.ui.screens.settings.SettingsScreen
import com.example.runpodmanager.ui.screens.splash.SplashScreen
import com.example.runpodmanager.ui.screens.terminal.TerminalScreen

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Settings : Screen("settings")
    data object PodList : Screen("pods")
    data object PodDetail : Screen("pods/{podId}") {
        fun createRoute(podId: String) = "pods/$podId"
    }
    data object CreatePod : Screen("create")
    data object Terminal : Screen("terminal/{host}/{port}") {
        fun createRoute(host: String, port: Int) = "terminal/$host/$port"
    }
}

@Composable
fun NavGraph(
    navController: NavHostController
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToPods = {
                    navController.navigate(Screen.PodList.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPods = {
                    navController.navigate(Screen.PodList.route) {
                        popUpTo(Screen.Settings.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.PodList.route) { backStackEntry ->
            val shouldRefresh = backStackEntry.savedStateHandle.get<Boolean>("refresh") ?: false
            PodListScreen(
                shouldRefresh = shouldRefresh,
                onRefreshHandled = {
                    backStackEntry.savedStateHandle["refresh"] = false
                },
                onNavigateToDetail = { podId ->
                    navController.navigate(Screen.PodDetail.createRoute(podId))
                },
                onNavigateToCreate = {
                    navController.navigate(Screen.CreatePod.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(
            route = Screen.PodDetail.route,
            arguments = listOf(
                navArgument("podId") { type = NavType.StringType }
            )
        ) {
            PodDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                onPodDeleted = {
                    navController.previousBackStackEntry?.savedStateHandle?.set("refresh", true)
                    navController.popBackStack()
                },
                onNavigateToTerminal = { host, port ->
                    navController.navigate(Screen.Terminal.createRoute(host, port))
                }
            )
        }

        composable(Screen.CreatePod.route) {
            CreatePodScreen(
                onNavigateBack = { navController.popBackStack() },
                onPodCreated = {
                    navController.previousBackStackEntry?.savedStateHandle?.set("refresh", true)
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.Terminal.route,
            arguments = listOf(
                navArgument("host") { type = NavType.StringType },
                navArgument("port") { type = NavType.IntType }
            )
        ) {
            TerminalScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
