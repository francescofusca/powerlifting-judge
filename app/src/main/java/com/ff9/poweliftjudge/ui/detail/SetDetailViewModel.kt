package com.ff9.poweliftjudge.ui.detail

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

data class DetailUiState(
    val lift: Lift? = null,
    val repStats: List<RepStats> = emptyList()
)

class SetDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as PLJudgeApp).container.repository

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    fun loadLift(liftId: Int) {
        viewModelScope.launch {
            repository.getLiftByIdFlow(liftId).collect { lift ->
                _uiState.value = DetailUiState(
                    lift = lift,
                    repStats = lift?.repTimes?.let { parseRepStats(it) } ?: emptyList()
                )
            }
        }
    }

    fun updateReps(liftId: Int, newReps: Int) {
        viewModelScope.launch {
            repository.updateReps(liftId, newReps)
        }
    }

    fun updateNotes(liftId: Int, newNotes: String) {
        viewModelScope.launch {
            repository.updateNotes(liftId, newNotes)
        }
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
