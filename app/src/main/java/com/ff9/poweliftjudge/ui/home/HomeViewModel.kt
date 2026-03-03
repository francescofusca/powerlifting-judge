package com.ff9.poweliftjudge.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.ff9.poweliftjudge.PLJudgeApp
import com.ff9.poweliftjudge.model.CustomExercise
import com.ff9.poweliftjudge.model.LiftType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

data class TotalUiState(
    val squatPR: Double = 0.0,
    val benchPR: Double = 0.0,
    val deadliftPR: Double = 0.0,
    val total: Double = 0.0,
    val weightUnit: String = "kg",
    val bodyWeight: Float = 0f,
    val relativeStrength: Double = 0.0,
    val hasData: Boolean = false
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as PLJudgeApp).container
    private val repository = container.repository
    private val preferences = container.preferences

    private val _customExercises = MutableStateFlow(preferences.getCustomExercises())
    val customExercises: StateFlow<List<CustomExercise>> = _customExercises.asStateFlow()

    fun addExercise(name: String): Boolean {
        val success = preferences.addCustomExercise(name)
        if (success) _customExercises.value = preferences.getCustomExercises()
        return success
    }

    fun removeExercise(name: String) {
        preferences.removeCustomExercise(name)
        _customExercises.value = preferences.getCustomExercises()
    }

    fun refreshExercises() {
        _customExercises.value = preferences.getCustomExercises()
    }

    val totalState: Flow<TotalUiState> = repository.getAllLiftsFlow().map { lifts ->
        val weightUnit = preferences.weightUnit
        val bodyWeight = preferences.bodyWeight

        val liftsWithWeight = lifts.filter { it.weight > 0 }
        if (liftsWithWeight.isEmpty()) return@map TotalUiState(weightUnit = weightUnit, bodyWeight = bodyWeight)

        val squatPR = liftsWithWeight
            .filter { it.type == LiftType.SQUAT.displayName }
            .maxByOrNull { it.weight }?.weight ?: 0.0

        val benchPR = liftsWithWeight
            .filter { it.type == LiftType.BENCH_PRESS.displayName }
            .maxByOrNull { it.weight }?.weight ?: 0.0

        val deadliftPR = liftsWithWeight
            .filter { it.type == LiftType.DEADLIFT.displayName }
            .maxByOrNull { it.weight }?.weight ?: 0.0

        val sumoPR = liftsWithWeight
            .filter { it.type == LiftType.SUMO_DEADLIFT.displayName }
            .maxByOrNull { it.weight }?.weight ?: 0.0

        val bestDeadlift = maxOf(deadliftPR, sumoPR)
        val total = squatPR + benchPR + bestDeadlift
        val hasData = total > 0

        val relativeStrength = if (bodyWeight > 0 && total > 0) total / bodyWeight else 0.0

        TotalUiState(
            squatPR = squatPR,
            benchPR = benchPR,
            deadliftPR = bestDeadlift,
            total = total,
            weightUnit = weightUnit,
            bodyWeight = bodyWeight,
            relativeStrength = relativeStrength,
            hasData = hasData
        )
    }
}
