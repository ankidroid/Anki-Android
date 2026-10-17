// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer.whiteboard

import android.graphics.Path
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// NATIVE graphics are needed: erasing uses PathMeasure to find the strokes under the eraser
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WhiteboardEraseGestureTest {
    private lateinit var repository: WhiteboardRepository
    private lateinit var viewModel: WhiteboardViewModel

    @Before
    fun setUp() {
        repository = WhiteboardRepository(SPMockBuilder().createSharedPreferences())
        viewModel = WhiteboardViewModel(repository)
        viewModel.loadState(isDarkMode = false)
    }

    @Test
    fun `cancelling an erase gesture restores the erased strokes`() {
        viewModel.addPath(horizontalLine())
        assertEquals(1, viewModel.paths.value.size, "the stroke should be on the whiteboard")

        // a second finger arrives after the first one already erased the stroke
        viewModel.enableEraser()
        viewModel.startPathEraseGesture(x = 50f, y = 0f)
        assertEquals(0, viewModel.paths.value.size, "the stroke should be erased")

        viewModel.cancelPathEraseGesture()

        assertEquals(1, viewModel.paths.value.size, "the stroke should be restored")
    }

    @Test
    fun `cancelling without an erase gesture keeps the strokes`() {
        viewModel.addPath(horizontalLine())
        viewModel.cancelPathEraseGesture()
        assertEquals(1, viewModel.paths.value.size, "strokes should not be erased")
    }

    @Test
    fun `cancelling after finished erase gesture doesn't restore strokes`() {
        viewModel.addPath(horizontalLine())
        viewModel.enableEraser()
        viewModel.startPathEraseGesture(x = 50f, y = 0f)
        viewModel.endPathEraseGesture()
        viewModel.cancelPathEraseGesture()
        assertEquals(0, viewModel.paths.value.size, "erased strokes should stay erased")
    }

    @Test
    fun `multi-touch after a cancelled erase preserves subsequent drawing`() {
        val view = createWhiteboardView()
        viewModel.addPath(horizontalLine())
        viewModel.setEraserMode(EraserMode.STROKE)
        viewModel.enableEraser()
        view.activeTool = viewModel.activeTool.value
        sendTouchEvent(view, MotionEvent.ACTION_DOWN)
        assertEquals(0, viewModel.paths.value.size)
        // Android can cancel an erase without delivering ACTION_UP.
        sendTouchEvent(view, MotionEvent.ACTION_CANCEL)

        viewModel.setActiveBrush(0)
        view.activeTool = viewModel.activeTool.value
        sendTouchEvent(view, MotionEvent.ACTION_DOWN, y = 100f)
        sendTouchEvent(view, MotionEvent.ACTION_UP, y = 100f)
        val newDrawing = viewModel.paths.value.last()

        sendTouchEvent(view, MotionEvent.ACTION_DOWN, y = 200f)
        sendTouchEvent(view, MotionEvent.ACTION_POINTER_DOWN, count = 2, y = 200f)
        sendTouchEvent(view, MotionEvent.ACTION_POINTER_UP, count = 2, y = 200f)
        sendTouchEvent(view, MotionEvent.ACTION_UP, y = 200f)

        assertTrue(newDrawing in viewModel.paths.value, "A later multi-touch gesture must preserve the completed drawing")
    }

    @Test
    fun `multi touch preserves a clear performed during erase`() {
        val view = createWhiteboardView()
        viewModel.addPath(horizontalLine())
        viewModel.addPath(horizontalLine(y = 100f))
        viewModel.setEraserMode(EraserMode.STROKE)
        viewModel.enableEraser()
        view.activeTool = viewModel.activeTool.value
        sendTouchEvent(view, MotionEvent.ACTION_DOWN)
        assertEquals(1, viewModel.paths.value.size)
        // A configured keyboard or shake binding can clear while the finger is held.
        viewModel.clearCanvas()
        sendTouchEvent(view, MotionEvent.ACTION_POINTER_DOWN, count = 2)
        assertEquals(0, viewModel.paths.value.size, "Cancellation must not undo an explicit clear")
    }

    @Test
    fun `multi touch does not restore a previous card after reset`() {
        val view = createWhiteboardView()
        viewModel.addPath(horizontalLine())
        viewModel.setEraserMode(EraserMode.STROKE)
        viewModel.enableEraser()
        view.activeTool = viewModel.activeTool.value
        sendTouchEvent(view, MotionEvent.ACTION_DOWN)
        assertEquals(0, viewModel.paths.value.size)
        // ReviewerFragment resets the canvas when the card updates.
        viewModel.reset()
        sendTouchEvent(view, MotionEvent.ACTION_POINTER_DOWN, count = 2)
        assertEquals(0, viewModel.paths.value.size, "Cancellation must not restore the previous card")
    }

    @Test
    fun `cancelling erase preserves a concurrently redone stroke`() {
        val view = createWhiteboardView()
        viewModel.addPath(horizontalLine())
        viewModel.addPath(horizontalLine(y = 100f))
        val redoneStroke = viewModel.paths.value.last()
        viewModel.undo()
        viewModel.setEraserMode(EraserMode.STROKE)
        viewModel.enableEraser()
        view.activeTool = viewModel.activeTool.value
        sendTouchEvent(view, MotionEvent.ACTION_DOWN)
        viewModel.redo()
        assertTrue(redoneStroke in viewModel.paths.value)
        sendTouchEvent(view, MotionEvent.ACTION_POINTER_DOWN, count = 2)
        assertTrue(redoneStroke in viewModel.paths.value, "Cancellation must preserve a completed redo")
    }

    @Test
    fun `two finger tap restores erased stroke and keeps redo`() {
        val view = createWhiteboardView()
        viewModel.addPath(horizontalLine())
        viewModel.addPath(horizontalLine(y = 100f))
        viewModel.undo()
        viewModel.setEraserMode(EraserMode.STROKE)
        viewModel.enableEraser()
        view.activeTool = viewModel.activeTool.value
        sendTouchEvent(view, MotionEvent.ACTION_DOWN)
        assertEquals(0, viewModel.paths.value.size)
        sendTouchEvent(view, MotionEvent.ACTION_POINTER_DOWN, count = 2)
        sendTouchEvent(view, MotionEvent.ACTION_POINTER_UP, count = 2)
        sendTouchEvent(view, MotionEvent.ACTION_MOVE)
        sendTouchEvent(view, MotionEvent.ACTION_UP)
        assertEquals(1, viewModel.paths.value.size)
        viewModel.redo()
        assertEquals(2, viewModel.paths.value.size)
    }

    private fun createWhiteboardView() =
        WhiteboardView(ApplicationProvider.getApplicationContext()).apply {
            onNewPath = viewModel::addPath
            onEraseGestureStart = viewModel::startPathEraseGesture
            onEraseGestureMove = viewModel::erasePathsToPoint
            onEraseGestureEnd = viewModel::endPathEraseGesture
            onEraseGestureCancel = viewModel::cancelPathEraseGesture
        }

    private fun sendTouchEvent(
        view: WhiteboardView,
        action: Int,
        count: Int = 1,
        y: Float = 0f,
    ) {
        val properties =
            Array(count) { index ->
                MotionEvent.PointerProperties().apply {
                    id = index
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                }
            }
        val coords =
            Array(count) { index ->
                MotionEvent.PointerCoords().apply {
                    x = 50f + index * 20f
                    this.y = y
                }
            }
        val indexedAction = if (count > 1) action or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT) else action
        val event = MotionEvent.obtain(0, 0, indexedAction, count, properties, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
        try {
            view.onTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    /** A line from (0, y) to (100, y), so (50, y) is on top of it */
    private fun horizontalLine(y: Float = 0f) =
        Path().apply {
            moveTo(0f, y)
            lineTo(100f, y)
        }
}
