package com.example.infiniteuniversedrawing

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.LinearInterpolator

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
        private const val MAX_STABLE_VIEWPORT_SCALE = 64.0
        private const val TARGET_STABLE_VIEWPORT_SCALE = 32.0
        private const val MIN_STABLE_VIEWPORT_SCALE = 1.0 / MAX_STABLE_VIEWPORT_SCALE
        private const val TARGET_MIN_STABLE_VIEWPORT_SCALE = 1.0 / TARGET_STABLE_VIEWPORT_SCALE
        private const val HOME_RETURN_SEGMENT_DURATION_MS = 450L
        private const val HOME_RETURN_FINAL_SEGMENT_DURATION_MS = 650L
        private val INITIAL_HOME_VIEWPORT_STATE = ViewportTransformState(scale = 1.0, offsetX = 0.0, offsetY = 0.0)
    }

    private data class Stroke(
        val path: Path,
        val paint: Paint,
        val requiresClearCompositing: Boolean
    )

    private val strokes = mutableListOf<Stroke>()
    private val redoStack = mutableListOf<Stroke>()

    private var _brushColor: Int = Color.BLACK
    private var _brushSize: Float = 12f
    private var _brushType: BrushType = BrushType.PEN

    private var viewportScale = 1.0
    private var viewportOffsetX = 0.0
    private var viewportOffsetY = 0.0
    private var homeViewportState = INITIAL_HOME_VIEWPORT_STATE
    private val homeCheckpoints = mutableListOf<ViewportTransformState>()
    private var viewportAnimator: ValueAnimator? = null

    private var currentPath = Path()
    private var currentPaintViewportScale = viewportScale
    private var currentPaint = createPaint()
    private var currentStrokeHasMovement = false
    private var currentStrokeStartScreenX = 0f
    private var currentStrokeStartScreenY = 0f

    private var lastX = 0f
    private var lastY = 0f

    private var loadedBitmap: Bitmap? = null
    private var loadedBitmapX: Float = 0f
    private var loadedBitmapY: Float = 0f
    private var loadedBitmapScale: Float = 1f

    private var isDrawingStroke = false
    private var isTransforming = false
    private var lastFocusX = 0f
    private var lastFocusY = 0f

    private val scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val viewportMatrix = Matrix()

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
        val visibleCanvasRect = calculateVisibleCanvasRect()
        drawCanvasBackdrop(canvas, visibleCanvasRect)

        if (requiresCompositingLayer()) {
            val layer = canvas.saveLayer(null, null)
            drawStrokes(canvas, visibleCanvasRect)
            canvas.restoreToCount(layer)
        } else {
            drawStrokes(canvas, visibleCanvasRect)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN ||
            event.actionMasked == MotionEvent.ACTION_POINTER_DOWN
        ) {
            cancelViewportAnimation()
        }
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

    internal fun requiresCompositingLayerForTesting(): Boolean = requiresCompositingLayer()

    internal fun setViewportTransform(scale: Double, offsetX: Double, offsetY: Double) {
        cancelViewportAnimation()
        applyViewportTransform(scale, offsetX, offsetY)
    }

    internal fun getHomeCheckpointCount(): Int = homeCheckpoints.size

    fun zoomBy(scaleFactor: Double, focusScreenX: Float = width / 2f, focusScreenY: Float = height / 2f) {
        cancelViewportAnimation()
        applyZoom(scaleFactor, focusScreenX, focusScreenY)
    }

    fun animateReturnHome(): Boolean {
        cancelViewportAnimation()
        return playNextHomeReturnSegment(currentViewportState())
    }

    fun isAtHome(): Boolean = currentViewportState().isApproximately(homeViewportState)

    fun addHomeCheckpoint(): Boolean {
        cancelViewportAnimation()
        val checkpoint = currentViewportState()
        if (checkpoint.isApproximately(homeViewportState)) return false
        if (homeCheckpoints.any { it.isApproximately(checkpoint) }) return false
        homeCheckpoints.add(checkpoint)
        return true
    }

    fun hasHomeCheckpoints(): Boolean = homeCheckpoints.isNotEmpty()

    fun clearHomeCheckpoints() {
        homeCheckpoints.clear()
    }

    override fun onDetachedFromWindow() {
        cancelViewportAnimation()
        super.onDetachedFromWindow()
    }

    private fun currentViewportState(): ViewportTransformState {
        return ViewportTransformState(
            scale = viewportScale,
            offsetX = viewportOffsetX,
            offsetY = viewportOffsetY
        )
    }

    private fun applyViewportTransform(
        scale: Double,
        offsetX: Double,
        offsetY: Double,
        normalizeViewport: Boolean = true
    ) {
        if (scale.isFinite() && scale > 0.0 && offsetX.isFinite() && offsetY.isFinite()) {
            viewportScale = scale
            viewportOffsetX = offsetX
            viewportOffsetY = offsetY
            updateViewportMatrix()
            if (normalizeViewport) {
                normalizeViewportScale(width / 2f, height / 2f)
            }
            invalidate()
        }
    }

    private fun applyZoom(scaleFactor: Double, focusScreenX: Float, focusScreenY: Float) {
        if (!scaleFactor.isFinite() || scaleFactor <= 0.0) return
        val (canvasFocusX, canvasFocusY) = mapScreenToCanvas(focusScreenX, focusScreenY)
        val nextScale = viewportScale * scaleFactor
        if (!nextScale.isFinite() || nextScale <= 0.0) return
        applyViewportTransform(
            scale = nextScale,
            offsetX = focusScreenX.toDouble() - (canvasFocusX.toDouble() * nextScale),
            offsetY = focusScreenY.toDouble() - (canvasFocusY.toDouble() * nextScale)
        )
    }

    private fun cancelViewportAnimation() {
        viewportAnimator?.cancel()
        viewportAnimator = null
    }

    private fun playNextHomeReturnSegment(startState: ViewportTransformState): Boolean {
        val remainingPath = buildReturnHomePath(startState, homeCheckpoints, homeViewportState)
        val targetState = remainingPath.firstOrNull() ?: return false
        val isFinalSegment = remainingPath.size == 1
        val fallbackDuration = if (isFinalSegment) {
            HOME_RETURN_FINAL_SEGMENT_DURATION_MS
        } else {
            HOME_RETURN_SEGMENT_DURATION_MS
        }
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = homeReturnSegmentDurationMs(startState.scale, targetState.scale, fallbackDuration)
            interpolator = LinearInterpolator()
            addUpdateListener { valueAnimator ->
                val interpolatedState = interpolateViewportState(
                    start = startState,
                    end = targetState,
                    fraction = valueAnimator.animatedValue as Float
                )
                applyViewportTransform(
                    scale = interpolatedState.scale,
                    offsetX = interpolatedState.offsetX,
                    offsetY = interpolatedState.offsetY,
                    normalizeViewport = false
                )
            }
        }
        viewportAnimator = animator
        animator.addListener(object : AnimatorListenerAdapter() {
            private var wasCancelled = false

            override fun onAnimationCancel(animation: Animator) {
                wasCancelled = true
                if (viewportAnimator === animator) {
                    viewportAnimator = null
                }
            }

            override fun onAnimationEnd(animation: Animator) {
                if (viewportAnimator === animator) {
                    viewportAnimator = null
                }
                if (!wasCancelled) {
                    applyViewportTransform(
                        scale = targetState.scale,
                        offsetX = targetState.offsetX,
                        offsetY = targetState.offsetY
                    )
                    playNextHomeReturnSegment(currentViewportState())
                }
            }
        })
        animator.start()
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
        loadedBitmapScale = 1f
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
        loadedBitmapScale = 1f
        invalidate()
    }

    private fun startStroke(screenX: Float, screenY: Float) {
        redoStack.clear()
        if (currentPaintViewportScale != viewportScale) refreshCurrentPaint()
        currentPath = Path()
        currentStrokeStartScreenX = screenX
        currentStrokeStartScreenY = screenY
        val (canvasX, canvasY) = mapScreenToCanvas(screenX, screenY)
        currentPath.moveTo(canvasX, canvasY)
        lastX = canvasX
        lastY = canvasY
        currentStrokeHasMovement = false
        isDrawingStroke = true
        parent?.requestDisallowInterceptTouchEvent(true)
    }

    private fun updateStroke(screenX: Float, screenY: Float) {
        if (!isDrawingStroke) return
        if (!currentStrokeHasMovement) {
            val deltaX = screenX - currentStrokeStartScreenX
            val deltaY = screenY - currentStrokeStartScreenY
            if ((deltaX * deltaX) + (deltaY * deltaY) < touchSlop * touchSlop) return
        }
        val (canvasX, canvasY) = mapScreenToCanvas(screenX, screenY)
        val midX = (lastX + canvasX) / 2f
        val midY = (lastY + canvasY) / 2f
        currentPath.quadTo(lastX, lastY, midX, midY)
        lastX = canvasX
        lastY = canvasY
        currentStrokeHasMovement = true
        invalidate()
    }

    private fun finishStroke(screenX: Float, screenY: Float) {
        if (!isDrawingStroke) return
        val (canvasX, canvasY) = mapScreenToCanvas(screenX, screenY)
        currentPath.lineTo(canvasX, canvasY)
        completeCurrentStroke()
        parent?.requestDisallowInterceptTouchEvent(false)
        invalidate()
    }

    private fun beginTransform(event: MotionEvent) {
        if (currentStrokeHasMovement) commitCurrentStroke() else discardCurrentStroke()
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
        resetCurrentStroke()
        endTransform()
        invalidate()
    }

    private fun commitCurrentStroke() {
        if (!isDrawingStroke) return
        currentPath.lineTo(lastX, lastY)
        completeCurrentStroke()
        invalidate()
    }

    private fun completeCurrentStroke() {
        strokes.add(
            Stroke(
                path = currentPath,
                paint = currentPaint,
                requiresClearCompositing = currentBrushUsesClearCompositing()
            )
        )
        refreshCurrentPaint()
        resetCurrentStroke()
    }

    private fun discardCurrentStroke() {
        if (!isDrawingStroke) return
        resetCurrentStroke()
        invalidate()
    }

    private fun resetCurrentStroke() {
        currentPath = Path()
        currentStrokeHasMovement = false
        currentStrokeStartScreenX = 0f
        currentStrokeStartScreenY = 0f
        isDrawingStroke = false
    }

    private fun mapScreenToCanvas(screenX: Float, screenY: Float): Pair<Float, Float> {
        // Direct affine math avoids an unnecessary float-matrix inversion and matches
        // the same screen = (canvas * scale) + offset transform used for rendering.
        return ((screenX.toDouble() - viewportOffsetX) / viewportScale).toFloat() to
            ((screenY.toDouble() - viewportOffsetY) / viewportScale).toFloat()
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

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            normalizeViewportScale(detector.focusX, detector.focusY)
            invalidate()
        }
    }

    private fun normalizeViewportScale(focusScreenX: Float, focusScreenY: Float) {
        val (focusCanvasX, focusCanvasY) = mapScreenToCanvas(focusScreenX, focusScreenY)
        when {
            viewportScale > MAX_STABLE_VIEWPORT_SCALE -> {
                val rebaseFactor = viewportScale / TARGET_STABLE_VIEWPORT_SCALE
                rebaseCanvasContent(rebaseFactor, focusCanvasX, focusCanvasY)
            }
            viewportScale < MIN_STABLE_VIEWPORT_SCALE -> {
                val rebaseFactor = viewportScale / TARGET_MIN_STABLE_VIEWPORT_SCALE
                rebaseCanvasContent(rebaseFactor, focusCanvasX, focusCanvasY)
            }
        }
    }

    private fun rebaseCanvasContent(scaleFactor: Double, anchorX: Float, anchorY: Float) {
        if (!scaleFactor.isFinite() || scaleFactor <= 0.0) return
        if (scaleFactor == 1.0) return

        val transform = Matrix().apply {
            setTranslate(-anchorX, -anchorY)
            postScale(scaleFactor.toFloat(), scaleFactor.toFloat())
        }

        strokes.forEach { stroke ->
            stroke.path.transform(transform)
            scalePaintForRebase(stroke.paint, scaleFactor)
        }
        redoStack.forEach { stroke ->
            stroke.path.transform(transform)
            scalePaintForRebase(stroke.paint, scaleFactor)
        }
        currentPath.transform(transform)

        loadedBitmapX = ((loadedBitmapX - anchorX) * scaleFactor).toFloat()
        loadedBitmapY = ((loadedBitmapY - anchorY) * scaleFactor).toFloat()
        loadedBitmapScale *= scaleFactor.toFloat()

        homeViewportState = rebaseViewportState(homeViewportState, scaleFactor, anchorX, anchorY)
        homeCheckpoints.indices.forEach { index ->
            homeCheckpoints[index] = rebaseViewportState(homeCheckpoints[index], scaleFactor, anchorX, anchorY)
        }

        viewportOffsetX += viewportScale * anchorX.toDouble()
        viewportOffsetY += viewportScale * anchorY.toDouble()
        viewportScale /= scaleFactor

        refreshCurrentPaint()
        updateViewportMatrix()
    }

    private fun scalePaintForRebase(paint: Paint, scaleFactor: Double) {
        paint.strokeWidth = (paint.strokeWidth * scaleFactor).toFloat()
        if (paint.maskFilter != null) {
            paint.maskFilter = android.graphics.BlurMaskFilter(
                paint.strokeWidth / 3f,
                android.graphics.BlurMaskFilter.Blur.NORMAL
            )
        }
    }

    private fun updateViewportMatrix() {
        viewportMatrix.reset()
        // Keep the viewport transform in screen = (canvas * scale) + offset form so
        // render math stays simple and touch mapping can use the same affine values directly.
        viewportMatrix.postScale(viewportScale.toFloat(), viewportScale.toFloat())
        viewportMatrix.postTranslate(viewportOffsetX.toFloat(), viewportOffsetY.toFloat())
    }

    private inline fun drawInViewport(canvas: Canvas, drawBlock: (Canvas) -> Unit) {
        canvas.save()
        canvas.concat(viewportMatrix)
        drawBlock(canvas)
        canvas.restore()
    }

    private fun drawCanvasBackdrop(canvas: Canvas, visibleCanvasRect: RectF) {
        canvas.drawColor(Color.WHITE)
        drawInViewport(canvas) { viewportCanvas ->
            loadedBitmap?.let { bitmap ->
                val bitmapBounds = RectF(
                    loadedBitmapX,
                    loadedBitmapY,
                    loadedBitmapX + (bitmap.width * loadedBitmapScale),
                    loadedBitmapY + (bitmap.height * loadedBitmapScale)
                )
                if (!RectF.intersects(bitmapBounds, visibleCanvasRect)) return@let
                viewportCanvas.save()
                viewportCanvas.translate(loadedBitmapX, loadedBitmapY)
                viewportCanvas.scale(loadedBitmapScale, loadedBitmapScale)
                viewportCanvas.drawBitmap(bitmap, 0f, 0f, null)
                viewportCanvas.restore()
            }
        }
    }

    private fun calculateVisibleCanvasRect(): RectF {
        val (topLeftX, topLeftY) = mapScreenToCanvas(0f, 0f)
        val (bottomRightX, bottomRightY) = mapScreenToCanvas(width.toFloat(), height.toFloat())
        val overscan = (2.0 / viewportScale.coerceAtLeast(MIN_VIEWPORT_SCALE_FOR_BRUSH_CALC)).toFloat()
        return RectF(
            minOf(topLeftX, bottomRightX) - overscan,
            minOf(topLeftY, bottomRightY) - overscan,
            maxOf(topLeftX, bottomRightX) + overscan,
            maxOf(topLeftY, bottomRightY) + overscan
        )
    }

    private fun pathIntersectsVisibleRect(path: Path, paint: Paint, visibleCanvasRect: RectF): Boolean {
        if (path.isEmpty) return false

        val coveragePath = Path()
        paint.getFillPath(path, coveragePath)

        val coverageBounds = RectF()
        coveragePath.computeBounds(coverageBounds, true)

        if (
            coverageBounds.left.isFinite() &&
            coverageBounds.top.isFinite() &&
            coverageBounds.right.isFinite() &&
            coverageBounds.bottom.isFinite() &&
            !coverageBounds.isEmpty
        ) {
            return RectF.intersects(coverageBounds, visibleCanvasRect)
        }

        val pathBounds = RectF()
        path.computeBounds(pathBounds, true)
        val strokePadding = maxOf(paint.strokeWidth, 1f)
        if (pathBounds.isEmpty()) {
            pathBounds.set(
                pathBounds.left - strokePadding,
                pathBounds.top - strokePadding,
                pathBounds.right + strokePadding,
                pathBounds.bottom + strokePadding
            )
        } else {
            pathBounds.inset(-strokePadding, -strokePadding)
        }
        return RectF.intersects(pathBounds, visibleCanvasRect)
    }

    private fun drawStrokes(canvas: Canvas, visibleCanvasRect: RectF) {
        drawInViewport(canvas) { viewportCanvas ->
            for (stroke in strokes) {
                if (
                    shouldAlwaysDrawStroke(stroke.paint) ||
                    pathIntersectsVisibleRect(stroke.path, stroke.paint, visibleCanvasRect)
                ) {
                    viewportCanvas.drawPath(stroke.path, stroke.paint)
                }
            }
            if (
                shouldAlwaysDrawStroke(currentPaint) ||
                pathIntersectsVisibleRect(currentPath, currentPaint, visibleCanvasRect)
            ) {
                viewportCanvas.drawPath(currentPath, currentPaint)
            }
        }
    }

    private fun shouldAlwaysDrawStroke(paint: Paint): Boolean {
        val projectedStrokeWidth = paint.strokeWidth.toDouble() * viewportScale
        if (!projectedStrokeWidth.isFinite()) return false

        val viewportMaxDimension = maxOf(width, height).toDouble()
        return viewportMaxDimension > 0.0 && projectedStrokeWidth >= viewportMaxDimension
    }

    private fun requiresCompositingLayer(): Boolean {
        return strokes.any { it.requiresClearCompositing } ||
            (isDrawingStroke && currentBrushUsesClearCompositing())
    }

    private fun refreshCurrentPaint() {
        currentPaint = createPaint()
        currentPaintViewportScale = viewportScale
    }

    private fun currentBrushUsesClearCompositing(): Boolean = _brushType == BrushType.ERASER
}
