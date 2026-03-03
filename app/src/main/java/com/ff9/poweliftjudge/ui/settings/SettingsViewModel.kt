package com.ff9.poweliftjudge.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ff9.poweliftjudge.PLJudgeApp
import com.ff9.poweliftjudge.data.backup.BackupManager
import com.ff9.poweliftjudge.model.CustomExercise
import com.ff9.poweliftjudge.model.HoldPoint
import com.ff9.poweliftjudge.model.LiftType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val countdownTimer: Int = 3,
    val squatThreshold: Int = 90,
    val benchThreshold: Int = 90,
    val deadliftThreshold: Int = 60,
    val sumoThreshold: Int = 60,
    val startSound: String = "start",
    val language: String = "en",
    val darkMode: Boolean = false,
    val weightUnit: String = "kg",
    val bodyWeight: String = "",
    val customExercises: List<CustomExercise> = emptyList(),
    val customThresholds: Map<String, Int> = emptyMap(),
    val holdPointsMap: Map<String, List<HoldPoint>> = emptyMap()
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as PLJudgeApp).container
    private val preferences = container.preferences
    private val backupManager = container.backupManager

    private val _uiState = MutableStateFlow(loadState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private fun loadState(): SettingsUiState {
        val customExercises = preferences.getCustomExercises()
        val customThresholds = customExercises.associate { ex ->
            ex.name to preferences.getThresholdByKey(ex.prefsKey, ex.defaultThreshold)
        }
        // Load hold points for all lift types
        val holdPointsMap = mutableMapOf<String, List<HoldPoint>>()
        LiftType.entries.forEach { lt ->
            holdPointsMap[lt.prefsKey] = preferences.getHoldPoints(lt)
        }
        customExercises.forEach { ex ->
            holdPointsMap[ex.prefsKey] = preferences.getHoldPoints(ex.prefsKey)
        }

        return SettingsUiState(
            countdownTimer = preferences.countdownTimer,
            squatThreshold = preferences.getThreshold(LiftType.SQUAT),
            benchThreshold = preferences.getThreshold(LiftType.BENCH_PRESS),
            deadliftThreshold = preferences.getThreshold(LiftType.DEADLIFT),
            sumoThreshold = preferences.getThreshold(LiftType.SUMO_DEADLIFT),
            startSound = preferences.startSound,
            language = preferences.selectedLanguage,
            darkMode = preferences.darkMode,
            weightUnit = preferences.weightUnit,
            bodyWeight = if (preferences.bodyWeight > 0f) preferences.bodyWeight.let {
                if (it == it.toLong().toFloat()) it.toLong().toString() else it.toString()
            } else "",
            customExercises = customExercises,
            customThresholds = customThresholds,
            holdPointsMap = holdPointsMap
        )
    }

    fun setCountdownTimer(value: Int) {
        preferences.countdownTimer = value
        _uiState.update { it.copy(countdownTimer = value) }
    }

    fun setSquatThreshold(value: Int) {
        preferences.setThreshold(LiftType.SQUAT, value)
        _uiState.update { it.copy(squatThreshold = value) }
    }

    fun setBenchThreshold(value: Int) {
        preferences.setThreshold(LiftType.BENCH_PRESS, value)
        _uiState.update { it.copy(benchThreshold = value) }
    }

    fun setDeadliftThreshold(value: Int) {
        preferences.setThreshold(LiftType.DEADLIFT, value)
        _uiState.update { it.copy(deadliftThreshold = value) }
    }

    fun setSumoThreshold(value: Int) {
        preferences.setThreshold(LiftType.SUMO_DEADLIFT, value)
        _uiState.update { it.copy(sumoThreshold = value) }
    }

    fun setStartSound(sound: String) {
        preferences.startSound = sound
        _uiState.update { it.copy(startSound = sound) }
    }

    fun setLanguage(lang: String) {
        preferences.selectedLanguage = lang
        _uiState.update { it.copy(language = lang) }
    }

    fun setDarkMode(enabled: Boolean) {
        preferences.darkMode = enabled
        _uiState.update { it.copy(darkMode = enabled) }
    }

    fun setWeightUnit(unit: String) {
        preferences.weightUnit = unit
        _uiState.update { it.copy(weightUnit = unit) }
    }

    fun setBodyWeight(weight: String) {
        val value = weight.toFloatOrNull() ?: 0f
        preferences.bodyWeight = value
        _uiState.update { it.copy(bodyWeight = weight) }
    }

    fun setCustomThreshold(name: String, value: Int) {
        val exercise = _uiState.value.customExercises.find { it.name == name } ?: return
        preferences.setThresholdByKey(exercise.prefsKey, value)
        _uiState.update { it.copy(customThresholds = it.customThresholds + (name to value)) }
    }

    fun deleteCustomExercise(name: String) {
        preferences.removeCustomExercise(name)
        _uiState.update {
            val exercises = preferences.getCustomExercises()
            it.copy(
                customExercises = exercises,
                customThresholds = it.customThresholds - name,
                holdPointsMap = it.holdPointsMap - "threshold_custom_${name.lowercase().replace(" ", "_")}"
            )
        }
    }

    fun addHoldPoint(prefsKey: String, holdPoint: HoldPoint) {
        val current = _uiState.value.holdPointsMap[prefsKey] ?: emptyList()
        val updated = (current + holdPoint).sortedBy { it.angleDegrees }
        preferences.saveHoldPoints(prefsKey, updated)
        _uiState.update { it.copy(holdPointsMap = it.holdPointsMap + (prefsKey to updated)) }
    }

    fun removeHoldPoint(prefsKey: String, index: Int) {
        val current = _uiState.value.holdPointsMap[prefsKey] ?: return
        if (index !in current.indices) return
        val updated = current.toMutableList().apply { removeAt(index) }
        preferences.saveHoldPoints(prefsKey, updated)
        _uiState.update { it.copy(holdPointsMap = it.holdPointsMap + (prefsKey to updated)) }
    }

    fun exportBackup(onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val json = backupManager.exportBackup()
                onResult(json)
            } catch (_: Exception) {
                onResult(null)
            }
        }
    }

    fun importBackup(jsonString: String, onResult: (BackupManager.ImportResult) -> Unit) {
        viewModelScope.launch {
            val result = backupManager.importBackup(jsonString)
            if (result is BackupManager.ImportResult.Success) {
                _uiState.value = loadState()
            }
            onResult(result)
        }
    }
}
