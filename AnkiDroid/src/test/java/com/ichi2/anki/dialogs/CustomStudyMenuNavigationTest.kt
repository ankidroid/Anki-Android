// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import android.os.Looper
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog
import com.ichi2.anki.ioDispatcher
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.utils.ext.DIALOG_FRAGMENT_TAG
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.testutils.EmptyAnkiActivity
import com.ichi2.testutils.uninitializeField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CustomStudyMenuNavigationTest : RobolectricTest() {
    @Before
    override fun setUp() {
        super.setUp()
        uninitializeField<CustomStudyDialog>("deferredInitialization")
    }

    @Test
    fun `recreated menu rejects a selection from its previous dialog`() =
        runTest {
            val (activity, fragment) = openMenu()
            val originalDialog = fragment.requireDialog()
            withQueuedChecks(this) { collectionScheduler ->
                onView(withText(TR.customStudyReviewAhead())).inRoot(isDialog()).perform(click())
                runCurrent()

                activity.showDialogFragment(DialogFragment())
                assertFalse(originalDialog.isShowing)
                assertFalse(fragment.isResumed)
                assertTrue(activity.supportFragmentManager.popBackStackImmediate())
                assertTrue(fragment.isResumed)
                val recreatedDialog = fragment.requireDialog()
                assertTrue(recreatedDialog !== originalDialog)

                // The old dialog's result arrives before the recreated menu finishes loading.
                collectionScheduler.runCurrent()
                runCurrent()
                shadowOf(Looper.getMainLooper()).idle()
                collectionScheduler.runCurrent()
                runCurrent()
                shadowOf(Looper.getMainLooper()).idle()

                assertSame(fragment, activity.supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG))
                assertEquals(1, activity.supportFragmentManager.backStackEntryCount)
                assertTrue(recreatedDialog.isShowing)
                assertFalse(assertNotNull(recreatedDialog.findViewById<View>(R.id.progress_bar)).isVisible)

                // Ignoring the old result must still allow a new selection from the recreated menu.
                onView(withText(TR.customStudyReviewAhead())).inRoot(isDialog()).perform(click())
                runCurrent()
                collectionScheduler.runCurrent()
                runCurrent()
                shadowOf(Looper.getMainLooper()).idle()

                val input = activity.supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG) as CustomStudyDialog
                assertNotNull(input.requireDialog().findViewById<View>(R.id.details_edit_text_2))
                assertEquals(2, activity.supportFragmentManager.backStackEntryCount)
            }
        }

    @Test
    fun `native cancellation must not reopen the menu as an input dialog`() =
        runTest {
            val (activity, fragment) = openMenu()
            val originalDialog = fragment.requireDialog()
            withQueuedChecks(this) { collectionScheduler ->
                onView(withText(TR.customStudyReviewAhead())).inRoot(isDialog()).perform(click())
                runCurrent()
                collectionScheduler.runCurrent()

                originalDialog.cancel()
                assertFalse(originalDialog.isShowing)
                assertTrue(fragment.isResumed, "The native dismissal callback is still queued")

                runCurrent()
                shadowOf(Looper.getMainLooper()).idle()
                activity.supportFragmentManager.executePendingTransactions()

                assertNull(activity.supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG))
            }
        }

    @Test
    fun `a queued menu result must not reopen a dismissed dialog`() =
        runTest {
            val (activity, fragment) = openMenu()
            val originalDialog = fragment.requireDialog()
            withQueuedChecks(this) { collectionScheduler ->
                onView(withText(TR.customStudyReviewAhead())).inRoot(isDialog()).perform(click())
                runCurrent()
                assertSame(fragment, activity.supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG))

                // Finish the backend read, leaving its UI continuation queued.
                collectionScheduler.runCurrent()
                fragment.dismiss()
                assertFalse(originalDialog.isShowing)
                assertTrue(fragment.isResumed, "The dismissal transaction has not executed yet")

                // The read result arrives before FragmentManager processes the pending dismissal.
                runCurrent()
                activity.supportFragmentManager.executePendingTransactions()

                assertNull(activity.supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG))
            }
        }

    @Test
    fun `a queued menu result must not open an input dialog when the activity is finishing`() =
        runTest {
            val (activity, fragment) = openMenu()
            withQueuedChecks(this) { collectionScheduler ->
                onView(withText(TR.customStudyReviewAhead())).inRoot(isDialog()).perform(click())
                runCurrent()
                collectionScheduler.runCurrent()

                activity.finish()
                assertTrue(activity.isFinishing)
                assertTrue(fragment.isResumed, "The activity has not paused yet")
                assertTrue(fragment.requireDialog().isShowing)
                assertFalse(activity.supportFragmentManager.isStateSaved)

                runCurrent()
                shadowOf(Looper.getMainLooper()).idle()
                activity.supportFragmentManager.executePendingTransactions()

                assertSame(fragment, activity.supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG))
                assertEquals(1, activity.supportFragmentManager.backStackEntryCount)
            }
        }

    @Test
    fun `two queued menu taps open exactly one input dialog`() =
        runTest {
            val (activity, fragment) = openMenu()
            withQueuedChecks(this) { collectionScheduler ->
                repeat(2) {
                    onView(withText(TR.customStudyReviewAhead())).inRoot(isDialog()).perform(click())
                    runCurrent()
                    assertSame(fragment, activity.supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG))
                }
                collectionScheduler.runCurrent()
                runCurrent()
                collectionScheduler.runCurrent()
                runCurrent()
                shadowOf(Looper.getMainLooper()).idle()

                val dialogs = activity.supportFragmentManager.fragments.filterIsInstance<CustomStudyDialog>()
                assertEquals(1, dialogs.size)
                assertEquals(2, activity.supportFragmentManager.backStackEntryCount)
                assertNotNull(dialogs.single().requireDialog().findViewById<View>(R.id.details_edit_text_2))
            }
        }

    private fun openMenu(): Pair<EmptyAnkiActivity, CustomStudyDialog> {
        addBasicNote()
        val controller = Robolectric.buildActivity(EmptyAnkiActivity::class.java).setup()
        saveControllerForCleanup(controller)
        advanceRobolectricLooper()
        val activity = controller.get()
        activity.showDialogFragment(CustomStudyDialog.createInstance(Consts.DEFAULT_DECK_ID))
        val fragment = activity.supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG) as CustomStudyDialog
        advanceRobolectricLooperUntil { fragment.dialog?.findViewById<View>(R.id.progress_bar)?.isVisible != true }
        return activity to fragment
    }

    private fun withQueuedChecks(
        scope: TestScope,
        block: (TestCoroutineScheduler) -> Unit,
    ) {
        val previousIo = ioDispatcher
        val mainDispatcher = StandardTestDispatcher(scope.testScheduler)
        val collectionScheduler = TestCoroutineScheduler()
        val previousCollection = CollectionManager.setTestDispatcher(StandardTestDispatcher(collectionScheduler), useReentrantLock = false)
        Dispatchers.setMain(mainDispatcher)
        ioDispatcher = mainDispatcher
        try {
            block(collectionScheduler)
        } finally {
            try {
                repeat(3) {
                    collectionScheduler.runCurrent()
                    scope.runCurrent()
                    shadowOf(Looper.getMainLooper()).idle()
                }
            } finally {
                CollectionManager.setTestDispatcher(previousCollection)
                ioDispatcher = previousIo
                Dispatchers.setMain(previousIo)
            }
        }
    }
}
