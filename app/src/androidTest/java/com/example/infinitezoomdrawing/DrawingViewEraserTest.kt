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

@RunWith(AndroidJUnit4::class)
class DrawingViewEraserTest {

    @Test
    fun eraser_clearsExistingStroke() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val drawingView = activity.findViewById<DrawingView>(R.id.drawingView)

                assertTrue("DrawingView should be laid out before exporting", drawingView.width > 0)
                assertTrue("DrawingView should be laid out before exporting", drawingView.height > 0)

                drawingView.brushType = BrushType.PEN
                drawingView.brushColor = Color.BLACK
                drawingView.brushSize = 24f
                dispatchStroke(drawingView, 40f, 120f, 200f, 120f)

                drawingView.brushType = BrushType.ERASER
                drawingView.brushSize = 24f
                dispatchStroke(drawingView, 120f, 40f, 120f, 200f)

                val bitmap = drawingView.exportBitmap()

                assertEquals(Color.BLACK, bitmap.getPixel(70, 120))
                assertEquals(Color.WHITE, bitmap.getPixel(120, 120))
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
}
