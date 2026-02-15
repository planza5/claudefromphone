package com.example.runpodmanager.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.runpodmanager.ui.screens.auto.AutoPodScreen
import com.example.runpodmanager.ui.screens.create.CreatePodScreen
import com.example.runpodmanager.ui.screens.settings.SettingsScreen
import com.example.runpodmanager.ui.screens.splash.SplashScreen
import com.example.runpodmanager.ui.screens.terminal.ProjectConsoleScreen
import com.example.runpodmanager.ui.screens.terminal.TerminalScreen

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Settings : Screen("settings")
    data object AutoPod : Screen("auto")
    data object CreatePod : Screen("create")
    data object ProjectConsole : Screen("project/{path}") {
        fun createRoute(path: String) = "project/${java.net.URLEncoder.encode(path, Charsets.UTF_8.name())}"
    }
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
                onNavigateToAuto = {
                    navController.navigate(Screen.AutoPod.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    // Si no hay back stack (venimos de Splash), navegar a AutoPod
                    if (!navController.popBackStack()) {
                        navController.navigate(Screen.AutoPod.route) {
                            popUpTo(Screen.Settings.route) { inclusive = true }
                        }
                    }
                },
                onNavigateToAuto = {
                    navController.navigate(Screen.AutoPod.route) {
                        popUpTo(Screen.Settings.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.AutoPod.route) {
            AutoPodScreen(
                onNavigateToTerminal = { host, port ->
                    navController.navigate(Screen.Terminal.createRoute(host, port))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(Screen.CreatePod.route) {
            CreatePodScreen(
                onNavigateBack = { navController.popBackStack() },
                onPodCreated = {
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
                onNavigateBack = { navController.popBackStack() },
                onNavigateToProject = { path ->
                    navController.navigate(Screen.ProjectConsole.createRoute(path))
                }
            )
        }

        composable(
            route = Screen.ProjectConsole.route,
            arguments = listOf(
                navArgument("path") { type = NavType.StringType }
            )
        ) {
            ProjectConsoleScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
