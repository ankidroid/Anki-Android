/*
 *  Copyright (c) 2026 Rakshit Rajendra <rrakzhit@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.ui

import android.content.Context
import android.content.SharedPreferences
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.PointerIcon
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.edit
import com.ichi2.anki.R
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.utils.isWindowCompact
import timber.log.Timber

/**
 * Helper class to manage resizable panes in a X-large layouts
 * Allows for dragging to resize panes and saves the pane states in SharedPreferences
 */
class ResizablePaneLayout
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : LinearLayout(context, attrs, defStyleAttr) {
        private lateinit var divider: View
        private lateinit var leftPane: View
        private lateinit var rightPane: View
        private val sharedPrefs: SharedPreferences = Prefs.getUiConfig(context)
        private var leftPaneWeightKey: String? = null
        private var rightPaneWeightKey: String? = null
        private val minWeight: Float = 0.5f
        private val dragColor: Int = context.getColor(R.color.drag_divider_color)
        private val idleColor: Int = context.getColor(R.color.idle_divider_color)

        var fragmentationEnabled: Boolean = true
            set(value) {
                if (field != value) {
                    field = value
                    checkStateChange(false)
                }
            }
        private val fragmented: Boolean
            get() = fragmentationEnabled && !resources.isWindowCompact()

        private var wasFragmented = fragmented
        private var onUIChange: (() -> Unit)? = null
        var isResizable = false
            private set

        init {
            // Get the keys from attributes declared in the layout
            context.theme
                .obtainStyledAttributes(
                    attrs,
                    R.styleable.ResizablePaneLayout,
                    0,
                    0,
                ).apply {
                    try {
                        leftPaneWeightKey = getString(R.styleable.ResizablePaneLayout_leftWeightKey)
                        rightPaneWeightKey = getString(R.styleable.ResizablePaneLayout_rightWeightKey)
                    } finally {
                        recycle()
                    }
                }
        }

        override fun onFinishInflate() {
            super.onFinishInflate()
            if (childCount != 3) {
                Timber.e("ResizablePaneLayout can only have 3 children")
                return
            }

            leftPane = getChildAt(0)
            divider = getChildAt(1)
            rightPane = getChildAt(2)
            refreshState(fragmented)
        }

        override fun onSizeChanged(
            w: Int,
            h: Int,
            oldw: Int,
            oldh: Int,
        ) {
            super.onSizeChanged(w, h, oldw, oldh)
            // Check if the mew size has changed
            // onSizeChanged is called during a layout pass and our requestLayout call will be ignores, so we add them at the end
            checkStateChange(true)
        }

        private fun checkStateChange(shouldPost: Boolean) {
            // Check if the mew size has changed enough to update the UI and callback
            val isFragmented = fragmented
            if (wasFragmented != isFragmented) {
                wasFragmented = isFragmented
                if (shouldPost) {
                    post {
                        refreshState(isFragmented)
                        onUIChange?.invoke()
                    }
                } else {
                    refreshState(isFragmented)
                    onUIChange?.invoke()
                }
            }
        }

        // Refreshes state of the UI based on the current state of the view
        fun refreshState(isFragmented: Boolean = fragmented) {
            if (!isFragmented) {
                removeResizableDivider()
            } else if (!isResizable) {
                setupResizableDivider()
            }
        }

        fun addOnUIChangedListener(listener: () -> Unit) {
            onUIChange = listener
        }

        private fun setupResizableDivider() {
            divider.visibility = VISIBLE
            rightPane.visibility = VISIBLE
            isResizable = true

            // Load saved weights if available
            loadSavedWeights()

            var initialTouchX = 0f
            var initialLeftWeight = 0f
            var initialRightWeight = 0f

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
                val leftParams = leftPane.layoutParams as LayoutParams
                val rightParams = rightPane.layoutParams as LayoutParams

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                    /*
                        Request parent to not intercept touch events so that the divider does not get intercepted by
                        the parent layout, when Full screen navigation drawer setting is enabled
                     */
                        v.parent.requestDisallowInterceptTouchEvent(true)

                        v.setBackgroundColor(dragColor)
                        initialTouchX = event.rawX
                        initialLeftWeight = leftParams.weight
                        initialRightWeight = rightParams.weight
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        v.parent.requestDisallowInterceptTouchEvent(true)

                        val deltaX = event.rawX - initialTouchX
                        val totalParentWidth = width.toFloat()

                        if (totalParentWidth > 0) { // Avoid division by zero
                            val sumOfInitialWeights = initialLeftWeight + initialRightWeight

                            // Calculate the change in weight based on the drag distance
                            val weightDelta = (deltaX / totalParentWidth) * sumOfInitialWeights

                            var newLeftWeight = initialLeftWeight + weightDelta

                            // Clamp the new weight for the left pane
                            // Ensures it's not too small and not too large (leaving space for the other pane's minWeight)
                            newLeftWeight = newLeftWeight.coerceIn(minWeight, sumOfInitialWeights - minWeight)
                            val newRightWeight = sumOfInitialWeights - newLeftWeight

                            // Apply the new weights
                            leftParams.weight = newLeftWeight
                            rightParams.weight = newRightWeight

                            leftPane.layoutParams = leftParams
                            rightPane.layoutParams = rightParams

                            // Request layout update for the parent
                            requestLayout()
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.setBackgroundColor(idleColor)

                        // Save the new weights to SharedPreferences
                        sharedPrefs.edit {
                            if (leftPaneWeightKey == null || rightPaneWeightKey == null) {
                                Timber.w("leftPaneWeightKey or rightPaneWeightKey is null")
                                return@edit
                            }
                            putFloat(leftPaneWeightKey, leftParams.weight)
                            putFloat(rightPaneWeightKey, rightParams.weight)
                        }
                        true
                    }
                    else -> false
                }
            }
        }

        private fun loadSavedWeights() {
            try {
                val leftParams = leftPane.layoutParams as LayoutParams
                val rightParams = rightPane.layoutParams as LayoutParams

                // Load saved weights from SharedPreferences
                val savedLeftWeight =
                    if (leftPaneWeightKey !=
                        null
                    ) {
                        sharedPrefs.getFloat(leftPaneWeightKey, leftParams.weight)
                    } else {
                        leftParams.weight
                    }
                val savedRightWeight =
                    if (rightPaneWeightKey !=
                        null
                    ) {
                        sharedPrefs.getFloat(rightPaneWeightKey, rightParams.weight)
                    } else {
                        rightParams.weight
                    }

                // Apply the saved weights
                leftParams.weight = savedLeftWeight
                rightParams.weight = savedRightWeight

                leftPane.layoutParams = leftParams
                rightPane.layoutParams = rightParams

                // Request layout update for the parent
                requestLayout()
            } catch (e: Exception) {
                Timber.w(e, "Failed to load saved pane weights")
            }
        }

        private fun removeResizableDivider() {
            val leftParams = leftPane.layoutParams as LayoutParams
            val rightParams = rightPane.layoutParams as LayoutParams
            leftParams.weight = 1.0f
            rightParams.weight = 0f
            leftPane.layoutParams = leftParams
            rightPane.layoutParams = rightParams

            divider.visibility = GONE
            rightPane.visibility = GONE

            requestLayout()
            isResizable = false
        }
    }
