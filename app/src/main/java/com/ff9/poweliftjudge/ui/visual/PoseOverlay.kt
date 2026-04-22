package com.ff9.poweliftjudge.ui.visual

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark

// ── Visual constants ────────────────────────────────────────────────────────

private val COLOR_GOOD   = Color(0xFF00E676)
private val COLOR_WARN   = Color(0xFFFFD600)
private val COLOR_BAD    = Color(0xFFFF5252)
private val COLOR_NEUTRAL = Color(0xFF80D8FF)
private val COLOR_JOINT  = Color.White
private const val LINE_STROKE = 7f
private const val JOINT_RADIUS = 11f
private const val CONFIDENCE = 0.30f   // low threshold: lateral profile can drop some landmarks

// ── Skeleton connections ─────────────────────────────────────────────────────

private val SKELETON = listOf(
    // Spine / torso
    PoseLandmark.LEFT_SHOULDER  to PoseLandmark.RIGHT_SHOULDER,
    PoseLandmark.LEFT_HIP       to PoseLandmark.RIGHT_HIP,
    PoseLandmark.LEFT_SHOULDER  to PoseLandmark.LEFT_HIP,
    PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_HIP,
    // Left arm
    PoseLandmark.LEFT_SHOULDER  to PoseLandmark.LEFT_ELBOW,
    PoseLandmark.LEFT_ELBOW     to PoseLandmark.LEFT_WRIST,
    // Right arm
    PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_ELBOW,
    PoseLandmark.RIGHT_ELBOW    to PoseLandmark.RIGHT_WRIST,
    // Left leg
    PoseLandmark.LEFT_HIP       to PoseLandmark.LEFT_KNEE,
    PoseLandmark.LEFT_KNEE      to PoseLandmark.LEFT_ANKLE,
    PoseLandmark.LEFT_ANKLE     to PoseLandmark.LEFT_HEEL,
    PoseLandmark.LEFT_HEEL      to PoseLandmark.LEFT_FOOT_INDEX,
    // Right leg
    PoseLandmark.RIGHT_HIP      to PoseLandmark.RIGHT_KNEE,
    PoseLandmark.RIGHT_KNEE     to PoseLandmark.RIGHT_ANKLE,
    PoseLandmark.RIGHT_ANKLE    to PoseLandmark.RIGHT_HEEL,
    PoseLandmark.RIGHT_HEEL     to PoseLandmark.RIGHT_FOOT_INDEX,
)

private val KEY_JOINTS = listOf(
    PoseLandmark.LEFT_HIP,       PoseLandmark.RIGHT_HIP,
    PoseLandmark.LEFT_KNEE,      PoseLandmark.RIGHT_KNEE,
    PoseLandmark.LEFT_ANKLE,     PoseLandmark.RIGHT_ANKLE,
    PoseLandmark.LEFT_SHOULDER,  PoseLandmark.RIGHT_SHOULDER,
    PoseLandmark.LEFT_ELBOW,     PoseLandmark.RIGHT_ELBOW,
    PoseLandmark.LEFT_WRIST,     PoseLandmark.RIGHT_WRIST,
)

// ── Composable ───────────────────────────────────────────────────────────────

@Composable
fun PoseOverlay(
    pose: Pose?,
    imageWidth: Int,
    imageHeight: Int,
    rotationDegrees: Int,
    isFrontCamera: Boolean,
    analysis: AnalysisResult,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (pose == null) return@Canvas

        // ── Step 1: logical image size after rotation ────────────────────────
        // ImageAnalysis delivers the raw camera frame (landscape for most phones).
        // rotationDegrees tells us how many degrees CW to rotate it to appear upright.
        val logW = if (rotationDegrees == 90 || rotationDegrees == 270)
            imageHeight.toFloat() else imageWidth.toFloat()
        val logH = if (rotationDegrees == 90 || rotationDegrees == 270)
            imageWidth.toFloat() else imageHeight.toFloat()

        // ── Step 2: FIT_CENTER scale + letterbox offset ──────────────────────
        // PreviewView is set to FIT_CENTER: entire image fits on screen with
        // black bars on sides OR top/bottom — no cropping, landmarks stay aligned.
        //   scale = min(screenW/logW, screenH/logH)
        val scaleToFit = minOf(size.width / logW, size.height / logH)
        val renderedW  = logW * scaleToFit
        val renderedH  = logH * scaleToFit
        val offsetX    = (size.width  - renderedW) / 2f
        val offsetY    = (size.height - renderedH) / 2f

        // ── Step 3: mapping function ─────────────────────────────────────────
        fun toScreen(lm: PoseLandmark): Offset? {
            if (lm.inFrameLikelihood < CONFIDENCE) return null

            // Normalize landmark to [0,1] in raw image space
            val nx = lm.position.x / imageWidth.toFloat()
            val ny = lm.position.y / imageHeight.toFloat()

            // Rotate normalized coords to match display orientation
            val (rnx, rny) = when (rotationDegrees) {
                90  -> (1f - ny) to nx         // 90° CW
                180 -> (1f - nx) to (1f - ny)  // 180°
                270 -> ny        to (1f - nx)  // 90° CCW
                else -> nx       to ny          // 0° (landscape)
            }

            // Mirror X for front camera (front camera is already mirrored by Android,
            // but landmarks are reported in the pre-mirror space)
            val mnx = if (isFrontCamera) 1f - rnx else rnx

            // Map to screen using FILL_CENTER rendered dimensions + offset
            return Offset(
                x = mnx * renderedW + offsetX,
                y = rny * renderedH + offsetY
            )
        }

        // ── Step 4: line color based on lift phase ───────────────────────────
        val lineColor = when {
            analysis.isGoodLift -> COLOR_GOOD
            analysis.depthOk    -> COLOR_WARN
            else                -> COLOR_NEUTRAL
        }

        // ── Step 5: draw skeleton lines ──────────────────────────────────────
        for ((aId, bId) in SKELETON) {
            val a = pose.getPoseLandmark(aId)?.let { toScreen(it) } ?: continue
            val b = pose.getPoseLandmark(bId)?.let { toScreen(it) } ?: continue
            drawLine(lineColor, a, b, strokeWidth = LINE_STROKE, cap = StrokeCap.Round)
        }

        // ── Step 6: draw joint circles ───────────────────────────────────────
        for (id in KEY_JOINTS) {
            val lm = pose.getPoseLandmark(id) ?: continue
            val pt = toScreen(lm) ?: continue
            drawCircle(lineColor,    radius = JOINT_RADIUS,        center = pt)
            drawCircle(COLOR_JOINT,  radius = JOINT_RADIUS / 3f,   center = pt)
        }

        // ── Step 7: horizontal depth reference line at knee level ─────────────
        val lKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val rKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
        val kneeY: Float? = listOfNotNull(
            lKnee?.let { toScreen(it) }?.y,
            rKnee?.let { toScreen(it) }?.y
        ).let { ys -> if (ys.isEmpty()) null else ys.average().toFloat() }

        if (kneeY != null) {
            val depthColor = if (analysis.depthOk) COLOR_GOOD else COLOR_BAD
            drawLine(
                color       = depthColor,
                start       = Offset(0f, kneeY),
                end         = Offset(size.width, kneeY),
                strokeWidth = 3f,
                alpha       = 0.75f
            )
            // Small dashed label area (just a solid bar, clean look)
            drawRect(
                color   = depthColor.copy(alpha = 0.15f),
                topLeft = Offset(0f, kneeY),
                size    = Size(size.width, 2f)
            )
        }
    }
}
