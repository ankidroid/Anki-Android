/*
 * Copyright (c) 2009 Andrew <andrewdubya@gmail.com>
 * Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com>
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>
 * Copyright (c) 2021 Nicolai Weitkemper <kontakt@nicolaiweitkemper.de>
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
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki

import android.annotation.SuppressLint
import android.graphics.*
import android.graphics.drawable.VectorDrawable
import android.net.Uri
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.annotation.CheckResult
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.ichi2.anki.dialogs.WhiteBoardWidthDialog
import com.ichi2.compat.CompatHelper
import com.ichi2.libanki.utils.Time
import com.ichi2.libanki.utils.TimeUtils
import com.ichi2.themes.Themes.currentTheme
import com.ichi2.utils.DisplayUtils.getDisplayDimensions
import com.ichi2.utils.KotlinCleanup
import com.mrudultora.colorpicker.ColorPickerPopUp
import timber.log.Timber
import java.io.FileNotFoundException
import kotlin.math.abs
import kotlin.math.max

/**
 * Whiteboard allowing the user to draw the card's answer on the touchscreen.
 */
@SuppressLint("ViewConstructor")
class Whiteboard(activity: AnkiActivity, handleMultiTouch: Boolean, inverted: Boolean) : View(activity, null) {
    private val mPaint: Paint
    private val mUndo = UndoList()
    private lateinit var mBitmap: Bitmap
    private lateinit var mCanvas: Canvas
    private val mPath: Path
    private val mBitmapPaint: Paint
    private val mAnkiActivity: AnkiActivity = activity
    private var mX = 0f
    private var mY = 0f
    private var mSecondFingerX0 = 0f
    private var mSecondFingerY0 = 0f
    private var mSecondFingerX = 0f
    private var mSecondFingerY = 0f
    private var mSecondFingerPointerId = 0
    private var mSecondFingerWithinTapTolerance = false

    var toggleStylus = false
    var isCurrentlyDrawing = false
        private set

    @get:CheckResult
    @get:VisibleForTesting
    var foregroundColor = 0
    private val mColorPalette: LinearLayout
    private val mHandleMultiTouch: Boolean = handleMultiTouch
    private var mOnPaintColorChangeListener: OnPaintColorChangeListener? = null
    val currentStrokeWidth: Int
        get() = AnkiDroidApp.getSharedPrefs(mAnkiActivity).getInt("whiteBoardStrokeWidth", 6)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.apply {
            drawColor(0)
            drawBitmap(mBitmap, 0f, 0f, mBitmapPaint)
            drawPath(mPath, mPaint)
        }
    }

    /** Handle motion events to draw using the touch screen or to interact with the flashcard behind
     * the whiteboard by using a second finger.
     *
     * @param event The motion event.
     * @return True if the event was handled, false otherwise
     */
    fun handleTouchEvent(event: MotionEvent): Boolean {
        return handleDrawEvent(event) || handleMultiTouchEvent(event)
    }

    /**
     * Handle motion events to draw using the touch screen. Only simple touch events are processed,
     * a multitouch event aborts to current stroke.
     *
     * @param event The motion event.
     * @return True if the event was handled, false otherwise or when drawing was aborted due to
     * detection of a multitouch event.
     */
    private fun handleDrawEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        if (event.getToolType(event.actionIndex) != MotionEvent.TOOL_TYPE_STYLUS && toggleStylus == true) {
            return false
        }
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                drawStart(x, y)
                invalidate()
                true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isCurrentlyDrawing) {
                    for (i in 0 until event.historySize) {
                        drawAlong(event.getHistoricalX(i), event.getHistoricalY(i))
                    }
                    drawAlong(x, y)
                    invalidate()
                    return true
                }
                false
            }
            MotionEvent.ACTION_UP -> {
                if (isCurrentlyDrawing) {
                    drawFinish()
                    invalidate()
                    return true
                }
                false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (isCurrentlyDrawing) {
                    drawAbort()
                }
                false
            }
            211, 213 -> {
                if (event.buttonState == MotionEvent.BUTTON_STYLUS_PRIMARY && !undoEmpty()) {
                    val didErase = mUndo.erase(event.x.toInt(), event.y.toInt())
                    if (didErase) {
                        mUndo.apply()
                        if (undoEmpty()) {
                            mAnkiActivity.invalidateOptionsMenu()
                        }
                    }
                }
                true
            }
            else -> false
        }
    }

    // Parse multitouch input to scroll the card behind the whiteboard or click on elements
    private fun handleMultiTouchEvent(event: MotionEvent): Boolean {
        return if (mHandleMultiTouch && event.pointerCount == 2) {
            when (event.actionMasked) {
                MotionEvent.ACTION_POINTER_DOWN -> {
                    reinitializeSecondFinger(event)
                    true
                }
                MotionEvent.ACTION_MOVE -> trySecondFingerScroll(event)
                MotionEvent.ACTION_POINTER_UP -> trySecondFingerClick(event)
                else -> false
            }
        } else {
            false
        }
    }

    /**
     * Clear the whiteboard.
     */
    fun clear() {
        mBitmap.eraseColor(0)
        mUndo.clear()
        invalidate()
        mAnkiActivity.invalidateOptionsMenu()
    }

    /**
     * Undo the last stroke
     */
    fun undo() {
        mUndo.pop()
        mUndo.apply()
        if (undoEmpty()) {
            mAnkiActivity.invalidateOptionsMenu()
        }
    }

    /** @return Whether there are strokes to undo
     */
    fun undoEmpty(): Boolean {
        return mUndo.empty()
    }

    private fun createBitmap(w: Int, h: Int) {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        mBitmap = bitmap
        mCanvas = Canvas(bitmap)
        clear()
    }

    private fun createBitmap() {
        // To fix issue #1336, just make the whiteboard big and square.
        val p = displayDimensions
        val bitmapSize = max(p.x, p.y)
        if (bitmapSize > 0) {
            createBitmap(bitmapSize, bitmapSize)
        } else {
            // Handles the case where bitmapSize is 0 or negative.
            Timber.e("Bitmap size less than zero")
        }
    }

    /**
     * On rotating the device onSizeChanged() helps to stretch the previously created Bitmap rather
     * than creating a new Bitmap which makes sure bitmap doesn't go out of screen.
     */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val scaledBitmap: Bitmap = Bitmap.createScaledBitmap(mBitmap, w, h, true)
        mBitmap = scaledBitmap
        mCanvas = Canvas(mBitmap)
    }

    private fun drawStart(x: Float, y: Float) {
        isCurrentlyDrawing = true
        mPath.reset()
        mPath.moveTo(x, y)
        mX = x
        mY = y
    }

    private fun drawAlong(x: Float, y: Float) {
        val dx = abs(x - mX)
        val dy = abs(y - mY)
        if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
            mPath.quadTo(mX, mY, (x + mX) / 2, (y + mY) / 2)
            mX = x
            mY = y
        }
    }

    private fun drawFinish() {
        isCurrentlyDrawing = false
        val pm = PathMeasure(mPath, false)
        mPath.lineTo(mX, mY)
        val paint = Paint(mPaint)
        val action = if (pm.length > 0) DrawPath(Path(mPath), paint) else DrawPoint(mX, mY, paint)
        action.apply(mCanvas)
        mUndo.add(action)
        // kill the path so we don't double draw
        mPath.reset()
        if (mUndo.size() == 1) {
            mAnkiActivity.invalidateOptionsMenu()
        }
    }

    private fun drawAbort() {
        drawFinish()
        undo()
    }

    // call this with an ACTION_POINTER_DOWN event to start a new round of detecting drag or tap with
    // a second finger
    private fun reinitializeSecondFinger(event: MotionEvent) {
        mSecondFingerWithinTapTolerance = true
        mSecondFingerPointerId = event.getPointerId(event.actionIndex)
        mSecondFingerX0 = event.getX(event.findPointerIndex(mSecondFingerPointerId))
        mSecondFingerY0 = event.getY(event.findPointerIndex(mSecondFingerPointerId))
    }

    private fun updateSecondFinger(event: MotionEvent): Boolean {
        val pointerIndex = event.findPointerIndex(mSecondFingerPointerId)
        if (pointerIndex > -1) {
            mSecondFingerX = event.getX(pointerIndex)
            mSecondFingerY = event.getY(pointerIndex)
            val dx = abs(mSecondFingerX0 - mSecondFingerX)
            val dy = abs(mSecondFingerY0 - mSecondFingerY)
            if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
                mSecondFingerWithinTapTolerance = false
            }
            return true
        }
        return false
    }

    // call this with an ACTION_POINTER_UP event to check whether it matches a tap of the second finger
    // if so, forward a click action and return true
    private fun trySecondFingerClick(event: MotionEvent): Boolean {
        if (mSecondFingerPointerId == event.getPointerId(event.actionIndex)) {
            updateSecondFinger(event)
            if (mSecondFingerWithinTapTolerance && mWhiteboardMultiTouchMethods != null) {
                mWhiteboardMultiTouchMethods!!.tapOnCurrentCard(mSecondFingerX.toInt(), mSecondFingerY.toInt())
                return true
            }
        }
        return false
    }

    // call this with an ACTION_MOVE event to check whether it is within the threshold for a tap of the second finger
    // in this case perform a scroll action
    private fun trySecondFingerScroll(event: MotionEvent): Boolean {
        if (updateSecondFinger(event) && !mSecondFingerWithinTapTolerance) {
            val dy = (mSecondFingerY0 - mSecondFingerY).toInt()
            if (dy != 0 && mWhiteboardMultiTouchMethods != null) {
                mWhiteboardMultiTouchMethods!!.scrollCurrentCardBy(dy)
                mSecondFingerX0 = mSecondFingerX
                mSecondFingerY0 = mSecondFingerY
            }
            return true
        }
        return false
    }

    fun onClick(view: View) {
        when (view.id) {
            R.id.pen_color_white -> {
                penColor = Color.WHITE
            }
            R.id.pen_color_black -> {
                penColor = Color.BLACK
            }
            R.id.pen_color_red -> {
                val redPenColor = ContextCompat.getColor(context, R.color.material_red_500)
                penColor = redPenColor
            }
            R.id.pen_color_green -> {
                val greenPenColor = ContextCompat.getColor(context, R.color.material_green_500)
                penColor = greenPenColor
            }
            R.id.pen_color_blue -> {
                val bluePenColor = ContextCompat.getColor(context, R.color.material_blue_500)
                penColor = bluePenColor
            }
            R.id.pen_color_yellow -> {
                val yellowPenColor = ContextCompat.getColor(context, R.color.material_yellow_500)
                penColor = yellowPenColor
            }
            R.id.pen_color_custom -> {
                ColorPickerPopUp(context).run {
                    setShowAlpha(true)
                    setDefaultColor(penColor)
                    setOnPickColorListener(object : ColorPickerPopUp.OnPickColorListener {

                        override fun onColorPicked(color: Int) {
                            penColor = color
                        }

                        override fun onCancel() {
                            // unused
                        }
                    })
                    show()
                }
            }
            R.id.stroke_width -> {
                handleWidthChangeDialog()
            }
        }
    }

    private fun handleWidthChangeDialog() {
        val whiteBoardWidthDialog = WhiteBoardWidthDialog(mAnkiActivity, currentStrokeWidth)
        whiteBoardWidthDialog.onStrokeWidthChanged { wbStrokeWidth: Int -> saveStrokeWidth(wbStrokeWidth) }
        whiteBoardWidthDialog.showStrokeWidthDialog()
    }

    private fun saveStrokeWidth(wbStrokeWidth: Int) {
        mPaint.strokeWidth = wbStrokeWidth.toFloat()
        AnkiDroidApp.getSharedPrefs(mAnkiActivity).edit {
            putInt("whiteBoardStrokeWidth", wbStrokeWidth)
        }
    }

    @get:VisibleForTesting
    var penColor: Int
        get() = mPaint.color
        set(color) {
            Timber.d("Setting pen color to %d", color)
            mPaint.color = color
            mColorPalette.visibility = GONE
            if (mOnPaintColorChangeListener != null) {
                mOnPaintColorChangeListener!!.onPaintColorChange(color)
            }
        }

    fun setOnPaintColorChangeListener(onPaintColorChangeListener: OnPaintColorChangeListener?) {
        mOnPaintColorChangeListener = onPaintColorChangeListener
    }

    /**
     * Keep a list of all points and paths so that the last stroke can be undone
     * pop() removes the last stroke from the list, and apply() redraws it to whiteboard.
     */
    private inner class UndoList {
        private val mList: MutableList<WhiteboardAction> = ArrayList()
        fun add(action: WhiteboardAction) {
            mList.add(action)
        }

        fun clear() {
            mList.clear()
        }

        fun size(): Int {
            return mList.size
        }

        fun pop() {
            mList.removeAt(mList.size - 1)
        }

        fun apply() {
            mBitmap.eraseColor(0)
            for (action in mList) {
                action.apply(mCanvas)
            }
            invalidate()
        }

        fun erase(x: Int, y: Int): Boolean {
            var didErase = false
            val clip = Region(0, 0, displayDimensions.x, displayDimensions.y)
            val eraserPath = Path()
            eraserPath.addRect((x - 10).toFloat(), (y - 10).toFloat(), (x + 10).toFloat(), (y + 10).toFloat(), Path.Direction.CW)
            val eraserRegion = Region()
            eraserRegion.setPath(eraserPath, clip)

            // used inside the loop – created here to make things a little more efficient
            val bounds = RectF()
            var lineRegion = Region()

            // we delete elements while iterating, so we need to use an iterator in order to avoid java.util.ConcurrentModificationException
            val iterator = mList.iterator()
            while (iterator.hasNext()) {
                val action = iterator.next()
                val path = action.path
                if (path != null) { // → line
                    val lineRegionSuccess = lineRegion.setPath(path, clip)
                    if (!lineRegionSuccess) {
                        // Small lines can be perfectly vertical/horizontal,
                        // thus giving us an empty region, which would make them undeletable.
                        // For this edge case, we create a Region ourselves.
                        path.computeBounds(bounds, true)
                        lineRegion = Region(Rect(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt() + 1, bounds.bottom.toInt() + 1))
                    }
                } else { // → point
                    val p = action.point
                    lineRegion = Region(p!!.x, p.y, p.x + 1, p.y + 1)
                }
                if (!lineRegion.quickReject(eraserRegion) && lineRegion.op(eraserRegion, Region.Op.INTERSECT)) {
                    iterator.remove()
                    didErase = true
                }
            }
            return didErase
        }

        fun empty(): Boolean {
            return mList.isEmpty()
        }
    }

    private interface WhiteboardAction {
        fun apply(canvas: Canvas)
        val path: Path?
        val point: Point?
    }

    private class DrawPoint(private val x: Float, private val y: Float, private val paint: Paint) : WhiteboardAction {
        override fun apply(canvas: Canvas) {
            canvas.drawPoint(x, y, paint)
        }

        override val path: Path?
            get() = null

        override val point: Point
            get() = Point(x.toInt(), y.toInt())
    }

    private class DrawPath(override val path: Path, private val paint: Paint) : WhiteboardAction {
        override fun apply(canvas: Canvas) {
            canvas.drawPath(path, paint)
        }

        override val point: Point?
            get() = null
    }

    @Throws(FileNotFoundException::class)
    fun saveWhiteboard(time: Time?): Uri {
        val bitmap = Bitmap.createBitmap(this.width, this.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (foregroundColor != Color.BLACK) {
            canvas.drawColor(Color.BLACK)
        } else {
            canvas.drawColor(Color.WHITE)
        }
        draw(canvas)
        val baseFileName = "Whiteboard" + TimeUtils.getTimestamp(time!!)
        return CompatHelper.compat.saveImage(context, bitmap, baseFileName, "jpg", Bitmap.CompressFormat.JPEG, 95)
    }

    @KotlinCleanup("fun interface & use SAM on callers")
    interface OnPaintColorChangeListener {
        fun onPaintColorChange(color: Int?)
    }

    companion object {
        private const val TOUCH_TOLERANCE = 4f
        private var mWhiteboardMultiTouchMethods: WhiteboardMultiTouchMethods? = null
        fun createInstance(context: AnkiActivity, handleMultiTouch: Boolean, whiteboardMultiTouchMethods: WhiteboardMultiTouchMethods?): Whiteboard {
            val whiteboard = Whiteboard(context, handleMultiTouch, currentTheme.isNightMode)
            mWhiteboardMultiTouchMethods = whiteboardMultiTouchMethods
            val lp2 = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            whiteboard.layoutParams = lp2
            val fl = context.findViewById<FrameLayout>(R.id.whiteboard)
            fl.addView(whiteboard)
            whiteboard.isEnabled = true
            return whiteboard
        }

        private val displayDimensions: Point
            get() = getDisplayDimensions(AnkiDroidApp.instance.applicationContext)
    }

    init {
        val whitePenColorButton = activity.findViewById<Button>(R.id.pen_color_white)
        val blackPenColorButton = activity.findViewById<Button>(R.id.pen_color_black)
        if (!inverted) {
            whitePenColorButton.visibility = GONE
            blackPenColorButton.setOnClickListener { view: View -> onClick(view) }
            foregroundColor = Color.BLACK
        } else {
            blackPenColorButton.visibility = GONE
            whitePenColorButton.setOnClickListener { view: View -> onClick(view) }
            foregroundColor = Color.WHITE
        }
        mPaint = Paint().apply {
            isAntiAlias = true
            isDither = true
            color = foregroundColor
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            strokeWidth = currentStrokeWidth.toFloat()
        }
        createBitmap()
        mPath = Path()
        mBitmapPaint = Paint(Paint.DITHER_FLAG)

        // selecting pen color to draw
        mColorPalette = activity.findViewById(R.id.whiteboard_editor)
        activity.findViewById<View>(R.id.pen_color_red).setOnClickListener { view: View -> onClick(view) }
        activity.findViewById<View>(R.id.pen_color_green).setOnClickListener { view: View -> onClick(view) }
        activity.findViewById<View>(R.id.pen_color_blue).setOnClickListener { view: View -> onClick(view) }
        activity.findViewById<View>(R.id.pen_color_yellow).setOnClickListener { view: View -> onClick(view) }
        activity.findViewById<View>(R.id.pen_color_custom).apply {
            setOnClickListener { view: View -> onClick(view) }
            (background as? VectorDrawable)?.setTint(foregroundColor)
        }
        activity.findViewById<View>(R.id.stroke_width).apply {
            setOnClickListener { view: View -> onClick(view) }
            (background as? VectorDrawable)?.setTint(foregroundColor)
        }
    }
}
