package com.ff9.poweliftjudge.model

data class CustomExercise(
    val name: String,
    val defaultThreshold: Int = 90
) {
    val prefsKey: String get() = "threshold_custom_${name.lowercase().replace(" ", "_")}"
}
