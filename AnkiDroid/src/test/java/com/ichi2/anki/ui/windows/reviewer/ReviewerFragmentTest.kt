// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer

import androidx.core.content.edit
import androidx.fragment.app.DialogFragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.dialogs.tags.TagsDialog
import com.ichi2.anki.preferences.reviewer.ViewerAction
import com.ichi2.anki.previewer.CardViewerActivity
import com.ichi2.anki.reviewer.MappableBinding.Companion.toPreferenceString
import com.ichi2.anki.reviewer.ReviewerBinding
import com.ichi2.anki.scheduling.SetDueDateDialog
import com.ichi2.anki.scheduling.singleDayText
import com.ichi2.anki.utils.ext.DIALOG_FRAGMENT_TAG
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ReviewerFragmentTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.IN_MEMORY_WITH_MEDIA

    @Test
    fun `shaking does not stack set due date dialogs`() = assertShakeDoesNotStackDialogs(ViewerAction.RESCHEDULE_NOTE)

    @Test
    fun `shaking does not stack edit tags dialogs`() = assertShakeDoesNotStackDialogs(ViewerAction.TAG)

    @Test
    fun `repeated set due date actions preserve the open dialog`() =
        runTest {
            val cardId = addBasicNote().firstCard().id
            withReviewer {
                viewModel.executeAction(ViewerAction.RESCHEDULE_NOTE)
                advanceUntilIdle()
                advanceRobolectricLooper()
                val dialog = assertIs<SetDueDateDialog>(currentDialog)
                dialog.singleDayText.setText("12")
                val backStackCount = parentFragmentManager.backStackEntryCount

                repeat(3) {
                    viewModel.executeAction(ViewerAction.RESCHEDULE_NOTE)
                }
                advanceUntilIdle()
                advanceRobolectricLooper()

                assertSame(dialog, currentDialog)
                assertEquals(backStackCount, parentFragmentManager.backStackEntryCount)
                assertEquals("12", dialog.singleDayText.text.toString())
                assertEquals(listOf(cardId), dialog.cardIds)
            }
        }

    @Test
    fun `repeated edit tags actions preserve unconfirmed selections`() =
        runTest {
            addBasicNote()
            withReviewer {
                val dialog = openEditTags(this)
                dialog.addTag("unconfirmed")
                advanceUntilIdle()
                val tags = dialog.viewModel.tags.await()
                assertTrue(tags.isChecked("unconfirmed"))
                val backStackCount = parentFragmentManager.backStackEntryCount

                repeat(3) {
                    viewModel.executeAction(ViewerAction.TAG)
                }
                advanceUntilIdle()
                advanceRobolectricLooper()

                assertSame(dialog, currentDialog)
                assertEquals(backStackCount, parentFragmentManager.backStackEntryCount)
                assertTrue(tags.isChecked("unconfirmed"))
            }
        }

    @Test
    fun `edit tags can reopen after dismissal without unconfirmed selections`() =
        runTest {
            addBasicNote()
            withReviewer {
                val dialog = openEditTags(this)
                dialog.addTag("unconfirmed")
                advanceUntilIdle()
                val tags = dialog.viewModel.tags.await()
                assertTrue(tags.isChecked("unconfirmed"))

                dialog.dismiss()
                advanceRobolectricLooper()
                assertNull(currentDialog)

                val reopenedDialog = openEditTags(this)
                assertNotSame(dialog, reopenedDialog)
                assertTrue(reopenedDialog.requireDialog().isShowing)
                val reopenedTags = reopenedDialog.viewModel.tags.await()
                assertFalse(reopenedTags.isChecked("unconfirmed"))
            }
        }

    private suspend fun TestScope.openEditTags(reviewer: ReviewerFragment): TagsDialog {
        reviewer.viewModel.executeAction(ViewerAction.TAG)
        advanceUntilIdle()
        advanceRobolectricLooper()
        return assertIs<TagsDialog>(reviewer.currentDialog)
    }

    private fun assertShakeDoesNotStackDialogs(action: ViewerAction) =
        runTest {
            addBasicNote()
            targetContext.sharedPrefs().edit {
                putString(action.preferenceKey, listOf(ReviewerBinding.fromGesture(Gesture.SHAKE)).toPreferenceString())
            }

            withReviewer { controller ->
                fun shake() {
                    hearShake()
                    advanceUntilIdle()
                    advanceRobolectricLooper()
                }

                shake()
                val dialog = assertNotNull(currentDialog)
                assertTrue(dialog.requireDialog().isShowing)
                val backStackCount = parentFragmentManager.backStackEntryCount

                // Android transfers window focus to the dialog while the reviewer remains resumed.
                controller.windowFocusChanged(false)
                repeat(3) { shake() }
                assertSame(dialog, currentDialog)
                assertEquals(backStackCount, parentFragmentManager.backStackEntryCount)

                dialog.dismiss()
                advanceRobolectricLooper()
                assertNull(currentDialog)
                controller.windowFocusChanged(true)

                shake()
                val reopenedDialog = assertNotNull(currentDialog)
                assertNotSame(dialog, reopenedDialog)
                assertTrue(reopenedDialog.requireDialog().isShowing)
            }
        }

    private val ReviewerFragment.currentDialog: DialogFragment?
        get() = parentFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG) as DialogFragment?

    private suspend fun TestScope.withReviewer(block: suspend ReviewerFragment.(ActivityController<CardViewerActivity>) -> Unit) {
        val intent = ReviewerFragment.getIntent(targetContext)
        Robolectric.buildActivity(CardViewerActivity::class.java, intent).use { controller ->
            controller.setup().windowFocusChanged(true)
            advanceUntilIdle()
            val reviewer = controller.get().fragment as ReviewerFragment
            reviewer.viewModel.onPageFinished(false)
            advanceUntilIdle()
            reviewer.block(controller)
        }
    }
}
