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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.sqrt

// ── Per-rep statistics ──────────────────────────────────────────────────────

data class RepVisualStat(
    val repNumber: Int,
    val meanVelocity: Float,     // m/s
    val peakVelocity: Float,     // m/s
    val rangeOfMotionCm: Float,  // cm – hip vertical displacement
    val eccentricTimeMs: Long,
    val concentricTimeMs: Long,
    val peakPowerW: Float,       // W = force × velocity
    val backScore: Float         // 0-100, spine alignment
)

// ── UI State ────────────────────────────────────────────────────────────────

data class VisualJudgeUiState(
    val liftName: String = "",
    val analysis: AnalysisResult = AnalysisResult(),
    val currentPose: Pose? = null,
    // Live velocity
    val currentVelocity: Float = 0f,   // m/s (positive = concentric)
    val currentPowerW: Float = 0f,
    val currentBackScore: Float = 100f,
    // Session
    val barbellKg: Float = 20f,
    val isActive: Boolean = false,
    val isRecording: Boolean = false,
    val savedVideoUri: Uri? = null,
    val repStats: List<RepVisualStat> = emptyList(),
    val showPostSeries: Boolean = false,
    // Camera
    val cameraFacing: Int = CameraSelector.LENS_FACING_BACK,
    val imageWidth: Int = 1,
    val imageHeight: Int = 1,
    val rotationDegrees: Int = 90
)

// ── ViewModel ───────────────────────────────────────────────────────────────

class VisualJudgeViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(VisualJudgeUiState())
    val uiState: StateFlow<VisualJudgeUiState> = _uiState.asStateFlow()

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val poseDetector: PoseDetector = PoseDetection.getClient(
        AccuratePoseDetectorOptions.Builder()
            .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
            .build()
    )

    private lateinit var analyzer: LiftAnalyzer
    private var cameraProvider: ProcessCameraProvider? = null

    // ── Velocity tracking ───────────────────────────────────────────────────
    // Ring buffer: (timestamp_ms, hipY_px)
    private val velocityBuffer = ArrayDeque<Pair<Long, Float>>(10)
    private val VELOCITY_WINDOW = 6          // frames
    private var bodyHeightPx = 400f          // updated from pose when athlete stands
    private val ASSUMED_HEIGHT_M = 1.75f     // used to convert px→m

    // Per-rep tracking
    private var repStartTime = 0L
    private var eccentricEndTime = 0L
    private var repPeakVelocity = 0f
    private var velocitySumForMean = 0f
    private var velocitySampleCount = 0
    private var hipYMin = Float.MAX_VALUE    // topmost position (concentric peak)
    private var hipYMax = Float.MIN_VALUE    // lowest position (bottom of rep)
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
        val mediaImage = proxy.image ?: run { proxy.close(); return }
        val image    = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
        val w        = proxy.width
        val h        = proxy.height
        val rotation = proxy.imageInfo.rotationDegrees
        val nowMs    = System.currentTimeMillis()

        poseDetector.process(image)
            .addOnSuccessListener { pose ->
                val result     = analyzer.analyze(pose, nowMs)
                val velocity   = computeVelocity(pose, nowMs, w, h, rotation)
                val backScore  = computeBackScore(pose)
                val barbellKg  = _uiState.value.barbellKg
                val powerW     = if (velocity > 0f) barbellKg * 9.81f * velocity else 0f

                updateRepTracking(result, velocity, backScore, barbellKg, nowMs)

                _uiState.update {
                    it.copy(
                        analysis        = result,
                        currentPose     = pose,
                        currentVelocity = velocity,
                        currentPowerW   = powerW,
                        currentBackScore = backScore,
                        imageWidth      = w,
                        imageHeight     = h,
                        rotationDegrees = rotation,
                        repStats        = repStatsList.toList()
                    )
                }
            }
            .addOnCompleteListener { proxy.close() }
    }

    // Velocity from hip Y tracking
    private fun computeVelocity(pose: Pose, nowMs: Long, imgW: Int, imgH: Int, rotation: Int): Float {
        val lHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val lAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val lShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)

        val hip = when {
            lHip != null && (lHip.inFrameLikelihood) > 0.4f -> lHip
            rHip != null && (rHip.inFrameLikelihood) > 0.4f -> rHip
            else -> return 0f
        }

        // Calibrate body height px when athlete is roughly upright
        if (lShoulder != null && lAnkle != null &&
            lShoulder.inFrameLikelihood > 0.4f && lAnkle.inFrameLikelihood > 0.4f) {
            val hPx = abs(lAnkle.position.y - lShoulder.position.y)
            if (hPx > 50f) bodyHeightPx = hPx * 0.85f  // shoulder-to-ankle ≈ 85% of full height
        }

        val hipY = hip.position.y  // in image px

        velocityBuffer.addLast(nowMs to hipY)
        if (velocityBuffer.size > VELOCITY_WINDOW) velocityBuffer.removeFirst()
        if (velocityBuffer.size < 3) return 0f

        val oldest = velocityBuffer.first()
        val newest = velocityBuffer.last()
        val dtSec  = (newest.first - oldest.first) / 1000f
        if (dtSec < 0.01f) return 0f

        // dy in image px → positive = downward in image; concentric = moving up = negative dy
        val dyPx = newest.second - oldest.second
        val dyM  = (dyPx / bodyHeightPx) * ASSUMED_HEIGHT_M
        // Concentric is negative dy (hip moving up) → invert so concentric = positive
        return -dyM / dtSec
    }

    // Back spine score: angle between (shoulder→hip) and (hip→knee) should be close to 180°
    private fun computeBackScore(pose: Pose): Float {
        val lShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val lHip      = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val lKnee     = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        if (lShoulder == null || lHip == null || lKnee == null) return 100f
        if (listOf(lShoulder, lHip, lKnee).any { it.inFrameLikelihood < 0.4f }) return 100f

        // Vector shoulder→hip and hip→knee
        val sx = lHip.position.x - lShoulder.position.x
        val sy = lHip.position.y - lShoulder.position.y
        val kx = lKnee.position.x - lHip.position.x
        val ky = lKnee.position.y - lHip.position.y

        val dot  = sx * kx + sy * ky
        val magS = sqrt(sx * sx + sy * sy)
        val magK = sqrt(kx * kx + ky * ky)
        if (magS < 1f || magK < 1f) return 100f

        val ratio = (dot / (magS * magK)).toDouble().coerceIn(-1.0, 1.0)
        val angleDeg = Math.toDegrees(Math.acos(ratio)).toFloat()
        // 180° = perfectly straight → score 100. 0° deviation → 100, max deviation 60° → 0
        return (100f - (angleDeg.coerceIn(120f, 180f) - 120f) * (100f / 60f)).coerceIn(0f, 100f)
    }

    // Track per-rep stats
    private fun updateRepTracking(
        result: AnalysisResult, velocity: Float, backScore: Float, barbellKg: Float, nowMs: Long
    ) {
        if (!_uiState.value.isActive) return

        // New rep started
        if (result.repCount > lastRepCount) {
            // Save stats for completed rep
            if (lastRepCount > 0 && repStartTime > 0) {
                val concentricMs = nowMs - (if (eccentricEndTime > 0) eccentricEndTime else repStartTime)
                val rangeM = (hipYMax - hipYMin) / bodyHeightPx * ASSUMED_HEIGHT_M
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
            // Reset for next rep
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

        // Accumulate within rep
        if (repStartTime > 0) {
            val absV = abs(velocity)
            if (absV > repPeakVelocity) repPeakVelocity = absV
            if (velocity > 0.05f) { // concentric
                velocitySumForMean  += absV
                velocitySampleCount++
            }
            if (velocity < -0.05f && eccentricEndTime == 0L) {
                eccentricEndTime = nowMs  // transition point
            }
            val hipY = _uiState.value.currentPose
                ?.getPoseLandmark(PoseLandmark.LEFT_HIP)?.position?.y ?: return
            if (hipY < hipYMin) hipYMin = hipY
            if (hipY > hipYMax) hipYMax = hipY
            backScoreSum    += backScore
            backScoreSamples++
        }
    }

    // Recording state is driven externally by ScreenRecordService via callbacks
    // (onScreenRecordingStarted / onScreenRecordingSaved)

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
        lastRepCount    = 0; repStartTime = 0L; eccentricEndTime = 0L
        repPeakVelocity = 0f; velocitySumForMean = 0f; velocitySampleCount = 0
        hipYMin = Float.MAX_VALUE; hipYMax = Float.MIN_VALUE
        backScoreSum = 0f; backScoreSamples = 0
        velocityBuffer.clear()
        _uiState.update { it.copy(analysis = AnalysisResult(), currentPose = null, repStats = emptyList(), isActive = false, showPostSeries = false) }
    }

    override fun onCleared() {
        super.onCleared()
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        poseDetector.close()
        ScreenRecordService.onSaved = null
    }
}
