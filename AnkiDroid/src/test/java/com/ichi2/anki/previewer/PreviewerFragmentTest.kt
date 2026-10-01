// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.previewer

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CommonString
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.browser.IdsFile
import com.ichi2.anki.cardviewer.CardMediaPlayer
import com.ichi2.anki.pages.AnkiServer
import com.ichi2.testutils.createTransientDirectory
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.shadows.ShadowToast
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class PreviewerFragmentTest : RobolectricTest() {
    @Test
    fun `server startup failure is not handled as an unavailable selection`() {
        val file = IdsFile(createTransientDirectory(), addBasicNote().cardIds(col))
        val intent = PreviewerFragment.getIntent(targetContext, file, currentIndex = 0)
        val failure = IOException("server bind failed")
        // A constructor failure never registers the ViewModel for cleanup, so avoid creating real media resources.
        Mockito.mockConstruction(CardMediaPlayer::class.java).use {
            Mockito
                .mockConstruction(AnkiServer::class.java) { server, _ ->
                    Mockito.doThrow(failure).`when`(server).start()
                }.use {
                    Robolectric.buildActivity(CardViewerActivity::class.java, intent).use { controller ->
                        try {
                            assertSame(failure, assertFailsWith<IOException> { controller.setup() })
                            assertNull(ShadowToast.getTextOfLatestToast())
                            assertFalse(controller.get().isFinishing)
                        } finally {
                            // Prevent lifecycle cleanup from retrying construction after the failed launch.
                            controller.get().finish()
                        }
                    }
                }
        }
    }

    @Test
    fun `missing selection closes previewer`() = assertUnavailableSelection { assertTrue(delete()) }

    @Test
    fun `truncated selection closes previewer`() = assertUnavailableSelection { writeBytes(readBytes().dropLast(1).toByteArray()) }

    @Test
    fun `empty selection closes previewer`() {
        assertUnavailableSelection { writeBytes(byteArrayOf(0, 0, 0, 0)) }
    }

    private fun assertUnavailableSelection(changeFile: IdsFile.() -> Unit) {
        val file = IdsFile(createTransientDirectory(), addBasicNote().cardIds(col)).apply(changeFile)
        val intent = PreviewerFragment.getIntent(targetContext, file, currentIndex = 0)
        Robolectric.buildActivity(CardViewerActivity::class.java, intent).use { controller ->
            val activity = controller.setup().get()
            assertTrue(activity.isFinishing)
            assertEquals(targetContext.getString(CommonString.something_wrong), ShadowToast.getTextOfLatestToast())
        }
    }

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
