// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.menu.MenuBuilder
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.collection.OpChanges
import kotlinx.coroutines.runBlocking
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendFactory
import net.ankiweb.rsdroid.exceptions.BackendIoException
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class StudyOptionsPhoneErrorLifecycleRegressionTest : RobolectricTest() {
    @After
    fun resetBackend() = BackendFactory.setOverride(null)

    @Test
    fun `unacknowledged phone filtered deck error survives recreation`() {
        val failure = BackendIoException(backendError { message = "Cannot empty the phone filtered deck" })
        runBlocking { CollectionManager.discardBackend() }
        BackendFactory.setOverride {
            object : Backend() {
                override fun emptyFilteredDeck(did: Long): OpChanges = throw failure
            }
        }
        val filtered = addDynamicDeck("Filtered")
        ensureCollectionLoadIsSynchronous()
        throwOnShowError = false
        ActivityScenario.launch(StudyOptionsActivity::class.java).use { scenario ->
            lateinit var original: AlertDialog
            scenario.onActivity { activity ->
                val pane = assertIs<StudyOptionsFragment>(activity.supportFragmentManager.findFragmentById(R.id.studyoptions_frame))
                advanceRobolectricLooperUntil { pane.viewModel.selectedDeckId == filtered }
                advanceRobolectricLooper()
                pane.onMenuItemSelected(MenuBuilder(activity).add(0, R.id.action_empty, 0, "Empty"))
                advanceRobolectricLooperUntil { ShadowDialog.getLatestDialog()?.isShowing == true }
                original = assertIs<AlertDialog>(ShadowDialog.getLatestDialog())
                assertEquals(failure.localizedMessage, getAlertDialogText(checkDismissed = true))
            }
            try {
                scenario.recreate()
                scenario.onActivity { activity ->
                    val pane = assertIs<StudyOptionsFragment>(activity.supportFragmentManager.findFragmentById(R.id.studyoptions_frame))
                    advanceRobolectricLooperUntil { pane.viewModel.selectedDeckId == filtered }
                    advanceRobolectricLooper()
                    val restored = assertIs<AlertDialog>(ShadowDialog.getLatestDialog())
                    assertNotSame(original, restored, "The destroyed activity's window must not stand in for the unacknowledged error")
                    assertTrue(restored.isShowing)
                    assertEquals(failure.localizedMessage, getAlertDialogText(checkDismissed = true))
                }
            } finally {
                original.dismiss()
            }
        }
    }
}
