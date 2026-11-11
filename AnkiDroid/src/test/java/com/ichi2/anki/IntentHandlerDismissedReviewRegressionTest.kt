// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.collection.OpChanges
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.dialogs.DialogHandler
import com.ichi2.testutils.rules.BackendOverrideRule
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbCorruptException
import net.ankiweb.rsdroid.exceptions.BackendIoException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class IntentHandlerDismissedReviewRegressionTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    @Test
    fun `finishing a shortcut while selection is queued must not reopen review`() = checkDismissedShortcut()

    @Test
    fun `finishing a shortcut before a selection error must not show a dialog`() =
        checkDismissedShortcut(BackendIoException(backendError { message = "Cannot select the shortcut deck" }))

    @Test
    fun `finishing a shortcut before corruption is returned must not reopen recovery`() =
        checkDismissedShortcut(BackendDbCorruptException(backendError { message = "database disk image is malformed" }))

    private fun checkDismissedShortcut(failure: Exception? = null) =
        runTest {
            var selectionFailure: Exception? = null
            backendOverride.replaceWith {
                object : Backend() {
                    override fun setCurrentDeck(did: Long): OpChanges = selectionFailure?.let { throw it } ?: super.setCurrentDeck(did)
                }
            }
            val target = addDeck("Shortcut deck")
            addNoteToDeck(target)
            setIntroductionSlidesShown(true)
            throwOnShowError = false
            val previousCorruption = DatabaseCorruption.isDetected
            val selectionScheduler = TestCoroutineScheduler()
            val previousQueue =
                CollectionManager.setTestDispatcher(StandardTestDispatcher(selectionScheduler), useReentrantLock = false)
            try {
                val destination = IntentHandler.getReviewDeckIntent(targetContext, target)
                val controller = Robolectric.buildActivity(IntentHandler::class.java, destination)
                saveControllerForCleanup(controller)
                val activity =
                    controller
                        .create()
                        .start()
                        .resume()
                        .visible()
                        .get()
                assertNull(shadowOf(activity).nextStartedActivity, "Selection is still queued")

                activity.finish()
                assertTrue(activity.isFinishing)
                assertFalse(activity.isDestroyed, "Exercise the window before onDestroy cancels the shortcut scope")
                selectionFailure = failure
                selectionScheduler.runCurrent()
                testScheduler.runCurrent()

                assertNull(shadowOf(activity).nextStartedActivity, "A dismissed shortcut must not reopen the app")
                assertFalse(ShadowDialog.getLatestDialog()?.isShowing == true, "A dismissed shortcut must not show an error")
            } finally {
                selectionScheduler.runCurrent()
                CollectionManager.setTestDispatcher(previousQueue)
                selectionFailure = null
                DatabaseCorruption.isDetected = previousCorruption
                DialogHandler.discardMessage()
            }
        }
}
