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
    var onNewPath: ((Path) -> Unit)? = null
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
     * Handles user touch input for drawing and erasing.
     * Ignores finger input if stylus-only mode is enabled.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isStylusOnlyMode && event.getToolType(0) != MotionEvent.TOOL_TYPE_STYLUS) {
            return false
        }

        val touchX = event.x
        val touchY = event.y
        val isPathEraser = isEraserActive && eraserMode == EraserMode.STROKE

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                hasMoved = false
                currentPath.moveTo(touchX, touchY)
                if (isPathEraser) {
                    onEraseGestureStart?.invoke()
                    onEraseGestureMove?.invoke(touchX, touchY)
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                hasMoved = true
                currentPath.lineTo(touchX, touchY)
                if (isPathEraser) {
                    onEraseGestureMove?.invoke(touchX, touchY)
                }
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                if (isPathEraser) {
                    onEraseGestureEnd?.invoke()
                } else {
                    if (!hasMoved) {
                        // A single tap. Add a tiny line segment to ensure it has a non-zero length,
                        // which makes it more robust for path operations.
                        currentPath.lineTo(touchX + 0.2f, touchY + 0.2f)
                    }
                    onNewPath?.invoke(Path(currentPath))
                }
                // Reset the path for the next gesture
                currentPath.reset()
                invalidate()
            }
            else -> return false
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
