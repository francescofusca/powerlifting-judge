package com.ff9.poweliftjudge.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.sqrt

/**
 * gx/gy/gz: normalized "world up" vector expressed in the device coordinate frame.
 * Using a 3D reference vector instead of a single Euler angle (pitch) avoids gimbal lock
 * when the phone is worn in portrait against the body (quadriceps, triceps, etc.).
 */
data class SensorData(
    val gx: Float = 0f,
    val gy: Float = 0f,
    val gz: Float = 1f,
    val timestamp: Long = 0L
)

class SensorDataSource(context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    val hasRotationVector: Boolean get() = rotationSensor != null

    fun sensorFlow(): Flow<SensorData> = callbackFlow {
        val rotationMatrix = FloatArray(9)
        val accelerometerReading = FloatArray(3)
        val magnetometerReading = FloatArray(3)
        var hasMagReading = false

        fun emitFromRotationMatrix(r: FloatArray) {
            // "World up" (0,0,1) expressed in device frame = R^T * [0,0,1] = (r[2][0], r[2][1], r[2][2])
            val gx = r[6]
            val gy = r[7]
            val gz = r[8]
            val n = sqrt(gx * gx + gy * gy + gz * gz)
            if (n > 1e-6f) {
                trySend(SensorData(gx / n, gy / n, gz / n, System.currentTimeMillis()))
            }
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return

                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        emitFromRotationMatrix(rotationMatrix)
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        System.arraycopy(event.values, 0, accelerometerReading, 0, 3)
                        if (hasMagReading && SensorManager.getRotationMatrix(
                                rotationMatrix, null, accelerometerReading, magnetometerReading
                            )
                        ) {
                            emitFromRotationMatrix(rotationMatrix)
                        } else {
                            // Fallback: use accelerometer vector directly as the "up" reference.
                            // When the device is roughly still, accelerometer ≈ gravity.
                            val ax = accelerometerReading[0]
                            val ay = accelerometerReading[1]
                            val az = accelerometerReading[2]
                            val n = sqrt(ax * ax + ay * ay + az * az)
                            if (n > 1e-6f) {
                                trySend(SensorData(ax / n, ay / n, az / n, System.currentTimeMillis()))
                            }
                        }
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        System.arraycopy(event.values, 0, magnetometerReading, 0, 3)
                        hasMagReading = true
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (rotationSensor != null) {
            sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
        } else {
            accelerometer?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
            magnetometer?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        }

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }
}
