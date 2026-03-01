package com.ff9.poweliftjudge.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.ff9.poweliftjudge.PLJudgeApp
import com.ff9.poweliftjudge.model.LiftType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SettingsUiState(
    val countdownTimer: Int = 3,
    val squatThreshold: Int = 90,
    val benchThreshold: Int = 90,
    val deadliftThreshold: Int = 60,
    val sumoThreshold: Int = 60,
    val benchHoldDuration: Float = 1.0f,
    val startSound: String = "start",
    val language: String = "en",
    val darkMode: Boolean = false,
    val weightUnit: String = "kg",
    val bodyWeight: String = ""
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = (application as PLJudgeApp).container.preferences

    private val _uiState = MutableStateFlow(loadState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private fun loadState(): SettingsUiState {
        return SettingsUiState(
            countdownTimer = preferences.countdownTimer,
            squatThreshold = preferences.getThreshold(LiftType.SQUAT),
            benchThreshold = preferences.getThreshold(LiftType.BENCH_PRESS),
            deadliftThreshold = preferences.getThreshold(LiftType.DEADLIFT),
            sumoThreshold = preferences.getThreshold(LiftType.SUMO_DEADLIFT),
            benchHoldDuration = preferences.benchHoldDuration,
            startSound = preferences.startSound,
            language = preferences.selectedLanguage,
            darkMode = preferences.darkMode,
            weightUnit = preferences.weightUnit,
            bodyWeight = if (preferences.bodyWeight > 0f) preferences.bodyWeight.let {
                if (it == it.toLong().toFloat()) it.toLong().toString() else it.toString()
            } else ""
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

    fun setBenchHoldDuration(value: Float) {
        // Round to 0.1 precision
        val rounded = (value * 10).toInt() / 10f
        preferences.benchHoldDuration = rounded
        _uiState.update { it.copy(benchHoldDuration = rounded) }
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
}
