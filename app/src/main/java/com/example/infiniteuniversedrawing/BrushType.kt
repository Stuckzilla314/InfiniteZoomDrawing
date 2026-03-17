package com.example.infiniteuniversedrawing

/**
 * Defines the available brush types for the drawing application.
 */
enum class BrushType {
    /** Standard rounded brush, fully opaque */
    PEN,
    /** Wide flat brush for bold strokes */
    MARKER,
    /** Soft brush with feathered edges */
    BRUSH,
    /** Removes paint from the canvas */
    ERASER
}
