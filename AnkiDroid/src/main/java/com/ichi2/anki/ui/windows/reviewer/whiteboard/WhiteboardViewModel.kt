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
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Path
import android.graphics.PathMeasure
import androidx.annotation.CheckResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ichi2.anki.common.utils.ext.indexOfOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import timber.log.Timber

/**
 * Represents a single drawing action on the whiteboard, such as a brush stroke or an eraser mark.
 */
data class DrawingAction(
    val path: Path,
    val color: Int,
    val strokeWidth: Float,
    val isEraser: Boolean = false,
)

/**
 * Defines the available eraser modes: erasing by pixel or by entire path.
 */
enum class EraserMode { INK, STROKE }

/**
 * Defines the available toolbar alignment options.
 */
enum class ToolbarAlignment { LEFT, RIGHT, BOTTOM }

/**
 * Represents a command that can be undone and redone.
 */
sealed class UndoableAction

data class AddAction(
    val added: DrawingAction,
) : UndoableAction()

data class RemoveAction(
    val removed: List<Pair<Int, DrawingAction>>,
) : UndoableAction()

data class ClearAction(
    val cleared: List<DrawingAction>,
) : UndoableAction()

/**
 * Manages the state and business logic for the whiteboard.
 * This includes handling drawing paths, undo/redo stacks, brush/eraser settings,
 * and persisting user preferences via the WhiteboardRepository.
 */
class WhiteboardViewModel(
    private val repository: WhiteboardRepository,
) : ViewModel() {
    // State for drawing history and undo/redo
    val paths = MutableStateFlow<List<DrawingAction>>(emptyList())
    private val undoStack = MutableStateFlow<List<UndoableAction>>(emptyList())
    private val redoStack = MutableStateFlow<List<UndoableAction>>(emptyList())
    val canUndo = undoStack.map { it.isNotEmpty() }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val canRedo = redoStack.map { it.isNotEmpty() }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // State for brushes
    val brushes = MutableStateFlow<List<BrushInfo>>(emptyList())
    val activeBrushIndex = MutableStateFlow(0)

    // State for the currently active tool's properties
    val inkEraserStrokeWidth = MutableStateFlow(WhiteboardRepository.DEFAULT_ERASER_WIDTH)
    val strokeEraserStrokeWidth = MutableStateFlow(WhiteboardRepository.DEFAULT_ERASER_WIDTH)

    val brushColor = MutableStateFlow(Color.BLACK)
    val activeStrokeWidth = MutableStateFlow(WhiteboardRepository.DEFAULT_STROKE_WIDTH)
    val isEraserActive = MutableStateFlow(false)
    val eraserMode = MutableStateFlow(EraserMode.INK)
    val isStylusOnlyMode = MutableStateFlow(false)
    val toolbarAlignment = MutableStateFlow(ToolbarAlignment.BOTTOM)
    val isToolbarShown = MutableStateFlow(true)

    val eraserDisplayWidth =
        combine(eraserMode, inkEraserStrokeWidth, strokeEraserStrokeWidth) { mode, inkWidth, strokeWidth ->
            if (mode == EraserMode.INK) inkWidth else strokeWidth
        }.stateIn(viewModelScope, SharingStarted.Eagerly, WhiteboardRepository.DEFAULT_ERASER_WIDTH)

    private val pathsErasedInCurrentGesture = mutableListOf<DrawingAction>()
    private var pathsBeforeGesture: List<DrawingAction> = emptyList()
    private var isDarkMode = false

    /**
     * Loads saved preferences for brushes and eraser settings from the repository.
     */
    fun loadState(isDarkMode: Boolean) {
        this.isDarkMode = isDarkMode
        brushes.value = repository.loadBrushes(isDarkMode)
        inkEraserStrokeWidth.value = repository.inkEraserWidth
        strokeEraserStrokeWidth.value = repository.strokeEraserWidth
        eraserMode.value = repository.eraserMode
        isStylusOnlyMode.value = repository.stylusOnlyMode
        toolbarAlignment.value = repository.toolbarAlignment
        isToolbarShown.value = repository.isToolbarShown

        val lastActiveIndex = repository.loadLastActiveBrushIndex(isDarkMode)

        if (!isEraserActive.value) {
            setActiveBrush(lastActiveIndex)
        }
    }

    /**
     * Adds a new completed path to the drawing history.
     */
    fun addPath(path: Path) {
        val isPixelEraser = isEraserActive.value && eraserMode.value == EraserMode.INK
        val newAction =
            DrawingAction(
                path,
                brushColor.value,
                activeStrokeWidth.value,
                isPixelEraser,
            )
        paths.update { it + newAction }
        undoStack.update { it + AddAction(newAction) }
        redoStack.value = emptyList()
    }

    /**
     * Clears the list of paths erased in the current gesture.
     */
    fun startPathEraseGesture() {
        pathsBeforeGesture = paths.value
        pathsErasedInCurrentGesture.clear()
    }

    /**
     * Finds and removes paths that intersect with the given point.
     */
    fun erasePathsAtPoint(
        x: Float,
        y: Float,
    ) {
        if (paths.value.isEmpty()) return

        val remainingPaths = paths.value.toMutableList()
        var pathWasErased = false

        val pathsToEvaluate = remainingPaths.filter { it !in pathsErasedInCurrentGesture && !it.isEraser }

        for (action in pathsToEvaluate) {
            if (isPathIntersectingWithCircle(action, x, y, activeStrokeWidth.value / 2)) {
                remainingPaths.remove(action)
                pathsErasedInCurrentGesture.add(action)
                pathWasErased = true
            }
        }

        if (pathWasErased) {
            paths.value = remainingPaths
        }
    }

    /**
     * Checks if a path intersects with a circular area.
     */
    private fun isPathIntersectingWithCircle(
        action: DrawingAction,
        cx: Float,
        cy: Float,
        eraserRadius: Float,
    ): Boolean {
        val path = action.path
        val pathStrokeWidth = action.strokeWidth

        val pathMeasure = PathMeasure(path, false)
        val length = pathMeasure.length
        val pos = FloatArray(2)

        val pathRadius = pathStrokeWidth / 2
        val totalRadius = eraserRadius + pathRadius
        val totalRadiusSquared = totalRadius * totalRadius

        if (length == 0f) {
            pathMeasure.getPosTan(0f, pos, null)
            val dx = pos[0] - cx
            val dy = pos[1] - cy
            return dx * dx + dy * dy <= totalRadiusSquared
        }

        var distance = 0f
        while (distance < length) {
            pathMeasure.getPosTan(distance, pos, null)
            val dx = pos[0] - cx
            val dy = pos[1] - cy
            if (dx * dx + dy * dy <= totalRadiusSquared) {
                return true
            }
            distance += 1f
        }
        return false
    }

    /**
     * Finalizes a path erase gesture, adding the erased paths to the undo stack.
     */
    fun endPathEraseGesture() {
        if (pathsErasedInCurrentGesture.isNotEmpty()) {
            val removedWithIndices =
                pathsErasedInCurrentGesture.mapNotNull { removedAction ->
                    pathsBeforeGesture
                        .indexOfOrNull(removedAction)
                        ?.let { Pair(it, removedAction) }
                }
            val action = RemoveAction(removedWithIndices)
            undoStack.update { it + action }
            redoStack.value = emptyList()
            pathsErasedInCurrentGesture.clear()
        }
    }

    /**
     * Reverts the last drawing action.
     */
    fun undo() {
        val lastAction = undoStack.value.lastOrNull() ?: return

        undoStack.update { it.dropLast(1) }
        redoStack.update { it + lastAction }

        when (lastAction) {
            is AddAction -> {
                paths.update { list -> list.filterNot { it === lastAction.added } }
            }
            is RemoveAction -> {
                paths.update { currentPaths ->
                    val mutablePaths = currentPaths.toMutableList()
                    lastAction.removed.sortedBy { it.first }.forEach { (index, action) ->
                        mutablePaths.add(index.coerceAtMost(mutablePaths.size), action)
                    }
                    mutablePaths
                }
            }
            is ClearAction -> {
                paths.update { it + lastAction.cleared }
            }
        }
    }

    /**
     * Restores the last undone drawing action.
     */
    fun redo() {
        val actionToRedo = redoStack.value.lastOrNull() ?: return

        redoStack.update { it.dropLast(1) }
        undoStack.update { it + actionToRedo }

        when (actionToRedo) {
            is AddAction -> {
                paths.update { it + actionToRedo.added }
            }
            is RemoveAction -> {
                val actionsToRemove = actionToRedo.removed.map { it.second }
                paths.update { list -> list.filterNot { it in actionsToRemove } }
            }
            is ClearAction -> {
                paths.value = emptyList()
            }
        }
    }

    /**
     * Clears all paths from the canvas.
     */
    fun clearCanvas() {
        if (paths.value.isNotEmpty()) {
            val action = ClearAction(paths.value)
            undoStack.update { it + action }
            redoStack.value = emptyList()
            paths.value = emptyList()
        }
    }

    /**
     * Sets the active brush by its index and deactivates the eraser if it was active.
     */
    fun setActiveBrush(index: Int) {
        val brush = brushes.value.getOrNull(index) ?: return

        isEraserActive.value = false
        activeBrushIndex.value = index
        repository.saveLastActiveBrushIndex(index, isDarkMode)

        brushColor.value = brush.color
        activeStrokeWidth.value = brush.width
    }

    /**
     * Toggles the eraser tool on or off.
     */
    fun enableEraser() {
        isEraserActive.value = true
        activeStrokeWidth.value = eraserDisplayWidth.value
    }

    /**
     * Sets the eraser mode (pixel or path).
     */
    fun setEraserMode(mode: EraserMode) {
        eraserMode.value = mode
        repository.eraserMode = mode
        if (isEraserActive.value) {
            activeStrokeWidth.value =
                if (mode == EraserMode.INK) {
                    inkEraserStrokeWidth.value
                } else {
                    strokeEraserStrokeWidth.value
                }
        }
    }

    /**
     * Sets the stroke width for the currently active tool (brush or eraser).
     */
    fun setActiveStrokeWidth(newWidth: Float) {
        if (isEraserActive.value) {
            if (eraserMode.value == EraserMode.INK) {
                inkEraserStrokeWidth.value = newWidth
                repository.inkEraserWidth = newWidth
            } else {
                strokeEraserStrokeWidth.value = newWidth
                repository.strokeEraserWidth = newWidth
            }
        } else {
            // Update the width of the active brush
            val activeIndex = activeBrushIndex.value
            val updatedBrushes = brushes.value.replaceAt(activeIndex) { it.copy(width = newWidth) }
            brushes.value = updatedBrushes
            repository.saveBrushes(updatedBrushes, isDarkMode)
        }
        activeStrokeWidth.value = newWidth
    }

    @CheckResult
    fun <T> List<T>.replaceAt(
        index: Int,
        replace: (T) -> T,
    ): List<T> = this.mapIndexed { i, value -> if (i == index) replace(value) else value }

    /**
     * Replaces the active brush's color with a new one.
     */
    fun updateBrushColor(newColor: Int) {
        Timber.i("Updating brush color to %d", newColor)
        val activeIndex = activeBrushIndex.value
        val updatedBrushes = brushes.value.replaceAt(activeIndex) { it.copy(color = newColor) }

        brushes.value = updatedBrushes
        repository.saveBrushes(brushes.value, isDarkMode)
        brushColor.value = newColor
    }

    /**
     * Adds a new brush color to the user's palette.
     */
    fun addBrush(color: Int) {
        val newBrush = BrushInfo(color = color, width = WhiteboardRepository.DEFAULT_STROKE_WIDTH)
        val updatedBrushes = brushes.value + newBrush
        brushes.value = updatedBrushes
        repository.saveBrushes(brushes.value, isDarkMode)
        setActiveBrush(updatedBrushes.lastIndex)
    }

    /**
     * Removes a brush from the user's palette by its index.
     */
    fun removeBrush(indexToRemove: Int) {
        if (brushes.value.size <= 1) return

        val oldActiveIndex = activeBrushIndex.value
        val updatedBrushes = brushes.value.filterIndexed { i, _ -> i != indexToRemove }
        brushes.value = updatedBrushes
        repository.saveBrushes(brushes.value, isDarkMode)

        when {
            oldActiveIndex == indexToRemove -> {
                val newIndex = (oldActiveIndex - 1).coerceAtLeast(0)
                setActiveBrush(newIndex)
            }
            oldActiveIndex > indexToRemove -> {
                setActiveBrush(oldActiveIndex - 1)
            }
        }
    }

    /**
     * Toggles the stylus-only drawing mode.
     */
    fun toggleStylusOnlyMode() {
        val newMode = !isStylusOnlyMode.value
        isStylusOnlyMode.value = newMode
        repository.stylusOnlyMode = newMode
    }

    /**
     * Sets the toolbar alignment.
     */
    fun setToolbarAlignment(alignment: ToolbarAlignment) {
        if (toolbarAlignment.value != alignment) {
            toolbarAlignment.value = alignment
            repository.toolbarAlignment = alignment
        }
    }

    /**
     * Sets the toolbar visibility.
     */
    fun setIsToolbarShown(isShown: Boolean) {
        if (isToolbarShown.value != isShown) {
            isToolbarShown.value = isShown
            repository.isToolbarShown = isShown
        }
    }

    /**
     * Clear the canvas and the undo/redo states
     */
    fun reset() {
        clearCanvas()
        undoStack.value = emptyList()
        redoStack.value = emptyList()
    }

    companion object {
        fun factory(sharedPreferences: SharedPreferences): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    WhiteboardViewModel(WhiteboardRepository(sharedPreferences))
                }
            }
    }
}
