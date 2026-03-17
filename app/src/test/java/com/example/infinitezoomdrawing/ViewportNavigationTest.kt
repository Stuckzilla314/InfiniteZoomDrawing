package com.example.infinitezoomdrawing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class ViewportNavigationTest {

    @Test
    fun buildReturnHomePath_returnsCheckpointsInReverseOrderBeforeHome() {
        val checkpointA = ViewportTransformState(scale = 1.5, offsetX = 50.0, offsetY = 10.0)
        val checkpointB = ViewportTransformState(scale = 3.0, offsetX = 120.0, offsetY = 60.0)
        val current = ViewportTransformState(scale = 6.0, offsetX = 260.0, offsetY = 180.0)

        val path = buildReturnHomePath(current, listOf(checkpointA, checkpointB))

        assertEquals(
            listOf(
                checkpointB,
                checkpointA,
                ViewportTransformState(scale = 1.0, offsetX = 0.0, offsetY = 0.0)
            ),
            path
        )
    }

    @Test
    fun buildReturnHomePath_skipsCheckpointsBeyondCurrentSavedViewport() {
        val checkpointA = ViewportTransformState(scale = 1.5, offsetX = 50.0, offsetY = 10.0)
        val checkpointB = ViewportTransformState(scale = 3.0, offsetX = 120.0, offsetY = 60.0)

        val path = buildReturnHomePath(checkpointB, listOf(checkpointA, checkpointB))

        assertEquals(
            listOf(
                checkpointA,
                ViewportTransformState(scale = 1.0, offsetX = 0.0, offsetY = 0.0)
            ),
            path
        )
    }

    @Test
    fun buildReturnHomePath_returnsEmptyListWhenAlreadyHome() {
        val path = buildReturnHomePath(
            current = ViewportTransformState(scale = 1.0, offsetX = 0.0, offsetY = 0.0),
            checkpoints = listOf(ViewportTransformState(scale = 2.0, offsetX = 100.0, offsetY = 60.0))
        )

        assertTrue(path.isEmpty())
    }

    @Test
    fun rebaseViewportState_updatesHomeToEquivalentPostRebaseTransform() {
        val rebasedHome = rebaseViewportState(
            state = ViewportTransformState(scale = 1.0, offsetX = 0.0, offsetY = 0.0),
            scaleFactor = 2.5,
            anchorX = 40f,
            anchorY = 24f
        )

        assertEquals(0.4, rebasedHome.scale, 1e-9)
        assertEquals(40.0, rebasedHome.offsetX, 1e-9)
        assertEquals(24.0, rebasedHome.offsetY, 1e-9)
    }

    @Test
    fun buildReturnHomePath_usesRebasedHomeAndCheckpointTransforms() {
        val home = rebaseViewportState(
            state = ViewportTransformState(scale = 1.0, offsetX = 0.0, offsetY = 0.0),
            scaleFactor = 2.0,
            anchorX = 50f,
            anchorY = 25f
        )
        val checkpoint = rebaseViewportState(
            state = ViewportTransformState(scale = 2.0, offsetX = 120.0, offsetY = 80.0),
            scaleFactor = 2.0,
            anchorX = 50f,
            anchorY = 25f
        )
        val current = rebaseViewportState(
            state = ViewportTransformState(scale = 4.0, offsetX = 260.0, offsetY = 180.0),
            scaleFactor = 2.0,
            anchorX = 50f,
            anchorY = 25f
        )

        val path = buildReturnHomePath(current, listOf(checkpoint), home)

        assertEquals(listOf(checkpoint, home), path)
    }

    @Test
    fun continuousZoomScaleFactor_matchesPreviousStepZoomRateOverFullInterval() {
        assertEquals(1.15, continuousZoomScaleFactor(1.15, 90.0, 90.0), 1e-9)
    }

    @Test
    fun continuousZoomScaleFactor_scalesSmoothlyForPartialFrameTime() {
        assertEquals(sqrt(1.15), continuousZoomScaleFactor(1.15, 90.0, 45.0), 1e-9)
    }

    @Test
    fun continuousZoomScaleFactor_preservesReciprocalZoomOutRate() {
        val zoomOutFactor = continuousZoomScaleFactor(1.0 / 1.15, 90.0, 90.0)

        assertEquals(1.0 / 1.15, zoomOutFactor, 1e-9)
    }

    @Test
    fun homeReturnSegmentDurationMs_matchesToolbarZoomOutRateAcrossScaleRatio() {
        assertEquals(180L, homeReturnSegmentDurationMs(startScale = 4.0, targetScale = 4.0 / (1.15 * 1.15), fallbackDurationMs = 650L))
    }

    @Test
    fun interpolateViewportState_scalesGeometricallyAtMidpoint() {
        val midpoint = interpolateViewportState(
            start = ViewportTransformState(scale = 4.0, offsetX = 200.0, offsetY = 100.0),
            end = ViewportTransformState(scale = 4.0 / (1.15 * 1.15), offsetX = 20.0, offsetY = 10.0),
            fraction = 0.5f
        )

        assertEquals(4.0 / 1.15, midpoint.scale, 1e-9)
        assertEquals(110.0, midpoint.offsetX, 1e-9)
        assertEquals(55.0, midpoint.offsetY, 1e-9)
    }
}
