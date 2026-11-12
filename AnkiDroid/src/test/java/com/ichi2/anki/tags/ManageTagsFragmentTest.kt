// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CommonPlurals
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.ui.internationalization.sentenceCase
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
    fun `undo remains available after deleting the last tag and recreating the screen`() {
        val note = addTags("source")
        screen.open()
        val undo = compose.onNodeWithTag("tag_undo")
        undo.assertIsDisplayed().assertIsEnabled()

        screen.action(TagAction.DELETE, "source")
        screen.confirm(TagAction.DELETE).performClick()
        compose.waitForIdle()
        assertEquals(emptyList(), col.getNote(note.id).tags)
        undo.assertIsEnabled()

        screen.recreate()
        undo.assertIsEnabled().performClick()
        compose.onNodeWithTag("tag:source").assertIsDisplayed()
        assertEquals(listOf("source"), col.getNote(note.id).tags)
        undo.assertIsEnabled()
        compose.onNodeWithTag("tag_redo").assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals(emptyList(), col.getNote(note.id).tags)
        undo.assertIsEnabled()
        compose.onNodeWithTag("tag_redo").assertIsNotEnabled()
    }

    @Test
    fun `expanding a tag disables redo when the backend clears its history`() {
        addTags("source", "parent::child")
        screen.open()
        screen.action(TagAction.DELETE, "source")
        screen.confirm(TagAction.DELETE).performClick()
        compose.onNodeWithTag("tag_undo").assertIsEnabled().performClick()
        compose.onNodeWithTag("tag_redo").assertIsEnabled()

        compose.onNodeWithTag("tag:parent").performClick()

        compose.onNodeWithTag("tag:parent::child").assertIsDisplayed()
        assertNull(col.undoStatus().redo)
        compose.onNodeWithTag("tag_redo").assertIsNotEnabled()
        compose.onNodeWithTag("tag_undo").assertIsEnabled()
    }

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
    @Config(qualifiers = "w320dp-h480dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `expanding the last tag reveals its child without another scroll`() = checkLastTagExpansion(false)

    @Test
    @Config(qualifiers = "w320dp-h480dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `expanding the last tag reveals its child with animations disabled`() = checkLastTagExpansion(true)

    private fun checkLastTagExpansion(removeAnimations: Boolean) {
        editPreferences { putBoolean(targetContext.getString(R.string.safe_display_key), removeAnimations) }
        addTags(*(0..29).map { "Tag${it.toString().padStart(2, '0')}" }.toTypedArray(), "ZZ::child")
        screen.open()
        val list = compose.onNodeWithTag("tag_list")
        list.performScrollToNode(hasTestTag("tag:ZZ"))
        compose.onNodeWithTag("tag:ZZ").performClick()
        val child = compose.onNodeWithTag("tag:ZZ::child").assertIsDisplayed()
        val viewport = list.fetchSemanticsNode().boundsInRoot
        assertTrue(child.fetchSemanticsNode().boundsInRoot.bottom <= viewport.bottom + 1)
        assertTrue(
            compose
                .onNodeWithTag("tag:ZZ")
                .fetchSemanticsNode()
                .boundsInRoot.top >= viewport.top - 1,
        )
        compose.onNodeWithContentDescription(targetContext.getString(CommonString.manage_tags_more_actions, "ZZ::child")).performClick()
        compose.onNodeWithText("ZZ::child").assertIsDisplayed()
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
    @Config(qualifiers = "w411dp-h891dp-port")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `portrait confirmation keeps actions visible`() = checkConfirmationActions()

    @Test
    @Config(qualifiers = "w891dp-h411dp-land")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `landscape confirmation keeps actions visible`() = checkConfirmationActions()

    private fun checkConfirmationActions() {
        addTags("used")
        screen.open()
        screen.clearUnused()
        screen.confirm(TagAction.CLEAR_UNUSED).assertIsDisplayed()
        compose.onNodeWithText(targetContext.getString(CommonString.dialog_cancel)).assertIsDisplayed().performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun `clear unused is hidden only when the collection has no tags`() =
        runTest {
            screen.open()
            val clearUnused =
                compose.onNodeWithContentDescription(
                    with(targetContext) { CollectionManager.TR.sentenceCase.clearUnusedTags },
                )
            clearUnused.assertDoesNotExist()

            addTags("used")
            screen.viewModel.refreshTags().join()
            clearUnused.assertIsDisplayed()
            screen.search("no matches")
            compose.onNodeWithTag("tag:used").assertDoesNotExist()
            clearUnused.assertIsDisplayed()

            screen.search("")
            screen.action(TagAction.DELETE, "used")
            screen.confirm(TagAction.DELETE).performClick()
            compose.waitForIdle()
            assertTrue(col.tags.all().isEmpty())
            clearUnused.assertDoesNotExist()
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
            compose.onNodeWithTag("tag_undo").performClick()
            compose.onNodeWithText(CollectionManager.TR.undoActionUndone("Clear unused tags")).assertIsDisplayed()
            assertEquals(setOf("used", "unused"), col.tags.all().toSet())
            compose.onNodeWithTag("tag_redo").performClick()
            compose.onNodeWithText(CollectionManager.TR.undoActionRedone("Clear unused tags")).assertIsDisplayed()
            assertEquals(listOf("used"), col.tags.all())
        }

    @Test
    fun `a trailing space does not rename a tag into a blank component`() =
        runTest {
            val note = addTags("source::child")
            screen.open()
            screen.action(TagAction.RENAME, "source")
            screen.enterText("target ")
            screen.confirm(TagAction.RENAME).assertIsNotEnabled().performClick()
            compose.waitForIdle()
            assertEquals(listOf("source::child"), col.getNote(note.id).tags)
            screen.enterText("target")
            screen.confirm(TagAction.RENAME).assertIsEnabled().performClick()
            compose.waitForIdle()
            assertEquals(listOf("target::child"), col.getNote(note.id).tags)
        }

    @Test
    fun `unused tags explain why rename is disabled in tag actions`() =
        runTest {
            addTags("unused").update { removeTag("unused") }
            val history = col.undoStatus()
            screen.open()
            screen.showActions("unused")
            compose.onNodeWithText(targetContext.getString(CommonString.manage_tags_rename_no_notes)).assertIsDisplayed()
            compose.onNodeWithText(targetContext.getString(CommonString.rename)).assertIsNotEnabled().performClick()
            compose.onNodeWithText(targetContext.getString(CommonString.dialog_positive_delete)).assertIsEnabled()
            compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).assertDoesNotExist()
            assertEquals(listOf("unused"), col.tags.all())
            assertEquals(history, col.undoStatus())
            assertTrue(
                screen.viewModel.pendingMessages.value
                    .isEmpty(),
            )
        }

    @Test
    fun `an unused parent with used descendants can be renamed`() {
        addTags("parent::used", "parent::unused").update { removeTag("parent::unused") }
        screen.open()
        screen.showActions("parent")
        compose.onNodeWithText(targetContext.getString(CommonString.rename)).assertIsEnabled()
        compose.onNodeWithText(targetContext.getString(CommonString.manage_tags_rename_no_notes)).assertDoesNotExist()
    }

    @Test
    fun `tag actions recheck rename availability when note usage changes without changing the tree`() =
        runTest {
            val note = addTags("source")
            screen.open()
            screen.showActions("source")
            val rename = compose.onNodeWithText(targetContext.getString(CommonString.rename))
            val warning = compose.onNodeWithText(targetContext.getString(CommonString.manage_tags_rename_no_notes))
            rename.assertIsEnabled()

            note.update { removeTag("source") }
            screen.viewModel.refreshTags().join()
            warning.assertIsDisplayed()
            rename.assertIsNotEnabled()

            note.update { addTag("source") }
            screen.viewModel.refreshTags().join()
            warning.assertDoesNotExist()
            rename.assertIsEnabled()
        }

    @Test
    fun `rename waits for the usage check while delete stays available`() =
        runTest {
            addTags("source")
            screen.open()
            val previous = CollectionManager.setTestDispatcher(StandardTestDispatcher(testScheduler), useReentrantLock = false)
            try {
                screen.showActions("source")
                compose.onNodeWithText(targetContext.getString(CommonString.rename)).assertIsNotEnabled()
                compose.onNodeWithText(targetContext.getString(CommonString.dialog_positive_delete)).assertIsEnabled()
                compose.onNodeWithText(targetContext.getString(CommonString.manage_tags_rename_no_notes)).assertDoesNotExist()
                testScheduler.advanceUntilIdle()
                compose.onNodeWithText(targetContext.getString(CommonString.rename)).assertIsEnabled()
            } finally {
                testScheduler.advanceUntilIdle()
                CollectionManager.setTestDispatcher(previous)
            }
        }

    @Test
    fun `backgrounding keeps an unfinished message queued for resume`() =
        runTest {
            addTags("source")
            screen.open()
            screen.action(TagAction.RENAME, "source")
            screen.enterText("renamed")
            screen.confirm(TagAction.RENAME).performClick()
            val message = targetContext.resources.getQuantityString(CommonPlurals.manage_tags_renamed_on_notes, 1, 1)
            compose.onNodeWithText(message).assertIsDisplayed()
            screen.controller.pause().stop()
            compose.mainClock.advanceTimeBy(10_000)
            compose.waitForIdle()
            assertEquals(1, screen.viewModel.pendingMessages.value.size)
            screen.controller.start().resume()
            compose.onNodeWithText(message).assertIsDisplayed()
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

    @Test
    @Config(qualifiers = "w411dp-h891dp-port")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `delete targets the clicked hierarchy after successive renames and row changes`() {
        val root = "I've::gota::lovely"
        val original = "$root::brunch"
        val renamed = "$root::bruncha"
        val target = "$renamed::a"
        val affected = addTags("$original::child")
        val sibling = addTags("$root::other::a")
        val unrelated = addTags("elsewhere::a")
        screen.open()
        screen.search("brunch")

        fun rename(
            from: String,
            to: String,
        ) {
            screen.action(TagAction.RENAME, from)
            screen.enterText(to)
            screen.confirm(TagAction.RENAME).performClick()
            compose.waitForIdle()
        }

        rename(original, "$original::hh")
        rename(original, renamed)
        rename(renamed, target)
        assertEquals(listOf("$target::hh::child"), col.getNote(affected.id).tags)

        // Reveal the target explicitly: backend renames may collapse the new branch.
        screen.search(target)
        compose.onNodeWithTag("tag_list").performScrollToNode(hasTestTag("tag:$target"))
        compose
            .onNodeWithContentDescription(targetContext.getString(CommonString.manage_tags_more_actions, target))
            .performTouchInput { click() }
        compose.onNodeWithTag("selected_tag_name").assertTextContains(target)
        compose.onNodeWithText(targetContext.getString(CommonString.dialog_positive_delete)).performClick()
        val confirmation = targetContext.getString(CommonString.manage_tags_delete_confirm, target)
        compose.onNodeWithText(confirmation).assertIsDisplayed()

        // Changing the rows behind the dialog must not retarget the selected tag by position.
        compose.runOnIdle { screen.viewModel.filter("other") }
        compose.onNodeWithText(confirmation).assertIsDisplayed()
        screen.confirm(TagAction.DELETE).performClick()
        compose.waitForIdle()
        assertEquals(emptyList(), col.getNote(affected.id).tags)
        assertEquals(listOf("$root::other::a"), col.getNote(sibling.id).tags)
        assertEquals(listOf("elsewhere::a"), col.getNote(unrelated.id).tags)
    }

    @After
    fun closeScreen() = screen.close()

    private fun addTags(vararg tags: String) = addBasicNote().update { tags.forEach(::addTag) }
}
