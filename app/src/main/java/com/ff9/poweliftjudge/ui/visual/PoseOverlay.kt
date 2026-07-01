package com.ff9.poweliftjudge.ui.visual

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap

// ── Visual constants ────────────────────────────────────────────────────────

private val COLOR_GOOD    = Color(0xFF00E676)
private val COLOR_WARN    = Color(0xFFFFD600)
private val COLOR_BAD     = Color(0xFFFF5252)
private val COLOR_NEUTRAL = Color(0xFF80D8FF)
private val COLOR_JOINT   = Color.White
private const val LINE_STROKE  = 7f
private const val JOINT_RADIUS = 11f
private const val VIS_THRESHOLD = 0.45f

// ── Composable ───────────────────────────────────────────────────────────────

/**
 * Draws the pose skeleton on top of a FIT_CENTER PreviewView.
 *
 * The [PoseSnapshot] is already in display orientation, so the only mapping
 * needed is the FIT_CENTER scale + letterbox offset that matches PreviewView.
 *
 * The image was rotated upright before being submitted to MediaPipe, so:
 *  - landmarks' (x, y) are normalized in the upright image's [0,1] space.
 *  - upright image aspect ratio = (after-rotation) imageW / imageH.
 */
@Composable
fun PoseOverlay(
    pose: PoseSnapshot?,
    imageWidth: Int,         // upright (after-rotation) width
    imageHeight: Int,        // upright (after-rotation) height
    isFrontCamera: Boolean,
    analysis: AnalysisResult,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (pose == null || imageWidth <= 0 || imageHeight <= 0) return@Canvas

        val logW = imageWidth.toFloat()
        val logH = imageHeight.toFloat()

        // FIT_CENTER: entire image fits on screen with letterbox bars.
        val scale = minOf(size.width / logW, size.height / logH)
        val renderedW = logW * scale
        val renderedH = logH * scale
        val offsetX   = (size.width  - renderedW) / 2f
        val offsetY   = (size.height - renderedH) / 2f

        fun toScreen(idx: Int): Offset? {
            if (pose.vis(idx) < VIS_THRESHOLD) return null
            val nx = pose.nx(idx)
            val ny = pose.ny(idx)
            // Front camera: the previewer mirrors the image visually,
            // landmarks come in pre-mirror coordinates → flip X here.
            val mx = if (isFrontCamera) 1f - nx else nx
            return Offset(mx * renderedW + offsetX, ny * renderedH + offsetY)
        }

        val lineColor = when {
            analysis.isGoodLift -> COLOR_GOOD
            analysis.depthOk    -> COLOR_WARN
            else                -> COLOR_NEUTRAL
        }

        // Skeleton
        for (edge in PoseSnapshot.SKELETON_EDGES) {
            val a = toScreen(edge[0]) ?: continue
            val b = toScreen(edge[1]) ?: continue
            drawLine(lineColor, a, b, strokeWidth = LINE_STROKE, cap = StrokeCap.Round)
        }

        // Key joints
        for (idx in PoseSnapshot.KEY_JOINTS) {
            val pt = toScreen(idx) ?: continue
            drawCircle(lineColor, radius = JOINT_RADIUS, center = pt)
            drawCircle(COLOR_JOINT, radius = JOINT_RADIUS / 3f, center = pt)
        }

        // Knee depth reference line
        val lKnee = toScreen(PoseSnapshot.LEFT_KNEE)?.y
        val rKnee = toScreen(PoseSnapshot.RIGHT_KNEE)?.y
        val kneeY = listOfNotNull(lKnee, rKnee).let {
            if (it.isEmpty()) null else it.average().toFloat()
        }
        if (kneeY != null) {
            val depthColor = if (analysis.depthOk) COLOR_GOOD else COLOR_BAD
            drawLine(depthColor,
                start = Offset(0f, kneeY), end = Offset(size.width, kneeY),
                strokeWidth = 3f, alpha = 0.75f)
            drawRect(depthColor.copy(alpha = 0.15f),
                topLeft = Offset(0f, kneeY), size = Size(size.width, 2f))
        }
    }
}
