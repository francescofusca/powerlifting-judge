package com.ff9.poweliftjudge.ui.judge

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
import com.ff9.poweliftjudge.model.HoldPoint
import com.ff9.poweliftjudge.model.LiftType
import com.ff9.poweliftjudge.model.RepStats
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.ceil

enum class JudgePhase {
    IDLE, COUNTDOWN, ACTIVE
}

enum class StatusColor {
    DEFAULT, PREPARING, GO, GOOD_LIFT, READY_NEXT, FAILED
}

private enum class LiftPhase {
    IDLE, DESCENDING, HOLDING, GOOD_LIFT, ASCENDING, REP_COMPLETE
}

data class JudgeUiState(
    val phase: JudgePhase = JudgePhase.IDLE,
    val countdownValue: Int = 0,
    val angleDelta: Float = 0f,
    val progress: Int = 0,
    val repsCount: Int = 0,
    val targetAngle: Int = 90,
    val liftType: LiftType = LiftType.SQUAT,
    val statusText: String = "PRESS START",
    val statusColor: StatusColor = StatusColor.DEFAULT,
    val isGoodLift: Boolean = false,
    // Hold system (replaces bench-specific fields)
    val holdActive: Boolean = false,
    val holdProgress: Float = 0f,
    val holdCompleted: Boolean = false,
    val currentHoldIndex: Int = 0,
    val totalHoldPoints: Int = 0,
    val currentHoldAngle: Int = 0,
    // Custom exercise
    val isCustomExercise: Boolean = false,
    val exerciseName: String = ""
)

class JudgeViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as PLJudgeApp).container
    private val preferences = container.preferences
    private val sensorDataSource = container.sensorDataSource

    private val _uiState = MutableStateFlow(JudgeUiState())
    val uiState: StateFlow<JudgeUiState> = _uiState.asStateFlow()

    private var startGravity: FloatArray? = null
    private var currentGx: Float = 0f
    private var currentGy: Float = 0f
    private var currentGz: Float = 1f
    private var sensorJob: Job? = null
    private var countdownJob: Job? = null

    // Time tracking
    private var setStartTime: Long = 0
    private var lastRepTime: Long = 0
    private val repTimesList = mutableListOf<Long>()

    // Phase tracking
    private var repStartTime: Long = 0
    private var descentStartTime: Long = 0
    private var ascentStartTime: Long = 0
    private val repStatsList = mutableListOf<RepStats>()

    // Throttle
    private var lastUiUpdate = 0L

    // Low-pass filter for hold stability
    private var filteredDelta: Float = 0f
    private val lowPassAlpha = 0.8f

    // Audio
    private var mediaPlayerBip: MediaPlayer? = null
    private var mediaPlayerStart: MediaPlayer? = null
    private var mediaPlayerBenchBip: MediaPlayer? = null

    // Vibrator
    private val vibrator: Vibrator

    // Unified lift state machine
    private var liftPhase: LiftPhase = LiftPhase.IDLE
    private var holdPoints: List<HoldPoint> = emptyList()
    private var currentHoldIndex: Int = 0
    private var completedHoldIndices = mutableSetOf<Int>()
    private var holdPauseJob: Job? = null
    private var holdAngleRef: Float = 0f
    private val holdTolerance = 5f

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

    fun initialize(liftTypeName: String) {
        val isBuiltIn = LiftType.entries.any { it.displayName.equals(liftTypeName, ignoreCase = true) }
        if (isBuiltIn) {
            val liftType = LiftType.fromDisplayName(liftTypeName)
            val threshold = preferences.getThreshold(liftType)
            holdPoints = preferences.getHoldPoints(liftType)
            _uiState.update {
                it.copy(
                    liftType = liftType,
                    targetAngle = threshold,
                    statusText = "PRESS START",
                    statusColor = StatusColor.DEFAULT,
                    isCustomExercise = false,
                    exerciseName = liftType.displayName,
                    totalHoldPoints = holdPoints.size
                )
            }
        } else {
            val customExercise = preferences.getCustomExercises()
                .find { it.name.equals(liftTypeName, ignoreCase = true) }
            val prefsKey = customExercise?.prefsKey
                ?: "threshold_custom_${liftTypeName.lowercase().replace(" ", "_")}"
            val defaultThreshold = customExercise?.defaultThreshold ?: 90
            val threshold = preferences.getThresholdByKey(prefsKey, defaultThreshold)
            holdPoints = preferences.getHoldPoints(prefsKey)
            _uiState.update {
                it.copy(
                    liftType = LiftType.SQUAT,
                    targetAngle = threshold,
                    statusText = "PRESS START",
                    statusColor = StatusColor.DEFAULT,
                    isCustomExercise = true,
                    exerciseName = liftTypeName,
                    totalHoldPoints = holdPoints.size
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
            mediaPlayerBenchBip = MediaPlayer.create(ctx, R.raw.benchbip)

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
        if (data.gx.isNaN() || data.gy.isNaN() || data.gz.isNaN()) return
        currentGx = data.gx
        currentGy = data.gy
        currentGz = data.gz

        val now = System.currentTimeMillis()
        if (now - lastUiUpdate < 50) return
        lastUiUpdate = now

        val state = _uiState.value
        if (state.phase != JudgePhase.ACTIVE) return

        val base = startGravity ?: return
        val delta = angleBetween(base[0], base[1], base[2], currentGx, currentGy, currentGz)

        // Apply low-pass filter
        filteredDelta = lowPassAlpha * filteredDelta + (1 - lowPassAlpha) * delta

        val targetAngle = state.targetAngle
        val progress = ((delta / targetAngle) * 100).toInt().coerceIn(0, 100)

        _uiState.update {
            it.copy(
                angleDelta = delta,
                progress = progress
            )
        }

        processLiftLogic(delta, now)
    }

    private fun angleBetween(
        ax: Float, ay: Float, az: Float,
        bx: Float, by: Float, bz: Float
    ): Float {
        val dot = (ax * bx + ay * by + az * bz).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(dot.toDouble())).toFloat()
    }

    private fun processLiftLogic(delta: Float, currentTime: Long) {
        val state = _uiState.value
        val targetAngle = state.targetAngle
        val threshold = targetAngle * 0.2f
        val hysteresis = targetAngle * 0.1f

        when (liftPhase) {
            LiftPhase.IDLE -> {
                if (delta > threshold) {
                    liftPhase = LiftPhase.DESCENDING
                    repStartTime = currentTime
                    descentStartTime = currentTime
                }
            }

            LiftPhase.DESCENDING -> {
                // Check if there's an uncompleted hold point we've reached
                val nextHoldIndex = findNextHoldIndex(delta)
                if (nextHoldIndex != null) {
                    val holdPoint = holdPoints[nextHoldIndex]
                    currentHoldIndex = nextHoldIndex
                    liftPhase = LiftPhase.HOLDING
                    startHold(holdPoint)
                } else if (allHoldsCompleted() && delta >= targetAngle) {
                    // All holds done (or none), reached target
                    ascentStartTime = currentTime
                    liftPhase = LiftPhase.GOOD_LIFT
                    onGoodLift()
                }
                // If returns to zero before reaching target, reset
                if (delta < threshold) {
                    liftPhase = LiftPhase.IDLE
                    descentStartTime = 0L
                    repStartTime = 0L
                    completedHoldIndices.clear()
                    _uiState.update { it.copy(currentHoldIndex = 0) }
                }
            }

            LiftPhase.HOLDING -> {
                val holdPoint = holdPoints[currentHoldIndex]
                // If moved away from hold angle, cancel
                if (delta < holdPoint.angleDegrees - holdTolerance) {
                    cancelHold()
                    liftPhase = LiftPhase.DESCENDING
                    _uiState.update {
                        it.copy(holdActive = false, holdProgress = 0f)
                    }
                }
            }

            LiftPhase.GOOD_LIFT -> {
                if (delta < targetAngle - hysteresis) {
                    liftPhase = LiftPhase.ASCENDING
                }
            }

            LiftPhase.ASCENDING -> {
                if (delta < threshold) {
                    liftPhase = LiftPhase.REP_COMPLETE
                    recordRepStats(currentTime)
                    liftPhase = LiftPhase.IDLE
                    resetRepState()
                    _uiState.update {
                        it.copy(
                            statusText = "READY FOR NEXT REP",
                            statusColor = StatusColor.READY_NEXT,
                            isGoodLift = false,
                            holdActive = false,
                            holdProgress = 0f,
                            holdCompleted = false,
                            currentHoldIndex = 0
                        )
                    }
                }
            }

            LiftPhase.REP_COMPLETE -> {
                liftPhase = LiftPhase.IDLE
            }
        }
    }

    private fun findNextHoldIndex(delta: Float): Int? {
        for (i in holdPoints.indices) {
            if (i in completedHoldIndices) continue
            val hp = holdPoints[i]
            if (delta >= hp.angleDegrees) {
                return i
            }
        }
        return null
    }

    private fun allHoldsCompleted(): Boolean {
        return completedHoldIndices.size >= holdPoints.size
    }

    private fun startHold(holdPoint: HoldPoint) {
        cancelHold()
        holdAngleRef = filteredDelta
        _uiState.update {
            it.copy(
                holdActive = true,
                holdProgress = 0f,
                holdCompleted = false,
                currentHoldIndex = currentHoldIndex,
                currentHoldAngle = holdPoint.angleDegrees
            )
        }

        holdPauseJob = viewModelScope.launch {
            val totalMs = holdPoint.durationMs
            val stepMs = 50L
            var elapsed = 0L

            while (elapsed < totalMs) {
                delay(stepMs)
                elapsed += stepMs

                // Check stability using filtered delta
                if (abs(filteredDelta - holdAngleRef) > holdTolerance) {
                    liftPhase = LiftPhase.DESCENDING
                    _uiState.update {
                        it.copy(holdActive = false, holdProgress = 0f)
                    }
                    return@launch
                }

                _uiState.update {
                    it.copy(holdProgress = elapsed.toFloat() / totalMs)
                }
            }

            // Hold completed successfully
            completedHoldIndices.add(currentHoldIndex)
            _uiState.update {
                it.copy(
                    holdActive = false,
                    holdProgress = 1f,
                    holdCompleted = allHoldsCompleted()
                )
            }

            playHoldBip()

            // Check if all holds done and we're at or past target
            val currentDelta = filteredDelta
            val targetAngle = _uiState.value.targetAngle
            if (allHoldsCompleted() && currentDelta >= targetAngle) {
                ascentStartTime = System.currentTimeMillis()
                liftPhase = LiftPhase.GOOD_LIFT
                onGoodLift()
            } else {
                // More holds to go or haven't reached target yet
                liftPhase = LiftPhase.DESCENDING
            }
        }
    }

    private fun cancelHold() {
        holdPauseJob?.cancel()
        holdPauseJob = null
    }

    // ── Common helpers ──

    private fun onGoodLift() {
        val newReps = _uiState.value.repsCount + 1
        val currentTime = System.currentTimeMillis()
        val timeSinceLastRep = currentTime - lastRepTime
        repTimesList.add(timeSinceLastRep)
        lastRepTime = currentTime

        _uiState.update {
            it.copy(
                repsCount = newReps,
                statusText = "GOOD LIFT!",
                statusColor = StatusColor.GOOD_LIFT,
                isGoodLift = true
            )
        }

        playBip()
        vibrate(300)
    }

    private fun recordRepStats(repEndTime: Long) {
        if (repStartTime > 0 && descentStartTime > 0 && ascentStartTime > 0) {
            val descentTime = ascentStartTime - descentStartTime
            val ascentTime = repEndTime - ascentStartTime
            val totalTime = repEndTime - repStartTime
            repStatsList.add(RepStats(descentTime, ascentTime, totalTime))
        }
    }

    private fun resetRepState() {
        repStartTime = 0
        descentStartTime = 0
        ascentStartTime = 0
        completedHoldIndices.clear()
        cancelHold()
    }

    private fun playBip() {
        try {
            mediaPlayerBip?.let { mp ->
                if (mp.isPlaying) mp.seekTo(0) else mp.start()
            }
        } catch (_: Exception) {}
    }

    private fun playHoldBip() {
        try {
            mediaPlayerBenchBip?.let { mp ->
                if (mp.isPlaying) mp.seekTo(0) else mp.start()
            }
        } catch (_: Exception) {}
    }

    fun startCountdown() {
        val countdownSeconds = preferences.countdownTimer
        repStatsList.clear()
        repTimesList.clear()
        liftPhase = LiftPhase.IDLE
        currentHoldIndex = 0
        completedHoldIndices.clear()
        filteredDelta = 0f

        _uiState.update {
            it.copy(
                phase = JudgePhase.COUNTDOWN,
                repsCount = 0,
                statusText = "GET READY...",
                statusColor = StatusColor.PREPARING,
                countdownValue = countdownSeconds,
                holdActive = false,
                holdProgress = 0f,
                holdCompleted = false,
                currentHoldIndex = 0
            )
        }

        countdownJob = viewModelScope.launch {
            // Calculate when to start the sound so it finishes near 0
            val soundDurationMs = try { mediaPlayerStart?.duration ?: 0 } catch (_: Exception) { 0 }
            val soundStartAtSecond = if (soundDurationMs > 0)
                ceil(soundDurationMs / 1000.0).toInt().coerceAtMost(countdownSeconds)
            else 0

            for (i in countdownSeconds downTo 1) {
                _uiState.update { it.copy(countdownValue = i) }
                if (soundStartAtSecond > 0 && i == soundStartAtSecond) {
                    try { mediaPlayerStart?.start() } catch (_: Exception) {}
                }
                delay(1000)
            }

            startGravity = floatArrayOf(currentGx, currentGy, currentGz)
            setStartTime = System.currentTimeMillis()
            lastRepTime = setStartTime

            _uiState.update {
                it.copy(
                    phase = JudgePhase.ACTIVE,
                    statusText = "GO!",
                    statusColor = StatusColor.GO
                )
            }

            vibrate(200)
        }
    }

    fun getFinishData(): FinishData {
        val totalTime = if (setStartTime > 0) System.currentTimeMillis() - setStartTime else 0L
        val repsCount = _uiState.value.repsCount

        // If there are more reps counted than stats recorded, add a partial entry
        if (repsCount > repStatsList.size && descentStartTime > 0) {
            val now = System.currentTimeMillis()
            val descentTime = if (ascentStartTime > 0) ascentStartTime - descentStartTime
                              else now - descentStartTime
            val ascentTime = if (ascentStartTime > 0) now - ascentStartTime else 0L
            val repTotal = now - repStartTime
            repStatsList.add(RepStats(descentTime, ascentTime, repTotal))
        }

        val statsJsonArray = JSONArray()
        val statsToInclude = repStatsList.take(repsCount)
        for (stat in statsToInclude) {
            val obj = JSONObject()
            obj.put("descentTime", stat.descentTime)
            obj.put("ascentTime", stat.ascentTime)
            obj.put("totalTime", stat.totalTime)
            statsJsonArray.put(obj)
        }
        val state = _uiState.value
        return FinishData(
            liftType = if (state.isCustomExercise) state.exerciseName else state.liftType.displayName,
            repsCount = repsCount,
            repStatsJson = statsJsonArray.toString(),
            totalTime = totalTime
        )
    }

    fun updateRepsCount(newReps: Int) {
        _uiState.update { it.copy(repsCount = newReps) }
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

    override fun onCleared() {
        super.onCleared()
        sensorJob?.cancel()
        countdownJob?.cancel()
        holdPauseJob?.cancel()
        mediaPlayerBip?.release()
        mediaPlayerStart?.release()
        mediaPlayerBenchBip?.release()
    }
}

data class FinishData(
    val liftType: String,
    val repsCount: Int,
    val repStatsJson: String,
    val totalTime: Long
)
