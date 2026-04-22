package com.ff9.poweliftjudge.ui.visual

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs
import kotlin.math.sqrt

// ── Phase shared across analyzers ──────────────────────────────────────────

enum class LiftAnalysisPhase {
    IDLE,
    DESCENDING,
    AT_BOTTOM,     // holding (bench pause) or passed depth (squat/deadlift)
    ASCENDING,
    LOCKOUT,       // top lockout hold (deadlift 1s hands)
    COMPLETED
}

data class AnalysisResult(
    val phase: LiftAnalysisPhase = LiftAnalysisPhase.IDLE,
    val repCount: Int = 0,
    val isGoodLift: Boolean = false,
    val feedback: String = "",          // real-time cue
    val hipAngle: Float = 0f,
    val kneeAngle: Float = 0f,
    val ankleAngle: Float = 0f,
    val depthOk: Boolean = false,
    val lockoutOk: Boolean = false,
    val holdProgress: Float = 0f,       // 0..1 for bench pause / deadlift hold
    val symmetry: Float = 100f          // left vs right landmark diff %
)

interface LiftAnalyzer {
    fun analyze(pose: Pose, nowMs: Long): AnalysisResult
    fun reset()
}

// ── Shared timing/threshold constants ───────────────────────────────────────

private const val MIN_PHASE_MS = 300L    // min time in a phase before it can transition
private const val MIN_REP_MS   = 800L    // min total rep duration to count as valid
private const val CONFIDENCE   = 0.5f

// ── Geometry helpers ────────────────────────────────────────────────────────

private fun angle(a: PoseLandmark, b: PoseLandmark, c: PoseLandmark): Float {
    val ax = a.position.x - b.position.x
    val ay = a.position.y - b.position.y
    val cx = c.position.x - b.position.x
    val cy = c.position.y - b.position.y
    val dot = ax * cx + ay * cy
    val magA = sqrt(ax * ax + ay * ay)
    val magC = sqrt(cx * cx + cy * cy)
    if (magA < 1e-4f || magC < 1e-4f) return 0f
    val ratio = (dot / (magA * magC)).toDouble().coerceIn(-1.0, 1.0)
    return Math.toDegrees(Math.acos(ratio)).toFloat()
}

private fun visible(lm: PoseLandmark?) = (lm?.inFrameLikelihood ?: 0f) > CONFIDENCE

private fun symmetryScore(leftY: Float, rightY: Float, scale: Float): Float {
    if (scale < 1f) return 100f
    return (100f - (abs(leftY - rightY) / scale * 100f)).coerceIn(0f, 100f)
}

// Exponential smoothing to tame single-frame jitter from ML Kit.
private class SmoothedAngle(private val alpha: Float = 0.35f) {
    private var value = Float.NaN
    fun update(x: Float): Float {
        value = if (value.isNaN()) x else value + alpha * (x - value)
        return value
    }
    fun reset() { value = Float.NaN }
}

// ── SQUAT ───────────────────────────────────────────────────────────────────
// IPF rule: hip crease below top of knee, controlled ascent to full lockout.
// State machine uses hysteresis on knee angle and a running minimum to
// detect the true bottom of the rep, not a single-frame spike.

class SquatAnalyzer : LiftAnalyzer {
    private var phase = LiftAnalysisPhase.IDLE
    private var repCount = 0
    private var phaseStartMs = 0L
    private var repStartMs = 0L
    private var depthReached = false
    private var minKneeAngleInRep = 180f
    private val kneeSmooth = SmoothedAngle()
    private val hipSmooth  = SmoothedAngle()

    private val DESCEND_START = 150f     // close below this to enter descent
    private val LOCKOUT       = 165f     // above this = standing
    private val RE_ARM        = 170f     // must re-extend past this before next rep
    private val ASCEND_DELTA  = 8f       // re-opening from minimum by this much = ascent

    override fun analyze(pose: Pose, nowMs: Long): AnalysisResult {
        val lHip      = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val lKnee     = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val lAnkle    = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val lShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rHip      = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val rKnee     = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)

        if (!visible(lHip) || !visible(lKnee) || !visible(lAnkle)) {
            return AnalysisResult(phase = phase, repCount = repCount,
                feedback = "Move into frame", depthOk = depthReached)
        }

        val rawKnee = angle(lHip!!, lKnee!!, lAnkle!!)
        val rawHip  = angle(lShoulder ?: lHip, lHip, lKnee)
        val kneeAngle = kneeSmooth.update(rawKnee)
        val hipAngle  = hipSmooth.update(rawHip)

        val depth = lHip.position.y > lKnee.position.y     // IPF depth check
        if (phase == LiftAnalysisPhase.DESCENDING && depth) depthReached = true
        // Only lock-in the bottom while descending; ascending must preserve
        // the reference so the rising-edge check works correctly.
        if (phase == LiftAnalysisPhase.DESCENDING && kneeAngle < minKneeAngleInRep) {
            minKneeAngleInRep = kneeAngle
        }

        val lockout   = kneeAngle > LOCKOUT
        val phaseTime = nowMs - phaseStartMs

        val symm = if (visible(rHip) && visible(rKnee))
            symmetryScore(lHip.position.y, rHip!!.position.y,
                abs(lKnee.position.y - lAnkle.position.y)) else 100f

        var feedback = ""
        when (phase) {
            LiftAnalysisPhase.IDLE -> {
                feedback = "Pronto"
                if (kneeAngle < DESCEND_START) {
                    repStartMs = nowMs
                    minKneeAngleInRep = kneeAngle
                    depthReached = depth
                    phase = LiftAnalysisPhase.DESCENDING
                    phaseStartMs = nowMs
                }
            }
            LiftAnalysisPhase.DESCENDING -> {
                feedback = if (depthReached) "Profondità OK" else "Scendi di più"
                if (phaseTime >= MIN_PHASE_MS &&
                    kneeAngle > minKneeAngleInRep + ASCEND_DELTA) {
                    phase = LiftAnalysisPhase.ASCENDING
                    phaseStartMs = nowMs
                }
            }
            LiftAnalysisPhase.ASCENDING -> {
                feedback = if (depthReached) "Spingi" else "Poco profondo"
                if (lockout && (nowMs - repStartMs) >= MIN_REP_MS) {
                    if (depthReached) { repCount++; feedback = "GOOD LIFT" }
                    else feedback = "NO LIFT - poco profondo"
                    phase = LiftAnalysisPhase.COMPLETED
                    phaseStartMs = nowMs
                } else if (kneeAngle < minKneeAngleInRep + ASCEND_DELTA / 2f) {
                    // athlete re-descending before lockout
                    phase = LiftAnalysisPhase.DESCENDING
                    phaseStartMs = nowMs
                }
            }
            LiftAnalysisPhase.COMPLETED -> {
                feedback = "GOOD LIFT"
                if (kneeAngle > RE_ARM && phaseTime >= MIN_PHASE_MS) {
                    depthReached = false
                    minKneeAngleInRep = 180f
                    phase = LiftAnalysisPhase.IDLE
                    phaseStartMs = nowMs
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
        phase = LiftAnalysisPhase.IDLE
        repCount = 0
        phaseStartMs = 0L; repStartMs = 0L
        depthReached = false
        minKneeAngleInRep = 180f
        kneeSmooth.reset(); hipSmooth.reset()
    }
}

// ── BENCH PRESS ─────────────────────────────────────────────────────────────
// IPF: bar touches chest, motionless press-start (~1s pause), lockout.

class BenchAnalyzer : LiftAnalyzer {
    private var phase = LiftAnalysisPhase.IDLE
    private var repCount = 0
    private var phaseStartMs = 0L
    private var repStartMs = 0L
    private var pauseStartMs = 0L
    private var minElbowInRep = 180f
    private val elbowSmooth = SmoothedAngle()

    private val PAUSE_MS      = 1000L
    private val DESCEND_START = 150f
    private val TOUCH_ANGLE   = 95f      // elbow must close to ~90° to touch chest
    private val LOCKOUT       = 160f
    private val RE_ARM        = 168f
    private val ASCEND_DELTA  = 6f

    override fun analyze(pose: Pose, nowMs: Long): AnalysisResult {
        val lShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val lElbow    = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
        val lWrist    = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val rElbow    = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
        val rWrist    = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)

        val shoulder = if (visible(lShoulder)) lShoulder else rShoulder
        val elbow    = if (visible(lElbow))    lElbow    else rElbow
        val wrist    = if (visible(lWrist))    lWrist    else rWrist

        if (!visible(shoulder) || !visible(elbow) || !visible(wrist)) {
            return AnalysisResult(phase = phase, repCount = repCount, feedback = "Move into frame")
        }

        val rawElbow = angle(shoulder!!, elbow!!, wrist!!)
        val elbowAngle = elbowSmooth.update(rawElbow)

        // Bottom-reference locked during descent / pause; untouched during press-up.
        if ((phase == LiftAnalysisPhase.DESCENDING || phase == LiftAnalysisPhase.AT_BOTTOM)
            && elbowAngle < minElbowInRep) {
            minElbowInRep = elbowAngle
        }
        val touchChest = elbowAngle < TOUCH_ANGLE
        val lockout    = elbowAngle > LOCKOUT
        val phaseTime  = nowMs - phaseStartMs

        val symm = if (visible(lElbow) && visible(rElbow))
            symmetryScore(lElbow!!.position.y, rElbow!!.position.y,
                abs(shoulder.position.y - elbow.position.y)) else 100f

        var feedback = ""
        var holdProgress = 0f

        when (phase) {
            LiftAnalysisPhase.IDLE -> {
                feedback = "Abbassa"
                if (elbowAngle < DESCEND_START) {
                    repStartMs = nowMs
                    minElbowInRep = elbowAngle
                    phase = LiftAnalysisPhase.DESCENDING
                    phaseStartMs = nowMs
                }
            }
            LiftAnalysisPhase.DESCENDING -> {
                feedback = if (touchChest) "Fermo!" else "Tocca il petto"
                if (touchChest && phaseTime >= MIN_PHASE_MS) {
                    phase = LiftAnalysisPhase.AT_BOTTOM
                    phaseStartMs = nowMs
                    pauseStartMs = nowMs
                }
            }
            LiftAnalysisPhase.AT_BOTTOM -> {
                val elapsed = nowMs - pauseStartMs
                holdProgress = (elapsed.toFloat() / PAUSE_MS).coerceIn(0f, 1f)
                feedback = if (holdProgress < 1f)
                    "Fermo ${((PAUSE_MS - elapsed) / 1000f + 0.5f).toInt()}s"
                else "PRESS!"
                // transition to ascending only after pause elapsed AND elbow re-opening
                if (elapsed >= PAUSE_MS &&
                    elbowAngle > minElbowInRep + ASCEND_DELTA) {
                    phase = LiftAnalysisPhase.ASCENDING
                    phaseStartMs = nowMs
                }
                // abort if arms re-extend before pause completed
                if (elapsed < PAUSE_MS && elbowAngle > minElbowInRep + ASCEND_DELTA * 2f) {
                    phase = LiftAnalysisPhase.DESCENDING  // restart — pause broken
                    phaseStartMs = nowMs
                }
            }
            LiftAnalysisPhase.ASCENDING -> {
                feedback = "Distendi"
                holdProgress = 1f
                if (lockout && (nowMs - repStartMs) >= MIN_REP_MS) {
                    repCount++
                    phase = LiftAnalysisPhase.COMPLETED
                    phaseStartMs = nowMs
                }
            }
            LiftAnalysisPhase.COMPLETED -> {
                feedback = "GOOD LIFT"
                holdProgress = 1f
                if (elbowAngle > RE_ARM && phaseTime >= MIN_PHASE_MS) {
                    minElbowInRep = 180f
                    phase = LiftAnalysisPhase.IDLE
                    phaseStartMs = nowMs
                }
            }
            else -> {}
        }

        return AnalysisResult(
            phase = phase, repCount = repCount,
            isGoodLift = phase == LiftAnalysisPhase.COMPLETED,
            feedback = feedback,
            kneeAngle = elbowAngle,   // reuse for elbow display
            lockoutOk = lockout,
            holdProgress = holdProgress,
            symmetry = symm
        )
    }

    override fun reset() {
        phase = LiftAnalysisPhase.IDLE
        repCount = 0
        phaseStartMs = 0L; repStartMs = 0L; pauseStartMs = 0L
        minElbowInRep = 180f
        elbowSmooth.reset()
    }
}

// ── DEADLIFT (conventional + sumo) ──────────────────────────────────────────
// IPF: lift to full lockout, then controlled descent with hands on bar
// (bar must come to rest, 1s with hands before release).

class DeadliftAnalyzer(@Suppress("unused") private val isSumo: Boolean = false) : LiftAnalyzer {
    private var phase = LiftAnalysisPhase.IDLE
    private var repCount = 0
    private var phaseStartMs = 0L
    private var repStartMs = 0L
    private var holdStartMs = 0L
    private var maxHipAngleInRep = 0f
    private val hipSmooth  = SmoothedAngle()
    private val kneeSmooth = SmoothedAngle()

    private val HOLD_MS       = 1000L
    private val LIFTOFF_ANGLE = 150f     // hip opening that flags lift-off from setup
    private val LOCKOUT       = 165f     // both hip and knee past this
    private val DESCEND_DELTA = 8f       // hip closing from max = descending

    override fun analyze(pose: Pose, nowMs: Long): AnalysisResult {
        val lHip     = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val lKnee    = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val lAnkle   = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val lShoulder= pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val lWrist   = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rHip     = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val rKnee    = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)

        if (!visible(lHip) || !visible(lKnee) || !visible(lShoulder)) {
            return AnalysisResult(phase = phase, repCount = repCount, feedback = "Move into frame")
        }

        val rawHip  = angle(lShoulder!!, lHip!!, lKnee!!)
        val rawKnee = if (visible(lAnkle)) angle(lHip, lKnee, lAnkle!!) else 180f
        val hipAngle  = hipSmooth.update(rawHip)
        val kneeAngle = kneeSmooth.update(rawKnee)

        if (phase == LiftAnalysisPhase.ASCENDING && hipAngle > maxHipAngleInRep) {
            maxHipAngleInRep = hipAngle
        }

        val lockout = hipAngle > LOCKOUT && kneeAngle > LOCKOUT
        val barOnFloor = visible(lWrist) && visible(lAnkle) &&
                abs(lWrist!!.position.y - lAnkle!!.position.y) < 80f
        val phaseTime = nowMs - phaseStartMs

        val symm = if (visible(rHip) && visible(rKnee))
            symmetryScore(lHip.position.y, rHip!!.position.y,
                abs(lHip.position.y - lKnee.position.y)) else 100f

        var feedback = ""
        var holdProgress = 0f

        when (phase) {
            LiftAnalysisPhase.IDLE -> {
                feedback = "Set up"
                if (hipAngle in 80f..LIFTOFF_ANGLE) {
                    repStartMs = nowMs
                    maxHipAngleInRep = hipAngle
                    phase = LiftAnalysisPhase.ASCENDING
                    phaseStartMs = nowMs
                }
            }
            LiftAnalysisPhase.ASCENDING -> {
                feedback = if (lockout) "Hold!" else "Spingi anche"
                if (lockout && phaseTime >= MIN_PHASE_MS &&
                    (nowMs - repStartMs) >= MIN_REP_MS) {
                    phase = LiftAnalysisPhase.LOCKOUT
                    phaseStartMs = nowMs
                } else if (hipAngle < maxHipAngleInRep - DESCEND_DELTA * 2f && !lockout) {
                    // dropped before lockout
                    phase = LiftAnalysisPhase.IDLE
                    phaseStartMs = nowMs
                    maxHipAngleInRep = 0f
                }
            }
            LiftAnalysisPhase.LOCKOUT -> {
                feedback = "Abbassa controllato"
                if (!lockout && phaseTime >= MIN_PHASE_MS) {
                    phase = LiftAnalysisPhase.DESCENDING
                    phaseStartMs = nowMs
                }
            }
            LiftAnalysisPhase.DESCENDING -> {
                feedback = "Mani sul bilanciere"
                if (barOnFloor && phaseTime >= MIN_PHASE_MS) {
                    phase = LiftAnalysisPhase.AT_BOTTOM
                    phaseStartMs = nowMs
                    holdStartMs = nowMs
                }
            }
            LiftAnalysisPhase.AT_BOTTOM -> {
                val elapsed = nowMs - holdStartMs
                holdProgress = (elapsed.toFloat() / HOLD_MS).coerceIn(0f, 1f)
                feedback = if (holdProgress < 1f)
                    "Tieni ${((HOLD_MS - elapsed) / 1000f + 0.5f).toInt()}s"
                else "GOOD LIFT"
                if (elapsed >= HOLD_MS) {
                    repCount++
                    phase = LiftAnalysisPhase.COMPLETED
                    phaseStartMs = nowMs
                }
            }
            LiftAnalysisPhase.COMPLETED -> {
                feedback = "GOOD LIFT"
                holdProgress = 1f
                if (phaseTime >= MIN_PHASE_MS) {
                    maxHipAngleInRep = 0f
                    phase = LiftAnalysisPhase.IDLE
                    phaseStartMs = nowMs
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
        phase = LiftAnalysisPhase.IDLE
        repCount = 0
        phaseStartMs = 0L; repStartMs = 0L; holdStartMs = 0L
        maxHipAngleInRep = 0f
        hipSmooth.reset(); kneeSmooth.reset()
    }
}
