package com.example.infiniteuniversedrawing

import android.graphics.Color
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class DrawingViewViewportGestureTest {

    @Test
    fun deepZoomViewport_keepsDrawingAlignedWithTouchInput() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val drawingView = activity.findViewById<DrawingView>(R.id.drawingView)

                drawingView.setViewportTransform(scale = 32.0, offsetX = 480.0, offsetY = 240.0)
                drawingView.brushType = BrushType.PEN
                drawingView.brushColor = Color.BLACK
                drawingView.brushSize = 24f

                dispatchStroke(drawingView, 600f, 400f, 760f, 400f)

                val bitmap = drawingView.exportBitmap()

                assertEquals(Color.BLACK, bitmap.getPixel(680, 400))
                assertEquals(Color.WHITE, bitmap.getPixel(540, 400))
            }
        }
    }

    @Test
    fun deepZoomBrushSize_staysRelativeToViewport() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val drawingView = activity.findViewById<DrawingView>(R.id.drawingView)

                drawingView.setViewportTransform(scale = 8.0, offsetX = 0.0, offsetY = 0.0)
                drawingView.brushType = BrushType.PEN
                drawingView.brushColor = Color.BLACK
                drawingView.brushSize = 24f

                dispatchStroke(drawingView, 200f, 120f, 320f, 120f)

                val bitmap = drawingView.exportBitmap()

                assertEquals(Color.BLACK, bitmap.getPixel(260, 120))
                assertEquals(Color.BLACK, bitmap.getPixel(260, 128))
                assertEquals(Color.WHITE, bitmap.getPixel(260, 145))
                assertEquals(Color.WHITE, bitmap.getPixel(260, 170))
            }
        }
    }

    @Test
    fun extremelyLargeViewportTransform_rebasesAndKeepsDrawingAligned() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val drawingView = activity.findViewById<DrawingView>(R.id.drawingView)

                drawingView.setViewportTransform(scale = 1_000_000.0, offsetX = 0.0, offsetY = 0.0)
                drawingView.brushType = BrushType.PEN
                drawingView.brushColor = Color.BLACK
                drawingView.brushSize = 24f

                dispatchStroke(drawingView, 300f, 300f, 420f, 300f)

                val bitmap = drawingView.exportBitmap()

                assertTrue(drawingView.getViewportScale() <= 64.0)
                assertEquals(Color.BLACK, bitmap.getPixel(360, 300))
                assertEquals(Color.WHITE, bitmap.getPixel(240, 300))
            }
        }
    }

    @Test
    fun twoFingerPan_updatesViewportOffset() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val drawingView = activity.findViewById<DrawingView>(R.id.drawingView)

                dispatchTwoFingerGesture(
                    drawingView = drawingView,
                    startFirst = Point(100f, 100f),
                    startSecond = Point(200f, 100f),
                    endFirst = Point(130f, 140f),
                    endSecond = Point(230f, 140f)
                )

                assertEquals(1.0, drawingView.getViewportScale(), 0.05)
                assertTrue(abs(drawingView.getViewportOffsetX() - 30.0) < 5.0)
                assertTrue(abs(drawingView.getViewportOffsetY() - 40.0) < 5.0)
            }
        }
    }

    @Test
    fun pinchZoom_increasesViewportScaleWithoutArtificialCap() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val drawingView = activity.findViewById<DrawingView>(R.id.drawingView)

                dispatchTwoFingerGesture(
                    drawingView = drawingView,
                    startFirst = Point(110f, 120f),
                    startSecond = Point(210f, 120f),
                    endFirst = Point(50f, 120f),
                    endSecond = Point(270f, 120f)
                )

                assertTrue(drawingView.getViewportScale() > 1.5)
            }
        }
    }

    @Test
    fun pinchStart_doesNotCommitZeroLengthStrokeBeforeTransformBegins() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val drawingView = activity.findViewById<DrawingView>(R.id.drawingView)

                drawingView.brushType = BrushType.PEN
                drawingView.brushColor = Color.BLACK
                drawingView.brushSize = 24f
                dispatchStroke(drawingView, 80f, 180f, 240f, 180f)

                drawingView.brushColor = Color.WHITE

                val downTime = SystemClock.uptimeMillis()
                obtainPointerEvent(
                    downTime = downTime,
                    eventTime = downTime,
                    action = MotionEvent.ACTION_DOWN,
                    points = listOf(Point(160f, 180f))
                ).also {
                    drawingView.onTouchEvent(it)
                    it.recycle()
                }
                obtainPointerEvent(
                    downTime = downTime,
                    eventTime = downTime + 16L,
                    action = MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                    points = listOf(Point(160f, 180f), Point(240f, 180f))
                ).also {
                    drawingView.onTouchEvent(it)
                    it.recycle()
                }

                val bitmap = drawingView.exportBitmap()
                try {
                    assertEquals(Color.BLACK, bitmap.getPixel(160, 180))
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }

    @Test
    fun pinchStart_doesNotCommitTinyStrokeBeforeTransformBegins() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val drawingView = activity.findViewById<DrawingView>(R.id.drawingView)

                drawingView.brushType = BrushType.PEN
                drawingView.brushColor = Color.BLACK
                drawingView.brushSize = 24f
                dispatchStroke(drawingView, 80f, 180f, 240f, 180f)

                drawingView.brushColor = Color.WHITE

                val downTime = SystemClock.uptimeMillis()
                obtainPointerEvent(
                    downTime = downTime,
                    eventTime = downTime,
                    action = MotionEvent.ACTION_DOWN,
                    points = listOf(Point(160f, 180f))
                ).also {
                    drawingView.onTouchEvent(it)
                    it.recycle()
                }
                obtainPointerEvent(
                    downTime = downTime,
                    eventTime = downTime + 8L,
                    action = MotionEvent.ACTION_MOVE,
                    points = listOf(Point(162f, 180f))
                ).also {
                    drawingView.onTouchEvent(it)
                    it.recycle()
                }
                obtainPointerEvent(
                    downTime = downTime,
                    eventTime = downTime + 16L,
                    action = MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                    points = listOf(Point(162f, 180f), Point(240f, 180f))
                ).also {
                    drawingView.onTouchEvent(it)
                    it.recycle()
                }

                val bitmap = drawingView.exportBitmap()
                try {
                    assertEquals(Color.BLACK, bitmap.getPixel(160, 180))
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }

    @Test
    fun deepZoomColorChange_preservesWhiteBackground() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val drawingView = activity.findViewById<DrawingView>(R.id.drawingView)

                drawingView.setViewportTransform(scale = 32.0, offsetX = 0.0, offsetY = 0.0)
                drawingView.brushType = BrushType.PEN
                drawingView.brushColor = Color.BLACK
                drawingView.brushSize = 24f
                dispatchStroke(drawingView, 160f, 180f, 240f, 180f)

                drawingView.brushColor = Color.BLUE
                drawingView.setViewportTransform(scale = 96.0, offsetX = 0.0, offsetY = 0.0)

                assertFalse(drawingView.requiresCompositingLayerForTesting())

                dispatchStroke(drawingView, 720f, 240f, 840f, 240f)
                assertFalse(drawingView.requiresCompositingLayerForTesting())

                val bitmap = drawingView.exportBitmap()
                try {
                    assertEquals(Color.BLACK, bitmap.getPixel(600, 180))
                    assertEquals(Color.BLUE, bitmap.getPixel(780, 240))
                    assertEquals(Color.WHITE, bitmap.getPixel(320, 180))
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }

    @Test
    fun repeatedDeepZoom_keepsTopColorStableDuringCompositing() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val drawingView = activity.findViewById<DrawingView>(R.id.drawingView)
                val focusScreenX = drawingView.width / 2f
                val focusScreenY = drawingView.height / 2f
                val focusCanvasX = 12.0
                val focusCanvasY = 12.0

                fun setAnchoredViewport(scale: Double) {
                    drawingView.setViewportTransform(
                        scale = scale,
                        offsetX = focusScreenX - (focusCanvasX * scale),
                        offsetY = focusScreenY - (focusCanvasY * scale)
                    )
                }

                setAnchoredViewport(scale = 8.0)
                drawingView.brushType = BrushType.PEN
                drawingView.brushSize = 80f
                drawingView.brushColor = Color.GREEN
                dispatchStroke(
                    drawingView,
                    focusScreenX - 160f,
                    focusScreenY,
                    focusScreenX + 160f,
                    focusScreenY
                )

                drawingView.brushColor = Color.BLACK
                dispatchStroke(
                    drawingView,
                    focusScreenX - 160f,
                    focusScreenY,
                    focusScreenX + 160f,
                    focusScreenY
                )

                drawingView.brushType = BrushType.ERASER
                drawingView.brushSize = 24f
                dispatchStroke(drawingView, 48f, 48f, 96f, 48f)

                assertTrue(drawingView.requiresCompositingLayerForTesting())

                listOf(8.0, 16.0, 32.0, 48.0, 96.0, 192.0, 384.0, 768.0, 1_536.0, 3_072.0).forEach { scale ->
                    setAnchoredViewport(scale)
                    val bitmap = drawingView.exportBitmap()
                    try {
                        assertEquals(
                            "Expected top-most black stroke to remain stable at scale=$scale",
                            Color.BLACK,
                            bitmap.getPixel(focusScreenX.toInt(), focusScreenY.toInt())
                        )
                        assertEquals(
                            "Expected untouched background to remain white at scale=$scale",
                            Color.WHITE,
                            bitmap.getPixel(40, 40)
                        )
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        }
    }

    @Test
    fun zoomNormalizationThreshold_keepsTopColorStableAcrossAdjacentScales() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val drawingView = activity.findViewById<DrawingView>(R.id.drawingView)
                val focusScreenX = drawingView.width / 2f
                val focusScreenY = drawingView.height / 2f
                val focusCanvasX = 12.0
                val focusCanvasY = 12.0
                val strokeHalfLength = 220f

                fun setAnchoredViewport(scale: Double) {
                    drawingView.setViewportTransform(
                        scale = scale,
                        offsetX = focusScreenX - (focusCanvasX * scale),
                        offsetY = focusScreenY - (focusCanvasY * scale)
                    )
                }

                setAnchoredViewport(scale = 32.0)
                drawingView.brushType = BrushType.PEN
                drawingView.brushSize = 96f
                drawingView.brushColor = Color.GREEN
                dispatchStroke(
                    drawingView,
                    focusScreenX - strokeHalfLength,
                    focusScreenY,
                    focusScreenX + strokeHalfLength,
                    focusScreenY
                )

                drawingView.brushColor = Color.BLACK
                dispatchStroke(
                    drawingView,
                    focusScreenX - strokeHalfLength,
                    focusScreenY,
                    focusScreenX + strokeHalfLength,
                    focusScreenY
                )

                drawingView.brushType = BrushType.ERASER
                drawingView.brushSize = 24f
                dispatchStroke(drawingView, 48f, 48f, 96f, 48f)

                assertTrue(drawingView.requiresCompositingLayerForTesting())

                // Probe adjacent scales around the 64x normalization boundary where rebasing occurs,
                // plus a couple of larger post-rebase scales to catch transient top-color flicker.
                listOf(60.0, 63.0, 64.0, 65.0, 66.0, 96.0, 128.0).forEach { scale ->
                    setAnchoredViewport(scale)
                    val bitmap = drawingView.exportBitmap()
                    try {
                        listOf(-120, 0, 120).forEach { xOffset ->
                            assertEquals(
                                "Expected top-most black stroke to remain stable at scale=$scale xOffset=$xOffset",
                                Color.BLACK,
                                bitmap.getPixel((focusScreenX + xOffset).toInt(), focusScreenY.toInt())
                            )
                        }
                        assertEquals(
                            "Expected untouched background to remain white at scale=$scale",
                            Color.WHITE,
                            bitmap.getPixel(40, 40)
                        )
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        }
    }

    @Test
    fun repeatedZoomAtViewportEdge_keepsTopColorStable() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val drawingView = activity.findViewById<DrawingView>(R.id.drawingView)

                drawingView.setViewportTransform(scale = 8.0, offsetX = 0.0, offsetY = 0.0)
                drawingView.brushType = BrushType.PEN
                drawingView.brushSize = 64f
                drawingView.brushColor = Color.RED
                dispatchStroke(drawingView, 0f, 180f, 180f, 180f)

                drawingView.brushColor = Color.BLUE
                dispatchStroke(drawingView, 0f, 180f, 180f, 180f)

                // Check repeated zoom steps around the 32x/64x normalization boundaries while the
                // overlapping strokes stay pinned to the viewport edge where flicker is easiest to spot.
                listOf(8.0, 24.0, 31.0, 32.0, 33.0, 63.0, 64.0, 65.0, 128.0, 256.0, 512.0, 1_024.0).forEach { scale ->
                    drawingView.setViewportTransform(scale = scale, offsetX = 0.0, offsetY = 0.0)
                    val bitmap = drawingView.exportBitmap()
                    try {
                        assertEquals(
                            "Expected viewport edge pixel to keep the top-most blue stroke at scale=$scale",
                            Color.BLUE,
                            bitmap.getPixel(0, 180)
                        )
                        assertEquals(
                            "Expected nearby edge sample to keep the top-most blue stroke at scale=$scale",
                            Color.BLUE,
                            bitmap.getPixel(32, 180)
                        )
                        assertEquals(
                            "Expected far background to remain white at scale=$scale",
                            Color.WHITE,
                            bitmap.getPixel(bitmap.width - 40, 40)
                        )
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        }
    }

    @Test
    fun deepZoomOffscreenStrokes_doNotTintBlankBackground() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val drawingView = activity.findViewById<DrawingView>(R.id.drawingView)

                drawingView.brushType = BrushType.PEN
                drawingView.brushSize = 24f
                drawingView.brushColor = Color.BLACK
                dispatchStroke(drawingView, 160f, 180f, 260f, 180f)
                drawingView.brushColor = Color.BLUE
                dispatchStroke(drawingView, 260f, 280f, 360f, 280f)

                drawingView.setViewportTransform(scale = 50.0, offsetX = 10_000.0, offsetY = 10_000.0)

                val bitmap = drawingView.exportBitmap()
                try {
                    assertEquals(Color.WHITE, bitmap.getPixel(40, 40))
                    assertEquals(Color.WHITE, bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
                    assertEquals(Color.WHITE, bitmap.getPixel(bitmap.width - 40, bitmap.height - 40))
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }

    @Test
    fun nestedBlackWhiteBlackDeepZoom_keepsAllStrokeColorsVisible() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val drawingView = activity.findViewById<DrawingView>(R.id.drawingView)
                val focusScreenX = drawingView.width / 2f
                val focusScreenY = drawingView.height / 2f
                val focusCanvasX = 12.0
                val focusCanvasY = 12.0
                val brushSize = 80f
                val strokeHalfLength = 220f
                val oldestStrokeScale = 8.0
                val middleStrokeScale = 64.0
                val newestStrokeScale = 512.0

                fun setAnchoredViewport(scale: Double) {
                    drawingView.setViewportTransform(
                        scale = scale,
                        offsetX = focusScreenX - (focusCanvasX * scale),
                        offsetY = focusScreenY - (focusCanvasY * scale)
                    )
                }

                fun halfScreenStrokeWidth(currentScale: Double, drawScale: Double): Double {
                    return (brushSize * currentScale / drawScale) / 2.0
                }

                setAnchoredViewport(scale = oldestStrokeScale)
                drawingView.brushType = BrushType.PEN
                drawingView.brushSize = brushSize
                drawingView.brushColor = Color.BLACK
                dispatchStroke(
                    drawingView,
                    focusScreenX - strokeHalfLength,
                    focusScreenY,
                    focusScreenX + strokeHalfLength,
                    focusScreenY
                )

                setAnchoredViewport(scale = middleStrokeScale)
                drawingView.brushColor = Color.WHITE
                dispatchStroke(
                    drawingView,
                    focusScreenX - strokeHalfLength,
                    focusScreenY,
                    focusScreenX + strokeHalfLength,
                    focusScreenY
                )

                setAnchoredViewport(scale = newestStrokeScale)
                drawingView.brushColor = Color.BLACK
                dispatchStroke(
                    drawingView,
                    focusScreenX - strokeHalfLength,
                    focusScreenY,
                    focusScreenX + strokeHalfLength,
                    focusScreenY
                )

                listOf(256.0, 512.0, 768.0, 1_024.0).forEach { testScale ->
                    setAnchoredViewport(testScale)

                    val newestHalfWidth = halfScreenStrokeWidth(testScale, newestStrokeScale)
                    val middleHalfWidth = halfScreenStrokeWidth(testScale, middleStrokeScale)
                    val oldestHalfWidth = halfScreenStrokeWidth(testScale, oldestStrokeScale)
                    val maxVisibleOffset = (focusScreenY - 24f).toDouble()

                    val whiteOffset = ((newestHalfWidth + middleHalfWidth) / 2.0)
                        .coerceAtMost(maxVisibleOffset)
                        .toInt()
                    val outerBlackOffset = ((middleHalfWidth + minOf(oldestHalfWidth, maxVisibleOffset)) / 2.0)
                        .coerceAtMost(maxVisibleOffset)
                        .toInt()

                    val bitmap = drawingView.exportBitmap()
                    try {
                        assertEquals(
                            "Expected newest black stroke to stay visible at scale=$testScale",
                            Color.BLACK,
                            bitmap.getPixel(focusScreenX.toInt(), focusScreenY.toInt())
                        )
                        assertEquals(
                            "Expected middle white stroke to stay visible at scale=$testScale",
                            Color.WHITE,
                            bitmap.getPixel(focusScreenX.toInt(), focusScreenY.toInt() - whiteOffset)
                        )
                        assertEquals(
                            "Expected oldest black stroke to stay visible at scale=$testScale",
                            Color.BLACK,
                            bitmap.getPixel(focusScreenX.toInt(), focusScreenY.toInt() - outerBlackOffset)
                        )
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        }
    }

    @Test
    fun deepZoomEraser_preservesWhiteBackground() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val drawingView = activity.findViewById<DrawingView>(R.id.drawingView)

                drawingView.setViewportTransform(scale = 96.0, offsetX = 0.0, offsetY = 0.0)
                drawingView.brushType = BrushType.PEN
                drawingView.brushColor = Color.BLACK
                drawingView.brushSize = 24f
                dispatchStroke(drawingView, 560f, 180f, 840f, 180f)

                drawingView.brushType = BrushType.ERASER
                drawingView.brushSize = 24f

                assertTrue(drawingView.requiresCompositingLayerForTesting())

                dispatchStroke(drawingView, 720f, 120f, 720f, 320f)

                val bitmap = drawingView.exportBitmap()
                try {
                    assertEquals(Color.BLACK, bitmap.getPixel(560, 180))
                    assertEquals(Color.WHITE, bitmap.getPixel(720, 180))
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }

    @Test
    fun zoomChanges_keepStrokeVisibleAtViewportEdge() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val drawingView = activity.findViewById<DrawingView>(R.id.drawingView)

                assertTrue("DrawingView should be laid out before testing", drawingView.width > 0)
                assertTrue("DrawingView should be laid out before testing", drawingView.height > 0)

                drawingView.setViewportTransform(scale = 8.0, offsetX = 0.0, offsetY = 0.0)
                drawingView.brushType = BrushType.PEN
                drawingView.brushColor = Color.BLACK
                drawingView.brushSize = 32f

                dispatchStroke(drawingView, 0f, 180f, 160f, 180f)

                val initialBitmap = drawingView.exportBitmap()
                try {
                    assertFalse(initialBitmap.isRecycled)
                    assertEquals(Color.BLACK, initialBitmap.getPixel(0, 180))
                } finally {
                    initialBitmap.recycle()
                }

                drawingView.setViewportTransform(scale = 24.0, offsetX = 0.0, offsetY = 0.0)
                val zoomedInBitmap = drawingView.exportBitmap()
                try {
                    assertFalse(zoomedInBitmap.isRecycled)
                    assertEquals(Color.BLACK, zoomedInBitmap.getPixel(0, 180))
                    assertEquals(Color.BLACK, zoomedInBitmap.getPixel(40, 180))
                } finally {
                    zoomedInBitmap.recycle()
                }

                drawingView.setViewportTransform(scale = 4.0, offsetX = 0.0, offsetY = 0.0)
                val zoomedOutBitmap = drawingView.exportBitmap()
                try {
                    assertFalse(zoomedOutBitmap.isRecycled)
                    assertEquals(Color.BLACK, zoomedOutBitmap.getPixel(0, 180))
                    assertEquals(Color.BLACK, zoomedOutBitmap.getPixel(20, 180))
                } finally {
                    zoomedOutBitmap.recycle()
                }
            }
        }
    }

    @Test
    fun pinchZoomOutAndBackIn_preservesDeepZoomDetail() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val drawingView = activity.findViewById<DrawingView>(R.id.drawingView)
                val centerX = drawingView.width / 2f
                val centerY = drawingView.height / 2f

                fun pinchZoom(zoomIn: Boolean) {
                    val startOffset = 60f
                    val endOffset = 240f
                    val innerOffset = if (zoomIn) startOffset else endOffset
                    val outerOffset = if (zoomIn) endOffset else startOffset
                    dispatchTwoFingerGesture(
                        drawingView = drawingView,
                        startFirst = Point(centerX - innerOffset, centerY),
                        startSecond = Point(centerX + innerOffset, centerY),
                        endFirst = Point(centerX - outerOffset, centerY),
                        endSecond = Point(centerX + outerOffset, centerY)
                    )
                }

                repeat(3) { pinchZoom(zoomIn = true) }

                drawingView.brushType = BrushType.PEN
                drawingView.brushSize = 18f
                drawingView.brushColor = Color.RED
                dispatchStroke(
                    drawingView,
                    centerX - 40f,
                    centerY,
                    centerX + 40f,
                    centerY
                )

                repeat(3) { pinchZoom(zoomIn = false) }
                repeat(3) { pinchZoom(zoomIn = true) }

                val bitmap = drawingView.exportBitmap()
                try {
                    assertEquals(Color.RED, bitmap.getPixel(centerX.toInt(), centerY.toInt()))
                    assertEquals(Color.WHITE, bitmap.getPixel(centerX.toInt(), (centerY - 80f).toInt()))
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }

    private fun dispatchStroke(
        drawingView: DrawingView,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float
    ) {
        val downTime = SystemClock.uptimeMillis()
        val eventTime = downTime + 16L
        MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, startX, startY, 0).also {
            drawingView.onTouchEvent(it)
            it.recycle()
        }
        MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_MOVE, endX, endY, 0).also {
            drawingView.onTouchEvent(it)
            it.recycle()
        }
        MotionEvent.obtain(downTime, eventTime + 16L, MotionEvent.ACTION_UP, endX, endY, 0).also {
            drawingView.onTouchEvent(it)
            it.recycle()
        }
    }

    private fun dispatchTwoFingerGesture(
        drawingView: DrawingView,
        startFirst: Point,
        startSecond: Point,
        endFirst: Point,
        endSecond: Point
    ) {
        val downTime = SystemClock.uptimeMillis()
        var eventTime = downTime

        obtainPointerEvent(
            downTime = downTime,
            eventTime = eventTime,
            action = MotionEvent.ACTION_DOWN,
            points = listOf(startFirst)
        ).also {
            drawingView.onTouchEvent(it)
            it.recycle()
        }

        eventTime += 16L
        obtainPointerEvent(
            downTime = downTime,
            eventTime = eventTime,
            action = MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            points = listOf(startFirst, startSecond)
        ).also {
            drawingView.onTouchEvent(it)
            it.recycle()
        }

        eventTime += 16L
        obtainPointerEvent(
            downTime = downTime,
            eventTime = eventTime,
            action = MotionEvent.ACTION_MOVE,
            points = listOf(
                Point((startFirst.x + endFirst.x) / 2f, (startFirst.y + endFirst.y) / 2f),
                Point((startSecond.x + endSecond.x) / 2f, (startSecond.y + endSecond.y) / 2f)
            )
        ).also {
            drawingView.onTouchEvent(it)
            it.recycle()
        }

        eventTime += 16L
        obtainPointerEvent(
            downTime = downTime,
            eventTime = eventTime,
            action = MotionEvent.ACTION_MOVE,
            points = listOf(endFirst, endSecond)
        ).also {
            drawingView.onTouchEvent(it)
            it.recycle()
        }

        eventTime += 16L
        obtainPointerEvent(
            downTime = downTime,
            eventTime = eventTime,
            action = MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            points = listOf(endFirst, endSecond)
        ).also {
            drawingView.onTouchEvent(it)
            it.recycle()
        }

        eventTime += 16L
        obtainPointerEvent(
            downTime = downTime,
            eventTime = eventTime,
            action = MotionEvent.ACTION_UP,
            points = listOf(endFirst)
        ).also {
            drawingView.onTouchEvent(it)
            it.recycle()
        }
    }

    private fun obtainPointerEvent(
        downTime: Long,
        eventTime: Long,
        action: Int,
        points: List<Point>
    ): MotionEvent {
        val properties = Array(points.size) { index ->
            MotionEvent.PointerProperties().apply {
                id = index
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        }
        val coords = Array(points.size) { index ->
            MotionEvent.PointerCoords().apply {
                x = points[index].x
                y = points[index].y
                pressure = 1f
                size = 1f
            }
        }
        return MotionEvent.obtain(
            downTime,
            eventTime,
            action,
            points.size,
            properties,
            coords,
            0,
            0,
            1f,
            1f,
            0,
            0,
            0,
            0
        )
    }

    private data class Point(val x: Float, val y: Float)
}
