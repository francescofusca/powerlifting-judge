package com.ff9.poweliftjudge.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Judge : Screen("judge/{liftType}") {
        fun createRoute(liftType: String) = "judge/$liftType"
    }
    data object SetSummary : Screen("summary/{liftType}/{totalReps}/{repStats}/{totalTime}") {
        fun createRoute(liftType: String, totalReps: Int, repStats: String, totalTime: Long) =
            "summary/$liftType/$totalReps/$repStats/$totalTime"
    }
    data object SetDetail : Screen("detail/{liftId}") {
        fun createRoute(liftId: Int) = "detail/$liftId"
    }
    data object History : Screen("history")
    data object Settings : Screen("settings")
    data object LiftStats : Screen("stats/{liftType}") {
        fun createRoute(liftType: String) = "stats/$liftType"
    }
    data object Calibrate : Screen("calibrate/{liftType}") {
        fun createRoute(liftType: String) = "calibrate/$liftType"
    }
}
