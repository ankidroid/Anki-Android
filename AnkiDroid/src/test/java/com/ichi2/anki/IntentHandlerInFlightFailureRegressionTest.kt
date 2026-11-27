// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.collection.OpChanges
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.dialogs.DialogHandler
import com.ichi2.testutils.rules.BackendOverrideRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.setMain
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
class IntentHandlerInFlightFailureRegressionTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    @Test
    fun `selection error after finishing an in-flight shortcut must not show a dialog`() =
        checkFailureAfterFinish(BackendIoException(backendError { message = "Cannot select the shortcut deck" }))

    @Test
    fun `corruption after finishing an in-flight shortcut must not reopen recovery`() =
        checkFailureAfterFinish(BackendDbCorruptException(backendError { message = "database disk image is malformed" }))

    private fun checkFailureAfterFinish(failure: Exception) =
        runTest {
            var selectionFailure: Exception? = null
            var selectionAttempted = false
            backendOverride.replaceWith {
                object : Backend() {
                    override fun setCurrentDeck(did: Long): OpChanges {
                        selectionFailure?.let {
                            selectionAttempted = true
                            throw it
                        }
                        return super.setCurrentDeck(did)
                    }
                }
            }
            val target = addDeck("Shortcut deck")
            addNoteToDeck(target)
            setIntroductionSlidesShown(true)
            throwOnShowError = false
            val previousCorruption = DatabaseCorruption.isDetected
            val selectionScheduler = TestCoroutineScheduler()
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
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
                testScheduler.runCurrent()
                assertNull(shadowOf(activity).nextStartedActivity, "Selection is still queued")

                // Complete backend work, but dismiss before Main receives its failure.
                selectionFailure = failure
                selectionScheduler.runCurrent()
                assertTrue(selectionAttempted, "The backend failure must already be pending")
                activity.finish()
                assertTrue(activity.isFinishing)
                assertFalse(activity.isDestroyed, "Exercise dismissal before onDestroy")
                testScheduler.runCurrent()

                assertTrue(activity.isFinishing)
                assertNull(shadowOf(activity).nextStartedActivity, "A dismissed shortcut must not reopen recovery")
                assertFalse(ShadowDialog.getLatestDialog()?.isShowing == true, "A dismissed shortcut must not show an error")
            } finally {
                selectionFailure = null
                selectionScheduler.runCurrent()
                CollectionManager.setTestDispatcher(previousQueue)
                DatabaseCorruption.isDetected = previousCorruption
                DialogHandler.discardMessage()
            }
        }
}
