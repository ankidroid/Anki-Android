// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer.whiteboard

import androidx.fragment.app.commitNow
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.testutils.launchFragmentInContainer
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class WhiteboardFragmentTest : RobolectricTest() {
    @Test
    fun `hiding toolbar does not crash after view is destroyed`() {
        changeToolbarVisibilityBeforeDestroyingView(isShown = false)
    }

    @Test
    fun `showing toolbar does not crash after view is destroyed`() {
        changeToolbarVisibilityBeforeDestroyingView(isShown = true)
    }

    @Test
    fun `toolbar visibility is no longer observed after view is destroyed`() {
        withWhiteboard { fragment ->
            val viewModel = ViewModelProvider(fragment)[WhiteboardViewModel::class.java]
            fragment.parentFragmentManager.commitNow { detach(fragment) }
            assertNull(fragment.view)

            viewModel.setIsToolbarShown(!viewModel.isToolbarShown.value)
        }
    }

    @Test
    fun `selecting an identical brush updates toolbar selection`() {
        launchFragmentInContainer<WhiteboardFragment>().use { scenario ->
            scenario.onFragment { fragment ->
                val viewModel = ViewModelProvider(fragment)[WhiteboardViewModel::class.java]
                viewModel.addBrush(viewModel.currentBrushColor)
                assertEquals(viewModel.brushes.value[0], viewModel.brushes.value[1])
            }
            advanceRobolectricLooper()
            scenario.onFragment { fragment ->
                ViewModelProvider(fragment)[WhiteboardViewModel::class.java].setActiveBrush(0)
            }
            advanceRobolectricLooper()
            scenario.onFragment { fragment ->
                assertTrue(fragment.isBrushSelected(0), "The first brush should initially be selected")
                ViewModelProvider(fragment)[WhiteboardViewModel::class.java].setActiveBrush(1)
            }
            advanceRobolectricLooper()
            scenario.onFragment { fragment ->
                assertTrue(fragment.isBrushSelected(1), "The second brush should now be selected")
                assertFalse(fragment.isBrushSelected(0), "The first brush should no longer be selected")
            }
        }
    }

    private fun WhiteboardFragment.isBrushSelected(index: Int): Boolean {
        val recycler = binding.whiteboardToolbar.findViewById<RecyclerView>(R.id.brush_recycler_view)
        val adapter = recycler.adapter as BrushAdapter
        // Bind a fresh holder so this assertion does not depend on RecyclerView having laid out.
        val holder = adapter.createViewHolder(recycler, adapter.getItemViewType(index))
        adapter.bindViewHolder(holder, index)
        return (holder.itemView as MaterialButton).isChecked
    }

    private fun changeToolbarVisibilityBeforeDestroyingView(isShown: Boolean) {
        WhiteboardRepository(getPreferences()).isToolbarShown = !isShown
        var toolbarUpdated = false
        withWhiteboard { fragment ->
            fragment.binding.whiteboardToolbar.onToolbarVisibilityChanged = { toolbarUpdated = true }
            val viewModel = ViewModelProvider(fragment)[WhiteboardViewModel::class.java]
            viewModel.setIsToolbarShown(isShown)

            // Destroy the view before the posted toolbar update can run.
            fragment.parentFragmentManager.commitNow { detach(fragment) }
            assertNull(fragment.view)
        }
        assertFalse(toolbarUpdated, "the destroyed toolbar should not be updated")
    }

    private fun withWhiteboard(block: (WhiteboardFragment) -> Unit) {
        launchFragmentInContainer<WhiteboardFragment>().use { scenario ->
            scenario.onFragment { fragment -> block(fragment) }
            advanceRobolectricLooper()
        }
    }
}
