package com.ff9.poweliftjudge.ui.calibrate

import android.app.Application
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ff9.poweliftjudge.PLJudgeApp
import com.ff9.poweliftjudge.R
import com.ff9.poweliftjudge.data.sensor.SensorData
import com.ff9.poweliftjudge.model.LiftType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

enum class CalibratePhase {
    IDLE, COUNTDOWN, ACTIVE, DONE
}

data class CalibrateUiState(
    val phase: CalibratePhase = CalibratePhase.IDLE,
    val countdownValue: Int = 0,
    val currentDelta: Float = 0f,
    val detectedAngle: Int = 0,
    val liftType: LiftType = LiftType.SQUAT,
    val statusText: String = "",
    val isCustomExercise: Boolean = false,
    val customExerciseName: String = ""
)

class CalibrateViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as PLJudgeApp).container
    private val preferences = container.preferences
    private val sensorDataSource = container.sensorDataSource

    private val _uiState = MutableStateFlow(CalibrateUiState())
    val uiState: StateFlow<CalibrateUiState> = _uiState.asStateFlow()

    private var sensorJob: Job? = null
    private var countdownJob: Job? = null

    private var startPitch: Float? = null
    private var currentPitch: Float = 0f

    // Stability detection
    private val bufferSize = 40 // ~2 seconds at 50ms
    private val circularBuffer = FloatArray(bufferSize)
    private var bufferIndex = 0
    private var bufferFilled = false
    private var activeStartTime = 0L
    private val ignoreInitialMs = 1500L
    private val stabilityThreshold = 6f // degrees
    private val minDelta = 15f // minimum movement from start

    // Throttle
    private var lastUiUpdate = 0L

    // Audio
    private var mediaPlayerBip: MediaPlayer? = null
    private var mediaPlayerStart: MediaPlayer? = null

    // Vibrator
    private val vibrator: Vibrator

    init {
        val ctx = getApplication<Application>()
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = ctx.getSystemService(VibratorManager::class.java)
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Vibrator::class.java)
        }
    }

    private var customPrefsKey: String? = null

    fun initialize(liftTypeName: String) {
        val isBuiltIn = LiftType.entries.any { it.displayName.equals(liftTypeName, ignoreCase = true) }
        if (isBuiltIn) {
            val liftType = LiftType.fromDisplayName(liftTypeName)
            _uiState.update {
                it.copy(liftType = liftType, isCustomExercise = false, customExerciseName = "")
            }
        } else {
            val customExercise = preferences.getCustomExercises()
                .find { it.name.equals(liftTypeName, ignoreCase = true) }
            customPrefsKey = customExercise?.prefsKey
                ?: "threshold_custom_${liftTypeName.lowercase().replace(" ", "_")}"
            _uiState.update {
                it.copy(
                    liftType = LiftType.SQUAT, // fallback
                    isCustomExercise = true,
                    customExerciseName = liftTypeName
                )
            }
        }
        initAudio()
        startSensorCollection()
    }

    private fun initAudio() {
        val ctx = getApplication<Application>()
        try {
            mediaPlayerBip = MediaPlayer.create(ctx, R.raw.bip)
            val startSound = preferences.startSound
            val soundResId = when (startSound) {
                "race_start" -> R.raw.race_start
                "beep_short" -> R.raw.beep_short
                else -> R.raw.start
            }
            mediaPlayerStart = MediaPlayer.create(ctx, soundResId)
        } catch (_: Exception) {}
    }

    private fun startSensorCollection() {
        sensorJob = viewModelScope.launch {
            sensorDataSource.sensorFlow().collect { data ->
                processSensor(data)
            }
        }
    }

    private fun processSensor(data: SensorData) {
        val pitch = data.pitch
        if (pitch.isNaN()) return
        currentPitch = pitch

        val now = System.currentTimeMillis()
        if (now - lastUiUpdate < 50) return
        lastUiUpdate = now

        val state = _uiState.value
        if (state.phase != CalibratePhase.ACTIVE) return

        val base = startPitch ?: return
        var delta = abs(pitch - base)
        if (delta.isNaN()) delta = 0f
        if (delta > 180) delta = 360 - delta

        _uiState.update { it.copy(currentDelta = delta) }

        // Ignore initial period
        if (now - activeStartTime < ignoreInitialMs) return

        // Fill circular buffer
        circularBuffer[bufferIndex] = delta
        bufferIndex = (bufferIndex + 1) % bufferSize
        if (bufferIndex == 0) bufferFilled = true

        // Check stability only when buffer is full
        if (!bufferFilled) return

        val min = circularBuffer.min()
        val max = circularBuffer.max()
        val range = max - min
        val avg = circularBuffer.average().toFloat()

        if (range < stabilityThreshold && avg >= minDelta) {
            // Stable position detected
            val detected = avg.roundToInt()

            // Save to preferences
            if (state.isCustomExercise && customPrefsKey != null) {
                preferences.setThresholdByKey(customPrefsKey!!, detected)
            } else {
                preferences.setThreshold(state.liftType, detected)
            }

            // Play confirmation
            playBip()
            vibrate(500)

            _uiState.update {
                it.copy(
                    phase = CalibratePhase.DONE,
                    detectedAngle = detected
                )
            }
        }
    }

    fun startCountdown() {
        val countdownSeconds = preferences.countdownTimer
        // Reset buffer
        bufferIndex = 0
        bufferFilled = false
        circularBuffer.fill(0f)

        _uiState.update {
            it.copy(
                phase = CalibratePhase.COUNTDOWN,
                countdownValue = countdownSeconds,
                currentDelta = 0f
            )
        }

        countdownJob = viewModelScope.launch {
            for (i in countdownSeconds downTo 1) {
                _uiState.update { it.copy(countdownValue = i) }
                delay(1000)
            }

            startPitch = currentPitch
            activeStartTime = System.currentTimeMillis()

            _uiState.update {
                it.copy(
                    phase = CalibratePhase.ACTIVE,
                    currentDelta = 0f
                )
            }

            try { mediaPlayerStart?.start() } catch (_: Exception) {}
            vibrate(200)
        }
    }

    private fun playBip() {
        try {
            mediaPlayerBip?.let { mp ->
                if (mp.isPlaying) mp.seekTo(0) else mp.start()
            }
        } catch (_: Exception) {}
    }

    private fun vibrate(durationMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        } catch (_: Exception) {}
    }

    fun cleanup() {
        sensorJob?.cancel()
        countdownJob?.cancel()
        mediaPlayerBip?.release()
        mediaPlayerStart?.release()
        mediaPlayerBip = null
        mediaPlayerStart = null
    }

    override fun onCleared() {
        super.onCleared()
        cleanup()
    }
}
