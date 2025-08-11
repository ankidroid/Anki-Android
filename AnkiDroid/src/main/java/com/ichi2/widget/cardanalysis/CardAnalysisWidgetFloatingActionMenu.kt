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
    private val addDeckLayout: LinearLayout = rootView.findViewById(R.id.add_deck_layout)
    private val addDeckAction: FloatingActionButton = rootView.findViewById(R.id.add_deck_action)
    private val themingLabel: View = rootView.findViewById(R.id.theming_label)
    private val fabBackground: View = rootView.findViewById(R.id.fab_bg_layout)

    private var isMenuExpanded = false

    init {
        setupClickListeners()
    }

    private fun setupClickListeners() {
        fabMain.setOnClickListener {
            if (isMenuExpanded) {
                listener.onThemingClicked()
                collapseMenu()
            } else {
                expandMenu()
            }
        }

        addDeckAction.setOnClickListener {
            listener.onAddDeckClicked()
            collapseMenu()
        }

        fabBackground.setOnClickListener {
            collapseMenu()
        }
    }

    private fun expandMenu() {
        isMenuExpanded = true

        // Show background overlay
        fabBackground.visibility = View.VISIBLE
        fabBackground
            .animate()
            .alpha(1f)
            .setDuration(200)
            .start()

        // Animate add deck layout
        addDeckLayout.visibility = View.VISIBLE
        addDeckLayout
            .animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(200)
            .start()

        // Animate theming label
        themingLabel.visibility = View.VISIBLE
        themingLabel
            .animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(200)
            .start()
    }

    private fun collapseMenu() {
        isMenuExpanded = false

        fabBackground
            .animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                fabBackground.visibility = View.GONE
            }.start()

        addDeckLayout
            .animate()
            .translationY(300f)
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                addDeckLayout.visibility = View.GONE
            }.start()

        themingLabel
            .animate()
            .translationX(50f)
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                themingLabel.visibility = View.GONE
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
