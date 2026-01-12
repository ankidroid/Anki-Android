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
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.card.MaterialCardView
import com.ichi2.anki.databinding.ViewWhiteboardToolbarBinding
import com.ichi2.utils.dp

/**
 * Tools configuration bar to be used along [WhiteboardView]
 */
class WhiteboardToolbar : MaterialCardView {
    private val binding: ViewWhiteboardToolbarBinding
    private val brushAdapter: BrushAdapter

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, com.google.android.material.R.attr.materialCardViewStyle)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        binding = ViewWhiteboardToolbarBinding.inflate(LayoutInflater.from(context), this)

        brushAdapter =
            BrushAdapter(
                onBrushClick = { view, index -> onBrushClick?.invoke(view, index) },
                onBrushLongClick = { index -> onBrushLongClick?.invoke(index) },
            )

        binding.brushRecyclerView.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = brushAdapter
        }
    }

    val undoButton get() = binding.undoButton
    val redoButton get() = binding.redoButton
    val eraserButton get() = binding.eraserButton
    val overflowButton get() = binding.overflowMenuButton

    var onBrushClick: ((view: View, index: Int) -> Unit)? = null
    var onBrushLongClick: ((index: Int) -> Unit)? = null

    /**
     * Updates the internal layout based on the toolbar alignment.
     * Switches the RecyclerView orientation and the main layout orientation.
     */
    fun setAlignment(alignment: ToolbarAlignment) {
        val isVertical = alignment == ToolbarAlignment.LEFT || alignment == ToolbarAlignment.RIGHT

        binding.innerControlsLayout.orientation = if (isVertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL

        val layoutManager = binding.brushRecyclerView.layoutManager as? LinearLayoutManager
        layoutManager?.orientation = if (isVertical) LinearLayoutManager.VERTICAL else LinearLayoutManager.HORIZONTAL

        val dp = 1.dp.toPx(context)
        val dividerMargin = 4 * dp
        val dividerParams = binding.controlsDivider.layoutParams as LinearLayout.LayoutParams
        if (isVertical) {
            dividerParams.width = LinearLayout.LayoutParams.MATCH_PARENT
            dividerParams.height = 1 * dp
            dividerParams.setMargins(0, dividerMargin, 0, dividerMargin)
            binding.innerControlsLayout.updateLayoutParams<LayoutParams> {
                marginEnd = 0
            }
        } else {
            dividerParams.width = 1 * dp
            dividerParams.height = LinearLayout.LayoutParams.MATCH_PARENT
            dividerParams.setMargins(dividerMargin, 0, dividerMargin, 0)
            // leave some space after the brushes
            binding.innerControlsLayout.updateLayoutParams<LayoutParams> {
                marginEnd = dividerMargin
            }
        }
    }

    /**
     * Updates the data in the RecyclerView adapter.
     */
    fun setBrushes(
        brushes: List<BrushInfo>,
        activeIndex: Int,
        isEraserActive: Boolean,
    ) {
        brushAdapter.updateData(brushes, activeIndex, isEraserActive)
    }

    /**
     * Updates the checked state of the brush buttons in the adapter.
     */
    fun updateSelection(
        activeIndex: Int,
        isEraserActive: Boolean,
    ) {
        brushAdapter.updateSelection(activeIndex, isEraserActive)
    }
}
