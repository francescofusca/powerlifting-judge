package com.ff9.poweliftjudge.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.ff9.poweliftjudge.model.CustomExercise
import com.ff9.poweliftjudge.model.HoldPoint
import com.ff9.poweliftjudge.model.LiftType
import org.json.JSONArray
import org.json.JSONObject

class UserPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var countdownTimer: Int
        get() = prefs.getInt("countdown_timer", 3)
        set(value) = prefs.edit().putInt("countdown_timer", value).apply()

    var startSound: String
        get() = prefs.getString("start_sound", "start") ?: "start"
        set(value) = prefs.edit().putString("start_sound", value).apply()

    var selectedLanguage: String
        get() = prefs.getString("selected_language", "en") ?: "en"
        set(value) = prefs.edit().putString("selected_language", value).apply()

    var darkMode: Boolean
        get() = prefs.getBoolean("dark_mode", false)
        set(value) = prefs.edit().putBoolean("dark_mode", value).apply()

    var weightUnit: String
        get() = prefs.getString("weight_unit", "kg") ?: "kg"
        set(value) = prefs.edit().putString("weight_unit", value).apply()

    var bodyWeight: Float
        get() = prefs.getFloat("body_weight", 0f)
        set(value) = prefs.edit().putFloat("body_weight", value).apply()

    var benchHoldDuration: Float
        get() = prefs.getFloat("bench_hold_duration", 1.0f)
        set(value) = prefs.edit().putFloat("bench_hold_duration", value).apply()

    fun getThreshold(liftType: LiftType): Int {
        return prefs.getInt(liftType.prefsKey, liftType.defaultThreshold)
    }

    fun setThreshold(liftType: LiftType, value: Int) {
        prefs.edit().putInt(liftType.prefsKey, value).apply()
    }

    // Generic threshold by key (for custom exercises)
    fun getThresholdByKey(prefsKey: String, default: Int): Int {
        return prefs.getInt(prefsKey, default)
    }

    fun setThresholdByKey(prefsKey: String, value: Int) {
        prefs.edit().putInt(prefsKey, value).apply()
    }

    // Custom exercises storage
    fun getCustomExercises(): List<CustomExercise> {
        val json = prefs.getString("custom_exercises", null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                CustomExercise(
                    name = obj.getString("name"),
                    defaultThreshold = obj.optInt("defaultThreshold", 90)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveCustomExercises(list: List<CustomExercise>) {
        val array = JSONArray()
        list.forEach { ex ->
            val obj = JSONObject()
            obj.put("name", ex.name)
            obj.put("defaultThreshold", ex.defaultThreshold)
            array.put(obj)
        }
        prefs.edit().putString("custom_exercises", array.toString()).apply()
    }

    fun addCustomExercise(name: String): Boolean {
        val existing = getCustomExercises()
        val builtInNames = LiftType.entries.map { it.displayName.lowercase() }
        val trimmed = name.trim()
        if (trimmed.isBlank()) return false
        if (existing.any { it.name.equals(trimmed, ignoreCase = true) }) return false
        if (builtInNames.any { it.equals(trimmed, ignoreCase = true) }) return false
        saveCustomExercises(existing + CustomExercise(trimmed))
        return true
    }

    fun removeCustomExercise(name: String) {
        val existing = getCustomExercises()
        val toRemove = existing.find { it.name.equals(name, ignoreCase = true) }
        if (toRemove != null) {
            prefs.edit().remove(toRemove.prefsKey).apply()
            prefs.edit().remove("hold_points_${toRemove.prefsKey}").apply()
            saveCustomExercises(existing.filter { it.name != toRemove.name })
        }
    }

    // Hold points storage
    fun getHoldPoints(prefsKey: String): List<HoldPoint> {
        val json = prefs.getString("hold_points_$prefsKey", null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                HoldPoint(
                    angleDegrees = obj.getInt("angle"),
                    durationMs = obj.getLong("durationMs")
                )
            }.sortedBy { it.angleDegrees }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveHoldPoints(prefsKey: String, holdPoints: List<HoldPoint>) {
        val array = JSONArray()
        holdPoints.sortedBy { it.angleDegrees }.forEach { hp ->
            val obj = JSONObject()
            obj.put("angle", hp.angleDegrees)
            obj.put("durationMs", hp.durationMs)
            array.put(obj)
        }
        prefs.edit().putString("hold_points_$prefsKey", array.toString()).apply()
    }

    fun getHoldPoints(liftType: LiftType): List<HoldPoint> = getHoldPoints(liftType.prefsKey)

    fun saveHoldPoints(liftType: LiftType, holdPoints: List<HoldPoint>) =
        saveHoldPoints(liftType.prefsKey, holdPoints)

    fun exportAllPrefs(): JSONObject {
        val result = JSONObject()
        prefs.all.forEach { (key, value) ->
            when (value) {
                is Boolean -> result.put(key, value)
                is Int -> result.put(key, value)
                is Long -> result.put(key, value)
                is Float -> result.put(key, value.toDouble())
                is String -> result.put(key, value)
            }
        }
        return result
    }

    fun importAllPrefs(json: JSONObject) {
        val editor = prefs.edit()
        editor.clear()
        json.keys().forEach { key ->
            when (val value = json.get(key)) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> {
                    // JSON integers come as Int or Long; floats come as Double
                    editor.putLong(key, value)
                }
                is Double -> {
                    // SharedPreferences stored floats get exported as doubles
                    // Check if this key is known to be a float
                    val floatKeys = setOf("bench_hold_duration", "body_weight")
                    if (floatKeys.contains(key)) {
                        editor.putFloat(key, value.toFloat())
                    } else {
                        // Could be an int stored as double by JSON
                        if (value == value.toLong().toDouble()) {
                            editor.putInt(key, value.toInt())
                        } else {
                            editor.putFloat(key, value.toFloat())
                        }
                    }
                }
                is String -> editor.putString(key, value)
            }
        }
        editor.apply()
    }

    fun migrateBenchHoldToHoldPoints() {
        if (prefs.getBoolean("hold_points_migrated", false)) return
        val benchHold = prefs.getFloat("bench_hold_duration", 1.0f)
        if (benchHold > 0f) {
            val existingPoints = getHoldPoints(LiftType.BENCH_PRESS)
            if (existingPoints.isEmpty()) {
                val benchThreshold = getThreshold(LiftType.BENCH_PRESS)
                val holdPoint = HoldPoint(
                    angleDegrees = benchThreshold,
                    durationMs = (benchHold * 1000).toLong()
                )
                saveHoldPoints(LiftType.BENCH_PRESS, listOf(holdPoint))
            }
        }
        prefs.edit().putBoolean("hold_points_migrated", true).apply()
    }
}
