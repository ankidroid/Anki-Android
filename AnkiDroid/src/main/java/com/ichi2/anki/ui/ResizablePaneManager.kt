// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Hari Srinivasan <harisrini21@gmail.com>

package com.ichi2.anki.ui

import android.content.SharedPreferences
import android.view.MotionEvent
import android.view.PointerIcon
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.edit
import com.ichi2.anki.R
import timber.log.Timber

/**
 * Helper class to manage resizable panes in a X-large layouts
 * Allows for dragging to resize panes and saves the pane states in SharedPreferences
 */
class ResizablePaneManager(
    private val parentLayout: LinearLayout,
    private val divider: View,
    private val startPane: View,
    private val endPane: View,
    private val sharedPrefs: SharedPreferences,
    private val startPaneWeightKey: String,
    private val endPaneWeightKey: String,
    private val minWeight: Float = 0.5f, // Minimum weight for each pane
    private val dragColor: Int = divider.context.getColor(R.color.drag_divider_color),
    private val idleColor: Int = divider.context.getColor(R.color.idle_divider_color),
) {
    init {
        setupResizableDivider()
    }

    private fun setupResizableDivider() {
        // Load saved weights if available
        loadSavedWeights()

        var initialTouchX = 0f
        var initialStartWeight = 0f
        var initialEndWeight = 0f

        divider.setOnHoverListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_HOVER_ENTER -> {
                    divider.pointerIcon =
                        PointerIcon.getSystemIcon(
                            divider.context,
                            PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW,
                        )
                    divider.setBackgroundColor(dragColor)
                    true
                }
                MotionEvent.ACTION_HOVER_EXIT -> {
                    divider.pointerIcon = null
                    divider.setBackgroundColor(idleColor)
                    true
                }
                else -> false
            }
        }

        divider.setOnTouchListener { v, event ->
            val startParams = startPane.layoutParams as LinearLayout.LayoutParams
            val endParams = endPane.layoutParams as LinearLayout.LayoutParams

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    /*
                        Request parent to not intercept touch events so that the divider does not get intercepted by
                        the parent layout, when Full screen navigation drawer setting is enabled
                     */
                    v.parent.requestDisallowInterceptTouchEvent(true)

                    v.setBackgroundColor(dragColor)
                    initialTouchX = event.rawX
                    initialStartWeight = startParams.weight
                    initialEndWeight = endParams.weight
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    v.parent.requestDisallowInterceptTouchEvent(true)

                    // The start pane is on the right in RTL layouts, so it shrinks when dragging right.
                    val deltaX = (event.rawX - initialTouchX).invertIfRtl(parentLayout)
                    val totalParentWidth = parentLayout.width.toFloat()

                    if (totalParentWidth > 0) { // Avoid division by zero
                        val sumOfInitialWeights = initialStartWeight + initialEndWeight

                        // Calculate the change in weight based on the drag distance
                        val weightDelta = (deltaX / totalParentWidth) * sumOfInitialWeights

                        var newStartWeight = initialStartWeight + weightDelta

                        // Clamp the new weight for the start pane
                        // Ensures it's not too small and not too large (leaving space for the other pane's minWeight)
                        newStartWeight = newStartWeight.coerceIn(minWeight, sumOfInitialWeights - minWeight)

                        val newEndWeight = sumOfInitialWeights - newStartWeight

                        // Apply the new weights
                        startParams.weight = newStartWeight
                        endParams.weight = newEndWeight

                        startPane.layoutParams = startParams
                        endPane.layoutParams = endParams

                        // Request layout update for the parent
                        parentLayout.requestLayout()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.setBackgroundColor(idleColor)

                    // Save the new weights to SharedPreferences
                    sharedPrefs.edit {
                        putFloat(startPaneWeightKey, startParams.weight)
                        putFloat(endPaneWeightKey, endParams.weight)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun loadSavedWeights() {
        try {
            val startParams = startPane.layoutParams as LinearLayout.LayoutParams
            val endParams = endPane.layoutParams as LinearLayout.LayoutParams

            // Load saved weights from SharedPreferences
            val savedStartWeight = sharedPrefs.getFloat(startPaneWeightKey, startParams.weight)
            val savedEndWeight = sharedPrefs.getFloat(endPaneWeightKey, endParams.weight)

            // Apply the saved weights
            startParams.weight = savedStartWeight
            endParams.weight = savedEndWeight

            startPane.layoutParams = startParams
            endPane.layoutParams = endParams

            // Request layout update for the parent
            parentLayout.requestLayout()
        } catch (e: Exception) {
            Timber.w(e, "Failed to load saved pane weights")
        }
    }
}

private fun Float.invertIfRtl(view: View): Float = if (view.layoutDirection == View.LAYOUT_DIRECTION_RTL) -this else this
