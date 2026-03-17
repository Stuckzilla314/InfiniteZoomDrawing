package com.example.infiniteuniversedrawing

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToLong

internal const val TOOLBAR_ZOOM_FACTOR = 1.15
internal const val CONTINUOUS_ZOOM_REPEAT_DELAY_MS = 90.0

internal data class ViewportTransformState(
    val scale: Double,
    val offsetX: Double,
    val offsetY: Double
) {
    fun isValid(): Boolean = scale.isFinite() && scale > 0.0 && offsetX.isFinite() && offsetY.isFinite()

    fun isApproximately(other: ViewportTransformState, epsilon: Double = 1e-6): Boolean {
        return abs(scale - other.scale) <= epsilon &&
            abs(offsetX - other.offsetX) <= epsilon &&
            abs(offsetY - other.offsetY) <= epsilon
    }
}

internal fun buildReturnHomePath(
    current: ViewportTransformState,
    checkpoints: List<ViewportTransformState>,
    home: ViewportTransformState = ViewportTransformState(scale = 1.0, offsetX = 0.0, offsetY = 0.0)
): List<ViewportTransformState> {
    if (!current.isValid() || !home.isValid() || current.isApproximately(home)) return emptyList()

    val validCheckpoints = checkpoints.filter { it.isValid() }
    val currentCheckpointIndex = validCheckpoints.indexOfLast { it.isApproximately(current) }
    val checkpointsOnWayHome = if (currentCheckpointIndex >= 0) {
        validCheckpoints.subList(0, currentCheckpointIndex)
    } else {
        validCheckpoints
    }

    val path = mutableListOf<ViewportTransformState>()
    var lastTarget = current
    checkpointsOnWayHome.asReversed().forEach { checkpoint ->
        if (!checkpoint.isApproximately(lastTarget)) {
            path.add(checkpoint)
            lastTarget = checkpoint
        }
    }
    if (!home.isApproximately(lastTarget)) {
        path.add(home)
    }
    return path
}

internal fun rebaseViewportState(
    state: ViewportTransformState,
    scaleFactor: Double,
    anchorX: Float,
    anchorY: Float
): ViewportTransformState {
    if (!state.isValid()) return state
    if (!scaleFactor.isFinite() || scaleFactor <= 0.0 || scaleFactor == 1.0) return state

    return ViewportTransformState(
        scale = state.scale / scaleFactor,
        offsetX = state.offsetX + (state.scale * anchorX.toDouble()),
        offsetY = state.offsetY + (state.scale * anchorY.toDouble())
    )
}

internal fun continuousZoomScaleFactor(
    stepZoomFactor: Double,
    stepIntervalMs: Double,
    elapsedMs: Double
): Double {
    if (!stepZoomFactor.isFinite() || stepZoomFactor <= 0.0) return 1.0
    if (!stepIntervalMs.isFinite() || stepIntervalMs <= 0.0) return 1.0
    if (!elapsedMs.isFinite() || elapsedMs <= 0.0) return 1.0
    return stepZoomFactor.pow(elapsedMs / stepIntervalMs)
}

internal fun homeReturnSegmentDurationMs(
    startScale: Double,
    targetScale: Double,
    fallbackDurationMs: Long
): Long {
    if (!startScale.isFinite() || startScale <= 0.0) return fallbackDurationMs
    if (!targetScale.isFinite() || targetScale <= 0.0) return fallbackDurationMs

    val scaleRatio = targetScale / startScale
    if (abs(scaleRatio - 1.0) <= 1e-9) return fallbackDurationMs

    val stepZoomFactor = if (scaleRatio < 1.0) {
        1.0 / TOOLBAR_ZOOM_FACTOR
    } else {
        TOOLBAR_ZOOM_FACTOR
    }
    val durationMs = CONTINUOUS_ZOOM_REPEAT_DELAY_MS * (ln(scaleRatio) / ln(stepZoomFactor))
    if (!durationMs.isFinite()) return fallbackDurationMs

    return max(CONTINUOUS_ZOOM_REPEAT_DELAY_MS, durationMs).roundToLong()
}

internal fun interpolateViewportState(
    start: ViewportTransformState,
    end: ViewportTransformState,
    fraction: Float
): ViewportTransformState {
    val clampedFraction = fraction.coerceIn(0f, 1f).toDouble()
    val interpolatedScale = if (
        start.scale.isFinite() && start.scale > 0.0 &&
        end.scale.isFinite() && end.scale > 0.0
    ) {
        start.scale * (end.scale / start.scale).pow(clampedFraction)
    } else {
        interpolateDouble(start.scale, end.scale, clampedFraction)
    }

    return ViewportTransformState(
        scale = interpolatedScale,
        offsetX = interpolateDouble(start.offsetX, end.offsetX, clampedFraction),
        offsetY = interpolateDouble(start.offsetY, end.offsetY, clampedFraction)
    )
}

private fun interpolateDouble(start: Double, end: Double, fraction: Double): Double {
    return start + ((end - start) * fraction)
}
