package com.ff9.poweliftjudge.ui.total

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ff9.poweliftjudge.PLJudgeApp
import com.ff9.poweliftjudge.model.LiftType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

data class LiftPR(
    val weight: Double = 0.0,
    val weightUnit: String = "kg",
    val date: Long = 0L,
    val reps: Int = 0
)

data class SessionTotal(
    val date: Long,
    val squatBest: Double,
    val benchBest: Double,
    val deadliftBest: Double,
    val total: Double,
    val weightUnit: String
)

data class PowerliftingTotalUiState(
    val squatPR: LiftPR = LiftPR(),
    val benchPR: LiftPR = LiftPR(),
    val deadliftPR: LiftPR = LiftPR(),
    val bestTotal: Double = 0.0,
    val bestTotalDate: Long = 0L,
    val currentTotal: Double = 0.0,
    val weightUnit: String = "kg",
    val bodyWeight: Float = 0f,
    val relativeStrength: Double = 0.0,
    val sessionTotals: List<SessionTotal> = emptyList(),
    val hasData: Boolean = false
)

class PowerliftingTotalViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as PLJudgeApp).container
    private val repository = container.repository
    private val preferences = container.preferences

    private val _uiState = MutableStateFlow(PowerliftingTotalUiState())
    val uiState: StateFlow<PowerliftingTotalUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllLiftsFlow().collect { lifts ->
                val weightUnit = preferences.weightUnit
                val bodyWeight = preferences.bodyWeight

                val liftsWithWeight = lifts.filter { it.weight > 0 }
                if (liftsWithWeight.isEmpty()) {
                    _uiState.value = PowerliftingTotalUiState(
                        weightUnit = weightUnit,
                        bodyWeight = bodyWeight
                    )
                    return@collect
                }

                // Individual PRs
                val squatLifts = liftsWithWeight.filter { it.type == LiftType.SQUAT.displayName }
                val benchLifts = liftsWithWeight.filter { it.type == LiftType.BENCH_PRESS.displayName }
                val dlLifts = liftsWithWeight.filter {
                    it.type == LiftType.DEADLIFT.displayName || it.type == LiftType.SUMO_DEADLIFT.displayName
                }

                val squatBest = squatLifts.maxByOrNull { it.weight }
                val benchBest = benchLifts.maxByOrNull { it.weight }
                val dlBest = dlLifts.maxByOrNull { it.weight }

                val squatPR = squatBest?.let { LiftPR(it.weight, it.weightUnit, it.date, it.reps) } ?: LiftPR()
                val benchPR = benchBest?.let { LiftPR(it.weight, it.weightUnit, it.date, it.reps) } ?: LiftPR()
                val deadliftPR = dlBest?.let { LiftPR(it.weight, it.weightUnit, it.date, it.reps) } ?: LiftPR()

                val currentTotal = squatPR.weight + benchPR.weight + deadliftPR.weight

                // Session totals: group by day
                val cal = Calendar.getInstance()
                val dayGroups = liftsWithWeight.groupBy { lift ->
                    cal.timeInMillis = lift.date
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    cal.timeInMillis
                }

                val sessionTotals = dayGroups.mapNotNull { (dayMs, dayLifts) ->
                    val daySQ = dayLifts.filter { it.type == LiftType.SQUAT.displayName }
                        .maxByOrNull { it.weight }?.weight ?: return@mapNotNull null
                    val dayBP = dayLifts.filter { it.type == LiftType.BENCH_PRESS.displayName }
                        .maxByOrNull { it.weight }?.weight ?: return@mapNotNull null
                    val dayDL = dayLifts.filter {
                        it.type == LiftType.DEADLIFT.displayName || it.type == LiftType.SUMO_DEADLIFT.displayName
                    }.maxByOrNull { it.weight }?.weight ?: return@mapNotNull null

                    if (daySQ <= 0 || dayBP <= 0 || dayDL <= 0) return@mapNotNull null

                    SessionTotal(
                        date = dayMs,
                        squatBest = daySQ,
                        benchBest = dayBP,
                        deadliftBest = dayDL,
                        total = daySQ + dayBP + dayDL,
                        weightUnit = weightUnit
                    )
                }.sortedByDescending { it.date }

                val bestSession = sessionTotals.maxByOrNull { it.total }
                val relativeStrength = if (bodyWeight > 0 && currentTotal > 0) currentTotal / bodyWeight else 0.0

                _uiState.value = PowerliftingTotalUiState(
                    squatPR = squatPR,
                    benchPR = benchPR,
                    deadliftPR = deadliftPR,
                    bestTotal = bestSession?.total ?: 0.0,
                    bestTotalDate = bestSession?.date ?: 0L,
                    currentTotal = currentTotal,
                    weightUnit = weightUnit,
                    bodyWeight = bodyWeight,
                    relativeStrength = relativeStrength,
                    sessionTotals = sessionTotals,
                    hasData = currentTotal > 0
                )
            }
        }
    }
}
