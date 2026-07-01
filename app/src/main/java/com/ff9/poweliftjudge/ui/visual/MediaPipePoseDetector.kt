package com.ff9.poweliftjudge.ui.visual

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

/**
 * MediaPipe Pose Landmarker wrapper.
 *
 *  - Tries GPU delegate first; on failure (common in Android emulators
 *    without proper GLES support, or in headless test envs) falls back
 *    to CPU. If both fail, the detector becomes a silent no-op so the
 *    surrounding screen does not crash.
 *  - LIVE_STREAM mode → results delivered via [onPose] callback.
 *  - Converts ImageProxy → rotated upright Bitmap before submitting.
 */
class MediaPipePoseDetector(
    private val context: Context,
    private val onPose: (PoseSnapshot) -> Unit
) {
    private var landmarker: PoseLandmarker? = null

    /** True if either GPU or CPU init succeeded; false means detector is a no-op. */
    val isReady: Boolean get() = landmarker != null

    /**
     * Set when GPU init fails and we fall back to CPU. Useful if the screen
     * wants to surface a "running on CPU — slower" hint.
     */
    var isCpuFallback: Boolean = false
        private set

    init {
        landmarker = tryCreate(Delegate.GPU)?.also { Log.i(TAG, "PoseLandmarker on GPU") }
            ?: tryCreate(Delegate.CPU)?.also {
                isCpuFallback = true
                Log.w(TAG, "PoseLandmarker fell back to CPU")
            }
        if (landmarker == null) {
            Log.e(TAG, "PoseLandmarker init failed on both GPU and CPU — pose disabled")
        }
    }

    private fun tryCreate(delegate: Delegate): PoseLandmarker? = try {
        val base = BaseOptions.builder()
            .setModelAssetPath(MODEL_ASSET)
            .setDelegate(delegate)
            .build()
        val opts = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumPoses(1)
            .setMinPoseDetectionConfidence(0.5f)
            .setMinPosePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setOutputSegmentationMasks(false)
            .setResultListener { result, _ -> handleResult(result) }
            .setErrorListener { e -> Log.w(TAG, "PoseLandmarker runtime error", e) }
            .build()
        PoseLandmarker.createFromOptions(context, opts)
    } catch (t: Throwable) {
        Log.w(TAG, "PoseLandmarker create failed (delegate=$delegate): ${t.message}")
        null
    }

    /** Submit a frame for async detection. Silently drops frames if the detector is unavailable. */
    fun detectAsync(proxy: ImageProxy, timestampMs: Long) {
        val det = landmarker ?: return
        try {
            val bitmap = proxy.toUprightBitmap() ?: return
            val mpImage = BitmapImageBuilder(bitmap).build()
            det.detectAsync(mpImage, timestampMs)
        } catch (_: Throwable) {
            // detector busy / model still warming up
        }
    }

    fun close() {
        try { landmarker?.close() } catch (_: Throwable) {}
        landmarker = null
    }

    // ── Result conversion ─────────────────────────────────────────────────────

    private fun handleResult(result: PoseLandmarkerResult) {
        val landmarks = result.landmarks().firstOrNull() ?: return
        val world     = result.worldLandmarks().firstOrNull()

        val n = PoseSnapshot.LANDMARK_COUNT
        if (landmarks.size < n) return

        val nrm = FloatArray(n * 2)
        val wld = FloatArray(n * 3)
        val vis = FloatArray(n)

        for (i in 0 until n) {
            val lm = landmarks[i]
            nrm[i * 2]     = lm.x()
            nrm[i * 2 + 1] = lm.y()
            vis[i] = lm.visibility().orElse(0f)
        }
        if (world != null && world.size >= n) {
            for (i in 0 until n) {
                val w = world[i]
                wld[i * 3]     = w.x()
                wld[i * 3 + 1] = w.y()
                wld[i * 3 + 2] = w.z()
            }
        }

        onPose(
            PoseSnapshot(
                normalized  = nrm,
                world       = wld,
                visibility  = vis,
                timestampMs = result.timestampMs()
            )
        )
    }

    // ── ImageProxy → upright Bitmap ───────────────────────────────────────────

    private fun ImageProxy.toUprightBitmap(): Bitmap? {
        val src = ImageProxyConverter.toBitmap(this) ?: return null
        val rot = imageInfo.rotationDegrees
        if (rot == 0) return src
        val m = Matrix().apply { postRotate(rot.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    companion object {
        private const val TAG = "PoseDetector"
        private const val MODEL_ASSET = "pose_landmarker_heavy.task"
    }
}
