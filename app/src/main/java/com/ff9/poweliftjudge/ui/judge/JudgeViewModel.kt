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
import kotlin.math.ceil

enum class JudgePhase {
    IDLE, COUNTDOWN, ACTIVE
}

enum class StatusColor {
    DEFAULT, PREPARING, GO, GOOD_LIFT, READY_NEXT, FAILED
}

/** Bench press sub-states for deterministic behavior */
private enum class BenchPhase {
    IDLE,
    DESCENDING,
    HOLDING,
    PRESS_SIGNAL,
    ASCENDING,
    REP_COMPLETE
}

data class JudgeUiState(
    val phase: JudgePhase = JudgePhase.IDLE,
    val countdownValue: Int = 0,
    val currentAngle: Float = 0f,
    val angleDelta: Float = 0f,
    val progress: Int = 0,
    val repsCount: Int = 0,
    val targetAngle: Int = 90,
    val liftType: LiftType = LiftType.SQUAT,
    val statusText: String = "PRESS START",
    val statusColor: StatusColor = StatusColor.DEFAULT,
    val isGoodLift: Boolean = false,
    // Competition bench
    val benchPauseActive: Boolean = false,
    val benchPauseProgress: Float = 0f,
    val benchPauseCompleted: Boolean = false
)

class JudgeViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as PLJudgeApp).container
    private val preferences = container.preferences
    private val sensorDataSource = container.sensorDataSource

    private val _uiState = MutableStateFlow(JudgeUiState())
    val uiState: StateFlow<JudgeUiState> = _uiState.asStateFlow()

    private var startPitch: Float? = null
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

    // Standard lift state
    private var isLiftGood = false
    private var wasInTargetZone = false

    // Throttle
    private var lastUiUpdate = 0L

    // Low-pass filter for bench hold stability
    private var filteredDelta: Float = 0f
    private val lowPassAlpha = 0.8f

    // Audio
    private var mediaPlayerBip: MediaPlayer? = null
    private var mediaPlayerStart: MediaPlayer? = null
    private var mediaPlayerBenchBip: MediaPlayer? = null

    // Vibrator
    private val vibrator: Vibrator

    // Competition bench state machine
    private var benchPhase: BenchPhase = BenchPhase.IDLE
    private var benchPauseJob: Job? = null
    private var pauseAngleRef: Float = 0f
    private val benchPauseTolerance = 5f // degrees
    private var benchHoldDurationMs: Long = 1000L

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
        val liftType = LiftType.fromDisplayName(liftTypeName)
        val threshold = preferences.getThreshold(liftType)
        benchHoldDurationMs = (preferences.benchHoldDuration * 1000).toLong()
        _uiState.update {
            it.copy(
                liftType = liftType,
                targetAngle = threshold,
                statusText = "PRESS START",
                statusColor = StatusColor.DEFAULT
            )
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
        val pitch = data.pitch
        if (pitch.isNaN()) return

        val now = System.currentTimeMillis()
        if (now - lastUiUpdate < 50) return
        lastUiUpdate = now

        val state = _uiState.value
        if (state.phase != JudgePhase.ACTIVE) {
            _uiState.update { it.copy(currentAngle = pitch) }
            return
        }

        val base = startPitch ?: return
        var delta = abs(pitch - base)
        if (delta.isNaN()) delta = 0f
        if (delta > 180) delta = 360 - delta

        // Apply low-pass filter
        filteredDelta = lowPassAlpha * filteredDelta + (1 - lowPassAlpha) * delta

        val targetAngle = state.targetAngle
        val progress = ((delta / targetAngle) * 100).toInt().coerceIn(0, 100)

        _uiState.update {
            it.copy(
                currentAngle = pitch,
                angleDelta = delta,
                progress = progress
            )
        }

        val isBench = state.liftType == LiftType.BENCH_PRESS
        if (isBench) {
            processBenchLogic(delta, now)
        } else {
            processStandardLiftLogic(delta, now)
        }
    }

    // ── Standard lift logic (squat, deadlift, sumo) ──

    private fun processStandardLiftLogic(delta: Float, currentTime: Long) {
        val state = _uiState.value
        val targetAngle = state.targetAngle
        val threshold = targetAngle * 0.2f

        // Start descent tracking
        if (delta < threshold && !wasInTargetZone && descentStartTime == 0L) {
            repStartTime = currentTime
            descentStartTime = currentTime
        } else if (delta >= targetAngle && !wasInTargetZone && descentStartTime > 0L) {
            ascentStartTime = currentTime
            wasInTargetZone = true
        }

        // Good lift detection
        if (delta >= targetAngle && !isLiftGood) {
            isLiftGood = true
            onGoodLift()
        }

        // Return to start position - complete rep
        if (delta < threshold && isLiftGood && wasInTargetZone) {
            recordRepStats(currentTime)
            resetRepState()
            _uiState.update {
                it.copy(
                    statusText = "READY FOR NEXT REP",
                    statusColor = StatusColor.READY_NEXT,
                    isGoodLift = false
                )
            }
        }
    }

    // ── Bench press state machine ──

    private fun processBenchLogic(delta: Float, currentTime: Long) {
        val state = _uiState.value
        val targetAngle = state.targetAngle
        val threshold = targetAngle * 0.2f
        val hysteresis = targetAngle * 0.1f // 10% hysteresis for phase transitions

        when (benchPhase) {
            BenchPhase.IDLE -> {
                if (delta > threshold) {
                    benchPhase = BenchPhase.DESCENDING
                    repStartTime = currentTime
                    descentStartTime = currentTime
                }
            }

            BenchPhase.DESCENDING -> {
                if (delta >= targetAngle) {
                    ascentStartTime = currentTime
                    if (benchHoldDurationMs <= 0L) {
                        // No hold required - skip directly to signal
                        benchPhase = BenchPhase.PRESS_SIGNAL
                        onGoodLift()
                    } else {
                        benchPhase = BenchPhase.HOLDING
                        startBenchPause(delta)
                    }
                }
                // If returns to zero before reaching target, reset
                if (delta < threshold) {
                    benchPhase = BenchPhase.IDLE
                    descentStartTime = 0L
                    repStartTime = 0L
                }
            }

            BenchPhase.HOLDING -> {
                // Stability is checked inside the coroutine timer.
                // If athlete moves too much, benchPauseJob cancels and we go back.
                if (delta < targetAngle - hysteresis) {
                    // Moved away from target - cancel hold
                    cancelBenchPause()
                    benchPhase = BenchPhase.DESCENDING
                    _uiState.update {
                        it.copy(benchPauseActive = false, benchPauseProgress = 0f)
                    }
                }
            }

            BenchPhase.PRESS_SIGNAL -> {
                // Wait for athlete to start ascending (pressing up)
                if (delta < targetAngle - hysteresis) {
                    benchPhase = BenchPhase.ASCENDING
                }
            }

            BenchPhase.ASCENDING -> {
                if (delta < threshold) {
                    // Returned to start - rep complete
                    benchPhase = BenchPhase.REP_COMPLETE
                    recordRepStats(currentTime)
                    benchPhase = BenchPhase.IDLE
                    resetRepState()
                    _uiState.update {
                        it.copy(
                            statusText = "READY FOR NEXT REP",
                            statusColor = StatusColor.READY_NEXT,
                            isGoodLift = false,
                            benchPauseActive = false,
                            benchPauseProgress = 0f,
                            benchPauseCompleted = false
                        )
                    }
                }
            }

            BenchPhase.REP_COMPLETE -> {
                // Transient state, should immediately go to IDLE
                benchPhase = BenchPhase.IDLE
            }
        }
    }

    private fun startBenchPause(currentDelta: Float) {
        cancelBenchPause()
        pauseAngleRef = filteredDelta
        _uiState.update {
            it.copy(benchPauseActive = true, benchPauseProgress = 0f, benchPauseCompleted = false)
        }

        benchPauseJob = viewModelScope.launch {
            val totalMs = benchHoldDurationMs
            val stepMs = 50L
            var elapsed = 0L

            while (elapsed < totalMs) {
                delay(stepMs)
                elapsed += stepMs

                // Check stability using filtered delta
                if (abs(filteredDelta - pauseAngleRef) > benchPauseTolerance) {
                    // Moved too much - go back to descending
                    benchPhase = BenchPhase.DESCENDING
                    _uiState.update {
                        it.copy(benchPauseActive = false, benchPauseProgress = 0f)
                    }
                    return@launch
                }

                _uiState.update {
                    it.copy(benchPauseProgress = elapsed.toFloat() / totalMs)
                }
            }

            // Hold completed successfully
            _uiState.update {
                it.copy(
                    benchPauseActive = false,
                    benchPauseProgress = 1f,
                    benchPauseCompleted = true
                )
            }

            // Play bench bip (PRESS command) - only sound here
            playBenchBip()

            // Transition to PRESS_SIGNAL - now count the rep
            benchPhase = BenchPhase.PRESS_SIGNAL
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
        }
    }

    private fun cancelBenchPause() {
        benchPauseJob?.cancel()
        benchPauseJob = null
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
        isLiftGood = false
        wasInTargetZone = false
        repStartTime = 0
        descentStartTime = 0
        ascentStartTime = 0
        cancelBenchPause()
    }

    private fun playBip() {
        try {
            mediaPlayerBip?.let { mp ->
                if (mp.isPlaying) mp.seekTo(0) else mp.start()
            }
        } catch (_: Exception) {}
    }

    private fun playBenchBip() {
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
        wasInTargetZone = false
        isLiftGood = false
        benchPhase = BenchPhase.IDLE
        filteredDelta = 0f

        _uiState.update {
            it.copy(
                phase = JudgePhase.COUNTDOWN,
                repsCount = 0,
                statusText = "GET READY...",
                statusColor = StatusColor.PREPARING,
                countdownValue = countdownSeconds,
                benchPauseActive = false,
                benchPauseProgress = 0f,
                benchPauseCompleted = false
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

            startPitch = _uiState.value.currentAngle
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

    /**
     * Build finish data, including a partial rep stats entry for the last
     * rep if it was counted but hasn't returned to zero yet.
     */
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
        // Only include up to repsCount entries
        val statsToInclude = repStatsList.take(repsCount)
        for (stat in statsToInclude) {
            val obj = JSONObject()
            obj.put("descentTime", stat.descentTime)
            obj.put("ascentTime", stat.ascentTime)
            obj.put("totalTime", stat.totalTime)
            statsJsonArray.put(obj)
        }
        return FinishData(
            liftType = _uiState.value.liftType.displayName,
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
        benchPauseJob?.cancel()
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
