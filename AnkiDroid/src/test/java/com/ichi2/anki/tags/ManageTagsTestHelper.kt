// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CommonString
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.ui.internationalization.sentenceCase
import org.robolectric.android.controller.ActivityController
import kotlin.test.assertIs
import androidx.appcompat.R as AppCompatR

/** Exercises the hosted Compose UI through semantics, including dialog restoration. */
internal class ManageTagsTestHelper(
    private val test: RobolectricTest,
    private val compose: ComposeTestRule,
) {
    lateinit var controller: ActivityController<SingleFragmentActivity>
        private set
    val fragment get() = assertIs<ManageTagsFragment>(controller.get().fragment)
    val viewModel get() = ViewModelProvider(fragment)[ManageTagsViewModel::class.java]

    fun open() {
        controller =
            test.startActivityControllerNormallyOpenCollectionWithIntent(
                SingleFragmentActivity::class.java,
                ManageTagsFragment.getIntent(test.targetContext),
            )
    }

    fun search(query: String) {
        compose.onNode(hasSetTextAction() and !hasAnyAncestor(isDialog())).performTextReplacement(query)
    }

    fun showActions(tag: String) {
        compose.onNodeWithTag("tag_list").performScrollToNode(hasTestTag("tag:$tag"))
        compose
            .onNode(
                hasContentDescription(test.targetContext.getString(CommonString.manage_tags_more_actions, tag)) and
                    hasAnyAncestor(hasTestTag("tag:$tag")),
            ).performClick()
    }

    fun action(
        action: TagAction,
        tag: String,
    ) {
        showActions(tag)
        compose
            .onNodeWithText(
                test.targetContext.getString(
                    when (action) {
                        TagAction.RENAME -> CommonString.rename
                        TagAction.DELETE -> CommonString.dialog_positive_delete
                        TagAction.CLEAR_UNUSED -> error("Use clearUnused()")
                    },
                ),
            ).performScrollTo()
            .performClick()
    }

    fun clearUnused() {
        compose
            .onNodeWithContentDescription(
                test.targetContext.getString(AppCompatR.string.abc_action_menu_overflow_description),
            ).performClick()
        compose.onNodeWithText(with(test.targetContext) { TR.sentenceCase.clearUnusedTags }).performClick()
    }

    fun enterText(text: String) = compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextReplacement(text)

    fun confirm(action: TagAction): SemanticsNodeInteraction =
        compose.onNodeWithText(
            test.targetContext.getString(
                when (action) {
                    TagAction.RENAME -> CommonString.rename
                    TagAction.DELETE -> CommonString.dialog_positive_delete
                    TagAction.CLEAR_UNUSED -> CommonString.manage_tags_clear_unused_action
                },
            ),
        )

    fun cancel() {
        val label = hasText(test.targetContext.getString(CommonString.dialog_cancel))
        val dialog = label and hasAnyAncestor(isDialog())
        compose.onNode(if (compose.onAllNodes(dialog).fetchSemanticsNodes().isNotEmpty()) dialog else label).performClick()
    }

    fun close() {
        if (::controller.isInitialized) controller.pause().stop().destroy()
    }

    fun recreate() {
        controller.recreate()
        RobolectricTest.advanceRobolectricLooper()
    }
}
