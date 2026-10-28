// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CommonString
import com.ichi2.anki.RobolectricTest
import com.ichi2.compose.theme.AnkiDroidTheme
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class TagRowTest : RobolectricTest(loadBackend = false) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `leaves ripple only for long presses while parents retain tap feedback`() {
        val item = mutableStateOf(tagListItem("tag", "tag", 0, hasChildren = false, collapsed = false))
        val enabled = mutableStateOf(true)
        val interactions = mutableListOf<Interaction>()
        val indication = RecordingIndication(interactions)
        var selected = 0
        var toggled = 0
        compose.setContent {
            AnkiDroidTheme {
                CompositionLocalProvider(LocalIndication provides indication) {
                    TagRow(item.value, enabled.value, onToggleCollapsed = { toggled++ }, onOverflowClick = { selected++ })
                }
            }
        }
        val row = compose.onNodeWithTag("tag:tag")
        row.performTouchInput { click() }
        compose.waitForIdle()
        assertTrue(interactions.isEmpty())
        assertEquals(0, selected)
        assertEquals(0, toggled)

        row.performTouchInput { longClick() }
        compose.waitForIdle()
        assertEquals(1, selected)
        assertEquals(0, toggled)
        val press = assertIs<PressInteraction.Press>(interactions.first())
        assertSame(press, assertIs<PressInteraction.Release>(interactions.last()).press)

        compose.runOnIdle {
            interactions.clear()
            enabled.value = false
        }
        row.performTouchInput { click() }
        row.performTouchInput { longClick() }
        compose.waitForIdle()
        assertTrue(interactions.isEmpty())
        assertEquals(1, selected)

        compose.runOnIdle {
            enabled.value = true
            item.value = item.value.copy(hasChildren = true)
        }
        row.performTouchInput { click() }
        compose.waitForIdle()
        assertEquals(1, toggled)
        assertTrue(interactions.any { it is PressInteraction.Press })
    }

    @Test
    @Config(sdk = [24, Config.TARGET_SDK])
    fun `expansion semantics follow state and leaves have no expansion action`() {
        val state = mutableStateOf(tagListItem("parent", "parent", 0, hasChildren = true, collapsed = true))
        var toggled = 0
        compose.setContent {
            AnkiDroidTheme { TagRow(state.value, actionsEnabled = true, onToggleCollapsed = { toggled++ }, onOverflowClick = {}) }
        }

        fun assertExpansion(
            description: String,
            action: String,
        ) {
            val config = compose.onNodeWithTag("tag:parent").fetchSemanticsNode().config
            assertEquals(description, config[SemanticsProperties.StateDescription])
            assertEquals(action, config[SemanticsActions.OnClick].label)
        }
        assertExpansion("Collapsed", "Expand")
        compose.onNodeWithTag("tag:parent").performClick()
        assertEquals(1, toggled)
        compose.runOnIdle { state.value = state.value.copy(collapsed = false) }
        assertExpansion("Expanded", "Collapse")
        compose.onNodeWithTag("tag:parent").performClick()
        assertEquals(2, toggled)
        compose.runOnIdle { state.value = tagListItem("parent::leaf", "leaf", 1, hasChildren = false, collapsed = false) }
        val leaf = compose.onNodeWithTag("tag:parent::leaf").fetchSemanticsNode().config
        assertFalse(leaf.contains(SemanticsProperties.StateDescription))
        assertFalse(leaf.contains(SemanticsActions.OnClick))
    }

    @Test
    fun `parent spelling changes update the action target with an unchanged leaf label`() {
        val state = mutableStateOf(tagListItem("parent::child", "child", 1, hasChildren = false, collapsed = false))
        var selected = ""
        compose.setContent {
            val item = state.value
            AnkiDroidTheme {
                TagRow(
                    item,
                    actionsEnabled = true,
                    onToggleCollapsed = {},
                    onOverflowClick = { selected = item.fullTag.value },
                )
            }
        }
        compose.runOnIdle { state.value = state.value.copy(fullTagName = "PARENT::child") }
        compose.onNodeWithContentDescription(targetContext.getString(CommonString.manage_tags_more_actions, "PARENT::child")).performClick()
        assertEquals("PARENT::child", selected)
        compose.runOnIdle { state.value = state.value.copy(fullTagName = "another::child") }
        compose.onNodeWithTag("tag:another::child").performTouchInput { longClick() }
        assertEquals("another::child", selected)
    }

    @Test
    fun `busy rows disable both expansion and overflow`() {
        compose.setContent {
            AnkiDroidTheme {
                TagRow(tagListItem("parent", "parent", 0, true, true), actionsEnabled = false, onToggleCollapsed = {}, onOverflowClick = {})
            }
        }
        compose.onNodeWithTag("tag:parent").assertIsNotEnabled()
        compose.onNodeWithContentDescription(targetContext.getString(CommonString.manage_tags_more_actions, "parent")).assertIsNotEnabled()
    }

    @Test
    fun `long pressing parents and leaves opens actions without toggling expansion`() {
        val item = mutableStateOf(tagListItem("parent", "parent", 0, hasChildren = true, collapsed = true))
        val enabled = mutableStateOf(true)
        var selected = 0
        var toggled = 0
        compose.setContent {
            AnkiDroidTheme {
                TagRow(item.value, enabled.value, onToggleCollapsed = { toggled++ }, onOverflowClick = { selected++ })
            }
        }
        for (hasChildren in listOf(true, false)) {
            compose.runOnIdle {
                item.value = item.value.copy(hasChildren = hasChildren)
                enabled.value = true
                selected = 0
            }
            compose.onNodeWithTag("tag:parent").performTouchInput { longClick() }
            assertEquals(1, selected)
            assertEquals(0, toggled)
            compose.runOnIdle { enabled.value = false }
            compose.onNodeWithTag("tag:parent").performTouchInput { longClick() }
            assertEquals(1, selected)
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    fun `deep tags keep their label and full-size overflow button on compact screens`() {
        val fullTag = (List(15) { "Level$it" } + "Leaf").joinToString("::")
        var selected = ""
        compose.setContent {
            AnkiDroidTheme {
                TagRow(
                    tagListItem(fullTag, "Leaf", 15, hasChildren = false, collapsed = false),
                    actionsEnabled = true,
                    onToggleCollapsed = {},
                    onOverflowClick = { selected = fullTag },
                )
            }
        }
        compose.onNodeWithText("Leaf").assertIsDisplayed()
        compose
            .onNodeWithContentDescription(targetContext.getString(CommonString.manage_tags_more_actions, fullTag))
            .assertIsDisplayed()
            .assertTouchWidthIsEqualTo(48.dp)
            .assertTouchHeightIsEqualTo(48.dp)
            .performTouchInput { click() }
        assertEquals(fullTag, selected)
    }
}

/** Observes the same interactions consumed by the ripple, without changing pointer handling. */
private class RecordingIndication(
    private val interactions: MutableList<Interaction>,
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): Modifier.Node =
        object : Modifier.Node(), DrawModifierNode {
            override fun onAttach() {
                coroutineScope.launch { interactionSource.interactions.collect { interactions.add(it) } }
            }

            override fun ContentDrawScope.draw() = drawContent()
        }

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = System.identityHashCode(this)
}
