package com.example.infinitezoomdrawing

import kotlin.math.abs

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
