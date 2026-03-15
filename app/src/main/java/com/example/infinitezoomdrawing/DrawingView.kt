package com.example.infinitezoomdrawing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
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

    companion object {
        private const val MIN_VIEWPORT_SCALE_FOR_BRUSH_CALC = 1e-12
    }

    private data class Stroke(val path: Path, val paint: Paint)

    private val strokes = mutableListOf<Stroke>()
    private val redoStack = mutableListOf<Stroke>()

    private var _brushColor: Int = Color.BLACK
    private var _brushSize: Float = 12f
    private var _brushType: BrushType = BrushType.PEN

    private var currentPath = Path()
    private var currentPaintViewportScale = 1.0
    private var currentPaint = createPaint()

    private var lastX = 0f
    private var lastY = 0f

    private var loadedBitmap: Bitmap? = null
    private var loadedBitmapX: Float = 0f
    private var loadedBitmapY: Float = 0f

    private var viewportScale = 1.0
    private var viewportOffsetX = 0.0
    private var viewportOffsetY = 0.0
    private var isDrawingStroke = false
    private var isTransforming = false
    private var lastFocusX = 0f
    private var lastFocusY = 0f

    private val scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())
    private val viewportMatrix = Matrix()
    private val inverseViewportMatrix = Matrix()
    private val mappedPoint = FloatArray(2)

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        updateViewportMatrix()
    }

    var brushColor: Int
        get() = _brushColor
        set(value) { _brushColor = value; refreshCurrentPaint() }

    var brushSize: Float
        get() = _brushSize
        set(value) { _brushSize = value; refreshCurrentPaint() }

    var brushType: BrushType
        get() = _brushType
        set(value) { _brushType = value; refreshCurrentPaint() }

    private fun createPaint(): Paint {
        val zoomAdjustedBrushSize = (
            _brushSize / viewportScale.coerceAtLeast(MIN_VIEWPORT_SCALE_FOR_BRUSH_CALC)
        ).toFloat()
        return Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            when (_brushType) {
                BrushType.PEN -> {
                    color = _brushColor
                    strokeWidth = zoomAdjustedBrushSize
                    alpha = 255
                    xfermode = null
                    maskFilter = null
                }
                BrushType.MARKER -> {
                    color = _brushColor
                    strokeWidth = zoomAdjustedBrushSize * 2.5f
                    alpha = 180
                    strokeCap = Paint.Cap.SQUARE
                    xfermode = null
                    maskFilter = null
                }
                BrushType.BRUSH -> {
                    color = _brushColor
                    strokeWidth = zoomAdjustedBrushSize * 1.5f
                    alpha = 150
                    xfermode = null
                    maskFilter = android.graphics.BlurMaskFilter(
                        zoomAdjustedBrushSize * 0.5f,
                        android.graphics.BlurMaskFilter.Blur.NORMAL
                    )
                }
                BrushType.ERASER -> {
                    color = Color.TRANSPARENT
                    strokeWidth = zoomAdjustedBrushSize * 2f
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                    maskFilter = null
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.WHITE)
        canvas.save()
        canvas.concat(viewportMatrix)
        loadedBitmap?.let { canvas.drawBitmap(it, loadedBitmapX, loadedBitmapY, null) }
        val layer = canvas.saveLayer(null, null)
        for (stroke in strokes) canvas.drawPath(stroke.path, stroke.paint)
        canvas.drawPath(currentPath, currentPaint)
        canvas.restoreToCount(layer)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startStroke(event.x, event.y)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                beginTransform(event)
            }
            MotionEvent.ACTION_MOVE -> {
                if (isTransformGesture(event)) {
                    updateTransform(event)
                } else {
                    updateStroke(event.x, event.y)
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount <= 2) endTransform()
            }
            MotionEvent.ACTION_UP -> {
                if (!isTransforming && !scaleGestureDetector.isInProgress) {
                    finishStroke(event.x, event.y)
                } else {
                    endTransform()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelCurrentInteraction()
            }
        }
        return true
    }

    internal fun getViewportScale(): Double = viewportScale

    internal fun getViewportOffsetX(): Double = viewportOffsetX

    internal fun getViewportOffsetY(): Double = viewportOffsetY

    internal fun setViewportTransform(scale: Double, offsetX: Double, offsetY: Double) {
        if (scale.isFinite() && scale > 0.0 && offsetX.isFinite() && offsetY.isFinite()) {
            viewportScale = scale
            viewportOffsetX = offsetX
            viewportOffsetY = offsetY
            updateViewportMatrix()
            invalidate()
        }
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

    private fun startStroke(screenX: Float, screenY: Float) {
        redoStack.clear()
        if (currentPaintViewportScale != viewportScale) refreshCurrentPaint()
        currentPath = Path()
        val (canvasX, canvasY) = mapScreenToCanvas(screenX, screenY)
        currentPath.moveTo(canvasX, canvasY)
        lastX = canvasX
        lastY = canvasY
        isDrawingStroke = true
        parent?.requestDisallowInterceptTouchEvent(true)
    }

    private fun updateStroke(screenX: Float, screenY: Float) {
        if (!isDrawingStroke) return
        val (canvasX, canvasY) = mapScreenToCanvas(screenX, screenY)
        val midX = (lastX + canvasX) / 2f
        val midY = (lastY + canvasY) / 2f
        currentPath.quadTo(lastX, lastY, midX, midY)
        lastX = canvasX
        lastY = canvasY
        invalidate()
    }

    private fun finishStroke(screenX: Float, screenY: Float) {
        if (!isDrawingStroke) return
        val (canvasX, canvasY) = mapScreenToCanvas(screenX, screenY)
        currentPath.lineTo(canvasX, canvasY)
        strokes.add(Stroke(currentPath, currentPaint))
        currentPath = Path()
        refreshCurrentPaint()
        isDrawingStroke = false
        parent?.requestDisallowInterceptTouchEvent(false)
        invalidate()
    }

    private fun beginTransform(event: MotionEvent) {
        commitCurrentStroke()
        isTransforming = true
        lastFocusX = focusX(event)
        lastFocusY = focusY(event)
        parent?.requestDisallowInterceptTouchEvent(true)
    }

    private fun updateTransform(event: MotionEvent) {
        val focusX = focusX(event)
        val focusY = focusY(event)
        if (canPanDuringTransform()) {
            viewportOffsetX += (focusX - lastFocusX).toDouble()
            viewportOffsetY += (focusY - lastFocusY).toDouble()
            updateViewportMatrix()
            invalidate()
        }
        lastFocusX = focusX
        lastFocusY = focusY
    }

    private fun endTransform() {
        isTransforming = false
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    private fun cancelCurrentInteraction() {
        currentPath = Path()
        isDrawingStroke = false
        endTransform()
        invalidate()
    }

    private fun commitCurrentStroke() {
        if (!isDrawingStroke) return
        currentPath.lineTo(lastX, lastY)
        strokes.add(Stroke(currentPath, currentPaint))
        currentPath = Path()
        refreshCurrentPaint()
        isDrawingStroke = false
        invalidate()
    }

    private fun mapScreenToCanvas(screenX: Float, screenY: Float): Pair<Float, Float> {
        mappedPoint[0] = screenX
        mappedPoint[1] = screenY
        inverseViewportMatrix.mapPoints(mappedPoint)
        return mappedPoint[0] to mappedPoint[1]
    }

    private fun isTransformGesture(event: MotionEvent): Boolean {
        return event.pointerCount > 1 || isTransforming || scaleGestureDetector.isInProgress
    }

    private fun canPanDuringTransform(): Boolean = isTransforming && !scaleGestureDetector.isInProgress

    private fun focusX(event: MotionEvent): Float {
        var total = 0f
        for (index in 0 until event.pointerCount) total += event.getX(index)
        return total / event.pointerCount
    }

    private fun focusY(event: MotionEvent): Float {
        var total = 0f
        for (index in 0 until event.pointerCount) total += event.getY(index)
        return total / event.pointerCount
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            isTransforming = true
            lastFocusX = detector.focusX
            lastFocusY = detector.focusY
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor.toDouble()
            if (!scaleFactor.isFinite() || scaleFactor <= 0.0) return false

            val focusX = detector.focusX.toDouble()
            val focusY = detector.focusY.toDouble()
            val (canvasFocusX, canvasFocusY) = mapScreenToCanvas(detector.focusX, detector.focusY)
            val nextScale = viewportScale * scaleFactor
            if (!nextScale.isFinite() || nextScale <= 0.0) return false

            viewportScale = nextScale
            viewportOffsetX = focusX - (canvasFocusX.toDouble() * viewportScale)
            viewportOffsetY = focusY - (canvasFocusY.toDouble() * viewportScale)
            updateViewportMatrix()
            lastFocusX = detector.focusX
            lastFocusY = detector.focusY
            invalidate()
            return true
        }
    }

    private fun updateViewportMatrix() {
        viewportMatrix.reset()
        // Keep the viewport transform in screen = (canvas * scale) + offset form so
        // the inverse matrix maps touch input back into the exact same draw space.
        viewportMatrix.postScale(viewportScale.toFloat(), viewportScale.toFloat())
        viewportMatrix.postTranslate(viewportOffsetX.toFloat(), viewportOffsetY.toFloat())
        viewportMatrix.invert(inverseViewportMatrix)
    }

    private fun refreshCurrentPaint() {
        currentPaint = createPaint()
        currentPaintViewportScale = viewportScale
    }
}
