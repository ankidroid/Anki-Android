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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequesterModifierNode
import androidx.compose.ui.focus.FocusTargetModifierNode
import androidx.compose.ui.focus.Focusability
import androidx.compose.ui.focus.requestFocus
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyInputModifierNode
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.node.DelegatingNode
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    helpItems: List<HelpItem>,
    supportItems: List<HelpItem>,
    onSettingsClick: () -> Unit,
    onHelpItemClick: (HelpItem) -> Unit,
    onSupportItemClick: (HelpItem) -> Unit,
    onKeyboardPaging: () -> Unit,
    scrollState: ScrollState,
) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
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
                    .keyboardPaging(scrollState, onKeyboardPaging)
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

private fun Modifier.keyboardPaging(
    scrollState: ScrollState,
    onPaging: () -> Unit,
) = this then KeyboardPagingElement(scrollState, onPaging)

private data class KeyboardPagingElement(
    val scrollState: ScrollState,
    val onPaging: () -> Unit,
) : ModifierNodeElement<KeyboardPagingNode>() {
    override fun create() = KeyboardPagingNode(scrollState, onPaging)

    override fun update(node: KeyboardPagingNode) {
        node.scrollState = scrollState
        node.onPaging = onPaging
    }
}

private enum class Page { UP, DOWN, TOP, BOTTOM }

private class KeyboardPagingNode(
    var scrollState: ScrollState,
    var onPaging: () -> Unit,
) : DelegatingNode(),
    KeyInputModifierNode {
    private val pagingFocus = delegate(FocusTargetModifierNode(focusability = Focusability.Never))
    private var pagingJob: Job? = null
    private var pendingTarget: Int? = null

    override fun onPreKeyEvent(event: KeyEvent): Boolean {
        val page =
            when {
                event.isCtrlPressed -> return false
                event.key == Key.PageUp -> Page.UP
                event.key == Key.PageDown -> Page.DOWN
                event.key == Key.Spacebar && event.isShiftPressed -> Page.UP
                event.key == Key.MoveHome -> Page.TOP
                event.key == Key.MoveEnd -> Page.BOTTOM
                event.key == Key.DirectionUp && event.isAltPressed -> Page.TOP
                event.key == Key.DirectionDown && event.isAltPressed -> Page.BOTTOM
                else -> return false
            }
        if (event.type == KeyEventType.KeyDown) scroll(page)
        return true
    }

    override fun onKeyEvent(event: KeyEvent) = false

    private fun scroll(page: Page) {
        val height = requireLayoutCoordinates().size.height
        val from = pendingTarget ?: scrollState.value
        val target =
            when (page) {
                Page.UP -> from - height
                Page.DOWN -> from + height
                Page.TOP -> 0
                Page.BOTTOM -> scrollState.maxValue
            }.coerceIn(0, scrollState.maxValue)
        val focusTarget = rowOnPage(target - scrollState.value, height, up = page == Page.UP || page == Page.TOP)
        onPaging()
        pagingFocus.focusability = Focusability.Always
        pagingFocus.requestFocus()
        pendingTarget = target
        pagingJob?.cancel()
        pagingJob =
            coroutineScope.launch {
                val job = coroutineContext.job
                try {
                    scrollState.animateScrollTo(target)
                    withFrameNanos { }
                } finally {
                    if (pagingJob == job) {
                        pagingJob = null
                        pendingTarget = null
                        if (isAttached) {
                            if (pagingFocus.focusState.isFocused) focusTarget?.requestFocus()
                            pagingFocus.focusability = Focusability.Never
                        }
                    }
                }
            }
    }

    private fun rowOnPage(
        shift: Int,
        height: Int,
        up: Boolean,
    ): KeyboardPagingTargetNode? {
        val viewport = requireLayoutCoordinates()
        var next: KeyboardPagingTargetNode? = null
        var nextBounds = Rect.Zero
        var nextIsFullyShown = false
        traverseDescendants(KeyboardPagingTargetNode.TraverseKey) { row ->
            val bounds = viewport.localBoundingBoxOf(row.requireLayoutCoordinates(), clipBounds = false).translate(0f, -shift.toFloat())
            if (bounds.bottom > 0 && bounds.top < height) {
                val isFullyShown = bounds.top >= 0 && bounds.bottom <= height
                val isCloser = if (up) bounds.top < nextBounds.top else bounds.bottom > nextBounds.bottom
                if (next == null || (isFullyShown && !nextIsFullyShown) || (isFullyShown == nextIsFullyShown && isCloser)) {
                    next = row as KeyboardPagingTargetNode
                    nextBounds = bounds
                    nextIsFullyShown = isFullyShown
                }
            }
            TraverseDescendantsAction.ContinueTraversal
        }
        return next
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
