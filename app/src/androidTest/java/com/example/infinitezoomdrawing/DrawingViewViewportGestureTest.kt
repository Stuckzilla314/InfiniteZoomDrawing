package com.example.infinitezoomdrawing

import android.graphics.Color
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
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
