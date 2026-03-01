package com.ff9.poweliftjudge

import android.app.Application
import com.ff9.poweliftjudge.data.preferences.UserPreferences
import com.ff9.poweliftjudge.data.repository.LiftRepository
import com.ff9.poweliftjudge.data.sensor.SensorDataSource
import com.ff9.poweliftjudge.database.LiftDatabase

class PLJudgeApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(context: Application) {
    private val database = LiftDatabase.getDatabase(context)
    val repository = LiftRepository(database.liftDao())
    val preferences = UserPreferences(context)
    val sensorDataSource = SensorDataSource(context)
}
