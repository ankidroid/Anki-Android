// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

import android.os.Build
import android.view.KeyEvent
import android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
import android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
import android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
import androidx.activity.ComponentDialog
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
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
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.AnnotatedString
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commitNow
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CommonPlurals
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.testutils.dispatchInsets
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.After
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Runs the Compose actions against a real collection. */
@RunWith(AndroidJUnit4::class)
class ManageTagsFragmentTest : RobolectricTest() {
    @get:Rule
    val compose = createComposeRule()
    private val screen = ManageTagsTestHelper(this, compose)

    @Test
    @Config(sdk = [29, 30])
    fun `keyboard resize preserves the window's keyboard visibility setting`() {
        Robolectric.buildActivity(FragmentActivity::class.java).use { controller ->
            val activity = controller.setup().get()
            val window = activity.window
            window.setSoftInputMode(SOFT_INPUT_STATE_ALWAYS_HIDDEN or SOFT_INPUT_ADJUST_PAN)

            activity.supportFragmentManager.commitNow { add(ManageTagsFragment(), null) }

            @Suppress("DEPRECATION")
            val adjustment = if (Build.VERSION.SDK_INT < 30) SOFT_INPUT_ADJUST_RESIZE else SOFT_INPUT_ADJUST_PAN
            assertEquals(SOFT_INPUT_STATE_ALWAYS_HIDDEN or adjustment, window.attributes.softInputMode)
        }
    }

    @Test
    fun `a search with a space between tag levels finds the child tag`() {
        addTags("science::biology", "history")
        screen.open()
        screen.search("science biology")
        compose.onNodeWithTag("tag:science::biology").assertIsDisplayed()
        compose.onNodeWithTag("tag:history").assertDoesNotExist()
    }

    @Test
    fun `choosing delete while back closes the actions sheet opens the confirmation`() = chooseActionWhileClosingSheet(TagAction.DELETE)

    @Test
    fun `choosing rename while back closes the actions sheet opens the rename dialog`() = chooseActionWhileClosingSheet(TagAction.RENAME)

    private fun chooseActionWhileClosingSheet(action: TagAction) {
        val note = addTags("source")
        screen.open()
        screen.showActions("source")
        compose.waitForIdle()
        val sheet = assertIs<ComponentDialog>(ShadowDialog.getLatestDialog())
        compose.mainClock.autoAdvance = false
        try {
            // Choose an action while the sheet is still sliding away after Back.
            compose.runOnUiThread { sheet.onBackPressedDispatcher.onBackPressed() }
            compose.mainClock.advanceTimeByFrame()
            screen.confirm(action).performSemanticsAction(SemanticsActions.OnClick)
            compose.mainClock.advanceTimeBy(1_000)
            screen.confirm(action).assertIsDisplayed()
            if (action == TagAction.RENAME) {
                compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).assertTextContains("source")
            } else {
                compose.onNodeWithText(targetContext.getString(CommonString.manage_tags_delete_confirm, "source")).assertIsDisplayed()
            }
            assertEquals(listOf("source"), col.getNote(note.id).tags)
        } finally {
            compose.mainClock.autoAdvance = true
        }
        screen.cancel()
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.onNodeWithTag("selected_tag_name").assertDoesNotExist()
    }

    @Test
    fun `an ideographic space in a new name creates a hierarchy separator`() {
        val note = addTags("source")
        screen.open()
        screen.action(TagAction.RENAME, "source")
        screen.enterText("日本\u3000語")
        screen.confirm(TagAction.RENAME).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(targetContext.getString(CommonString.vague_error)).assertDoesNotExist()
        assertEquals(listOf("日本::語"), col.getNote(note.id).tags)

        screen.search("日本\u3000語")
        compose.onNodeWithTag("tag:日本::語").assertIsDisplayed()
    }

    @Test
    fun `rename and delete shortcuts act on the focused tag after filtering and renaming`() {
        val first = addTags("first::leaf")
        val second = addTags("second::leaf")
        screen.open()
        screen.search("leaf")

        tagShortcut("first::leaf", Key.R)
        compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).assertTextContains("first::leaf")
        assertEquals(listOf("first::leaf"), col.getNote(first.id).tags)
        screen.enterText("first::renamed")
        screen.confirm(TagAction.RENAME).performClick()
        compose.waitForIdle()
        assertEquals(listOf("first::renamed"), col.getNote(first.id).tags)

        tagShortcut("second::leaf", Key.Delete)
        compose.onNodeWithText(targetContext.getString(CommonString.manage_tags_delete_confirm, "second::leaf")).assertIsDisplayed()
        assertEquals(listOf("second::leaf"), col.getNote(second.id).tags)
        screen.cancel()
        tagShortcut("second::leaf", Key.Backspace)
        screen.confirm(TagAction.DELETE).performClick()
        compose.waitForIdle()
        assertEquals(emptyList(), col.getNote(second.id).tags)
        assertEquals(listOf("first::renamed"), col.getNote(first.id).tags)

        val shortcuts =
            screen.fragment.shortcuts.shortcuts
                .map { it.toShortcutInfo() }
        assertTrue(shortcuts.any { it.keycode == KeyEvent.KEYCODE_R && it.modifiers == 0 })
        assertTrue(shortcuts.any { it.keycode == KeyEvent.KEYCODE_FORWARD_DEL && it.modifiers == 0 })
        assertTrue(shortcuts.any { it.keycode == KeyEvent.KEYCODE_DEL && it.modifiers == 0 })
    }

    @Test
    fun `unused tag rename shortcut warns in a snackbar without opening a dialog`() {
        addTags("unused").update { removeTag("unused") }
        val history = col.undoStatus()
        screen.open()
        tagShortcut("unused", Key.R)
        compose.onNodeWithTag("tag_snackbar").assertIsDisplayed()
        compose.onNodeWithText(targetContext.getString(CommonString.manage_tags_rename_no_notes)).assertIsDisplayed()
        compose.onNodeWithTag("selected_tag_name").assertDoesNotExist()
        compose.onNode(isDialog()).assertDoesNotExist()
        assertEquals(history, col.undoStatus())
    }

    @Test
    fun `shortcuts work as soon as the actions sheet opens`() {
        addTags("source")
        screen.open()

        fun pressSheetShortcut(keyCode: Int) {
            compose.onNodeWithText(targetContext.getString(CommonString.rename)).assertIsEnabled()
            val window = requireNotNull(ShadowDialog.getLatestDialog().window)
            val down = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            compose.runOnUiThread {
                window.decorView.dispatchKeyEvent(down)
                window.decorView.dispatchKeyEvent(KeyEvent.changeAction(down, KeyEvent.ACTION_UP))
            }
            compose.waitForIdle()
        }

        screen.showActions("source")
        pressSheetShortcut(KeyEvent.KEYCODE_R)
        compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).assertTextContains("source")
        screen.cancel()
        tagShortcut("source", Key.Enter)
        pressSheetShortcut(KeyEvent.KEYCODE_FORWARD_DEL)
        compose.onNodeWithText(targetContext.getString(CommonString.manage_tags_delete_confirm, "source")).assertIsDisplayed()
    }

    @Test
    fun `unused tag actions disable rename shortcuts and still confirm deletion`() {
        addTags("unused").update { removeTag("unused") }
        val history = col.undoStatus()
        screen.open()
        screen.showActions("unused")
        compose.onNodeWithText(targetContext.getString(CommonString.manage_tags_rename_no_notes)).assertIsDisplayed()
        compose.onNodeWithText(targetContext.getString(CommonString.rename)).assertIsNotEnabled()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).assertDoesNotExist()
        assertEquals(history, col.undoStatus())

        val delete = compose.onNodeWithText(targetContext.getString(CommonString.dialog_positive_delete))
        delete.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        delete.performKeyInput { pressKey(Key.R) }
        compose.onNodeWithText(targetContext.getString(CommonString.manage_tags_rename_no_notes)).assertIsDisplayed()
        delete.performKeyInput { pressKey(Key.Delete) }
        compose.onNodeWithText(targetContext.getString(CommonString.manage_tags_delete_confirm, "unused")).assertIsDisplayed()
        assertEquals(history, col.undoStatus())
        screen.cancel()
    }

    @Test
    fun `tag shortcuts do not trigger while editing search or a rename`() {
        val note = addTags("source")
        screen.open()
        screen.search("source")
        val search = compose.onNode(hasSetTextAction() and !hasAnyAncestor(isDialog()))
        search.assertIsFocused().performKeyInput {
            pressKey(Key.R)
            pressKey(Key.Delete)
            pressKey(Key.Backspace)
        }
        compose.onNode(isDialog()).assertDoesNotExist()
        assertEquals(listOf("source"), col.getNote(note.id).tags)

        screen.search("")
        tagShortcut("source", Key.R)
        val input = compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog()))
        input.assertIsFocused().performKeyInput {
            pressKey(Key.R)
            pressKey(Key.Delete)
            pressKey(Key.Backspace)
        }
        compose.onNodeWithText(targetContext.getString(CommonString.manage_tags_delete_title)).assertDoesNotExist()
        assertEquals(listOf("source"), col.getNote(note.id).tags)
        screen.cancel()
    }

    @Test
    fun `a branch collapsed in search results stays collapsed after process death`() {
        addTags("source::branch::leaf")
        screen.open()
        screen.search("leaf")
        compose.onNodeWithTag("tag:source::branch::leaf").assertIsDisplayed()
        compose.onNodeWithTag("tag:source::branch").performClick()
        compose.onNodeWithTag("tag:source::branch::leaf").assertDoesNotExist()

        screen.recreateAfterProcessDeath()
        compose.onNode(hasSetTextAction() and !hasAnyAncestor(isDialog())).assertTextContains("leaf")
        compose.onNodeWithTag("tag:source::branch").assertIsDisplayed()
        compose.onNodeWithTag("tag:source::branch::leaf").assertDoesNotExist()

        screen.search("lea")
        compose.onNodeWithTag("tag:source::branch::leaf").assertIsDisplayed()
    }

    @Test
    fun `the tag search is restored after process death`() {
        addTags("biology", "chemistry")
        screen.open()
        screen.search("bio")
        compose.onNodeWithTag("tag:biology").assertIsDisplayed()
        compose.onNodeWithTag("tag:chemistry").assertDoesNotExist()
        val originalViewModel = screen.viewModel

        screen.recreateAfterProcessDeath()
        compose.waitForIdle()

        assertNotSame(originalViewModel, screen.viewModel)
        compose.onNode(hasSetTextAction() and !hasAnyAncestor(isDialog())).assertTextContains("bio")
        compose.onNodeWithTag("tag:biology").assertIsDisplayed()
        compose.onNodeWithTag("tag:chemistry").assertDoesNotExist()
    }

    private fun tagShortcut(
        tag: String,
        key: Key,
    ) {
        compose.onNodeWithTag("tag_list").performScrollToNode(hasTestTag("tag:$tag"))
        val target = compose.onNodeWithContentDescription(targetContext.getString(CommonString.manage_tags_more_actions, tag))
        target.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        target.assertIsFocused().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    @Test
    fun `undo remains available after deleting the last tag and recreating the screen`() {
        val note = addTags("source")
        screen.open()
        val undo = compose.onNodeWithTag("tag_undo")
        undo.assertIsDisplayed().assertIsNotEnabled()

        screen.action(TagAction.DELETE, "source")
        screen.confirm(TagAction.DELETE).performClick()
        compose.waitForIdle()
        assertEquals(emptyList(), col.getNote(note.id).tags)
        undo.assertIsEnabled()

        screen.recreate()
        undo.assertIsEnabled().performClick()
        compose.onNodeWithTag("tag:source").assertIsDisplayed()
        assertEquals(listOf("source"), col.getNote(note.id).tags)
        undo.assertIsNotEnabled()
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
        compose.onNodeWithTag("tag_undo").assertIsNotEnabled()
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

    /**
     * An IME or accessibility service can replace the search text twice before the next frame.
     * Returning to the original text is still a change relative to the first replacement.
     */
    @Test
    fun `two search edits before recomposition keep the final text`() {
        addTags("abc")
        screen.open()
        screen.search("abc")
        val input = compose.onNode(hasSetTextAction())
        // Put the cursor at the start, so the final edit (cursor at the end) still reaches the
        // field's callback even though its text matches the value in the last composition.
        input.performSemanticsAction(SemanticsActions.SetSelection) { it(0, 0, false) }
        val setText = requireNotNull(input.fetchSemanticsNode().config[SemanticsActions.SetText].action)
        compose.runOnUiThread {
            // Invoke both edits before Compose renders either of them.
            assertTrue(setText(AnnotatedString("abcd")))
            assertTrue(setText(AnnotatedString("abc")))
        }
        // Fails on the buggy head: the last composed query dropped the second edit.
        assertEquals("abc", screen.viewModel.searchQuery.value)
        compose.onNodeWithTag("tag:abc").assertIsDisplayed()
    }

    /**
     * The dialog rejects existing destinations. That rule must also hold if a queued edit creates
     * the destination after confirmation, before the rename can access the collection.
     */
    @Test
    fun `rename does not merge into a destination created while confirmation waits`() =
        runTest {
            val source = addTags("source")
            val other = addTags("other")
            screen.open()
            screen.action(TagAction.RENAME, "source")
            screen.enterText("destination")
            val confirm = screen.confirm(TagAction.RENAME).assertIsEnabled()

            // Another client already has a write queued to add the tag. Neither operation can
            // access the collection until the test scheduler is resumed.
            val previous = CollectionManager.setTestDispatcher(StandardTestDispatcher(testScheduler), useReentrantLock = false)
            try {
                val edit =
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        CollectionManager.withCol {
                            getNote(other.id).also {
                                it.addTag("destination")
                                updateNote(it)
                            }
                        }
                    }
                confirm.performClick()
                compose.waitForIdle()
                assertTrue(assertIs<ManageTagsState.Content>(screen.viewModel.state.value).isWorking)
                testScheduler.advanceUntilIdle()
                edit.join()
            } finally {
                CollectionManager.setTestDispatcher(previous)
            }

            // Fails on the buggy head: the rename merges source into the newly created destination.
            assertEquals(listOf("source"), col.getNote(source.id).tags)
            assertEquals(setOf("destination", "other"), col.getNote(other.id).tags.toSet())
            compose.onNodeWithText(targetContext.getString(CommonString.tag_already_exists)).assertIsDisplayed()
        }

    /**
     * The backend appends a separator to a destination ending in a colon. If another client
     * creates the resulting path after validation, the rename must still reject the merge.
     */
    @Test
    fun `a queued rename rejects a destination whose separator overlaps a trailing colon`() =
        runTest {
            // Renaming this parent to `target:` would turn the child into `target:::child`.
            val source = addTags("source::child")
            val other = addTags("other")
            screen.open()
            screen.action(TagAction.RENAME, "source")
            screen.enterText("target:")
            val confirm = screen.confirm(TagAction.RENAME).assertIsEnabled()

            // Put the concurrent write ahead of the rename. Validation saw a free destination,
            // but the collection-level check must see the newly registered child.
            val previous = CollectionManager.setTestDispatcher(StandardTestDispatcher(testScheduler), useReentrantLock = false)
            try {
                val edit =
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        CollectionManager.withCol {
                            getNote(other.id).also {
                                it.addTag("target:::child")
                                updateNote(it)
                            }
                        }
                    }
                confirm.performClick()
                compose.waitForIdle()
                assertTrue(assertIs<ManageTagsState.Content>(screen.viewModel.state.value).isWorking)
                testScheduler.advanceUntilIdle()
                edit.join()
            } finally {
                CollectionManager.setTestDispatcher(previous)
            }

            // On the buggy revision, the overlapping separator hides the collision and
            // this assertion finds the other note's tag on the source note.
            assertEquals(listOf("source::child"), col.getNote(source.id).tags)
            assertEquals(setOf("other", "target:::child"), col.getNote(other.id).tags.toSet())
            compose.onNodeWithText(targetContext.getString(CommonString.tag_already_exists)).assertIsDisplayed()
        }

    /**
     * A rejected keyboard rename keeps the source. Restore focus there after the operation
     * rather than selecting a destination created by another client.
     */
    @Test
    fun `a rejected keyboard rename leaves focus on the source`() =
        runTest {
            val source = addTags("source")
            val other = addTags("other")
            screen.open()
            val sourceActions =
                compose.onNodeWithContentDescription(
                    targetContext.getString(CommonString.manage_tags_more_actions, "source"),
                )
            prepareRenameShortcut(sourceActions)
            screen.enterText("destination")
            val confirm = screen.confirm(TagAction.RENAME).assertIsEnabled()

            // Another client already has a write queued to add the tag. Neither operation can
            // access the collection until the test scheduler is resumed.
            val previous = CollectionManager.setTestDispatcher(StandardTestDispatcher(testScheduler), useReentrantLock = false)
            try {
                val edit =
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        CollectionManager.withCol {
                            getNote(other.id).also {
                                it.addTag("destination")
                                updateNote(it)
                            }
                        }
                    }
                confirm.performClick()
                compose.waitForIdle()
                assertTrue(assertIs<ManageTagsState.Content>(screen.viewModel.state.value).isWorking)
                testScheduler.advanceUntilIdle()
                edit.join()
            } finally {
                CollectionManager.setTestDispatcher(previous)
            }

            // The mutation was rejected. Focus must follow the unchanged source rather than
            // the newly created destination, or the next keyboard action can target that tag.
            assertEquals(listOf("source"), col.getNote(source.id).tags)
            assertEquals(setOf("destination", "other"), col.getNote(other.id).tags.toSet())
            compose.onNodeWithText(targetContext.getString(CommonString.tag_already_exists)).assertIsDisplayed()
            sourceActions.assertIsFocused().performKeyInput { pressKey(Key.Delete) }
            compose.onNodeWithText(targetContext.getString(CommonString.manage_tags_delete_confirm, "source")).assertIsDisplayed()
        }

    /**
     * A collection refresh can start after the dialog rendered an enabled confirm handler. A
     * confirmation that cannot run must keep the user's draft available for retry.
     */
    @Test
    fun `rename keeps its draft when a refresh starts just before confirm`() =
        runTest {
            val note = addTags("source")
            screen.open()
            screen.action(TagAction.RENAME, "source")
            screen.enterText("destination")
            val confirm = screen.confirm(TagAction.RENAME).assertIsEnabled()
            val onClick = requireNotNull(confirm.fetchSemanticsNode().config[SemanticsActions.OnClick].action)

            // A refresh owns the collection queue but Compose hasn't rendered the disabled
            // button yet. Deliver the already-installed handler before rendering another frame.
            val previous = CollectionManager.setTestDispatcher(StandardTestDispatcher(testScheduler), useReentrantLock = false)
            try {
                compose.runOnUiThread {
                    val refresh = screen.viewModel.refreshTags()
                    assertFalse(refresh.isCompleted)
                    assertTrue(onClick())
                }
                testScheduler.advanceUntilIdle()
            } finally {
                CollectionManager.setTestDispatcher(previous)
            }

            assertEquals(listOf("source"), col.getNote(note.id).tags)
            // Fails on the buggy revision: the rejected rename closed the dialog and lost its draft.
            compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).assertTextContains("destination")
            confirm.assertIsEnabled().performClick()
            assertEquals(listOf("destination"), col.getNote(note.id).tags)
        }

    /**
     * A destination can have different Unicode code points but normalize to the same stored tag.
     * Treat it as unchanged so the backend does not rewrite the subtree and drop unused tags.
     */
    @Test
    fun `a rename that normalizes to the current tag keeps unused descendants`() {
        val note = addTags("caf\u00e9")
        val unused = "caf\u00e9::draft"
        // Save an expansion preference to register an unused child in the actual tag cache.
        col.tags.setCollapsed(unused, true)
        assertTrue(unused in col.tags.all())
        screen.open()
        screen.action(TagAction.RENAME, "caf\u00e9")
        screen.enterText("cafe\u0301")

        // IME confirmation follows the same validation as the button. On the buggy head it
        // submits the equivalent spelling and the backend deletes this unused cached child.
        compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performImeAction()
        compose.waitForIdle()
        assertTrue(unused in col.tags.all())
        assertEquals(listOf("caf\u00e9"), col.getNote(note.id).tags)
        screen.confirm(TagAction.RENAME).assertIsNotEnabled()
    }

    /**
     * Android's Unicode case folding equates U+A7C0 and U+A7C1, while the backend registry
     * stores them as distinct tags. An ordinary rename must reject that occupied destination.
     */
    @Test
    fun `rename rejects an occupied destination even when Android treats the names as equal`() {
        val sourceTag = "\ua7c0"
        val destinationTag = "\ua7c1"
        val source = addTags(sourceTag)
        val destination = addTags(destinationTag)
        // Both notes really have different tags in the collection. This is not a case-only
        // rename, even though the Android-side identity comparison treats it as one.
        assertTrue(col.tags.all().containsAll(listOf(sourceTag, destinationTag)))
        screen.open()
        screen.action(TagAction.RENAME, sourceTag)
        screen.enterText(destinationTag)
        compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performImeAction()
        compose.waitForIdle()

        // Fails on the buggy revision after it merges the source into the occupied destination.
        assertEquals(listOf(sourceTag), col.getNote(source.id).tags)
        assertEquals(listOf(destinationTag), col.getNote(destination.id).tags)
    }

    /**
     * Anki preserves registered parent tags' casing. Changing only an ancestor's case must
     * not rewrite the selected subtree or delete its unused cached descendants.
     */
    @Test
    fun `a rename that only changes a registered parent case preserves the tag tree`() {
        addTags("Parent")
        val note = addTags("Parent::child")
        val unused = "Parent::child::draft"
        // Register the unused child without adding it to a note, so an unintended rewrite
        // really loses user data instead of being concealed by the unchanged note tag.
        col.tags.setCollapsed(unused, true)
        assertTrue(unused in col.tags.all())
        screen.open()
        // Search opens matching ancestors even if their saved expansion is collapsed.
        screen.search("child")
        screen.action(TagAction.RENAME, "Parent::child")
        screen.enterText("parent::child")

        // On the buggy head Anki canonicalizes the destination back to Parent::child,
        // then removes the old cached subtree and re-registers only its uses on notes.
        compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performImeAction()
        compose.waitForIdle()
        assertTrue(unused in col.tags.all())
        assertEquals(listOf("Parent::child"), col.getNote(note.id).tags)
    }

    /**
     * Android's ICU can equate two names that the registry keeps distinct. An available
     * destination should still be usable when only its ICU-equivalent spelling exists.
     */
    @Test
    fun `rename allows an available destination that Android equates with an existing tag`() {
        val source = addTags("source")
        val otherTag = "\ua7c0"
        val destinationTag = "\ua7c1"
        val other = addTags(otherTag)
        // Only the other spelling has a registry entry. This is a legitimate new name.
        assertTrue(otherTag in col.tags.all())
        assertFalse(destinationTag in col.tags.all())
        screen.open()
        screen.action(TagAction.RENAME, "source")
        screen.enterText(destinationTag)

        // Fails on the buggy head: client-side equality blocks the valid rename before submit.
        screen.confirm(TagAction.RENAME).assertIsEnabled().performClick()
        assertEquals(listOf(destinationTag), col.getNote(source.id).tags)
        assertEquals(listOf(otherTag), col.getNote(other.id).tags)
    }

    /**
     * Appending the subtree separator to a destination ending in a colon can move a child
     * to a different path. Keep the dialog check for literal ASCII names while verifying
     * possible Unicode case collisions using the backend.
     */
    @Test
    fun `a trailing colon does not merge a renamed subtree into an occupied tag`() {
        val source = addTags("source::child")
        val other = addTags("target:::child")
        // Register a tag that does not appear to be a child of the typed destination `target:`.
        assertTrue("target:::child" in col.tags.all())
        screen.open()
        screen.action(TagAction.RENAME, "source")
        screen.enterText("target:")

        // Do not let a deferred collision check enable a merge into the other note's tag.
        compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performImeAction()
        compose.waitForIdle()
        assertEquals(listOf("source::child"), col.getNote(source.id).tags)
        assertEquals(listOf("target:::child"), col.getNote(other.id).tags)
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
        Prefs.hkey = "test-key"
        addTags("used")
        screen.open()
        screen.clearUnused()
        val disclosure = compose.onNodeWithTag("tag_sync_warning").assertIsDisplayed()
        val instructions =
            compose.onNodeWithText(
                with(targetContext) {
                    getString(CommonString.manage_tags_dialog_sync_instructions, CollectionManager.TR.sentenceCase.clearUnusedTags)
                },
            )
        instructions.assertDoesNotExist()
        screen.confirm(TagAction.CLEAR_UNUSED).assertIsDisplayed()
        disclosure.performClick()
        instructions.assertIsDisplayed()
        screen.confirm(TagAction.CLEAR_UNUSED).assertIsDisplayed()
        disclosure.performClick()
        instructions.assertDoesNotExist()
        compose.onNodeWithText(targetContext.getString(CommonString.dialog_cancel)).assertIsDisplayed().performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun `AnkiWeb tag warning follows deletions and survives a partial undo`() {
        Prefs.hkey = "test-key"
        addTags("first", "second")
        screen.open()
        val banner = compose.onNode(hasTestTag("tag_sync_warning") and !hasAnyAncestor(isDialog()))
        banner.assertDoesNotExist()
        screen.clearUnused()
        val callout = hasTestTag("tag_sync_warning") and hasAnyAncestor(isDialog())
        compose.onNode(callout).assertIsDisplayed()
        screen.confirm(TagAction.CLEAR_UNUSED).performClick()
        banner.assertDoesNotExist()

        screen.action(TagAction.DELETE, "first")
        compose.onNode(hasTestTag("tag_sync_warning") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        screen.confirm(TagAction.DELETE).performClick()
        banner.assertIsDisplayed()
        screen.action(TagAction.DELETE, "second")
        screen.confirm(TagAction.DELETE).performClick()
        banner.assertIsDisplayed()

        compose.onNodeWithTag("tag_undo").assertIsEnabled().performClick()
        banner.assertIsDisplayed()
        compose.onNodeWithTag("tag_undo").assertIsEnabled().performClick()
        banner.assertDoesNotExist()
        compose.onNodeWithTag("tag_redo").assertIsEnabled().performClick()
        banner.assertIsDisplayed()
    }

    /** Renaming a tag after undoing its deletion must not bring the old deletion warning back. */
    @Test
    fun `a rename does not reactivate an undone tag deletion warning`() {
        Prefs.hkey = "test-key"
        val note = addTags("alpha")
        screen.open()
        screen.action(TagAction.DELETE, "alpha")
        screen.confirm(TagAction.DELETE).performClick()
        val banner = compose.onNode(hasTestTag("tag_sync_warning") and !hasAnyAncestor(isDialog()))
        banner.assertIsDisplayed()

        // This undo restores the tag and leaves the deletion eligible for redo.
        compose.onNodeWithTag("tag_undo").performClick()
        banner.assertDoesNotExist()

        // A new rename clears redo and removes the old name without redoing the deletion.
        screen.action(TagAction.RENAME, "alpha")
        screen.enterText("beta")
        screen.confirm(TagAction.RENAME).performClick()
        compose.onNodeWithTag("tag:beta").assertIsDisplayed()
        assertEquals(listOf("beta"), col.getNote(note.id).tags)
        // A history-wide set of absent names wrongly treats this rename as a redo.
        banner.assertDoesNotExist()
    }

    /** The warning should follow a deletion undone or redone elsewhere in the shared collection. */
    @Test
    fun `the sync warning follows an undo from outside Manage Tags`() =
        runTest {
            Prefs.hkey = "test-key"
            val note = addTags("deleted")
            screen.open()
            screen.action(TagAction.DELETE, "deleted")
            screen.confirm(TagAction.DELETE).performClick()
            val banner = compose.onNode(hasTestTag("tag_sync_warning") and !hasAnyAncestor(isDialog()))
            banner.assertIsDisplayed()

            // Use the normal collection operation path for a different screen. It publishes
            // changes but does not use this screen's undo history object.
            undoableOp { undo() }
            compose.onNodeWithTag("tag:deleted").assertIsDisplayed()
            assertEquals(listOf("deleted"), col.getNote(note.id).tags)
            // Before the fix, the warning retains the obsolete step after the row has returned.
            banner.assertDoesNotExist()

            // Reapplying the same deletion externally should bring the warning back.
            undoableOp { redo() }
            compose.onNodeWithTag("tag:deleted").assertDoesNotExist()
            banner.assertIsDisplayed()
        }

    /** Process recreation restores the same Manage Tags visit, including an active sync warning. */
    @Test
    fun `a restored Manage Tags visit retains its sync warning and dismissal`() {
        Prefs.hkey = "test-key"
        addTags("deleted")
        screen.open()
        screen.action(TagAction.DELETE, "deleted")
        screen.confirm(TagAction.DELETE).performClick()
        val banner = compose.onNode(hasTestTag("tag_sync_warning") and !hasAnyAncestor(isDialog()))
        banner.assertIsDisplayed()

        // Restore the activity's saved state with a new ViewModel, as after process death.
        // The deletion still exists and this ViewModel no longer offers undo for it.
        screen.recreateAfterProcessDeath()
        assertTrue("deleted" !in col.tags.all())
        compose.onNodeWithTag("tag_undo").assertIsNotEnabled()
        // Fails on the original head, where the warning only tracks live undo steps.
        banner.assertIsDisplayed()

        // A restored deletion should still respect the dismissal saved for this visit.
        compose.onNodeWithContentDescription(targetContext.getString(CommonString.manage_tags_dismiss_sync_warning)).performClick()
        screen.recreateAfterProcessDeath()
        banner.assertDoesNotExist()
    }

    @Test
    fun `dismissing the AnkiWeb tag warning takes precedence over deletion history`() {
        Prefs.hkey = "test-key"
        addTags("first", "second", "unused").update { removeTag("unused") }
        screen.open()
        val banner = compose.onNode(hasTestTag("tag_sync_warning") and !hasAnyAncestor(isDialog()))
        screen.clearUnused()
        screen.confirm(TagAction.CLEAR_UNUSED).performClick()
        banner.assertIsDisplayed()
        compose.onNodeWithContentDescription(targetContext.getString(CommonString.manage_tags_dismiss_sync_warning)).performClick()
        banner.assertDoesNotExist()
        compose.onNodeWithTag("tag_undo").assertIsEnabled().performClick()
        compose.waitForIdle()
        assertTrue("unused" in col.tags.all())
        banner.assertDoesNotExist()
        compose.onNodeWithTag("tag_redo").assertIsEnabled().performClick()
        compose.waitForIdle()
        assertTrue("unused" !in col.tags.all())
        banner.assertDoesNotExist()
        screen.recreate()
        banner.assertDoesNotExist()
        screen.action(TagAction.DELETE, "first")
        screen.confirm(TagAction.DELETE).performClick()
        banner.assertDoesNotExist()

        screen.close()
        screen.open()
        banner.assertDoesNotExist()
        screen.action(TagAction.DELETE, "second")
        screen.confirm(TagAction.DELETE).performClick()
        banner.assertIsDisplayed()
    }

    @Test
    fun `signed out tag management omits the sync warning`() {
        Prefs.hkey = null
        checkSyncWarningAbsent()
    }

    @Test
    fun `custom sync server tag management omits the AnkiWeb warning`() {
        Prefs.hkey = "custom-key"
        editPreferences { putBoolean(targetContext.getString(R.string.custom_sync_server_enabled_key), true) }
        checkSyncWarningAbsent()
    }

    private fun checkSyncWarningAbsent() {
        addTags("used")
        screen.open()
        compose.onNodeWithTag("tag_sync_warning").assertDoesNotExist()
        screen.clearUnused()
        compose.onNodeWithText(targetContext.getString(CommonString.manage_tags_clear_unused_confirm)).assertIsDisplayed()
        compose.onNodeWithTag("tag_sync_warning").assertDoesNotExist()
        screen.cancel()
        screen.action(TagAction.DELETE, "used")
        compose.onNodeWithTag("tag_sync_warning").assertDoesNotExist()
        screen.confirm(TagAction.DELETE).performClick()
        compose.onNodeWithTag("tag_sync_warning").assertDoesNotExist()
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
    fun `cancelling a rename shortcut keeps the tag focused for another shortcut`() =
        runTest {
            addTags("source")
            screen.open()
            val target = compose.onNodeWithContentDescription(targetContext.getString(CommonString.manage_tags_more_actions, "source"))
            prepareRenameShortcut(target)
            compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).assertIsFocused()
            screen.cancel()
            compose.waitForIdle()
            target.assertIsFocused().performKeyInput { pressKey(Key.Delete) }
            compose.onNodeWithText(targetContext.getString(CommonString.manage_tags_delete_confirm, "source")).assertIsDisplayed()
            screen.cancel()
        }

    @Test
    fun `confirming a rename shortcut keeps the renamed tag focused for another shortcut`() =
        runTest {
            val note = addTags("source")
            addTags("other")
            screen.open()
            val target = compose.onNodeWithContentDescription(targetContext.getString(CommonString.manage_tags_more_actions, "source"))
            prepareRenameShortcut(target)
            screen.enterText("renamed")
            screen.confirm(TagAction.RENAME).performClick()
            compose.waitForIdle()
            assertEquals(listOf("renamed"), col.getNote(note.id).tags)
            compose
                .onNodeWithContentDescription(targetContext.getString(CommonString.manage_tags_more_actions, "renamed"))
                .assertIsFocused()
                .performKeyInput { pressKey(Key.Delete) }
            compose.onNodeWithText(targetContext.getString(CommonString.manage_tags_delete_confirm, "renamed")).assertIsDisplayed()
            screen.cancel()
        }

    @Test
    @Config(qualifiers = "w320dp-h480dp-mdpi")
    fun `confirming a rename shortcut focuses a parent after it moves outside the visible rows`() =
        runTest {
            val note = addTags("A::child")
            addTags(*(0..39).map { "Tag${it.toString().padStart(2, '0')}" }.toTypedArray())
            screen.open()
            prepareRenameShortcut(compose.onNodeWithTag("tag:A"))
            screen.enterText("Z")
            screen.confirm(TagAction.RENAME).performClick()
            compose.waitForIdle()
            assertEquals(listOf("Z::child"), col.getNote(note.id).tags)
            compose
                .onNodeWithTag("tag:Z")
                .assertIsDisplayed()
                .assertIsFocused()
                .performKeyInput { pressKey(Key.R) }
            compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).assertTextContains("Z")
            screen.cancel()
        }

    @Test
    fun `confirming a rename shortcut keeps focus moved to search during the operation`() =
        runTest {
            val note = addTags("source")
            screen.open()
            val target = compose.onNodeWithContentDescription(targetContext.getString(CommonString.manage_tags_more_actions, "source"))
            prepareRenameShortcut(target)
            screen.enterText("renamed")
            val confirm = screen.confirm(TagAction.RENAME).assertIsEnabled()
            val search = compose.onNode(hasSetTextAction() and !hasAnyAncestor(isDialog()))
            val previous = CollectionManager.setTestDispatcher(StandardTestDispatcher(testScheduler), useReentrantLock = false)
            try {
                confirm.performClick()
                compose.waitForIdle()
                search.performClick().assertIsFocused()
                testScheduler.advanceUntilIdle()
            } finally {
                CollectionManager.setTestDispatcher(previous)
            }
            compose.waitForIdle()
            assertEquals(listOf("renamed"), col.getNote(note.id).tags)
            search.assertIsFocused()
        }

    @Test
    fun `cancelling a rename shortcut restores focus to an expandable tag row`() =
        runTest {
            addTags("parent::child")
            screen.open()
            val target = compose.onNodeWithTag("tag:parent")
            prepareRenameShortcut(target)
            screen.cancel()
            target.assertIsFocused().performKeyInput { pressKey(Key.R) }
            compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).assertTextContains("parent")
            screen.cancel()
        }

    @Test
    fun `an unused tag retains shortcut focus after the rename usage check`() =
        runTest {
            addTags("unused").update { removeTag("unused") }
            screen.open()
            val target = compose.onNodeWithContentDescription(targetContext.getString(CommonString.manage_tags_more_actions, "unused"))
            prepareRenameShortcut(target)
            compose.onNodeWithText(targetContext.getString(CommonString.manage_tags_rename_no_notes)).assertIsDisplayed()
            compose.onNode(isDialog()).assertDoesNotExist()
            target.assertIsFocused().performKeyInput { pressKey(Key.Delete) }
            compose.onNodeWithText(targetContext.getString(CommonString.manage_tags_delete_confirm, "unused")).assertIsDisplayed()
            screen.cancel()
        }

    @Test
    fun `cancelling a rename keeps focus moved to search during the usage check`() =
        runTest {
            addTags("source")
            screen.open()
            val target = compose.onNodeWithContentDescription(targetContext.getString(CommonString.manage_tags_more_actions, "source"))
            val search = compose.onNode(hasSetTextAction() and !hasAnyAncestor(isDialog()))
            prepareRenameShortcut(target) {
                search.performClick()
                screen.search("source")
                search.assertIsFocused()
            }
            screen.cancel()
            search.assertIsFocused()
        }

    @Test
    fun `undoing with the shortcut keeps the tag focused for another shortcut`() =
        runTest {
            val deleted = addTags("source")
            addTags("other")
            screen.open()
            screen.action(TagAction.DELETE, "source")
            screen.confirm(TagAction.DELETE).performClick()
            compose.waitForIdle()
            val target = compose.onNodeWithContentDescription(targetContext.getString(CommonString.manage_tags_more_actions, "other"))
            performHistoryShortcut(target, Key.Z)
            assertEquals(listOf("source"), col.getNote(deleted.id).tags)
            target.assertIsFocused().performKeyInput { pressKey(Key.Delete) }
            compose.onNodeWithText(targetContext.getString(CommonString.manage_tags_delete_confirm, "other")).assertIsDisplayed()
            screen.cancel()
        }

    @Test
    fun `both redo shortcuts keep the tag focused for another shortcut`() =
        runTest {
            val deleted = addTags("source")
            addTags("other")
            screen.open()
            screen.action(TagAction.DELETE, "source")
            screen.confirm(TagAction.DELETE).performClick()
            compose.waitForIdle()
            val target = compose.onNodeWithContentDescription(targetContext.getString(CommonString.manage_tags_more_actions, "other"))
            for (shift in listOf(false, true)) {
                screen.viewModel.undo().join()
                performHistoryShortcut(target, if (shift) Key.Z else Key.Y, shift)
                assertEquals(emptyList(), col.getNote(deleted.id).tags)
                target.assertIsFocused().performKeyInput { pressKey(Key.Delete) }
                compose.onNodeWithText(targetContext.getString(CommonString.manage_tags_delete_confirm, "other")).assertIsDisplayed()
                screen.cancel()
            }
        }

    @Test
    fun `undo shortcut restores focus to an expandable tag row`() =
        runTest {
            addTags("source", "parent::child")
            screen.open()
            screen.action(TagAction.DELETE, "source")
            screen.confirm(TagAction.DELETE).performClick()
            val target = compose.onNodeWithTag("tag:parent")
            performHistoryShortcut(target, Key.Z)
            target.assertIsFocused().performKeyInput { pressKey(Key.R) }
            compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).assertTextContains("parent")
            screen.cancel()
        }

    @Test
    fun `undo shortcut keeps focus moved to search during the operation`() =
        runTest {
            val deleted = addTags("source")
            addTags("other")
            screen.open()
            screen.action(TagAction.DELETE, "source")
            screen.confirm(TagAction.DELETE).performClick()
            val target = compose.onNodeWithContentDescription(targetContext.getString(CommonString.manage_tags_more_actions, "other"))
            val search = compose.onNode(hasSetTextAction() and !hasAnyAncestor(isDialog()))
            performHistoryShortcut(target, Key.Z) {
                search.performClick()
                search.assertIsFocused()
            }
            assertEquals(listOf("source"), col.getNote(deleted.id).tags)
            search.assertIsFocused()
        }

    private fun TestScope.performHistoryShortcut(
        target: SemanticsNodeInteraction,
        key: Key,
        shift: Boolean = false,
        whileWorking: () -> Unit = {},
    ) {
        target.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        val previous = CollectionManager.setTestDispatcher(StandardTestDispatcher(testScheduler), useReentrantLock = false)
        try {
            target.assertIsFocused().performKeyInput {
                keyDown(Key.CtrlLeft)
                if (shift) keyDown(Key.ShiftLeft)
                pressKey(key)
                if (shift) keyUp(Key.ShiftLeft)
                keyUp(Key.CtrlLeft)
            }
            // Render the disabled actions while undo or redo waits for the collection.
            compose.waitForIdle()
            whileWorking()
            testScheduler.advanceUntilIdle()
        } finally {
            CollectionManager.setTestDispatcher(previous)
        }
        compose.waitForIdle()
    }

    private fun TestScope.prepareRenameShortcut(
        target: SemanticsNodeInteraction,
        whileChecking: () -> Unit = {},
    ) {
        target.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        val previous = CollectionManager.setTestDispatcher(StandardTestDispatcher(testScheduler), useReentrantLock = false)
        try {
            target.assertIsFocused().performKeyInput { pressKey(Key.R) }
            // Render the disabled actions while the usage check waits for the collection.
            compose.waitForIdle()
            whileChecking()
            testScheduler.advanceUntilIdle()
        } finally {
            CollectionManager.setTestDispatcher(previous)
        }
        compose.waitForIdle()
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
    @Ignore("Temporarily disabled while this screen test times out")
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
