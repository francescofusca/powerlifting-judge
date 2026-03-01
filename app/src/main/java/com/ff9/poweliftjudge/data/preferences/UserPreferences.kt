package com.ff9.poweliftjudge.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.ff9.poweliftjudge.model.LiftType

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
}
