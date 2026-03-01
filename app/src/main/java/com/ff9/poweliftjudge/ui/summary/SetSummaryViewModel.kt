package com.ff9.poweliftjudge.ui.summary

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ff9.poweliftjudge.PLJudgeApp
import com.ff9.poweliftjudge.database.Lift
import com.ff9.poweliftjudge.model.RepStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray

data class SummaryUiState(
    val liftType: String = "",
    val totalReps: Int = 0,
    val totalTimeMs: Long = 0,
    val repStats: List<RepStats> = emptyList(),
    val notes: String = "",
    val weight: String = "",
    val weightUnit: String = "kg",
    val rpe: Int = 0,
    val saved: Boolean = false,
    val isNewPR: Boolean = false
)

class SetSummaryViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as PLJudgeApp).container
    private val repository = container.repository
    private val preferences = container.preferences

    private val _uiState = MutableStateFlow(SummaryUiState())
    val uiState: StateFlow<SummaryUiState> = _uiState.asStateFlow()

    private var repStatsJson: String = "[]"

    fun initialize(liftType: String, totalReps: Int, repStatsJsonStr: String, totalTimeMs: Long) {
        repStatsJson = repStatsJsonStr
        val stats = parseRepStats(repStatsJsonStr)
        _uiState.value = SummaryUiState(
            liftType = liftType,
            totalReps = totalReps,
            totalTimeMs = totalTimeMs,
            repStats = stats,
            weightUnit = preferences.weightUnit
        )
    }

    fun updateNotes(notes: String) {
        _uiState.value = _uiState.value.copy(notes = notes)
    }

    fun updateWeight(weight: String) {
        _uiState.value = _uiState.value.copy(weight = weight)
    }

    fun updateRpe(rpe: Int) {
        _uiState.value = _uiState.value.copy(rpe = rpe)
    }

    fun save() {
        viewModelScope.launch {
            val state = _uiState.value
            val weightValue = state.weight.toDoubleOrNull() ?: 0.0

            // Get previous max before inserting
            val previousMax = repository.getMaxWeightForType(state.liftType) ?: 0.0

            repository.insertLift(
                Lift(
                    id = 0,
                    type = state.liftType,
                    date = System.currentTimeMillis(),
                    valid = true,
                    reps = state.totalReps,
                    repTimes = repStatsJson,
                    totalTime = state.totalTimeMs,
                    notes = state.notes,
                    weight = weightValue,
                    weightUnit = state.weightUnit,
                    rpe = state.rpe
                )
            )

            val isNewPR = weightValue > 0 && weightValue > previousMax
            _uiState.value = _uiState.value.copy(saved = !isNewPR, isNewPR = isNewPR)
        }
    }

    fun dismissPR() {
        _uiState.value = _uiState.value.copy(isNewPR = false, saved = true)
    }

    private fun parseRepStats(json: String): List<RepStats> {
        return try {
            val jsonArray = JSONArray(json)
            List(jsonArray.length()) { i ->
                val obj = jsonArray.getJSONObject(i)
                RepStats(
                    descentTime = obj.getLong("descentTime"),
                    ascentTime = obj.getLong("ascentTime"),
                    totalTime = obj.getLong("totalTime")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
