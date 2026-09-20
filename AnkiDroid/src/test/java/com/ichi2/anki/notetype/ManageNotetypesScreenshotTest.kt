// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.notetype

import androidx.test.core.app.ActivityScenario
import com.ichi2.anki.R
import com.ichi2.anki.ScreenshotTest
import com.ichi2.testutils.scrollToEnd
import com.ichi2.testutils.simulateKeyboard
import org.junit.Test

class ManageNotetypesScreenshotTest : ScreenshotTest() {
    @Test
    fun keyboard_scrolled_to_bottom() =
        runTest {
            ensureCollectionLoadIsSynchronous()
            repeat(10) { addStandardNoteType("Note type $it", arrayOf("front", "back"), "", "") }
            withManageNoteTypes { activity ->
                activity.binding.toolbar.menu
                    .findItem(R.id.search_item)
                    .expandActionView()
                advanceRobolectricLooper()
                activity.simulateKeyboard()

                activity.binding.noteTypesList.scrollToEnd()
                captureScreen("keyboard_scrolled_to_bottom")

                val currentState = activity.viewModel.state.value
                activity.viewModel.onItemLongClick(currentState.noteTypes.last())
                advanceRobolectricLooper()
                captureScreen("keyboard_multi_select_mode")
            }
        }

    @Test
    fun base_and_selected() =
        withManageNoteTypes { activity ->
            captureScreen("base")

            activity.binding.appBarLayout.isLifted = true
            captureScreen("appbar_lifted")
            activity.binding.appBarLayout.isLifted = false

            // enable multi select mode by selecting the first entry
            val currentState = activity.viewModel.state.value
            activity.viewModel.onItemLongClick(currentState.noteTypes[0])
            captureScreen("multi_select_mode")
        }

    private fun withManageNoteTypes(block: (ManageNotetypes) -> Unit) {
        val intent = ManageNoteTypesDestination().toIntent(targetContext)
        ActivityScenario.launch<ManageNotetypes>(intent).use { scenario ->
            scenario.onActivity(block)
        }
    }
}
