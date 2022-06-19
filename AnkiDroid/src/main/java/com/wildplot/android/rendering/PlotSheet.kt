/****************************************************************************************
 * Copyright (c) 2014 Michael Goldbach <michael@wildplot.com>                           *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/
package com.wildplot.android.rendering

import android.graphics.Typeface
import com.wildplot.android.rendering.graphics.wrapper.ColorWrap
import com.wildplot.android.rendering.graphics.wrapper.GraphicsWrap
import com.wildplot.android.rendering.graphics.wrapper.RectangleWrap
import com.wildplot.android.rendering.interfaces.Drawable
import com.wildplot.android.rendering.interfaces.Legendable
import timber.log.Timber
import java.util.*

/**
 * This is a sheet that is used to plot mathematical functions including coordinate systems and optional extras like
 * legends and descriptors. Additionally all conversions from image to plot coordinates are done here
 */
class PlotSheet : Drawable {
    protected var typeface = Typeface.DEFAULT
    private var hasTitle = false
    private var fontSize = 10f
    private var fontSizeSet = false
    private var backgroundColor = ColorWrap.white
    var textColor = ColorWrap.black

    /**
     * title of plotSheet
     */
    protected var title = "PlotSheet"
        /**
         * set the title of the plot
         *
         * @param value title string shown above plot
         */
        set(value) {
            field = value
            hasTitle = true
        }
    private var isBackwards = false

    /**
     * thickness of frame in pixel
     */
    private var leftFrameThickness = 0f
    private var upperFrameThickness = 0f
    private var rightFrameThickness = 0f
    private var bottomFrameThickness = 0f

    /**
     * states if there is a border between frame and plot
     */
    private var isBordered = true
    // if class should be made threadable for mulitplot mode, than
    // this must be done otherwise
    /**
     * screen that is currently rendered
     */
    private val currentScreen = 0

    /**
     * the ploting screens, screen 0 is the only one in single mode
     */
    private val screenParts = Vector<MultiScreenPart>()

    // Use LinkedHashMap so that the legend items will be displayed in the order
    // in which they were added
    private val mLegendMap: MutableMap<String?, ColorWrap> = LinkedHashMap()
    private var mDrawablesPrepared = false

    /**
     * Create a virtual sheet used for the plot
     *
     * @param xStart    the start of the x-range
     * @param xEnd      the end of the x-range
     * @param yStart    the start of the y-range
     * @param yEnd      the end of the y-range
     * @param drawables list of Drawables that shall be drawn onto the sheet
     */
    constructor(
        xStart: Double,
        xEnd: Double,
        yStart: Double,
        yEnd: Double,
        drawables: Vector<Drawable>
    ) {
        val xRange = doubleArrayOf(xStart, xEnd)
        val yRange = doubleArrayOf(yStart, yEnd)
        screenParts.add(0, MultiScreenPart(xRange, yRange, drawables))
    }

    /**
     * Create a virtual sheet used for the plot
     *
     * @param xStart the start of the x-range
     * @param xEnd   the end of the x-range
     * @param yStart the start of the y-range
     * @param yEnd   the end of the y-range
     */
    constructor(xStart: Double, xEnd: Double, yStart: Double, yEnd: Double) {
        val xRange = doubleArrayOf(xStart, xEnd)
        val yRange = doubleArrayOf(yStart, yEnd)
        screenParts.add(0, MultiScreenPart(xRange, yRange))
    }

    /**
     * add another Drawable object that shall be drawn onto the sheet
     * this adds only drawables for the first screen in multimode plots for
     *
     * @param draw Drawable object which will be addet to plot sheet
     */
    fun addDrawable(draw: Drawable?) {
        screenParts[0].addDrawable(draw!!)
        mDrawablesPrepared = false
    }

    /**
     * Converts a given x coordinate from plotting field coordinate to a graphic field coordinate
     *
     * @param x     given graphic x coordinate
     * @param field the graphic field
     * @return the converted x value
     */
    fun xToGraphic(x: Double, field: RectangleWrap): Float {
        val xQuotient = (field.width - leftFrameThickness - rightFrameThickness) /
            Math.abs(
                screenParts[currentScreen].getxRange()[1] -
                    screenParts[currentScreen].getxRange()[0]
            )
        val xDistanceFromLeft = x - screenParts[currentScreen].getxRange()[0]
        return field.x + leftFrameThickness + (xDistanceFromLeft * xQuotient).toFloat()
    }

    /**
     * Converts a given y coordinate from plotting field coordinate to a graphic field coordinate.
     *
     * @param y     given graphic y coordinate
     * @param field the graphic field
     * @return the converted y value
     */
    fun yToGraphic(y: Double, field: RectangleWrap): Float {
        val yQuotient = (field.height - upperFrameThickness - bottomFrameThickness) /
            Math.abs(
                screenParts[currentScreen].getyRange()[1] -
                    screenParts[currentScreen].getyRange()[0]
            )
        val yDistanceFromTop = screenParts[currentScreen].getyRange()[1] - y
        return (field.y + upperFrameThickness + yDistanceFromTop * yQuotient).toFloat()
    }

    /**
     * Convert a coordinate system point to a point used for graphical processing (with hole pixels)
     *
     * @param x     given x-coordinate
     * @param y     given y-coordinate
     * @param field clipping bounds for drawing
     * @return the point in graphical coordinates
     */
    fun toGraphicPoint(x: Double, y: Double, field: RectangleWrap): FloatArray {
        return floatArrayOf(xToGraphic(x, field), yToGraphic(y, field))
    }

    override fun paint(g: GraphicsWrap) {
        val field = g.clipBounds
        prepareDrawables()
        val offFrameDrawables = Vector<Drawable>()
        val onFrameDrawables = Vector<Drawable>()
        g.typeface = typeface
        g.color = backgroundColor
        g.fillRect(0f, 0f, field.width.toFloat(), field.height.toFloat())
        g.color = ColorWrap.BLACK
        if (fontSizeSet) {
            g.fontSize = fontSize
        }
        if (screenParts[0].drawables.size != 0) {
            for (draw in screenParts[0].drawables) {
                if (!draw.isOnFrame()) {
                    offFrameDrawables.add(draw)
                } else {
                    onFrameDrawables.add(draw)
                }
            }
        }
        for (offFrameDrawing in offFrameDrawables) {
            offFrameDrawing.paint(g)
        }

        // paint white frame to over paint everything that was drawn over the border
        val oldColor = g.color
        if (leftFrameThickness > 0 || rightFrameThickness > 0 || upperFrameThickness > 0 || bottomFrameThickness > 0) {
            g.color = backgroundColor
            // upper frame
            g.fillRect(0f, 0f, field.width.toFloat(), upperFrameThickness)

            // left frame
            g.fillRect(0f, upperFrameThickness, leftFrameThickness, field.height.toFloat())

            // right frame
            g.fillRect(
                field.width + 1 - rightFrameThickness,
                upperFrameThickness,
                rightFrameThickness +
                    leftFrameThickness,
                field.height - bottomFrameThickness
            )

            // bottom frame
            g.fillRect(
                leftFrameThickness,
                field.height - bottomFrameThickness,
                field.width - rightFrameThickness,
                bottomFrameThickness + 1
            )

            // make small black border frame
            if (isBordered) {
                drawBorder(g, field)
            }
            g.color = oldColor
            if (hasTitle) {
                drawTitle(g, field)
            }
            val keyList: List<String?> = Vector(mLegendMap.keys)
            if (isBackwards) {
                Collections.reverse(keyList)
            }
            val oldFontSize = g.fontSize
            g.fontSize = oldFontSize * 0.9f
            val fm = g.fontMetrics
            val height = fm.height
            val spacerValue = height * 0.5f
            var xPointer = spacerValue
            var ySpacer = spacerValue
            var legendCnt = 0
            Timber.d("should draw legend now, number of legend entries: %d", mLegendMap.size)
            for (legendName in keyList) {
                val stringWidth = fm.stringWidth(" : $legendName")
                val color = mLegendMap[legendName]
                g.color = color!!
                if (legendCnt++ != 0 && xPointer + height * 2.0f + stringWidth >= field.width) {
                    xPointer = spacerValue
                    ySpacer += height + spacerValue
                }
                g.fillRect(xPointer, ySpacer, height, height)
                g.color = textColor
                g.drawString(" : $legendName", xPointer + height, ySpacer + height)
                xPointer += height * 1.3f + stringWidth
                Timber.d(
                    "drawing a legend Item: (%s) %d, x: %,.2f , y: %,.2f",
                    legendName,
                    legendCnt,
                    xPointer + height,
                    ySpacer + height
                )
            }
            g.fontSize = oldFontSize
            g.color = textColor
        }
        for (onFrameDrawing in onFrameDrawables) {
            onFrameDrawing.paint(g)
        }
    }

    private fun drawBorder(g: GraphicsWrap, field: RectangleWrap) {
        g.color = ColorWrap.black
        // upper border
        val borderThickness = 1
        g.fillRect(
            leftFrameThickness - borderThickness + 1,
            upperFrameThickness - borderThickness + 1,
            field.width - leftFrameThickness - rightFrameThickness + 2 * borderThickness - 2,
            borderThickness.toFloat()
        )

        // lower border
        g.fillRect(
            leftFrameThickness - borderThickness + 1,
            field.height - bottomFrameThickness,
            field.width - leftFrameThickness - rightFrameThickness + 2 * borderThickness - 2,
            borderThickness.toFloat()
        )

        // left border
        g.fillRect(
            leftFrameThickness - borderThickness + 1,
            upperFrameThickness - borderThickness + 1,
            borderThickness.toFloat(),
            field.height - upperFrameThickness - bottomFrameThickness + 2 * borderThickness - 2
        )

        // right border
        g.fillRect(
            field.width - rightFrameThickness,
            upperFrameThickness - borderThickness + 1,
            borderThickness.toFloat(),
            field.height - upperFrameThickness - bottomFrameThickness + 2 * borderThickness - 2
        )
    }

    private fun drawTitle(g: GraphicsWrap, field: RectangleWrap) {
        val oldFontSize = g.fontSize
        val newFontSize = oldFontSize * 2
        g.fontSize = newFontSize
        val fm = g.fontMetrics
        val height = fm.height
        val width = fm.stringWidth(title)
        g.drawString(title, field.width / 2 - width / 2, upperFrameThickness - 10 - height)
        g.fontSize = oldFontSize
    }

    /**
     * sort runnables and group them together to use lesser threads
     */
    private fun prepareDrawables() {
        if (!mDrawablesPrepared) {
            mDrawablesPrepared = true
            val drawables = screenParts[0].drawables
            val onFrameDrawables = Vector<Drawable>()
            val offFrameDrawables = Vector<Drawable>()
            var onFrameContainer = DrawableContainer(true, false)
            var offFrameContainer = DrawableContainer(false, false)
            for (drawable in drawables) {
                if (drawable is Legendable && (drawable as Legendable).nameIsSet()) {
                    val color = (drawable as Legendable).color!!
                    val name = (drawable as Legendable).name
                    mLegendMap[name] = color
                }
                if (drawable.isOnFrame()) {
                    if (drawable.isClusterable()) {
                        if (onFrameContainer.isCritical() != drawable.isCritical()) {
                            if (onFrameContainer.size > 0) {
                                onFrameDrawables.add(onFrameContainer)
                            }
                            onFrameContainer = DrawableContainer(true, drawable.isCritical())
                        }
                        onFrameContainer.addDrawable(drawable)
                    } else {
                        if (onFrameContainer.size > 0) {
                            onFrameDrawables.add(onFrameContainer)
                        }
                        onFrameDrawables.add(drawable)
                        onFrameContainer = DrawableContainer(true, false)
                    }
                } else {
                    if (drawable.isClusterable()) {
                        if (offFrameContainer.isCritical() != drawable.isCritical()) {
                            if (offFrameContainer.size > 0) {
                                offFrameDrawables.add(offFrameContainer)
                            }
                            offFrameContainer = DrawableContainer(false, drawable.isCritical())
                        }
                        offFrameContainer.addDrawable(drawable)
                    } else {
                        if (offFrameContainer.size > 0) {
                            offFrameDrawables.add(offFrameContainer)
                        }
                        offFrameDrawables.add(drawable)
                        offFrameContainer = DrawableContainer(false, false)
                    }
                }
            }
            if (onFrameContainer.size > 0) {
                onFrameDrawables.add(onFrameContainer)
            }
            if (offFrameContainer.size > 0) {
                offFrameDrawables.add(offFrameContainer)
            }
            screenParts[0].drawables.removeAllElements()
            screenParts[0].drawables.addAll(offFrameDrawables)
            screenParts[0].drawables.addAll(onFrameDrawables)
        }
    }

    /**
     * the x-range for the plot
     *
     * @return double array in the length of two with the first element beeingt left and the second element being the right border
     */
    fun getxRange(): DoubleArray {
        return screenParts[0].getxRange()
    }

    /**
     * the <-range for the plot
     *
     * @return double array in the length of two with the first element being lower and the second element being the upper border
     */
    fun getyRange(): DoubleArray {
        return screenParts[0].getyRange()
    }

    /**
     * returns the size in pixel of the outer frame
     *
     * @return the size of the outer frame for left, right, upper and bottom frame
     */
    val frameThickness: FloatArray
        get() = floatArrayOf(
            leftFrameThickness,
            rightFrameThickness,
            upperFrameThickness,
            bottomFrameThickness
        )

    /**
     * set the size of the outer frame in pixel
     */
    fun setFrameThickness(
        leftFrameThickness: Float,
        rightFrameThickness: Float,
        upperFrameThickness: Float,
        bottomFrameThickness: Float
    ) {
        if (leftFrameThickness < 0 || rightFrameThickness < 0 || upperFrameThickness < 0 || bottomFrameThickness < 0) {
            Timber.e("PlotSheet:Error::Wrong Frame size (smaller than 0)")
            this.bottomFrameThickness = 0f
            this.upperFrameThickness = this.bottomFrameThickness
            this.rightFrameThickness = this.upperFrameThickness
            this.leftFrameThickness = this.rightFrameThickness
        }
        this.leftFrameThickness = leftFrameThickness
        this.rightFrameThickness = rightFrameThickness
        this.upperFrameThickness = upperFrameThickness
        this.bottomFrameThickness = bottomFrameThickness
    }

    /**
     * deactivates the border between outer frame and plot
     */
    fun unsetBorder() {
        isBordered = false
    }

    override fun isOnFrame(): Boolean {
        return false
    }

    override fun isClusterable(): Boolean {
        return true
    }

    override fun isCritical(): Boolean {
        return false
    }

    /**
     * Show the legend items in reverse order of the order in which they were added.
     *
     * @param isBackwards If true, the legend items are shown in reverse order.
     */
    fun setIsBackwards(isBackwards: Boolean) {
        this.isBackwards = isBackwards
    }

    fun setFontSize(fontSize: Float) {
        fontSizeSet = true
        this.fontSize = fontSize
    }

    fun setBackgroundColor(backgroundColor: ColorWrap) {
        this.backgroundColor = backgroundColor
    }

    companion object {
        const val LEFT_FRAME_THICKNESS_INDEX = 0
        const val RIGHT_FRAME_THICKNESS_INDEX = 1
        const val UPPER_FRAME_THICKNESS_INDEX = 2
        const val BOTTOM_FRAME_THICKNESS_INDEX = 3
    }
}
