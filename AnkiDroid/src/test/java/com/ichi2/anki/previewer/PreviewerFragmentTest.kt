// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.previewer

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.browser.IdsFile
import com.ichi2.testutils.createTransientDirectory
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class PreviewerFragmentTest : RobolectricTest() {
    @Test
    fun `rotation retains loaded selection when its file has disappeared`() {
        val ids = addBasicAndReversedNote().cardIds(col)
        val file = IdsFile(createTransientDirectory(), ids)
        val intent = PreviewerFragment.getIntent(targetContext, file, currentIndex = 0)
        ActivityScenario.launch<CardViewerActivity>(intent).use { scenario ->
            lateinit var original: PreviewerViewModel
            scenario.onActivity { activity ->
                original = (activity.supportFragmentManager.fragments.single() as PreviewerFragment).viewModel
            }
            assertTrue(file.delete())
            scenario.recreate()
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                val restored = (activity.supportFragmentManager.fragments.single() as PreviewerFragment).viewModel
                assertSame(original, restored)
                assertEquals(ids, restored.selectedCardIds)
            }
        }
    }

    @Test
    fun `previewer - back button`() {
        val note = addBasicAndReversedNote()

        val intent =
            PreviewerFragment.getIntent(
                targetContext,
                idsFile = IdsFile(createTransientDirectory(), note.cardIds(col)),
                currentIndex = 0,
            )

        ActivityScenario.launch<CardViewerActivity>(intent).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { previewer ->
                assertThat("Activity is not finishing", !previewer.isFinishing)
                // this needs to test the keypress, not the dispatcher
                Espresso.pressBack()
                assertThat("Activity is finishing after back press", previewer.isFinishing)
            }
        }
    }
}
