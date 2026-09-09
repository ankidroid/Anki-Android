// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.notetype

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import com.ichi2.anki.R
import com.ichi2.anki.ScreenshotTest
import com.ichi2.testutils.dispatchInsets
import com.ichi2.utils.dp
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
                advanceRobolectricLooper()

                val list = activity.binding.noteTypesList
                list.scrollToPosition(list.adapter!!.itemCount - 1)
                while (list.canScrollVertically(1)) list.scrollBy(0, 50)
                advanceRobolectricLooper()
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

    /** Shows the keyboard's bounds as a translucent overlay. */
    private fun ManageNotetypes.simulateKeyboard() {
        val keyboardHeight = 300.dp
        dispatchInsets(navBarBottom = 48.dp, imeBottom = keyboardHeight)
        val decor = window.decorView as ViewGroup
        val overlay = View(this).apply { setBackgroundColor(0x80000000.toInt()) }
        decor.addView(
            overlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                keyboardHeight.toPx(this),
                Gravity.BOTTOM,
            ),
        )
    }
}
