package com.ff9.poweliftjudge.ui.visual

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

// ── Phase shared across analyzers ──────────────────────────────────────────

enum class LiftAnalysisPhase {
    IDLE,
    DESCENDING,
    AT_BOTTOM,
    ASCENDING,
    LOCKOUT,
    COMPLETED
}

data class AnalysisResult(
    val phase: LiftAnalysisPhase = LiftAnalysisPhase.IDLE,
    val repCount: Int = 0,
    val isGoodLift: Boolean = false,
    val feedback: String = "",
    val hipAngle: Float = 0f,
    val kneeAngle: Float = 0f,
    val ankleAngle: Float = 0f,
    val depthOk: Boolean = false,
    val lockoutOk: Boolean = false,
    val holdProgress: Float = 0f,
    val symmetry: Float = 100f
)

interface LiftAnalyzer {
    fun analyze(pose: PoseSnapshot, nowMs: Long): AnalysisResult
    fun reset()
}

// ── Shared timing/threshold constants ───────────────────────────────────────

private const val MIN_PHASE_MS = 250L
private const val MIN_REP_MS   = 700L
private const val VIS_OK       = 0.5f

// ── Geometry helpers (3D world coordinates) ─────────────────────────────────

/** Angle ABC in degrees, computed in 3D world space (metres). */
private fun angle3D(pose: PoseSnapshot, a: Int, b: Int, c: Int): Float {
    val ax = pose.wx(a) - pose.wx(b); val ay = pose.wy(a) - pose.wy(b); val az = pose.wz(a) - pose.wz(b)
    val cx = pose.wx(c) - pose.wx(b); val cy = pose.wy(c) - pose.wy(b); val cz = pose.wz(c) - pose.wz(b)
    val dot  = ax * cx + ay * cy + az * cz
    val magA = sqrt(ax * ax + ay * ay + az * az)
    val magC = sqrt(cx * cx + cy * cy + cz * cz)
    if (magA < 1e-4f || magC < 1e-4f) return 0f
    val ratio = (dot / (magA * magC)).toDouble().coerceIn(-1.0, 1.0)
    return Math.toDegrees(acos(ratio)).toFloat()
}

private fun visible(pose: PoseSnapshot, vararg idx: Int) = idx.all { pose.vis(it) >= VIS_OK }

private fun symmetryY(pose: PoseSnapshot, leftIdx: Int, rightIdx: Int, scaleIdxA: Int, scaleIdxB: Int): Float {
    val scale = abs(pose.ny(scaleIdxA) - pose.ny(scaleIdxB))
    if (scale < 0.001f) return 100f
    val diff = abs(pose.ny(leftIdx) - pose.ny(rightIdx))
    return (100f - (diff / scale * 100f)).coerceIn(0f, 100f)
}

/** Exponential smoothing for a single scalar (taming MediaPipe per-frame jitter). */
private class Smoothed(private val alpha: Float = 0.4f) {
    private var v = Float.NaN
    fun update(x: Float): Float { v = if (v.isNaN()) x else v + alpha * (x - v); return v }
    fun reset() { v = Float.NaN }
}

// ── SQUAT ───────────────────────────────────────────────────────────────────
// IPF: hip crease below top of knee, controlled ascent to full lockout.

class SquatAnalyzer : LiftAnalyzer {
    private var phase = LiftAnalysisPhase.IDLE
    private var repCount = 0
    private var phaseStartMs = 0L
    private var repStartMs = 0L
    private var depthReached = false
    private var minKneeAngleInRep = 180f
    private val kneeS = Smoothed()
    private val hipS  = Smoothed()

    private val DESCEND_START = 150f
    private val LOCKOUT       = 165f
    private val RE_ARM        = 170f
    private val ASCEND_DELTA  = 8f

    override fun analyze(pose: PoseSnapshot, nowMs: Long): AnalysisResult {
        val needed = intArrayOf(
            PoseSnapshot.LEFT_HIP, PoseSnapshot.LEFT_KNEE, PoseSnapshot.LEFT_ANKLE
        )
        if (!visible(pose, *needed)) {
            return AnalysisResult(phase = phase, repCount = repCount,
                feedback = "Inquadrami di lato", depthOk = depthReached)
        }

        val rawKnee = angle3D(pose,
            PoseSnapshot.LEFT_HIP, PoseSnapshot.LEFT_KNEE, PoseSnapshot.LEFT_ANKLE)
        val rawHip  = angle3D(pose,
            PoseSnapshot.LEFT_SHOULDER, PoseSnapshot.LEFT_HIP, PoseSnapshot.LEFT_KNEE)
        val kneeAngle = kneeS.update(rawKnee)
        val hipAngle  = hipS.update(rawHip)

        // IPF depth check: in normalized image space, larger Y = lower on screen.
        // We use the *world Y* which grows downward as well in MediaPipe convention.
        val depth = pose.wy(PoseSnapshot.LEFT_HIP) > pose.wy(PoseSnapshot.LEFT_KNEE)
        if (phase == LiftAnalysisPhase.DESCENDING && depth) depthReached = true
        if (phase == LiftAnalysisPhase.DESCENDING && kneeAngle < minKneeAngleInRep) {
            minKneeAngleInRep = kneeAngle
        }

        val lockout = kneeAngle > LOCKOUT
        val phaseTime = nowMs - phaseStartMs
        val symm = symmetryY(pose,
            PoseSnapshot.LEFT_HIP, PoseSnapshot.RIGHT_HIP,
            PoseSnapshot.LEFT_KNEE, PoseSnapshot.LEFT_ANKLE)

        var feedback = ""
        when (phase) {
            LiftAnalysisPhase.IDLE -> {
                feedback = "Pronto"
                if (kneeAngle < DESCEND_START) {
                    repStartMs = nowMs
                    minKneeAngleInRep = kneeAngle
                    depthReached = depth
                    phase = LiftAnalysisPhase.DESCENDING; phaseStartMs = nowMs
                }
            }
            LiftAnalysisPhase.DESCENDING -> {
                feedback = if (depthReached) "Profondità OK" else "Scendi di più"
                if (phaseTime >= MIN_PHASE_MS &&
                    kneeAngle > minKneeAngleInRep + ASCEND_DELTA) {
                    phase = LiftAnalysisPhase.ASCENDING; phaseStartMs = nowMs
                }
            }
            LiftAnalysisPhase.ASCENDING -> {
                feedback = if (depthReached) "Spingi" else "Poco profondo"
                if (lockout && (nowMs - repStartMs) >= MIN_REP_MS) {
                    if (depthReached) { repCount++; feedback = "GOOD LIFT" }
                    else feedback = "NO LIFT - poco profondo"
                    phase = LiftAnalysisPhase.COMPLETED; phaseStartMs = nowMs
                } else if (kneeAngle < minKneeAngleInRep + ASCEND_DELTA / 2f) {
                    phase = LiftAnalysisPhase.DESCENDING; phaseStartMs = nowMs
                }
            }
            LiftAnalysisPhase.COMPLETED -> {
                feedback = "GOOD LIFT"
                if (kneeAngle > RE_ARM && phaseTime >= MIN_PHASE_MS) {
                    depthReached = false
                    minKneeAngleInRep = 180f
                    phase = LiftAnalysisPhase.IDLE; phaseStartMs = nowMs
                }
            }
            else -> {}
        }

        return AnalysisResult(
            phase = phase, repCount = repCount,
            isGoodLift = phase == LiftAnalysisPhase.COMPLETED,
            feedback = feedback,
            hipAngle = hipAngle, kneeAngle = kneeAngle,
            depthOk = depthReached, lockoutOk = lockout,
            symmetry = symm
        )
    }

    override fun reset() {
        phase = LiftAnalysisPhase.IDLE; repCount = 0
        phaseStartMs = 0L; repStartMs = 0L
        depthReached = false; minKneeAngleInRep = 180f
        kneeS.reset(); hipS.reset()
    }
}

// ── BENCH PRESS ─────────────────────────────────────────────────────────────

class BenchAnalyzer : LiftAnalyzer {
    private var phase = LiftAnalysisPhase.IDLE
    private var repCount = 0
    private var phaseStartMs = 0L
    private var repStartMs = 0L
    private var pauseStartMs = 0L
    private var minElbowInRep = 180f
    private val elbowS = Smoothed()

    private val PAUSE_MS      = 1000L
    private val DESCEND_START = 150f
    private val TOUCH_ANGLE   = 95f
    private val LOCKOUT       = 160f
    private val RE_ARM        = 168f
    private val ASCEND_DELTA  = 6f

    override fun analyze(pose: PoseSnapshot, nowMs: Long): AnalysisResult {
        val (sIdx, eIdx, wIdx) = pickSide(pose)
            ?: return AnalysisResult(phase = phase, repCount = repCount, feedback = "Inquadrami")

        val rawElbow = angle3D(pose, sIdx, eIdx, wIdx)
        val elbowAngle = elbowS.update(rawElbow)

        if ((phase == LiftAnalysisPhase.DESCENDING || phase == LiftAnalysisPhase.AT_BOTTOM)
            && elbowAngle < minElbowInRep) minElbowInRep = elbowAngle

        val touchChest = elbowAngle < TOUCH_ANGLE
        val lockout    = elbowAngle > LOCKOUT
        val phaseTime  = nowMs - phaseStartMs
        val symm = symmetryY(pose,
            PoseSnapshot.LEFT_ELBOW, PoseSnapshot.RIGHT_ELBOW,
            PoseSnapshot.LEFT_SHOULDER, PoseSnapshot.LEFT_ELBOW)

        var feedback = ""; var holdProgress = 0f
        when (phase) {
            LiftAnalysisPhase.IDLE -> {
                feedback = "Abbassa"
                if (elbowAngle < DESCEND_START) {
                    repStartMs = nowMs; minElbowInRep = elbowAngle
                    phase = LiftAnalysisPhase.DESCENDING; phaseStartMs = nowMs
                }
            }
            LiftAnalysisPhase.DESCENDING -> {
                feedback = if (touchChest) "Fermo!" else "Tocca il petto"
                if (touchChest && phaseTime >= MIN_PHASE_MS) {
                    phase = LiftAnalysisPhase.AT_BOTTOM
                    phaseStartMs = nowMs; pauseStartMs = nowMs
                }
            }
            LiftAnalysisPhase.AT_BOTTOM -> {
                val elapsed = nowMs - pauseStartMs
                holdProgress = (elapsed.toFloat() / PAUSE_MS).coerceIn(0f, 1f)
                feedback = if (holdProgress < 1f)
                    "Fermo ${((PAUSE_MS - elapsed) / 1000f + 0.5f).toInt()}s" else "PRESS!"
                if (elapsed >= PAUSE_MS && elbowAngle > minElbowInRep + ASCEND_DELTA) {
                    phase = LiftAnalysisPhase.ASCENDING; phaseStartMs = nowMs
                } else if (elapsed < PAUSE_MS && elbowAngle > minElbowInRep + ASCEND_DELTA * 2f) {
                    phase = LiftAnalysisPhase.DESCENDING; phaseStartMs = nowMs
                }
            }
            LiftAnalysisPhase.ASCENDING -> {
                feedback = "Distendi"; holdProgress = 1f
                if (lockout && (nowMs - repStartMs) >= MIN_REP_MS) {
                    repCount++
                    phase = LiftAnalysisPhase.COMPLETED; phaseStartMs = nowMs
                }
            }
            LiftAnalysisPhase.COMPLETED -> {
                feedback = "GOOD LIFT"; holdProgress = 1f
                if (elbowAngle > RE_ARM && phaseTime >= MIN_PHASE_MS) {
                    minElbowInRep = 180f
                    phase = LiftAnalysisPhase.IDLE; phaseStartMs = nowMs
                }
            }
            else -> {}
        }

        return AnalysisResult(
            phase = phase, repCount = repCount,
            isGoodLift = phase == LiftAnalysisPhase.COMPLETED,
            feedback = feedback,
            kneeAngle = elbowAngle,   // reused for elbow display
            lockoutOk = lockout,
            holdProgress = holdProgress,
            symmetry = symm
        )
    }

    private fun pickSide(pose: PoseSnapshot): Triple<Int, Int, Int>? {
        val leftScore  = pose.vis(PoseSnapshot.LEFT_SHOULDER) +
                         pose.vis(PoseSnapshot.LEFT_ELBOW) +
                         pose.vis(PoseSnapshot.LEFT_WRIST)
        val rightScore = pose.vis(PoseSnapshot.RIGHT_SHOULDER) +
                         pose.vis(PoseSnapshot.RIGHT_ELBOW) +
                         pose.vis(PoseSnapshot.RIGHT_WRIST)
        return when {
            leftScore  >= 1.5f && leftScore  >= rightScore ->
                Triple(PoseSnapshot.LEFT_SHOULDER, PoseSnapshot.LEFT_ELBOW, PoseSnapshot.LEFT_WRIST)
            rightScore >= 1.5f ->
                Triple(PoseSnapshot.RIGHT_SHOULDER, PoseSnapshot.RIGHT_ELBOW, PoseSnapshot.RIGHT_WRIST)
            else -> null
        }
    }

    override fun reset() {
        phase = LiftAnalysisPhase.IDLE; repCount = 0
        phaseStartMs = 0L; repStartMs = 0L; pauseStartMs = 0L
        minElbowInRep = 180f; elbowS.reset()
    }
}

// ── DEADLIFT (conventional + sumo) ──────────────────────────────────────────

class DeadliftAnalyzer(@Suppress("unused") private val isSumo: Boolean = false) : LiftAnalyzer {
    private var phase = LiftAnalysisPhase.IDLE
    private var repCount = 0
    private var phaseStartMs = 0L
    private var repStartMs = 0L
    private var holdStartMs = 0L
    private var maxHipAngleInRep = 0f
    private val hipS  = Smoothed()
    private val kneeS = Smoothed()

    private val HOLD_MS       = 1000L
    private val LIFTOFF_ANGLE = 150f
    private val LOCKOUT       = 165f
    private val DESCEND_DELTA = 8f

    override fun analyze(pose: PoseSnapshot, nowMs: Long): AnalysisResult {
        if (!visible(pose,
                PoseSnapshot.LEFT_HIP, PoseSnapshot.LEFT_KNEE, PoseSnapshot.LEFT_SHOULDER)) {
            return AnalysisResult(phase = phase, repCount = repCount, feedback = "Inquadrami di lato")
        }
        val rawHip  = angle3D(pose,
            PoseSnapshot.LEFT_SHOULDER, PoseSnapshot.LEFT_HIP, PoseSnapshot.LEFT_KNEE)
        val rawKnee = if (pose.vis(PoseSnapshot.LEFT_ANKLE) >= VIS_OK)
            angle3D(pose, PoseSnapshot.LEFT_HIP, PoseSnapshot.LEFT_KNEE, PoseSnapshot.LEFT_ANKLE)
            else 180f
        val hipAngle  = hipS.update(rawHip)
        val kneeAngle = kneeS.update(rawKnee)

        if (phase == LiftAnalysisPhase.ASCENDING && hipAngle > maxHipAngleInRep) {
            maxHipAngleInRep = hipAngle
        }

        val lockout = hipAngle > LOCKOUT && kneeAngle > LOCKOUT
        val barOnFloor = pose.vis(PoseSnapshot.LEFT_WRIST) >= VIS_OK &&
                pose.vis(PoseSnapshot.LEFT_ANKLE) >= VIS_OK &&
                abs(pose.ny(PoseSnapshot.LEFT_WRIST) - pose.ny(PoseSnapshot.LEFT_ANKLE)) < 0.08f
        val phaseTime = nowMs - phaseStartMs
        val symm = symmetryY(pose,
            PoseSnapshot.LEFT_HIP, PoseSnapshot.RIGHT_HIP,
            PoseSnapshot.LEFT_HIP, PoseSnapshot.LEFT_KNEE)

        var feedback = ""; var holdProgress = 0f
        when (phase) {
            LiftAnalysisPhase.IDLE -> {
                feedback = "Set up"
                if (hipAngle in 80f..LIFTOFF_ANGLE) {
                    repStartMs = nowMs; maxHipAngleInRep = hipAngle
                    phase = LiftAnalysisPhase.ASCENDING; phaseStartMs = nowMs
                }
            }
            LiftAnalysisPhase.ASCENDING -> {
                feedback = if (lockout) "Hold!" else "Spingi"
                if (lockout && phaseTime >= MIN_PHASE_MS &&
                    (nowMs - repStartMs) >= MIN_REP_MS) {
                    phase = LiftAnalysisPhase.LOCKOUT; phaseStartMs = nowMs
                } else if (hipAngle < maxHipAngleInRep - DESCEND_DELTA * 2f && !lockout) {
                    phase = LiftAnalysisPhase.IDLE; phaseStartMs = nowMs
                    maxHipAngleInRep = 0f
                }
            }
            LiftAnalysisPhase.LOCKOUT -> {
                feedback = "Abbassa controllato"
                if (!lockout && phaseTime >= MIN_PHASE_MS) {
                    phase = LiftAnalysisPhase.DESCENDING; phaseStartMs = nowMs
                }
            }
            LiftAnalysisPhase.DESCENDING -> {
                feedback = "Mani sul bilanciere"
                if (barOnFloor && phaseTime >= MIN_PHASE_MS) {
                    phase = LiftAnalysisPhase.AT_BOTTOM
                    phaseStartMs = nowMs; holdStartMs = nowMs
                }
            }
            LiftAnalysisPhase.AT_BOTTOM -> {
                val elapsed = nowMs - holdStartMs
                holdProgress = (elapsed.toFloat() / HOLD_MS).coerceIn(0f, 1f)
                feedback = if (holdProgress < 1f)
                    "Tieni ${((HOLD_MS - elapsed) / 1000f + 0.5f).toInt()}s" else "GOOD LIFT"
                if (elapsed >= HOLD_MS) {
                    repCount++
                    phase = LiftAnalysisPhase.COMPLETED; phaseStartMs = nowMs
                }
            }
            LiftAnalysisPhase.COMPLETED -> {
                feedback = "GOOD LIFT"; holdProgress = 1f
                if (phaseTime >= MIN_PHASE_MS) {
                    maxHipAngleInRep = 0f
                    phase = LiftAnalysisPhase.IDLE; phaseStartMs = nowMs
                }
            }
        }

        return AnalysisResult(
            phase = phase, repCount = repCount,
            isGoodLift = phase == LiftAnalysisPhase.COMPLETED,
            feedback = feedback,
            hipAngle = hipAngle, kneeAngle = kneeAngle,
            lockoutOk = lockout,
            holdProgress = holdProgress,
            symmetry = symm
        )
    }

    override fun reset() {
        phase = LiftAnalysisPhase.IDLE; repCount = 0
        phaseStartMs = 0L; repStartMs = 0L; holdStartMs = 0L
        maxHipAngleInRep = 0f; hipS.reset(); kneeS.reset()
    }
}
