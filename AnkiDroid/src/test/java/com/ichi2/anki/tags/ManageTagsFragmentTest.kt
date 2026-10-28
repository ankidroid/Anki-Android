// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.utils.dp
import com.ichi2.testutils.dispatchInsets
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Runs the Compose actions against a real collection. */
@RunWith(AndroidJUnit4::class)
class ManageTagsFragmentTest : RobolectricTest() {
    @get:Rule
    val compose = createComposeRule()
    private val screen = ManageTagsTestHelper(this, compose)

    @Test
    @Config(qualifiers = "en")
    fun `tag actions expose only rename and delete`() {
        addTags("source")
        screen.open()
        screen.showActions("source")
        compose.onNodeWithText(targetContext.getString(CommonString.rename)).assertIsDisplayed()
        compose.onNodeWithText(targetContext.getString(CommonString.dialog_positive_delete)).assertIsDisplayed()
        compose.onNodeWithText("Move to…").assertDoesNotExist()
        compose.onNodeWithText("Merge with…").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w320dp-h480dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `a new hierarchy search starts at its first match`() {
        addTags(*(0..39).map { "Destination${it.toString().padStart(2, '0')}" }.toTypedArray())
        screen.open()
        compose.onNodeWithTag("tag_list").performScrollToNode(hasTestTag("tag:Destination29"))
        screen.search("Destination2")
        compose.onNodeWithTag("tag:Destination20").assertIsDisplayed()
    }

    @Test
    fun `remove animations setting makes hierarchy changes immediate`() {
        editPreferences { putBoolean(targetContext.getString(R.string.safe_display_key), true) }
        addTags("parent::child", "unrelated")
        screen.open()
        val following = compose.onNodeWithTag("tag:unrelated")
        val collapsedTop = following.fetchSemanticsNode().boundsInWindow.top
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithTag("tag:parent").performClick()
            compose.mainClock.advanceTimeBy(48)
            val expandedTop = following.fetchSemanticsNode().boundsInWindow.top
            assertTrue(expandedTop > collapsedTop)
            compose.mainClock.advanceTimeBy(1_000)
            assertEquals(expandedTop, following.fetchSemanticsNode().boundsInWindow.top)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun `expanding and collapsing animate surrounding rows without moving the parent`() {
        addTags("parent::child", "unrelated")
        screen.open()
        val parent = compose.onNodeWithTag("tag:parent")
        val following = compose.onNodeWithTag("tag:unrelated")
        val parentBounds = parent.fetchSemanticsNode().boundsInWindow
        val collapsedTop = following.fetchSemanticsNode().boundsInWindow.top
        compose.mainClock.autoAdvance = false
        try {
            fun togglePositions(): List<Float> {
                parent.performClick()
                return List(40) {
                    compose.mainClock.advanceTimeByFrame()
                    assertEquals(parentBounds, parent.fetchSemanticsNode().boundsInWindow)
                    following.fetchSemanticsNode().boundsInWindow.top
                }
            }

            val expanding = togglePositions()
            val expandedTop = expanding.last()
            assertTrue(expandedTop > collapsedTop)
            assertTrue(expanding.any { it > collapsedTop && it < expandedTop }, "Expansion snapped: $expanding")

            val collapsing = togglePositions()
            assertEquals(collapsedTop, collapsing.last())
            assertTrue(collapsing.any { it > collapsedTop && it < expandedTop }, "Collapse snapped: $collapsing")
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun `expanding a tag keeps the row in place while saving its state`() =
        runTest {
            addTags("parent::child", "unrelated")
            screen.open()
            val row = compose.onNodeWithTag("tag:parent")
            val originalBounds = row.fetchSemanticsNode().boundsInWindow
            val previous = CollectionManager.setTestDispatcher(StandardTestDispatcher(testScheduler), useReentrantLock = false)
            try {
                row.performClick()
                assertFalse(assertIs<ManageTagsState.Content>(screen.viewModel.state.value).isWorking)
                row.assertIsEnabled()
                compose.onNodeWithTag("tag:parent::child").assertIsDisplayed()
                assertEquals(originalBounds, row.fetchSemanticsNode().boundsInWindow)
            } finally {
                testScheduler.advanceUntilIdle()
                CollectionManager.setTestDispatcher(previous)
            }
            compose.onNodeWithTag("tag:parent::child").assertIsDisplayed()
            assertEquals(originalBounds, row.fetchSemanticsNode().boundsInWindow)
        }

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    fun `keyboard leaves the last search result reachable without double counting navigation bars`() {
        addTags(*(0..29).map { "Tag${it.toString().padStart(2, '0')}" }.toTypedArray())
        screen.open()
        screen.search("Tag")
        val activity = screen.controller.get()
        for (navigationBar in listOf(0.dp, 48.dp)) {
            activity.dispatchInsets(navBarBottom = navigationBar)
            val list = compose.onNodeWithTag("tag_list")
            val originalBottom = list.fetchSemanticsNode().boundsInWindow.bottom
            activity.dispatchInsets(navBarBottom = navigationBar, imeBottom = 260.dp)
            list.performScrollToNode(hasTestTag("tag:Tag29"))
            val keyboardTop = activity.window.decorView.height - 260.dp.toPx(activity)
            val lastRowBottom =
                compose
                    .onNodeWithTag("tag:Tag29")
                    .fetchSemanticsNode()
                    .boundsInWindow.bottom
            assertEquals(keyboardTop.toDouble(), lastRowBottom.toDouble(), absoluteTolerance = 1.0)
            activity.dispatchInsets(navBarBottom = navigationBar)
            assertEquals(originalBottom, list.fetchSemanticsNode().boundsInWindow.bottom)
        }
    }

    @Test
    fun `rename preserves Unicode spelling and rejects hidden collisions`() =
        runTest {
            val note = addTags("Straße", "i", "ı", "parent::child")
            screen.open()
            screen.action(TagAction.RENAME, "Straße")
            screen.confirm(TagAction.RENAME).assertIsNotEnabled()
            compose.onNodeWithText(targetContext.getString(CommonString.manage_tags_rename_invalid)).assertDoesNotExist()
            screen.enterText("STRASSE")
            screen.confirm(TagAction.RENAME).assertIsEnabled().performClick()
            compose.waitForIdle()
            assertTrue("STRASSE" in col.getNote(note.id).tags)
            screen.search("i")
            screen.action(TagAction.RENAME, "i")
            for (collision in listOf("ı", "PARENT::child")) {
                screen.enterText(collision)
                screen.confirm(TagAction.RENAME).assertIsNotEnabled()
                compose.onNodeWithText(targetContext.getString(CommonString.tag_already_exists)).assertIsDisplayed()
                assertTrue("i" in col.getNote(note.id).tags)
            }
        }

    @Test
    fun `delete warns about hidden descendants and cancellation preserves them`() =
        runTest {
            val note = addTags("parent::shown", "parent::hidden::leaf", "unrelated")
            screen.open()
            screen.search("shown")
            screen.action(TagAction.DELETE, "parent")
            compose.onNodeWithText(targetContext.getString(CommonString.manage_tags_delete_confirm, "parent")).assertIsDisplayed()
            screen.cancel()
            assertEquals(3, col.getNote(note.id).tags.size)
            screen.action(TagAction.DELETE, "parent")
            screen.confirm(TagAction.DELETE).performClick()
            compose.waitForIdle()
            assertEquals(listOf("unrelated"), col.getNote(note.id).tags)
        }

    @Test
    fun `clear unused requires confirmation and keeps used tags`() =
        runTest {
            addTags("used", "unused").update { removeTag("unused") }
            screen.open()
            screen.clearUnused()
            screen.confirm(TagAction.CLEAR_UNUSED).performClick()
            compose.waitForIdle()
            assertEquals(listOf("used"), col.tags.all())
        }

    @Test
    fun `renaming an unused tag shows the backend warning`() =
        runTest {
            addTags("unused").update { removeTag("unused") }
            screen.open()
            screen.action(TagAction.RENAME, "unused")
            screen.enterText("renamed")
            screen.confirm(TagAction.RENAME).performClick()
            compose.onNodeWithText(CollectionManager.TR.browsingTagRenameWarningEmpty()).assertIsDisplayed()
            assertEquals(listOf("unused"), col.tags.all())
        }

    @Test
    fun `backgrounding keeps an unfinished message queued for resume`() =
        runTest {
            addTags("unused").update { removeTag("unused") }
            screen.open()
            screen.action(TagAction.RENAME, "unused")
            screen.enterText("renamed")
            screen.confirm(TagAction.RENAME).performClick()
            compose.onNodeWithText(CollectionManager.TR.browsingTagRenameWarningEmpty()).assertIsDisplayed()
            screen.controller.pause().stop()
            compose.mainClock.advanceTimeBy(10_000)
            compose.waitForIdle()
            assertEquals(1, screen.viewModel.pendingMessages.value.size)
            screen.controller.start().resume()
            compose.onNodeWithText(CollectionManager.TR.browsingTagRenameWarningEmpty()).assertIsDisplayed()
        }

    @Test
    fun `failed deletion keeps content visible and supports retry and dismissal`() =
        runTest {
            addTags("source")
            screen.open()

            fun failDeletion() {
                screen.action(TagAction.DELETE, "source")
                CollectionManager.emulatedOpenFailure = CollectionManager.CollectionOpenFailure.LOCKED
                try {
                    screen.confirm(TagAction.DELETE).performClick()
                    compose.waitForIdle()
                } finally {
                    CollectionManager.emulatedOpenFailure = null
                }
                compose.onNodeWithText(targetContext.getString(CommonString.vague_error)).assertIsDisplayed()
                compose.onNodeWithTag("tag:source").assertExists()
            }
            failDeletion()
            compose.onNodeWithText(targetContext.getString(CommonString.dialog_ok)).performClick()
            compose.waitForIdle()
            assertNull(assertIs<ManageTagsState.Content>(screen.viewModel.state.value).error)
            failDeletion()
            screen.cancel()
            screen.controller
                .pause()
                .stop()
                .start()
                .resume()
            screen.recreate()
            compose.onNodeWithText(targetContext.getString(CommonString.vague_error)).assertDoesNotExist()
            failDeletion()
        }

    @Test
    fun `actions are disabled while work is pending and search remains available`() =
        runTest {
            addTags("source::child")
            screen.open()
            compose.waitForIdle()
            val previous = CollectionManager.setTestDispatcher(StandardTestDispatcher(testScheduler), useReentrantLock = false)
            try {
                val refresh = screen.viewModel.refreshTags()
                assertTrue(assertIs<ManageTagsState.Content>(screen.viewModel.state.value).isWorking)
                compose.onNodeWithTag("tag:source").assertIsNotEnabled()
                screen.search("source")
                refresh.join()
                compose.onNodeWithTag("tag:source").assertIsEnabled()
            } finally {
                CollectionManager.setTestDispatcher(previous)
            }
        }

    @Test
    fun `action and input drafts survive activity recreation`() =
        runTest {
            val note = addTags("source", "target")
            screen.open()
            screen.action(TagAction.RENAME, "source")
            screen.enterText("draft")
            screen.recreate()
            compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).assertTextContains("draft")
            screen.confirm(TagAction.RENAME).assertIsEnabled()
            screen.cancel()
            screen.action(TagAction.DELETE, "source")
            screen.recreate()
            screen.confirm(TagAction.DELETE).performClick()
            compose.waitForIdle()
            assertEquals(listOf("target"), col.getNote(note.id).tags)
        }

    @Test
    fun `sheet actions survive recreation and are reachable in landscape`() =
        runTest {
            RuntimeEnvironment.setQualifiers("+land")
            addTags("source")
            screen.open()
            screen.showActions("source")
            screen.recreate()
            compose.onNodeWithText(targetContext.getString(CommonString.rename)).assertIsDisplayed()
            compose.onNodeWithText(targetContext.getString(CommonString.dialog_positive_delete)).performClick()
            screen.confirm(TagAction.DELETE).performClick()
            compose.waitForIdle()
            assertTrue(col.tags.all().isEmpty())
        }

    @After
    fun closeScreen() = screen.close()

    private fun addTags(vararg tags: String) = addBasicNote().update { tags.forEach(::addTag) }
}
