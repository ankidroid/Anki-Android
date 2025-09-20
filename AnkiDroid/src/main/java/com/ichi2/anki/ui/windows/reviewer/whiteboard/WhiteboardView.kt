/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.ui.windows.reviewer.whiteboard

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
import androidx.core.graphics.createBitmap
import com.ichi2.anki.R

/**
 * A custom view for the whiteboard that handles drawing and touch events.
 */
class WhiteboardView : View {
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context) : this(context, null)

    // Callbacks for user actions
    var onNewPath: ((Path, Paint) -> Unit)? = null
    var onEraseGestureStart: (() -> Unit)? = null
    var onEraseGestureMove: ((Float, Float) -> Unit)? = null
    var onEraseGestureEnd: (() -> Unit)? = null

    // Public properties for tool state
    var isEraserActive: Boolean = false
    var eraserMode: EraserMode = EraserMode.INK
    var isStylusOnlyMode: Boolean = false

    // Internal drawing state
    private val currentPath = Path()
    private val currentPaint =
        Paint().apply {
            isAntiAlias = true
            isDither = true
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
    private val eraserPreviewPaint =
        Paint(currentPaint).apply {
            color = context.getColor(R.color.whiteboard_eraser)
        }
    private var history: List<DrawingAction> = emptyList()
    private lateinit var bufferCanvas: Canvas
    private lateinit var bufferBitmap: Bitmap
    private val canvasPaint = Paint(Paint.DITHER_FLAG)

    private var hasMoved = false
    private var lastX = 0f
    private var lastY = 0f

    // Add this property to your WhiteboardView class
    private var activePointerId = MotionEvent.INVALID_POINTER_ID

    /**
     * Recreates the drawing buffer when the view size changes.
     */
    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int,
    ) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (::bufferBitmap.isInitialized) bufferBitmap.recycle()
        bufferBitmap = createBitmap(w, h)
        bufferCanvas = Canvas(bufferBitmap)
        redrawHistory()
    }

    /**
     * Draws the whiteboard content.
     * This includes the historical drawing buffer and the current live path.
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Draw the committed history
        canvas.drawBitmap(bufferBitmap, 0f, 0f, canvasPaint)

        // Draw the live preview path for the current gesture
        if (isEraserActive) {
            canvas.drawPath(currentPath, eraserPreviewPaint)
        } else {
            // Draw the normal brush or pixel eraser preview
            canvas.drawPath(currentPath, currentPaint)
        }
    }

    /**
     * Finalizes the current drawing stroke, if one is in progress.
     * This should be called from your UI (e.g., a color button's click listener)
     * to end a stroke before its natural completion.
     */
    fun finalizeCurrentStroke() {
        if (currentPath.isEmpty) {
            return
        }

        if (!hasMoved) {
            currentPath.lineTo(lastX + 0.2f, lastY + 0.2f)
        }

        onNewPath?.invoke(Path(currentPath), currentPaint)

        currentPath.reset()
        invalidate()
    }

    /**
     * Notifies the view that a brush property (like color) has changed externally.
     * If a stroke is currently being drawn by a different pointer, this will
     * finalize that stroke immediately.
     */
    fun onBrushChanged() {
        // Check if a drawing gesture is active (activePointerId is valid)
        if (activePointerId != MotionEvent.INVALID_POINTER_ID) {
            // A stroke is in progress, so finalize it now.
            finalizeCurrentStroke()
            // Invalidate the pointer to stop further drawing in this gesture.
            activePointerId = MotionEvent.INVALID_POINTER_ID
        }
    }

    /**
     * Handles user touch input for drawing and erasing.
     * Ignores finger input if stylus-only mode is enabled.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isStylusOnlyMode && event.getToolType(event.actionIndex) != MotionEvent.TOOL_TYPE_STYLUS) {
            return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                val touchX = event.x
                val touchY = event.y

                hasMoved = false
                currentPath.moveTo(touchX, touchY)
                lastX = touchX
                lastY = touchY
                invalidate()
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // A second finger touch finalizes the current stroke.
                if (activePointerId != MotionEvent.INVALID_POINTER_ID) {
                    finalizeCurrentStroke()
                }
                // Invalidate the pointer to stop further drawing.
                activePointerId = MotionEvent.INVALID_POINTER_ID
            }

            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = event.findPointerIndex(activePointerId)
                // Proceed only if the move event is for our active drawing finger.
                if (pointerIndex != -1) {
                    val touchX = event.getX(pointerIndex)
                    val touchY = event.getY(pointerIndex)

                    hasMoved = true
                    val midX = (touchX + lastX) / 2
                    val midY = (touchY + lastY) / 2
                    currentPath.quadTo(lastX, lastY, midX, midY)
                    lastX = touchX
                    lastY = touchY
                    invalidate()
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val liftedPointerId = event.getPointerId(event.actionIndex)
                // Finalize the stroke only if the finger that was lifted is the one that was drawing.
                if (liftedPointerId == activePointerId) {
                    finalizeCurrentStroke()
                }
                // If the last finger is up, reset the pointer.
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                currentPath.reset()
                activePointerId = MotionEvent.INVALID_POINTER_ID
                invalidate()
            }
        }
        return true
    }

    /**
     * Replaces the current drawing history with a new set of actions and redraws the buffer.
     */
    fun setHistory(actions: List<DrawingAction>) {
        history = actions
        redrawHistory()
    }

    /**
     * Configures the paint for the live drawing preview based on the current tool.
     */
    fun setCurrentBrush(
        color: Int,
        strokeWidth: Float,
    ) {
        currentPaint.strokeWidth = strokeWidth
        currentPaint.xfermode = null
        currentPaint.color = color

        // Configure the stroke eraser's preview paint separately
        eraserPreviewPaint.strokeWidth = strokeWidth
    }

    /**
     * Redraws all historical paths onto the offscreen buffer.
     */
    private fun redrawHistory() {
        if (!::bufferCanvas.isInitialized) return
        bufferCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val tempPaint =
            Paint().apply {
                isAntiAlias = true
                isDither = true
                style = Paint.Style.STROKE
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
            }
        for (action in history) {
            tempPaint.strokeWidth = action.strokeWidth
            if (action.isEraser) {
                tempPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            } else {
                tempPaint.xfermode = null
                tempPaint.color = action.color
            }
            bufferCanvas.drawPath(action.path, tempPaint)
        }
        invalidate()
    }
}
