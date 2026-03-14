package com.example.infinitezoomdrawing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Custom view that provides a drawing canvas with support for multiple brush types,
 * colors, and brush sizes. Supports undo/redo and clear operations.
 */
class DrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class Stroke(val path: Path, val paint: Paint)

    private val strokes = mutableListOf<Stroke>()
    private val redoStack = mutableListOf<Stroke>()

    private var _brushColor: Int = Color.BLACK
    private var _brushSize: Float = 12f
    private var _brushType: BrushType = BrushType.PEN

    private var currentPath = Path()
    private var currentPaint = createPaint()

    private var lastX = 0f
    private var lastY = 0f

    private var loadedBitmap: Bitmap? = null
    private var loadedBitmapX: Float = 0f
    private var loadedBitmapY: Float = 0f

    var brushColor: Int
        get() = _brushColor
        set(value) { _brushColor = value; currentPaint = createPaint() }

    var brushSize: Float
        get() = _brushSize
        set(value) { _brushSize = value; currentPaint = createPaint() }

    var brushType: BrushType
        get() = _brushType
        set(value) { _brushType = value; currentPaint = createPaint() }

    private fun createPaint(): Paint {
        return Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            when (_brushType) {
                BrushType.PEN -> {
                    color = _brushColor
                    strokeWidth = _brushSize
                    alpha = 255
                    xfermode = null
                    maskFilter = null
                }
                BrushType.MARKER -> {
                    color = _brushColor
                    strokeWidth = _brushSize * 2.5f
                    alpha = 180
                    strokeCap = Paint.Cap.SQUARE
                    xfermode = null
                    maskFilter = null
                }
                BrushType.BRUSH -> {
                    color = _brushColor
                    strokeWidth = _brushSize * 1.5f
                    alpha = 150
                    xfermode = null
                    maskFilter = android.graphics.BlurMaskFilter(
                        _brushSize * 0.5f,
                        android.graphics.BlurMaskFilter.Blur.NORMAL
                    )
                }
                BrushType.ERASER -> {
                    color = Color.TRANSPARENT
                    strokeWidth = _brushSize * 2f
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                    maskFilter = null
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.WHITE)
        loadedBitmap?.let { canvas.drawBitmap(it, loadedBitmapX, loadedBitmapY, null) }
        for (stroke in strokes) canvas.drawPath(stroke.path, stroke.paint)
        canvas.drawPath(currentPath, currentPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                redoStack.clear()
                currentPath = Path()
                currentPath.moveTo(x, y)
                lastX = x
                lastY = y
            }
            MotionEvent.ACTION_MOVE -> {
                val midX = (lastX + x) / 2f
                val midY = (lastY + y) / 2f
                currentPath.quadTo(lastX, lastY, midX, midY)
                lastX = x
                lastY = y
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                currentPath.lineTo(x, y)
                strokes.add(Stroke(currentPath, currentPaint))
                currentPath = Path()
                currentPaint = createPaint()
                invalidate()
            }
        }
        return true
    }

    fun undo() {
        if (strokes.isNotEmpty()) { redoStack.add(strokes.removeLast()); invalidate() }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) { strokes.add(redoStack.removeLast()); invalidate() }
    }

    fun clearCanvas() {
        strokes.clear()
        redoStack.clear()
        currentPath.reset()
        loadedBitmap = null
        invalidate()
    }

    fun canUndo(): Boolean = strokes.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun exportBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(
            width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888
        )
        draw(Canvas(bitmap))
        return bitmap
    }

    fun loadBitmap(bitmap: Bitmap) {
        strokes.clear()
        redoStack.clear()
        currentPath.reset()
        if (width > 0 && height > 0) applyLoadedBitmap(bitmap)
        else post { applyLoadedBitmap(bitmap) }
    }

    private fun applyLoadedBitmap(bitmap: Bitmap) {
        val scale = minOf(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
        val scaledBitmap = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
        loadedBitmap = scaledBitmap
        loadedBitmapX = (width - scaledBitmap.width) / 2f
        loadedBitmapY = (height - scaledBitmap.height) / 2f
        invalidate()
    }
}
