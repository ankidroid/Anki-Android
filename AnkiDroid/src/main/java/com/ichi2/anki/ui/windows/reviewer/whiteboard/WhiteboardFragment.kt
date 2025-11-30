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

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.ThemeUtils
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.anki.databinding.FragmentWhiteboardBinding
import com.ichi2.anki.databinding.PopupBrushOptionsBinding
import com.ichi2.anki.databinding.PopupEraserOptionsBinding
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.compat.setTooltipTextCompat
import com.ichi2.themes.Themes
import com.ichi2.utils.dp
import com.ichi2.utils.increaseHorizontalPaddingOfMenuIcons
import com.ichi2.utils.toRGBAHex
import dev.androidbroadcast.vbpd.viewBinding
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import kotlin.math.roundToInt

/**
 * Fragment that displays a whiteboard and its controls.
 */
class WhiteboardFragment :
    Fragment(R.layout.fragment_whiteboard),
    PopupMenu.OnMenuItemClickListener {
    private val viewModel: WhiteboardViewModel by viewModels {
        WhiteboardViewModel.factory(AnkiDroidApp.sharedPrefs())
    }

    val binding by viewBinding(FragmentWhiteboardBinding::bind)

    private var eraserPopup: PopupWindow? = null
    private var brushConfigPopup: PopupWindow? = null

    /**
     * Sets up the view, observers, and event listeners.
     */
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        val isNightMode = Themes.systemIsInNightMode(requireContext())
        viewModel.loadState(isNightMode)

        setupUI()
        observeViewModel(binding.whiteboardView)

        binding.whiteboardView.onNewPath = viewModel::addPath
        binding.whiteboardView.onEraseGestureStart = viewModel::startPathEraseGesture
        binding.whiteboardView.onEraseGestureMove = viewModel::erasePathsAtPoint
        binding.whiteboardView.onEraseGestureEnd = viewModel::endPathEraseGesture
    }

    private fun setupUI() {
        binding.overflowMenuButton.setOnClickListener {
            val popupMenu = PopupMenu(requireContext(), binding.overflowMenuButton)
            requireActivity().menuInflater.inflate(R.menu.whiteboard, popupMenu.menu)
            with(popupMenu.menu) {
                findItem(R.id.action_toggle_stylus).isChecked = viewModel.isStylusOnlyMode.value
                (this as? MenuBuilder)?.setOptionalIconsVisible(true)
                context?.increaseHorizontalPaddingOfMenuIcons(this)

                val alignmentMenuItemId =
                    when (viewModel.toolbarAlignment.value) {
                        ToolbarAlignment.LEFT -> R.id.action_align_left
                        ToolbarAlignment.RIGHT -> R.id.action_align_right
                        ToolbarAlignment.BOTTOM -> R.id.action_align_bottom
                    }
                findItem(alignmentMenuItemId).isEnabled = false
            }
            popupMenu.setOnMenuItemClickListener(this)
            popupMenu.show()
        }

        binding.undoButton.setOnClickListener { viewModel.undo() }
        binding.redoButton.setOnClickListener { viewModel.redo() }
        binding.eraserButton.setOnClickListener {
            if (viewModel.isEraserActive.value) {
                binding.eraserButton.isChecked = true
                if (eraserPopup?.isShowing == true) {
                    eraserPopup?.dismiss()
                } else {
                    showEraserOptionsPopup(it)
                }
            } else {
                viewModel.enableEraser()
            }
        }

        viewModel.canUndo.onEach { binding.undoButton.isEnabled = it }.launchIn(lifecycleScope)
        viewModel.canRedo.onEach { binding.redoButton.isEnabled = it }.launchIn(lifecycleScope)
    }

    /**
     * Sets up observers for the ViewModel's state flows.
     */
    private fun observeViewModel(whiteboardView: WhiteboardView) {
        viewModel.paths.onEach(whiteboardView::setHistory).launchIn(lifecycleScope)

        combine(
            viewModel.brushColor,
            viewModel.activeStrokeWidth,
        ) { color, width ->
            whiteboardView.setCurrentBrush(color, width)
        }.launchIn(lifecycleScope)

        combine(
            viewModel.isEraserActive,
            viewModel.eraserMode,
            viewModel.eraserDisplayWidth,
        ) { isActive, mode, width ->
            whiteboardView.isEraserActive = isActive
            binding.eraserButton.updateState(isActive, mode, width)
            whiteboardView.eraserMode = mode
            if (!isActive) {
                eraserPopup?.dismiss()
            }
        }.launchIn(lifecycleScope)

        viewModel.brushes
            .onEach { brushesInfo ->
                updateBrushToolbar(brushesInfo)
                updateToolbarSelection()
            }.launchIn(lifecycleScope)

        viewModel.activeBrushIndex.onEach { updateToolbarSelection() }.launchIn(lifecycleScope)
        viewModel.isEraserActive
            .onEach {
                updateToolbarSelection()
            }.launchIn(lifecycleScope)

        viewModel.isStylusOnlyMode
            .onEach { isEnabled ->
                whiteboardView.isStylusOnlyMode = isEnabled
            }.launchIn(lifecycleScope)

        viewModel.toolbarAlignment
            .onEach { alignment ->
                updateLayoutForAlignment(alignment)
            }.launchIn(lifecycleScope)
    }

    private fun updateBrushToolbar(brushesInfo: List<BrushInfo>) {
        binding.brushToolbarContainerHorizontal.removeAllViews()
        binding.brushToolbarContainerVertical.removeAllViews()
        brushesInfo.forEachIndexed { index, brush ->
            val inflater = LayoutInflater.from(requireContext())
            val buttonHorizontal =
                inflater.inflate(
                    R.layout.button_color_brush,
                    binding.brushToolbarContainerHorizontal,
                    false,
                ) as MaterialButton
            configureBrushButton(buttonHorizontal, brush, index)
            binding.brushToolbarContainerHorizontal.addView(buttonHorizontal)

            val buttonVertical =
                inflater.inflate(
                    R.layout.button_color_brush,
                    binding.brushToolbarContainerVertical,
                    false,
                ) as MaterialButton
            configureBrushButton(buttonVertical, brush, index)
            binding.brushToolbarContainerVertical.addView(buttonVertical)
        }

        fun addBrushButton() =
            MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialIconButtonStyle).apply {
                setIconResource(R.drawable.ic_add)
                setTooltipTextCompat(getString(R.string.add_brush))
                val color = ThemeUtils.getThemeAttrColor(requireContext(), androidx.appcompat.R.attr.colorControlNormal)
                iconTint = ColorStateList.valueOf(color)
                setOnClickListener { showAddColorDialog() }
            }
        binding.brushToolbarContainerHorizontal.addView(addBrushButton())
        binding.brushToolbarContainerVertical.addView(addBrushButton())
    }

    /**
     * Configures a brush button's properties and listeners.
     */
    private fun configureBrushButton(
        button: MaterialButton,
        brush: BrushInfo,
        index: Int,
    ) {
        button.isCheckable = true
        button.text = brush.width.roundToInt().toString()
        button.tag = index
        button.iconTint = null

        (button.icon?.mutate() as? LayerDrawable)?.let { layerDrawable ->
            (layerDrawable.findDrawableByLayerId(R.id.brush_preview_fill) as? GradientDrawable)?.setColor(brush.color)
        }

        button.setOnClickListener {
            if (viewModel.activeBrushIndex.value == index && !viewModel.isEraserActive.value) {
                button.isChecked = true
                showBrushConfigurationPopup(it, index)
            } else {
                viewModel.setActiveBrush(index)
            }
        }

        button.setOnLongClickListener {
            if (viewModel.brushes.value.size > 1) {
                showRemoveColorDialog(index)
            } else {
                Timber.i("Tried to remove the last brush of the whiteboard")
                showSnackbar(R.string.cannot_remove_last_brush_message)
            }
            true
        }
    }

    /**
     * Updates the selection state of the eraser and brush buttons.
     */
    private fun updateToolbarSelection() {
        val activeIndex = viewModel.activeBrushIndex.value
        val isEraserActive = viewModel.isEraserActive.value

        val configureSelection: (View) -> Unit = { view ->
            val button = view as MaterialButton
            val buttonIndex = button.tag as? Int
            button.isChecked = (buttonIndex == activeIndex && !isEraserActive)
        }

        binding.brushToolbarContainerHorizontal.children.forEach(configureSelection)
        binding.brushToolbarContainerVertical.children.forEach(configureSelection)
    }

    /**
     * Shows a popup for adding a new brush color.
     */
    private fun showAddColorDialog() {
        requireContext()
            .showColorPickerDialog(viewModel.brushColor.value) { color ->
                Timber.i("Added brush with color ${color.toRGBAHex()}")
                viewModel.addBrush(color)
            }
    }

    /**
     * Shows a confirmation dialog for removing a brush.
     */
    private fun showRemoveColorDialog(index: Int) {
        AlertDialog
            .Builder(requireContext())
            .setMessage(R.string.whiteboard_remove_brush_message)
            .setPositiveButton(R.string.dialog_remove) { dialog, _ ->
                Timber.i("Removed brush of index %d", index)
                viewModel.removeBrush(index)
            }.setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    /**
     * Shows a popup for adjusting the stroke width or color of a specific brush.
     */
    private fun showBrushConfigurationPopup(
        anchorView: View,
        brushIndex: Int,
    ) {
        Timber.i("Showing brush %d popup", brushIndex)

        val inflater = LayoutInflater.from(requireContext())
        val popupBrushBinding = PopupBrushOptionsBinding.inflate(inflater)

        val currentBrush = viewModel.brushes.value.getOrNull(brushIndex) ?: return

        val previewDrawable = (popupBrushBinding.colorPickerButton.icon as? LayerDrawable)
        val fillDrawable = previewDrawable?.findDrawableByLayerId(R.id.brush_preview_fill) as? GradientDrawable
        fillDrawable?.setColor(currentBrush.color)
        popupBrushBinding.colorPickerButton.icon = previewDrawable

        popupBrushBinding.colorPickerButton.setOnClickListener {
            showChangeColorDialog()
        }

        popupBrushBinding.strokeWidthSlider.value = currentBrush.width
        popupBrushBinding.strokeWidthValueIndicator.text = currentBrush.width.roundToInt().toString()
        popupBrushBinding.colorPickerButton.iconSize = currentBrush.width.roundToInt()

        // Set slider colors
        val color = currentBrush.color
        val colorStateList = ColorStateList.valueOf(color)
        popupBrushBinding.strokeWidthSlider.trackActiveTintList = colorStateList
        popupBrushBinding.strokeWidthSlider.thumbTintList = colorStateList
        popupBrushBinding.strokeWidthSlider.haloTintList = colorStateList

        popupBrushBinding.strokeWidthSlider.addOnChangeListener { _, value, fromUser ->
            // Dynamically change the size of the brush preview icon
            popupBrushBinding.colorPickerButton.iconSize = value.roundToInt()
            popupBrushBinding.strokeWidthValueIndicator.text = value.roundToInt().toString()

            if (fromUser) viewModel.setActiveStrokeWidth(value)
        }
        popupBrushBinding.strokeWidthSlider.setLabelFormatter { value: Float ->
            value.roundToInt().toString()
        }

        brushConfigPopup =
            PopupWindow(popupBrushBinding.root, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        brushConfigPopup?.elevation = resources.getDimension(R.dimen.study_screen_elevation)
        brushConfigPopup?.setOnDismissListener {
            brushConfigPopup = null
        }

        popupBrushBinding.root.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val yOffset = -(anchorView.height + popupBrushBinding.root.measuredHeight)
        val xOffset = (anchorView.width - popupBrushBinding.root.measuredWidth) / 2
        brushConfigPopup?.showAsDropDown(anchorView, xOffset, yOffset)
    }

    /**
     * Shows a color picker popup to change the active brush's color.
     */
    private fun showChangeColorDialog() {
        requireContext()
            .showColorPickerDialog(viewModel.brushColor.value) { color ->
                viewModel.updateBrushColor(color)
                brushConfigPopup?.dismiss()
            }
    }

    /**
     * Shows a popup with eraser options (mode, width, clear).
     */
    private fun showEraserOptionsPopup(anchorView: View) {
        val inflater = LayoutInflater.from(requireContext())
        val eraserWidthBinding = PopupEraserOptionsBinding.inflate(inflater)

        eraserWidthBinding.eraserWidthSlider.value = viewModel.eraserDisplayWidth.value
        eraserWidthBinding.eraserWidthSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) viewModel.setActiveStrokeWidth(value)
        }
        eraserWidthBinding.eraserWidthSlider.setLabelFormatter { value: Float ->
            value.roundToInt().toString()
        }

        eraserWidthBinding.eraserModeToggleGroup.clearOnButtonCheckedListeners()
        when (viewModel.eraserMode.value) {
            EraserMode.STROKE -> eraserWidthBinding.eraserModeToggleGroup.check(R.id.eraser_mode_stroke)
            EraserMode.INK -> eraserWidthBinding.eraserModeToggleGroup.check(R.id.eraser_mode_ink)
        }
        eraserWidthBinding.eraserModeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.eraser_mode_stroke -> {
                        viewModel.setEraserMode(EraserMode.STROKE)
                        eraserWidthBinding.eraserWidthSlider.value =
                            viewModel.strokeEraserStrokeWidth.value
                    }

                    R.id.eraser_mode_ink -> {
                        viewModel.setEraserMode(EraserMode.INK)
                        eraserWidthBinding.eraserWidthSlider.value =
                            viewModel.inkEraserStrokeWidth.value
                    }
                }
            }
        }

        eraserWidthBinding.clearButton.setOnClickListener {
            viewModel.clearCanvas()
            eraserPopup?.dismiss()
        }

        eraserPopup = PopupWindow(eraserWidthBinding.root, 360.dp.toPx(requireContext()), ViewGroup.LayoutParams.WRAP_CONTENT, true)
        eraserPopup?.elevation = 8f
        eraserPopup?.setOnDismissListener {
            updateToolbarSelection()
            eraserPopup = null
        }

        eraserWidthBinding.root.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val yOffset = -(anchorView.height + eraserWidthBinding.root.measuredHeight)
        val xOffset = (anchorView.width - eraserWidthBinding.root.measuredWidth) / 2
        eraserPopup?.showAsDropDown(anchorView, xOffset, yOffset)
    }

    /**
     * Updates the toolbar's constraints and orientation.
     */
    private fun updateLayoutForAlignment(alignment: ToolbarAlignment) {
        val isVertical = alignment == ToolbarAlignment.LEFT || alignment == ToolbarAlignment.RIGHT
        binding.innerControlsLayout.orientation = if (isVertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL

        if (isVertical) {
            binding.brushScrollViewHorizontal.visibility = View.GONE
            binding.brushScrollViewVertical.visibility = View.VISIBLE
        } else {
            binding.brushScrollViewHorizontal.visibility = View.VISIBLE
            binding.brushScrollViewVertical.visibility = View.GONE
        }

        val dp = 1.dp.toPx(requireContext())
        val dividerParams = binding.controlsDivider.layoutParams as LinearLayout.LayoutParams
        val dividerMargin = 4 * dp
        if (isVertical) {
            dividerParams.width = LinearLayout.LayoutParams.MATCH_PARENT
            dividerParams.height = 1 * dp
            dividerParams.setMargins(0, dividerMargin, 0, dividerMargin)
        } else {
            dividerParams.width = 1 * dp
            dividerParams.height = LinearLayout.LayoutParams.MATCH_PARENT
            dividerParams.setMargins(dividerMargin, 0, dividerMargin, 0)
        }
        binding.controlsDivider.layoutParams = dividerParams

        val constraintSet = ConstraintSet()
        constraintSet.clone(binding.root)
        val containerId = binding.controlsContainer.id
        constraintSet.clear(containerId)

        when (alignment) {
            ToolbarAlignment.BOTTOM -> {
                constraintSet.connect(containerId, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
                constraintSet.connect(containerId, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                constraintSet.connect(containerId, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                constraintSet.constrainWidth(containerId, ConstraintSet.WRAP_CONTENT)
                constraintSet.constrainHeight(containerId, ConstraintSet.WRAP_CONTENT)
                constraintSet.constrainedWidth(containerId, true)
                constraintSet.setMargin(containerId, ConstraintSet.START, 24 * dp)
                constraintSet.setMargin(containerId, ConstraintSet.END, 24 * dp)
                constraintSet.setMargin(containerId, ConstraintSet.BOTTOM, 8 * dp)
            }
            ToolbarAlignment.RIGHT -> {
                constraintSet.connect(containerId, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                constraintSet.connect(containerId, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                constraintSet.connect(containerId, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
                constraintSet.constrainWidth(containerId, ConstraintSet.WRAP_CONTENT)
                constraintSet.constrainHeight(containerId, ConstraintSet.WRAP_CONTENT)
                constraintSet.setMargin(containerId, ConstraintSet.END, 8 * dp)
            }
            ToolbarAlignment.LEFT -> {
                constraintSet.connect(containerId, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                constraintSet.connect(containerId, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                constraintSet.connect(containerId, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
                constraintSet.constrainWidth(containerId, ConstraintSet.WRAP_CONTENT)
                constraintSet.constrainHeight(containerId, ConstraintSet.WRAP_CONTENT)
                constraintSet.setMargin(containerId, ConstraintSet.START, 8 * dp)
            }
        }

        constraintSet.applyTo(binding.root)
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        Timber.i("WhiteboardFragment::onMenuItemClick %s", item.title)
        when (item.itemId) {
            R.id.action_toggle_stylus -> {
                item.isChecked = !item.isChecked
                viewModel.toggleStylusOnlyMode()
            }
            R.id.action_align_left -> viewModel.setToolbarAlignment(ToolbarAlignment.LEFT)
            R.id.action_align_bottom -> viewModel.setToolbarAlignment(ToolbarAlignment.BOTTOM)
            R.id.action_align_right -> viewModel.setToolbarAlignment(ToolbarAlignment.RIGHT)
            else -> return false
        }
        return true
    }

    fun resetCanvas() = viewModel.reset()

    /**
     * @return whether the whiteboard is completely empty, including the undo and redo stacks.
     */
    fun isEmpty(): Boolean = !viewModel.canUndo.value && !viewModel.canRedo.value
}
