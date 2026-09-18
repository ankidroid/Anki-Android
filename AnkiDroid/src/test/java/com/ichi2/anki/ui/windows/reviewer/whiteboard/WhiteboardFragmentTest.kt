// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer.whiteboard

import androidx.fragment.app.commitNow
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.testutils.launchFragmentInContainer
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

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
