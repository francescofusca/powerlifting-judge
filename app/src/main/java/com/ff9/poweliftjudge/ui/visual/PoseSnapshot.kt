package com.ff9.poweliftjudge.ui.visual

/**
 * Backend-agnostic pose representation used by analyzers and overlay.
 *
 * Both ML Kit and MediaPipe land here. The 33 indices match the
 * MediaPipe Pose Landmarker convention (also a superset of ML Kit's
 * BlazePose body landmarks), so we can reuse a single index set.
 *
 * Coordinates:
 *  - [normalized] are in [0,1] image space (after rotation), ready
 *    to feed PoseOverlay's FIT_CENTER mapping.
 *  - [world] are in metres relative to the athlete's hip centre
 *    (MediaPipe only). z grows away from camera.
 *  - [visibility] in [0,1]; treat <0.5 as "low confidence".
 */
data class PoseSnapshot(
    val normalized: FloatArray,    // size = 33 * 2  (x, y per landmark)
    val world: FloatArray,         // size = 33 * 3  (x, y, z per landmark, metres)
    val visibility: FloatArray,    // size = 33
    val timestampMs: Long
) {
    fun nx(i: Int) = normalized[i * 2]
    fun ny(i: Int) = normalized[i * 2 + 1]
    fun wx(i: Int) = world[i * 3]
    fun wy(i: Int) = world[i * 3 + 1]
    fun wz(i: Int) = world[i * 3 + 2]
    fun vis(i: Int) = visibility[i]

    companion object {
        // MediaPipe / BlazePose landmark indices
        const val NOSE             = 0
        const val LEFT_EYE_INNER   = 1
        const val LEFT_EYE         = 2
        const val LEFT_EYE_OUTER   = 3
        const val RIGHT_EYE_INNER  = 4
        const val RIGHT_EYE        = 5
        const val RIGHT_EYE_OUTER  = 6
        const val LEFT_EAR         = 7
        const val RIGHT_EAR        = 8
        const val MOUTH_LEFT       = 9
        const val MOUTH_RIGHT      = 10
        const val LEFT_SHOULDER    = 11
        const val RIGHT_SHOULDER   = 12
        const val LEFT_ELBOW       = 13
        const val RIGHT_ELBOW      = 14
        const val LEFT_WRIST       = 15
        const val RIGHT_WRIST      = 16
        const val LEFT_PINKY       = 17
        const val RIGHT_PINKY      = 18
        const val LEFT_INDEX       = 19
        const val RIGHT_INDEX      = 20
        const val LEFT_THUMB       = 21
        const val RIGHT_THUMB      = 22
        const val LEFT_HIP         = 23
        const val RIGHT_HIP        = 24
        const val LEFT_KNEE        = 25
        const val RIGHT_KNEE       = 26
        const val LEFT_ANKLE       = 27
        const val RIGHT_ANKLE      = 28
        const val LEFT_HEEL        = 29
        const val RIGHT_HEEL       = 30
        const val LEFT_FOOT_INDEX  = 31
        const val RIGHT_FOOT_INDEX = 32

        const val LANDMARK_COUNT   = 33

        val SKELETON_EDGES: Array<IntArray> = arrayOf(
            intArrayOf(LEFT_SHOULDER, RIGHT_SHOULDER),
            intArrayOf(LEFT_HIP,      RIGHT_HIP),
            intArrayOf(LEFT_SHOULDER, LEFT_HIP),
            intArrayOf(RIGHT_SHOULDER,RIGHT_HIP),
            intArrayOf(LEFT_SHOULDER, LEFT_ELBOW),
            intArrayOf(LEFT_ELBOW,    LEFT_WRIST),
            intArrayOf(RIGHT_SHOULDER,RIGHT_ELBOW),
            intArrayOf(RIGHT_ELBOW,   RIGHT_WRIST),
            intArrayOf(LEFT_HIP,      LEFT_KNEE),
            intArrayOf(LEFT_KNEE,     LEFT_ANKLE),
            intArrayOf(LEFT_ANKLE,    LEFT_HEEL),
            intArrayOf(LEFT_HEEL,     LEFT_FOOT_INDEX),
            intArrayOf(RIGHT_HIP,     RIGHT_KNEE),
            intArrayOf(RIGHT_KNEE,    RIGHT_ANKLE),
            intArrayOf(RIGHT_ANKLE,   RIGHT_HEEL),
            intArrayOf(RIGHT_HEEL,    RIGHT_FOOT_INDEX),
        )

        val KEY_JOINTS = intArrayOf(
            LEFT_SHOULDER, RIGHT_SHOULDER,
            LEFT_ELBOW,    RIGHT_ELBOW,
            LEFT_WRIST,    RIGHT_WRIST,
            LEFT_HIP,      RIGHT_HIP,
            LEFT_KNEE,     RIGHT_KNEE,
            LEFT_ANKLE,    RIGHT_ANKLE,
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PoseSnapshot) return false
        return timestampMs == other.timestampMs && normalized.contentEquals(other.normalized)
    }
    override fun hashCode(): Int = timestampMs.hashCode() * 31 + normalized.contentHashCode()
}
