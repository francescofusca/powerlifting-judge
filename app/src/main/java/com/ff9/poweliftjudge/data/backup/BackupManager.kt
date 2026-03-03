package com.ff9.poweliftjudge.data.backup

import com.ff9.poweliftjudge.data.preferences.UserPreferences
import com.ff9.poweliftjudge.data.repository.LiftRepository
import com.ff9.poweliftjudge.database.Lift
import org.json.JSONArray
import org.json.JSONObject

class BackupManager(
    private val repository: LiftRepository,
    private val preferences: UserPreferences
) {

    suspend fun exportBackup(): String {
        val root = JSONObject()
        root.put("version", BACKUP_VERSION)
        root.put("exportDate", System.currentTimeMillis())
        root.put("app", "PLJudge")

        // Export lifts
        val lifts = repository.getAllLifts()
        val liftsArray = JSONArray()
        lifts.forEach { lift ->
            val obj = JSONObject()
            obj.put("id", lift.id)
            obj.put("type", lift.type)
            obj.put("date", lift.date)
            obj.put("valid", lift.valid)
            obj.put("reps", lift.reps)
            obj.put("repTimes", lift.repTimes)
            obj.put("totalTime", lift.totalTime)
            obj.put("notes", lift.notes)
            obj.put("weight", lift.weight)
            obj.put("weightUnit", lift.weightUnit)
            obj.put("rpe", lift.rpe)
            liftsArray.put(obj)
        }
        root.put("lifts", liftsArray)

        // Export preferences
        root.put("settings", preferences.exportAllPrefs())

        return root.toString(2)
    }

    suspend fun importBackup(jsonString: String): ImportResult {
        return try {
            val root = JSONObject(jsonString)

            // Validate
            val app = root.optString("app", "")
            if (app != "PLJudge") {
                return ImportResult.Error("Invalid backup file")
            }

            // Import lifts
            val liftsArray = root.optJSONArray("lifts")
            if (liftsArray != null) {
                repository.deleteAll()
                for (i in 0 until liftsArray.length()) {
                    val obj = liftsArray.getJSONObject(i)
                    val lift = Lift(
                        id = 0, // autoGenerate new IDs
                        type = obj.getString("type"),
                        date = obj.getLong("date"),
                        valid = obj.optBoolean("valid", true),
                        reps = obj.optInt("reps", 1),
                        repTimes = obj.optString("repTimes", ""),
                        totalTime = obj.optLong("totalTime", 0L),
                        notes = obj.optString("notes", ""),
                        weight = obj.optDouble("weight", 0.0),
                        weightUnit = obj.optString("weightUnit", "kg"),
                        rpe = obj.optInt("rpe", 0)
                    )
                    repository.insertLift(lift)
                }
            }

            // Import preferences
            val settings = root.optJSONObject("settings")
            if (settings != null) {
                preferences.importAllPrefs(settings)
            }

            val liftCount = liftsArray?.length() ?: 0
            ImportResult.Success(liftCount)
        } catch (e: Exception) {
            ImportResult.Error(e.message ?: "Unknown error")
        }
    }

    sealed class ImportResult {
        data class Success(val liftCount: Int) : ImportResult()
        data class Error(val message: String) : ImportResult()
    }

    companion object {
        const val BACKUP_VERSION = 1
    }
}
