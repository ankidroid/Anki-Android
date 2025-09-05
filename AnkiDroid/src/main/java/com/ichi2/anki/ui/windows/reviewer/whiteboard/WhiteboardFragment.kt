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
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.ThemeUtils
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.slider.Slider
import com.ichi2.anki.R
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.compat.setTooltipTextCompat
import com.ichi2.themes.Themes
import com.ichi2.utils.dp
import com.ichi2.utils.increaseHorizontalPaddingOfMenuIcons
import com.mrudultora.colorpicker.ColorPickerPopUp
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
        WhiteboardViewModel.factory(sharedPrefs())
    }
    private lateinit var brushToolbarContainerHorizontal: LinearLayout
    private lateinit var brushToolbarContainerVertical: LinearLayout
    private var eraserPopup: PopupWindow? = null
    private var strokeWidthPopup: PopupWindow? = null

    /**
     * Sets up the view, observers, and event listeners.
     */
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        val whiteboardView = view.findViewById<WhiteboardView>(R.id.whiteboard_view)
        brushToolbarContainerHorizontal = view.findViewById(R.id.brush_toolbar_container_horizontal)
        brushToolbarContainerVertical = view.findViewById(R.id.brush_toolbar_container_vertical)

        val isNightMode = Themes.systemIsInNightMode(requireContext())
        viewModel.loadState(isNightMode)

        setupUI(view)
        observeViewModel(whiteboardView)

        whiteboardView.onNewPath = viewModel::addPath
        whiteboardView.onEraseGestureStart = viewModel::startPathEraseGesture
        whiteboardView.onEraseGestureMove = viewModel::erasePathsAtPoint
        whiteboardView.onEraseGestureEnd = viewModel::endPathEraseGesture
    }

    private fun setupUI(view: View) {
        val undoButton = view.findViewById<ImageButton>(R.id.undo_button)
        val redoButton = view.findViewById<ImageButton>(R.id.redo_button)
        val eraserButton = view.findViewById<EraserButton>(R.id.eraser_button)

        val overflowMenuButton = view.findViewById<ImageButton>(R.id.overflow_menu_button)
        overflowMenuButton.setOnClickListener {
            val popupMenu = PopupMenu(requireContext(), overflowMenuButton)
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

        undoButton.setOnClickListener { viewModel.undo() }
        redoButton.setOnClickListener { viewModel.redo() }
        eraserButton.setOnClickListener {
            if (viewModel.isEraserActive.value) {
                eraserButton.isChecked = true
                if (eraserPopup?.isShowing == true) {
                    eraserPopup?.dismiss()
                } else {
                    showEraserOptionsPopup(it)
                }
            } else {
                viewModel.enableEraser()
            }
        }

        viewModel.canUndo.onEach { undoButton.isEnabled = it }.launchIn(lifecycleScope)
        viewModel.canRedo.onEach { redoButton.isEnabled = it }.launchIn(lifecycleScope)
    }

    /**
     * Sets up observers for the ViewModel's state flows.
     */
    private fun observeViewModel(whiteboardView: WhiteboardView) {
        val eraserButton = view?.findViewById<EraserButton>(R.id.eraser_button)

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
            eraserButton?.updateState(isActive, mode, width)
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
        brushToolbarContainerHorizontal.removeAllViews()
        brushToolbarContainerVertical.removeAllViews()
        brushesInfo.forEachIndexed { index, brush ->
            val inflater = LayoutInflater.from(requireContext())
            val buttonHorizontal = inflater.inflate(R.layout.button_color_brush, brushToolbarContainerHorizontal, false) as MaterialButton
            configureBrushButton(buttonHorizontal, brush, index)
            brushToolbarContainerHorizontal.addView(buttonHorizontal)

            val buttonVertical = inflater.inflate(R.layout.button_color_brush, brushToolbarContainerVertical, false) as MaterialButton
            configureBrushButton(buttonVertical, brush, index)
            brushToolbarContainerVertical.addView(buttonVertical)
        }

        fun addBrushButton() =
            MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialIconButtonStyle).apply {
                setIconResource(R.drawable.ic_add)
                setTooltipTextCompat(getString(R.string.add_brush))
                val color = ThemeUtils.getThemeAttrColor(requireContext(), androidx.appcompat.R.attr.colorControlNormal)
                iconTint = ColorStateList.valueOf(color)
                setOnClickListener { showAddColorDialog() }
            }
        brushToolbarContainerHorizontal.addView(addBrushButton())
        brushToolbarContainerVertical.addView(addBrushButton())
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
                showStrokeWidthPopup(it, index)
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

        brushToolbarContainerHorizontal.children.forEach(configureSelection)
        brushToolbarContainerVertical.children.forEach(configureSelection)
    }

    /**
     * Shows a popup for adding a new brush color.
     */
    private fun showAddColorDialog() {
        ColorPickerPopUp(context).run {
            setShowAlpha(true)
            setDefaultColor(viewModel.brushColor.value)
            setOnPickColorListener(
                object : ColorPickerPopUp.OnPickColorListener {
                    override fun onColorPicked(color: Int) {
                        Timber.i("Added brush with color %d", color)
                        viewModel.addBrush(color)
                    }

                    override fun onCancel() {}
                },
            )
            show()
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
     * Shows a popup for adjusting the stroke width of a specific brush.
     */
    private fun showStrokeWidthPopup(
        anchorView: View,
        brushIndex: Int,
    ) {
        Timber.i("Showing brush %d popup", brushIndex)
        val inflater = LayoutInflater.from(requireContext())
        val popupView = inflater.inflate(R.layout.popup_brush_options, null)
        val strokeSlider = popupView.findViewById<Slider>(R.id.stroke_width_slider)
        val colorButton = popupView.findViewById<MaterialButton>(R.id.color_picker_button)
        val valueIndicator = popupView.findViewById<TextView>(R.id.stroke_width_value_indicator)

        val currentBrush = viewModel.brushes.value.getOrNull(brushIndex) ?: return

        val previewDrawable = (colorButton.icon as? LayerDrawable)
        val fillDrawable = previewDrawable?.findDrawableByLayerId(R.id.brush_preview_fill) as? GradientDrawable
        fillDrawable?.setColor(currentBrush.color)
        colorButton.icon = previewDrawable

        colorButton.setOnClickListener {
            showChangeColorDialog()
        }

        strokeSlider.value = currentBrush.width
        valueIndicator.text = currentBrush.width.roundToInt().toString()
        colorButton.iconSize = currentBrush.width.roundToInt()

        // Set slider colors
        val color = currentBrush.color
        val colorStateList = ColorStateList.valueOf(color)
        strokeSlider.trackActiveTintList = colorStateList
        strokeSlider.thumbTintList = colorStateList
        strokeSlider.haloTintList = colorStateList

        strokeSlider.addOnChangeListener { _, value, fromUser ->
            // Dynamically change the size of the brush preview icon
            colorButton.iconSize = value.roundToInt()
            valueIndicator.text = value.roundToInt().toString()

            if (fromUser) viewModel.setActiveStrokeWidth(value)
        }
        strokeSlider.setLabelFormatter { value: Float -> value.roundToInt().toString() }

        strokeWidthPopup = PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        strokeWidthPopup?.elevation = resources.getDimension(R.dimen.study_screen_elevation)
        strokeWidthPopup?.setOnDismissListener {
            updateToolbarSelection()
            strokeWidthPopup = null
        }

        popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val yOffset = -(anchorView.height + popupView.measuredHeight)
        val xOffset = (anchorView.width - popupView.measuredWidth) / 2
        strokeWidthPopup?.showAsDropDown(anchorView, xOffset, yOffset)
    }

    /**
     * Shows a color picker popup to change the active brush's color.
     */
    private fun showChangeColorDialog() {
        ColorPickerPopUp(requireContext())
            .setShowAlpha(true)
            .setDefaultColor(viewModel.brushColor.value)
            .setOnPickColorListener(
                object : ColorPickerPopUp.OnPickColorListener {
                    override fun onColorPicked(color: Int) {
                        viewModel.updateBrushColor(color)
                        strokeWidthPopup?.dismiss()
                    }

                    override fun onCancel() {}
                },
            ).show()
    }

    /**
     * Shows a popup with eraser options (mode, width, clear).
     */
    private fun showEraserOptionsPopup(anchorView: View) {
        val inflater = LayoutInflater.from(requireContext())
        val popupView = inflater.inflate(R.layout.popup_eraser_options, null)
        val slider = popupView.findViewById<Slider>(R.id.eraser_width_slider)
        val toggleGroup = popupView.findViewById<MaterialButtonToggleGroup>(R.id.eraser_mode_toggle_group)
        val clearButton = popupView.findViewById<MaterialButton>(R.id.clear_button_popup)

        slider.value = viewModel.eraserDisplayWidth.value
        slider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) viewModel.setActiveStrokeWidth(value)
        }
        slider.setLabelFormatter { value: Float -> value.roundToInt().toString() }

        toggleGroup.clearOnButtonCheckedListeners()
        when (viewModel.eraserMode.value) {
            EraserMode.STROKE -> toggleGroup.check(R.id.eraser_mode_stroke)
            EraserMode.INK -> toggleGroup.check(R.id.eraser_mode_ink)
        }
        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.eraser_mode_stroke -> {
                        viewModel.setEraserMode(EraserMode.STROKE)
                        slider.value = viewModel.strokeEraserStrokeWidth.value
                    }
                    R.id.eraser_mode_ink -> {
                        viewModel.setEraserMode(EraserMode.INK)
                        slider.value = viewModel.inkEraserStrokeWidth.value
                    }
                }
            }
        }

        clearButton.setOnClickListener {
            viewModel.clearCanvas()
            eraserPopup?.dismiss()
        }

        eraserPopup = PopupWindow(popupView, 360.dp.toPx(requireContext()), ViewGroup.LayoutParams.WRAP_CONTENT, true)
        eraserPopup?.elevation = 8f
        eraserPopup?.setOnDismissListener {
            updateToolbarSelection()
            eraserPopup = null
        }

        popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val yOffset = -(anchorView.height + popupView.measuredHeight)
        val xOffset = (anchorView.width - popupView.measuredWidth) / 2
        eraserPopup?.showAsDropDown(anchorView, xOffset, yOffset)
    }

    /**
     * Updates the toolbar's constraints and orientation.
     */
    private fun updateLayoutForAlignment(alignment: ToolbarAlignment) {
        val controlsContainer = view?.findViewById<View>(R.id.controls_container) ?: return
        val innerLayout = view?.findViewById<LinearLayout>(R.id.inner_controls_layout) ?: return
        val divider = view?.findViewById<View>(R.id.controls_divider) ?: return
        val rootLayout = view as? ConstraintLayout ?: return
        val brushScrollViewHorizontal = view?.findViewById<HorizontalScrollView>(R.id.brush_scroll_view_horizontal) ?: return
        val brushScrollViewVertical = view?.findViewById<ScrollView>(R.id.brush_scroll_view_vertical) ?: return

        val isVertical = alignment == ToolbarAlignment.LEFT || alignment == ToolbarAlignment.RIGHT
        innerLayout.orientation = if (isVertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL

        if (isVertical) {
            brushScrollViewHorizontal.visibility = View.GONE
            brushScrollViewVertical.visibility = View.VISIBLE
        } else {
            brushScrollViewHorizontal.visibility = View.VISIBLE
            brushScrollViewVertical.visibility = View.GONE
        }

        val dp = 1.dp.toPx(requireContext())
        val dividerParams = divider.layoutParams as LinearLayout.LayoutParams
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
        divider.layoutParams = dividerParams

        val constraintSet = ConstraintSet()
        constraintSet.clone(rootLayout)
        val containerId = controlsContainer.id
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

        constraintSet.applyTo(rootLayout)
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
}
