package com.ff9.poweliftjudge.model

enum class LiftType(
    val displayName: String,
    val defaultThreshold: Int,
    val prefsKey: String
) {
    SQUAT("Squat", 90, "threshold_squat"),
    BENCH_PRESS("Bench Press", 90, "threshold_bench"),
    DEADLIFT("Deadlift", 60, "threshold_deadlift"),
    SUMO_DEADLIFT("Sumo Deadlift", 60, "threshold_sumo");

    companion object {
        fun fromDisplayName(name: String): LiftType {
            return entries.find { it.displayName.equals(name, ignoreCase = true) } ?: SQUAT
        }
    }
}
