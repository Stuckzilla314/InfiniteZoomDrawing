package com.example.infiniteuniversedrawing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit tests for BrushType enum.
 */
class BrushTypeTest {

    @Test
    fun brushType_allValuesExist() {
        assertNotNull(BrushType.PEN)
        assertNotNull(BrushType.MARKER)
        assertNotNull(BrushType.BRUSH)
        assertNotNull(BrushType.ERASER)
    }

    @Test
    fun brushType_correctCount() {
        assertEquals(4, BrushType.values().size)
    }

    @Test
    fun brushType_valueOf() {
        assertEquals(BrushType.PEN, BrushType.valueOf("PEN"))
        assertEquals(BrushType.MARKER, BrushType.valueOf("MARKER"))
        assertEquals(BrushType.BRUSH, BrushType.valueOf("BRUSH"))
        assertEquals(BrushType.ERASER, BrushType.valueOf("ERASER"))
    }
}
