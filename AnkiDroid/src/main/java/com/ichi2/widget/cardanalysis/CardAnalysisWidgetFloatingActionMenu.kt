/*
 *  Copyright (c) 2025 Snowiee <xenonnn4w@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.widget.cardanalysis

import android.view.View
import android.widget.LinearLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.ichi2.anki.R

/**
 * Manages the floating action menu for the Card Analysis Widget configuration.
 * This class handles the animation and visibility of the floating action buttons.
 */
class CardAnalysisWidgetFloatingActionMenu(
    rootView: View,
    private val listener: CardAnalysisWidgetFloatingActionMenuListener,
) {
    private val fabMain: FloatingActionButton = rootView.findViewById(R.id.fab_main)
    private val themeLayout: LinearLayout = rootView.findViewById(R.id.theme_layout)
    private val themeAction: FloatingActionButton = rootView.findViewById(R.id.theme_action)
    private val addDeckLabel: View = rootView.findViewById(R.id.add_deck_label)
    private val fabBackground: View = rootView.findViewById(R.id.fab_bg_layout)

    private var isMenuExpanded = false

    init {
        setupClickListeners()
    }

    private fun setupClickListeners() {
        fabMain.setOnClickListener {
            if (isMenuExpanded) {
                listener.onAddDeckClicked()
                collapseMenu()
            } else {
                expandMenu()
            }
        }

        themeAction.setOnClickListener {
            listener.onThemingClicked()
            collapseMenu()
        }

        fabBackground.setOnClickListener {
            collapseMenu()
        }
    }

    private fun expandMenu() {
        isMenuExpanded = true
        fabMain.setImageResource(R.drawable.ic_add_white)

        // Show background overlay
        fabBackground.visibility = View.VISIBLE
        fabBackground
            .animate()
            .alpha(1f)
            .setDuration(200)
            .start()

        // Animate theme layout (top button)
        themeLayout.visibility = View.VISIBLE
        themeLayout
            .animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(200)
            .start()

        // Animate add deck label
        addDeckLabel.visibility = View.VISIBLE
        addDeckLabel
            .animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(200)
            .start()
    }

    private fun collapseMenu() {
        isMenuExpanded = false
        fabMain.setImageResource(R.drawable.ic_settings_black)

        fabBackground
            .animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                fabBackground.visibility = View.GONE
            }.start()

        themeLayout
            .animate()
            .translationY(300f)
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                themeLayout.visibility = View.GONE
            }.start()

        addDeckLabel
            .animate()
            .translationX(50f)
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                addDeckLabel.visibility = View.GONE
            }.start()
    }

    /**
     * Interface for handling floating action menu events.
     */
    interface CardAnalysisWidgetFloatingActionMenuListener {
        fun onAddDeckClicked()

        fun onThemingClicked()
    }
}
