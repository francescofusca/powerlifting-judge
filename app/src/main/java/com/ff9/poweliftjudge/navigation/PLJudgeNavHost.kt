package com.ff9.poweliftjudge.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ff9.poweliftjudge.ui.detail.SetDetailScreen
import com.ff9.poweliftjudge.ui.history.HistoryScreen
import com.ff9.poweliftjudge.ui.home.HomeScreen
import com.ff9.poweliftjudge.ui.judge.JudgeScreen
import com.ff9.poweliftjudge.ui.calibrate.CalibrateScreen
import com.ff9.poweliftjudge.ui.settings.SettingsScreen
import com.ff9.poweliftjudge.ui.stats.LiftStatsScreen
import com.ff9.poweliftjudge.ui.summary.SetSummaryScreen
import com.ff9.poweliftjudge.ui.total.PowerliftingTotalScreen
import java.net.URLDecoder

@Composable
fun PLJudgeNavHost(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onLiftSelected = { liftType ->
                    navController.navigate(Screen.Judge.createRoute(liftType))
                },
                onHistoryClick = {
                    navController.navigate(Screen.History.route)
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                },
                onTotalClick = {
                    navController.navigate(Screen.PowerliftingTotal.route)
                }
            )
        }

        composable(
            route = Screen.Judge.route,
            arguments = listOf(navArgument("liftType") { type = NavType.StringType })
        ) { backStackEntry ->
            val liftType = backStackEntry.arguments?.getString("liftType") ?: "Squat"
            JudgeScreen(
                liftType = liftType,
                onFinishSaved = { type, reps, stats, totalTime ->
                    navController.navigate(
                        Screen.SetSummary.createRoute(type, reps, stats, totalTime)
                    ) {
                        popUpTo(Screen.Home.route)
                    }
                },
                onDiscard = {
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                }
            )
        }

        composable(
            route = Screen.SetSummary.route,
            arguments = listOf(
                navArgument("liftType") { type = NavType.StringType },
                navArgument("totalReps") { type = NavType.IntType },
                navArgument("repStats") { type = NavType.StringType },
                navArgument("totalTime") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val liftType = backStackEntry.arguments?.getString("liftType") ?: ""
            val totalReps = backStackEntry.arguments?.getInt("totalReps") ?: 0
            val repStats = URLDecoder.decode(
                backStackEntry.arguments?.getString("repStats") ?: "[]", "UTF-8"
            )
            val totalTime = backStackEntry.arguments?.getLong("totalTime") ?: 0L
            SetSummaryScreen(
                liftType = liftType,
                totalReps = totalReps,
                repStatsJson = repStats,
                totalTimeMs = totalTime,
                onSaved = {
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                },
                onDiscard = {
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                }
            )
        }

        composable(
            route = Screen.SetDetail.route,
            arguments = listOf(navArgument("liftId") { type = NavType.IntType })
        ) { backStackEntry ->
            val liftId = backStackEntry.arguments?.getInt("liftId") ?: 0
            SetDetailScreen(
                liftId = liftId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.History.route) {
            HistoryScreen(
                onLiftClick = { liftId ->
                    navController.navigate(Screen.SetDetail.createRoute(liftId))
                },
                onStatsClick = { liftType ->
                    navController.navigate(Screen.LiftStats.createRoute(liftType))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.LiftStats.route,
            arguments = listOf(navArgument("liftType") { type = NavType.StringType })
        ) { backStackEntry ->
            val liftType = backStackEntry.arguments?.getString("liftType") ?: "Squat"
            LiftStatsScreen(
                liftType = liftType,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onCalibrate = { liftType ->
                    navController.navigate(Screen.Calibrate.createRoute(liftType))
                }
            )
        }

        composable(
            route = Screen.Calibrate.route,
            arguments = listOf(navArgument("liftType") { type = NavType.StringType })
        ) { backStackEntry ->
            val liftType = backStackEntry.arguments?.getString("liftType") ?: "Squat"
            CalibrateScreen(
                liftType = liftType,
                onDone = { navController.popBackStack() }
            )
        }

        composable(Screen.PowerliftingTotal.route) {
            PowerliftingTotalScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
