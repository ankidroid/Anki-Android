// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequesterModifierNode
import androidx.compose.ui.focus.requestFocus
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyInputModifierNode
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.TraversableNode.Companion.TraverseDescendantsAction
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.node.traverseDescendants
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.ichi2.anki.dialogs.help.HelpItem
import com.ichi2.compose.ui.components.GroupedList
import com.ichi2.compose.ui.components.GroupedListScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    helpItems: List<HelpItem>,
    supportItems: List<HelpItem>,
    onSettingsClick: () -> Unit,
    onHelpItemClick: (HelpItem) -> Unit,
    onSupportItemClick: (HelpItem) -> Unit,
    scrollState: ScrollState,
) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopAppBar(
            title = { Text(stringResource(CommonString.bottom_nav_more)) },
            windowInsets = WindowInsets(0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        Column(
            Modifier
                .fillMaxSize()
                .nestedScroll(rememberTouchNestedScrollConnection())
                .keyboardPaging(scrollState)
                .verticalScroll(scrollState)
                .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
        ) {
            GroupedList {
                moreItem(R.drawable.ic_settings_black, CommonString.settings, onSettingsClick)
            }
            MoreSectionHeader(stringResource(CommonString.help))
            GroupedList {
                for (item in helpItems) {
                    moreItem(item.iconResId, item.titleResId) { onHelpItemClick(item) }
                }
            }
            MoreSectionHeader(stringResource(CommonString.help_title_support_ankidroid))
            GroupedList {
                for (item in supportItems) {
                    moreItem(item.iconResId, item.titleResId) { onSupportItemClick(item) }
                }
            }
        }
    }
}

@Composable
private fun MoreSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

private fun GroupedListScope.moreItem(
    @DrawableRes icon: Int,
    @StringRes title: Int,
    onClick: () -> Unit,
) {
    item(
        modifier = Modifier.keyboardPagingTarget().clickable(role = Role.Button, onClick = onClick),
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
    ) {
        Text(stringResource(title))
    }
}

@Composable
private fun rememberTouchNestedScrollConnection(): NestedScrollConnection {
    val interop = rememberNestedScrollInteropConnection()
    val inputModeManager = LocalInputModeManager.current
    return remember(interop, inputModeManager) { TouchNestedScrollConnection(interop, inputModeManager) }
}

private class TouchNestedScrollConnection(
    private val interop: NestedScrollConnection,
    private val inputModeManager: InputModeManager,
) : NestedScrollConnection {
    private val isTouch: Boolean
        get() = inputModeManager.inputMode == InputMode.Touch

    override fun onPreScroll(
        available: Offset,
        source: NestedScrollSource,
    ): Offset = if (isTouch) interop.onPreScroll(available, source) else Offset.Zero

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset = if (isTouch) interop.onPostScroll(consumed, available, source) else Offset.Zero

    override suspend fun onPreFling(available: Velocity): Velocity = interop.onPreFling(available)

    override suspend fun onPostFling(
        consumed: Velocity,
        available: Velocity,
    ): Velocity = interop.onPostFling(consumed, available)
}

private fun Modifier.keyboardPaging(scrollState: ScrollState) = this then KeyboardPagingElement(scrollState)

private data class KeyboardPagingElement(
    val scrollState: ScrollState,
) : ModifierNodeElement<KeyboardPagingNode>() {
    override fun create() = KeyboardPagingNode(scrollState)

    override fun update(node: KeyboardPagingNode) {
        node.scrollState = scrollState
    }
}

private class KeyboardPagingNode(
    var scrollState: ScrollState,
) : Modifier.Node(),
    KeyInputModifierNode {
    override fun onPreKeyEvent(event: KeyEvent): Boolean {
        val up =
            when {
                event.isCtrlPressed -> return false
                event.key == Key.PageUp -> true
                event.key == Key.PageDown -> false
                event.key == Key.Spacebar && event.isShiftPressed -> true
                else -> return false
            }
        if (event.type == KeyEventType.KeyDown) page(up)
        return true
    }

    override fun onKeyEvent(event: KeyEvent) = false

    private fun page(up: Boolean) {
        val viewport = requireLayoutCoordinates()
        val height = viewport.size.height
        val target = (scrollState.value + if (up) -height else height).coerceIn(0, scrollState.maxValue)
        val shift = (target - scrollState.value).toFloat()
        var next: KeyboardPagingTargetNode? = null
        var nextTop = 0f
        traverseDescendants(KeyboardPagingTargetNode.TraverseKey) { row ->
            val bounds = viewport.localBoundingBoxOf(row.requireLayoutCoordinates(), clipBounds = false).translate(0f, -shift)
            val isOnNewPage = bounds.top >= 0 && bounds.bottom <= height
            if (isOnNewPage && (next == null || (if (up) bounds.top < nextTop else bounds.top > nextTop))) {
                next = row as KeyboardPagingTargetNode
                nextTop = bounds.top
            }
            TraverseDescendantsAction.ContinueTraversal
        }
        val focusTarget = next
        coroutineScope.launch {
            scrollState.animateScrollTo(target)
            withFrameNanos { }
            focusTarget?.requestFocus()
        }
    }
}

private fun Modifier.keyboardPagingTarget() = this then KeyboardPagingTargetElement

private data object KeyboardPagingTargetElement : ModifierNodeElement<KeyboardPagingTargetNode>() {
    override fun create() = KeyboardPagingTargetNode()

    override fun update(node: KeyboardPagingTargetNode) = Unit
}

private class KeyboardPagingTargetNode :
    Modifier.Node(),
    FocusRequesterModifierNode,
    TraversableNode {
    override val traverseKey = TraverseKey

    companion object TraverseKey
}
