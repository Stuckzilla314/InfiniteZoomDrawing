package com.example.infinitezoomdrawing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
