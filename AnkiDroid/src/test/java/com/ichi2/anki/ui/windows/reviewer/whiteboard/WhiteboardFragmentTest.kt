// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer.whiteboard

import androidx.fragment.app.commitNow
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.testutils.launchFragmentInContainer
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class WhiteboardFragmentTest : RobolectricTest() {
    @Test
    fun `toolbar visibility is no longer observed after view is destroyed`() {
        withWhiteboard { fragment ->
            val viewModel = ViewModelProvider(fragment)[WhiteboardViewModel::class.java]
            fragment.parentFragmentManager.commitNow { detach(fragment) }
            assertNull(fragment.view)

            viewModel.setIsToolbarShown(!viewModel.isToolbarShown.value)
        }
    }

    private fun withWhiteboard(block: (WhiteboardFragment) -> Unit) {
        launchFragmentInContainer<WhiteboardFragment>().use { scenario ->
            scenario.onFragment { fragment -> block(fragment) }
            advanceRobolectricLooper()
        }
    }
}
