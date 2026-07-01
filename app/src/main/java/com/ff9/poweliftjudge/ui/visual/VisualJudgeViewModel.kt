package com.ff9.poweliftjudge.ui.visual

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

// ── Per-rep statistics ──────────────────────────────────────────────────────

data class RepVisualStat(
    val repNumber: Int,
    val meanVelocity: Float,
    val peakVelocity: Float,
    val rangeOfMotionCm: Float,
    val eccentricTimeMs: Long,
    val concentricTimeMs: Long,
    val peakPowerW: Float,
    val backScore: Float
)

// ── UI State ────────────────────────────────────────────────────────────────

data class VisualJudgeUiState(
    val liftName: String = "",
    val analysis: AnalysisResult = AnalysisResult(),
    val currentPose: PoseSnapshot? = null,
    val currentVelocity: Float = 0f,
    val currentPowerW: Float = 0f,
    val currentBackScore: Float = 100f,
    val barbellKg: Float = 20f,
    val isActive: Boolean = false,
    val isRecording: Boolean = false,
    val savedVideoUri: Uri? = null,
    val repStats: List<RepVisualStat> = emptyList(),
    val showPostSeries: Boolean = false,
    val cameraFacing: Int = CameraSelector.LENS_FACING_BACK,
    val imageWidth: Int = 1,         // upright (after-rotation)
    val imageHeight: Int = 1,
    val rotationDegrees: Int = 90,
    val detectorStatus: DetectorStatus = DetectorStatus.OK
)

enum class DetectorStatus { OK, CPU_FALLBACK, UNAVAILABLE }

// ── ViewModel ───────────────────────────────────────────────────────────────

class VisualJudgeViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(VisualJudgeUiState())
    val uiState: StateFlow<VisualJudgeUiState> = _uiState.asStateFlow()

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    /**
     * MediaPipe init can fail on emulators that lack a proper GLES context.
     * Wrapped in try/catch so opening the screen never crashes — the detector
     * falls back internally (GPU → CPU → no-op).
     */
    private val poseDetector: MediaPipePoseDetector? = try {
        MediaPipePoseDetector(application.applicationContext) { snap -> onPose(snap) }
    } catch (t: Throwable) {
        android.util.Log.e("VisualJudgeVM", "Pose detector init failed", t)
        null
    }

    init {
        val status = when {
            poseDetector == null || !poseDetector.isReady -> DetectorStatus.UNAVAILABLE
            poseDetector.isCpuFallback                    -> DetectorStatus.CPU_FALLBACK
            else                                          -> DetectorStatus.OK
        }
        _uiState.update { it.copy(detectorStatus = status) }
    }

    private lateinit var analyzer: LiftAnalyzer
    private var cameraProvider: ProcessCameraProvider? = null

    // ── Velocity tracking from MediaPipe world Y (metres) ───────────────────
    private val velocityBuffer = ArrayDeque<Pair<Long, Float>>(10)
    private val VELOCITY_WINDOW = 6

    // Per-rep tracking
    private var repStartTime = 0L
    private var eccentricEndTime = 0L
    private var repPeakVelocity = 0f
    private var velocitySumForMean = 0f
    private var velocitySampleCount = 0
    private var hipYMin = Float.MAX_VALUE
    private var hipYMax = Float.MIN_VALUE
    private var lastRepCount = 0
    private var backScoreSum = 0f
    private var backScoreSamples = 0
    private val repStatsList = mutableListOf<RepVisualStat>()

    // ── Init ─────────────────────────────────────────────────────────────────

    fun onScreenRecordingStarted() = _uiState.update { it.copy(isRecording = true) }

    fun onScreenRecordingSaved(uri: Uri?) =
        _uiState.update { it.copy(isRecording = false, savedVideoUri = uri) }

    fun initialize(liftName: String, barbellKg: Float = 20f) {
        analyzer = when (liftName.lowercase()) {
            "bench press", "panca"          -> BenchAnalyzer()
            "deadlift", "stacco"            -> DeadliftAnalyzer(isSumo = false)
            "sumo deadlift", "stacco sumo"  -> DeadliftAnalyzer(isSumo = true)
            else                            -> SquatAnalyzer()
        }
        repStatsList.clear()
        _uiState.update { it.copy(liftName = liftName, barbellKg = barbellKg, repStats = emptyList()) }
    }

    fun setBarbellKg(kg: Float) = _uiState.update { it.copy(barbellKg = kg) }

    fun startSet() = _uiState.update { it.copy(isActive = true) }

    // ── Camera ───────────────────────────────────────────────────────────────

    fun bindCamera(context: Context, lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) {
        ProcessCameraProvider.getInstance(context).also { future ->
            future.addListener({
                cameraProvider = future.get()
                bindUseCases(context, lifecycleOwner, surfaceProvider)
            }, ContextCompat.getMainExecutor(context))
        }
    }

    private fun bindUseCases(context: Context, lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) {
        val provider = cameraProvider ?: return
        provider.unbindAll()
        val selector = CameraSelector.Builder().requireLensFacing(_uiState.value.cameraFacing).build()
        val preview  = Preview.Builder().build().also { it.setSurfaceProvider(surfaceProvider) }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build().also { it.setAnalyzer(cameraExecutor, ::analyzeImage) }
        try { provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis) } catch (_: Exception) {}
    }

    // ── Pose analysis ─────────────────────────────────────────────────────────

    @androidx.camera.core.ExperimentalGetImage
    private fun analyzeImage(proxy: ImageProxy) {
        val rot = proxy.imageInfo.rotationDegrees
        val uprightW = if (rot == 90 || rot == 270) proxy.height else proxy.width
        val uprightH = if (rot == 90 || rot == 270) proxy.width  else proxy.height
        _uiState.update { it.copy(imageWidth = uprightW, imageHeight = uprightH, rotationDegrees = rot) }

        try {
            poseDetector?.detectAsync(proxy, System.currentTimeMillis())
        } finally {
            proxy.close()
        }
    }

    /** Callback invoked by [MediaPipePoseDetector] on its worker thread. */
    private fun onPose(pose: PoseSnapshot) {
        val nowMs = System.currentTimeMillis()
        val result   = analyzer.analyze(pose, nowMs)
        val velocity = computeVelocity(pose, nowMs)
        val backScore = computeBackScore(pose)
        val barbellKg = _uiState.value.barbellKg
        val powerW    = if (velocity > 0f) barbellKg * 9.81f * velocity else 0f

        updateRepTracking(result, velocity, backScore, barbellKg, nowMs, pose)

        _uiState.update {
            it.copy(
                analysis         = result,
                currentPose      = pose,
                currentVelocity  = velocity,
                currentPowerW    = powerW,
                currentBackScore = backScore,
                repStats         = repStatsList.toList()
            )
        }
    }

    /**
     * Velocity from world Y (metres). MediaPipe world coordinates are
     * centred on the hip, with Y growing downward. Concentric (athlete
     * moving up) corresponds to negative ΔY → we negate.
     */
    private fun computeVelocity(pose: PoseSnapshot, nowMs: Long): Float {
        if (pose.vis(PoseSnapshot.LEFT_HIP) < 0.5f &&
            pose.vis(PoseSnapshot.RIGHT_HIP) < 0.5f) return 0f
        val hipY = if (pose.vis(PoseSnapshot.LEFT_HIP) >= pose.vis(PoseSnapshot.RIGHT_HIP))
            pose.wy(PoseSnapshot.LEFT_HIP) else pose.wy(PoseSnapshot.RIGHT_HIP)

        velocityBuffer.addLast(nowMs to hipY)
        if (velocityBuffer.size > VELOCITY_WINDOW) velocityBuffer.removeFirst()
        if (velocityBuffer.size < 3) return 0f

        val oldest = velocityBuffer.first()
        val newest = velocityBuffer.last()
        val dtSec  = (newest.first - oldest.first) / 1000f
        if (dtSec < 0.01f) return 0f
        val dyM = newest.second - oldest.second
        return -dyM / dtSec
    }

    /** Back score from 3D shoulder→hip→knee angle. 180° = perfect = 100. */
    private fun computeBackScore(pose: PoseSnapshot): Float {
        if (pose.vis(PoseSnapshot.LEFT_SHOULDER) < 0.5f ||
            pose.vis(PoseSnapshot.LEFT_HIP) < 0.5f ||
            pose.vis(PoseSnapshot.LEFT_KNEE) < 0.5f) return 100f
        val sx = pose.wx(PoseSnapshot.LEFT_HIP) - pose.wx(PoseSnapshot.LEFT_SHOULDER)
        val sy = pose.wy(PoseSnapshot.LEFT_HIP) - pose.wy(PoseSnapshot.LEFT_SHOULDER)
        val sz = pose.wz(PoseSnapshot.LEFT_HIP) - pose.wz(PoseSnapshot.LEFT_SHOULDER)
        val kx = pose.wx(PoseSnapshot.LEFT_KNEE) - pose.wx(PoseSnapshot.LEFT_HIP)
        val ky = pose.wy(PoseSnapshot.LEFT_KNEE) - pose.wy(PoseSnapshot.LEFT_HIP)
        val kz = pose.wz(PoseSnapshot.LEFT_KNEE) - pose.wz(PoseSnapshot.LEFT_HIP)
        val dot  = sx * kx + sy * ky + sz * kz
        val magS = sqrt(sx * sx + sy * sy + sz * sz)
        val magK = sqrt(kx * kx + ky * ky + kz * kz)
        if (magS < 1e-3f || magK < 1e-3f) return 100f
        val ratio = (dot / (magS * magK)).toDouble().coerceIn(-1.0, 1.0)
        val angleDeg = Math.toDegrees(acos(ratio)).toFloat()
        return (100f - (angleDeg.coerceIn(120f, 180f) - 120f) * (100f / 60f)).coerceIn(0f, 100f)
    }

    private fun updateRepTracking(
        result: AnalysisResult, velocity: Float, backScore: Float,
        barbellKg: Float, nowMs: Long, pose: PoseSnapshot
    ) {
        if (!_uiState.value.isActive) return

        if (result.repCount > lastRepCount) {
            if (lastRepCount > 0 && repStartTime > 0) {
                val concentricMs = nowMs - (if (eccentricEndTime > 0) eccentricEndTime else repStartTime)
                val rangeM = (hipYMax - hipYMin)
                repStatsList.add(RepVisualStat(
                    repNumber        = lastRepCount,
                    meanVelocity     = if (velocitySampleCount > 0) velocitySumForMean / velocitySampleCount else 0f,
                    peakVelocity     = repPeakVelocity,
                    rangeOfMotionCm  = (rangeM * 100f).coerceAtLeast(0f),
                    eccentricTimeMs  = if (eccentricEndTime > 0) eccentricEndTime - repStartTime else 0,
                    concentricTimeMs = concentricMs.coerceAtLeast(0),
                    peakPowerW       = repPeakVelocity * barbellKg * 9.81f,
                    backScore        = if (backScoreSamples > 0) backScoreSum / backScoreSamples else 100f
                ))
            }
            lastRepCount         = result.repCount
            repStartTime         = nowMs
            eccentricEndTime     = 0L
            repPeakVelocity      = 0f
            velocitySumForMean   = 0f
            velocitySampleCount  = 0
            hipYMin              = Float.MAX_VALUE
            hipYMax              = Float.MIN_VALUE
            backScoreSum         = 0f
            backScoreSamples     = 0
        }

        if (repStartTime > 0) {
            val absV = abs(velocity)
            if (absV > repPeakVelocity) repPeakVelocity = absV
            if (velocity > 0.05f) {
                velocitySumForMean += absV
                velocitySampleCount++
            }
            if (velocity < -0.05f && eccentricEndTime == 0L) eccentricEndTime = nowMs
            val hipY = pose.wy(PoseSnapshot.LEFT_HIP)
            if (hipY < hipYMin) hipYMin = hipY
            if (hipY > hipYMax) hipYMax = hipY
            backScoreSum    += backScore
            backScoreSamples++
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    fun flipCamera(context: Context, lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) {
        val newFacing = if (_uiState.value.cameraFacing == CameraSelector.LENS_FACING_BACK)
            CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        _uiState.update { it.copy(cameraFacing = newFacing) }
        bindUseCases(context, lifecycleOwner, surfaceProvider)
    }

    fun finishSet() {
        _uiState.update { it.copy(isActive = false, showPostSeries = true, repStats = repStatsList.toList()) }
    }

    fun dismissPostSeries() = _uiState.update { it.copy(showPostSeries = false) }
    fun dismissSaveDialog() = _uiState.update { it.copy(savedVideoUri = null) }

    fun resetAnalyzer() {
        analyzer.reset()
        repStatsList.clear()
        lastRepCount = 0; repStartTime = 0L; eccentricEndTime = 0L
        repPeakVelocity = 0f; velocitySumForMean = 0f; velocitySampleCount = 0
        hipYMin = Float.MAX_VALUE; hipYMax = Float.MIN_VALUE
        backScoreSum = 0f; backScoreSamples = 0
        velocityBuffer.clear()
        _uiState.update { it.copy(analysis = AnalysisResult(), currentPose = null,
            repStats = emptyList(), isActive = false, showPostSeries = false) }
    }

    override fun onCleared() {
        super.onCleared()
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        poseDetector?.close()
        ScreenRecordService.onSaved = null
    }
}
