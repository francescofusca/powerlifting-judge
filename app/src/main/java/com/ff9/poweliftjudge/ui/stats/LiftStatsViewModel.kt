package com.ff9.poweliftjudge.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.ff9.poweliftjudge.PLJudgeApp
import com.ff9.poweliftjudge.database.Lift
import com.ff9.poweliftjudge.model.LiftType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import java.util.Calendar

data class RecentSet(
    val weight: Double,
    val weightUnit: String,
    val reps: Int,
    val date: Long
)

data class LiftStatsUiState(
    val liftType: LiftType = LiftType.SQUAT,
    val totalSets: Int = 0,
    val totalReps: Int = 0,
    val prWeight: Double = 0.0,
    val prDate: Long = 0L,
    val prWeightUnit: String = "kg",
    val totalVolume: Double = 0.0,
    val volumeUnit: String = "kg",
    val est1rm: Double = 0.0,
    val est1rmUnit: String = "kg",
    val avgDescent: Long = 0L,
    val avgAscent: Long = 0L,
    val avgRepTime: Long = 0L,
    val hasTimeData: Boolean = false,
    val bestSetReps: Int = 0,
    val bestSetWeight: Double = 0.0,
    val bestSetDate: Long = 0L,
    val bestSetUnit: String = "kg",
    val recentSets: List<RecentSet> = emptyList(),
    val setsLastWeek: Int = 0,
    val setsLastMonth: Int = 0,
    val avgRpe: Double = 0.0,
    val hasRpeData: Boolean = false,
    val hasData: Boolean = false
)

class LiftStatsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as PLJudgeApp).container.repository

    fun getStatsFlow(liftTypeName: String): Flow<LiftStatsUiState> {
        val liftType = LiftType.fromDisplayName(liftTypeName)
        return repository.getLiftsByTypeFlow(liftType.displayName).map { lifts ->
            computeStats(lifts, liftType)
        }
    }

    private fun computeStats(lifts: List<Lift>, liftType: LiftType): LiftStatsUiState {
        if (lifts.isEmpty()) return LiftStatsUiState(liftType = liftType)

        val totalSets = lifts.size
        val totalReps = lifts.sumOf { it.reps }

        // PR
        val liftsWithWeight = lifts.filter { it.weight > 0 }
        val prLift = liftsWithWeight.maxByOrNull { it.weight }
        val prWeight = prLift?.weight ?: 0.0
        val prDate = prLift?.date ?: 0L
        val prUnit = prLift?.weightUnit ?: "kg"

        // Total volume
        val totalVolume = liftsWithWeight.sumOf { it.weight * it.reps }
        val volumeUnit = liftsWithWeight.firstOrNull()?.weightUnit ?: "kg"

        // Estimated 1RM (Epley) from best set
        var est1rm = 0.0
        var est1rmUnit = "kg"
        for (lift in liftsWithWeight) {
            val e = if (lift.reps == 1) lift.weight else lift.weight * (1 + lift.reps / 30.0)
            if (e > est1rm) {
                est1rm = e
                est1rmUnit = lift.weightUnit
            }
        }

        // Average rep times from repTimes JSON
        var totalDescentMs = 0L
        var totalAscentMs = 0L
        var totalRepTimeMs = 0L
        var timeEntries = 0
        for (lift in lifts) {
            val repTimes = lift.repTimes
            if (repTimes.isBlank() || repTimes == "[]") continue
            try {
                val arr = JSONArray(repTimes)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val descent = obj.optLong("descentTime", 0)
                    val ascent = obj.optLong("ascentTime", 0)
                    val total = obj.optLong("totalTime", 0)
                    if (total > 0) {
                        totalDescentMs += descent
                        totalAscentMs += ascent
                        totalRepTimeMs += total
                        timeEntries++
                    }
                }
            } catch (_: Exception) {}
        }
        val avgDescent = if (timeEntries > 0) totalDescentMs / timeEntries else 0L
        val avgAscent = if (timeEntries > 0) totalAscentMs / timeEntries else 0L
        val avgRepTime = if (timeEntries > 0) totalRepTimeMs / timeEntries else 0L

        // Best set: highest weight with most reps at that weight
        val bestSet = liftsWithWeight
            .sortedWith(compareByDescending<Lift> { it.weight }.thenByDescending { it.reps })
            .firstOrNull()

        // Recent sets (last 10 with weight)
        val recentSets = liftsWithWeight
            .sortedByDescending { it.date }
            .take(10)
            .map { RecentSet(it.weight, it.weightUnit, it.reps, it.date) }

        // Frequency
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        cal.add(Calendar.DAY_OF_YEAR, -7)
        val weekAgo = cal.timeInMillis
        cal.timeInMillis = now
        cal.add(Calendar.MONTH, -1)
        val monthAgo = cal.timeInMillis

        val setsLastWeek = lifts.count { it.date >= weekAgo }
        val setsLastMonth = lifts.count { it.date >= monthAgo }

        // Average RPE
        val liftsWithRpe = lifts.filter { it.rpe > 0 }
        val avgRpe = if (liftsWithRpe.isNotEmpty()) liftsWithRpe.map { it.rpe }.average() else 0.0

        return LiftStatsUiState(
            liftType = liftType,
            totalSets = totalSets,
            totalReps = totalReps,
            prWeight = prWeight,
            prDate = prDate,
            prWeightUnit = prUnit,
            totalVolume = totalVolume,
            volumeUnit = volumeUnit,
            est1rm = est1rm,
            est1rmUnit = est1rmUnit,
            avgDescent = avgDescent,
            avgAscent = avgAscent,
            avgRepTime = avgRepTime,
            hasTimeData = timeEntries > 0,
            bestSetReps = bestSet?.reps ?: 0,
            bestSetWeight = bestSet?.weight ?: 0.0,
            bestSetDate = bestSet?.date ?: 0L,
            bestSetUnit = bestSet?.weightUnit ?: "kg",
            recentSets = recentSets,
            setsLastWeek = setsLastWeek,
            setsLastMonth = setsLastMonth,
            avgRpe = avgRpe,
            hasRpeData = liftsWithRpe.isNotEmpty(),
            hasData = true
        )
    }
}
