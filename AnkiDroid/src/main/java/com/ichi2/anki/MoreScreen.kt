// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
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
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.rotary.RotaryInputModifierNode
import androidx.compose.ui.input.rotary.RotaryScrollEvent
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.TraversableNode.Companion.TraverseDescendantsAction
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.node.traverseDescendants
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.ichi2.anki.common.android.Animations
import com.ichi2.anki.dialogs.help.HelpItem
import com.ichi2.compose.ui.components.GroupedList
import com.ichi2.compose.ui.components.GroupedListScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor
import android.view.KeyEvent as AndroidKeyEvent

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MoreScreen(
    helpItems: List<HelpItem>,
    supportItems: List<HelpItem>,
    onSettingsClick: () -> Unit,
    onHelpItemClick: (HelpItem) -> Unit,
    onSupportItemClick: (HelpItem) -> Unit,
    onKeyboardScroll: () -> Unit,
    scrollState: ScrollState,
) {
    CompositionLocalProvider(
        LocalContentColor provides MaterialTheme.colorScheme.onBackground,
        LocalBringIntoViewSpec provides rememberBringIntoViewSpec(),
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
                    .nestedScroll(rememberTouchNestedScrollConnection(onKeyboardScroll))
                    .keyboardPaging(scrollState, onKeyboardScroll)
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
private fun rememberTouchNestedScrollConnection(onKeyboardScroll: () -> Unit): NestedScrollConnection {
    val interop = rememberNestedScrollInteropConnection()
    val inputModeManager = LocalInputModeManager.current
    return remember(interop, inputModeManager, onKeyboardScroll) {
        TouchNestedScrollConnection(interop, inputModeManager, onKeyboardScroll)
    }
}

private class TouchNestedScrollConnection(
    private val interop: NestedScrollConnection,
    private val inputModeManager: InputModeManager,
    private val onKeyboardScroll: () -> Unit,
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
    ): Offset {
        if (isTouch) return interop.onPostScroll(consumed, available, source)
        if (consumed.y != 0f) onKeyboardScroll()
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity = interop.onPreFling(available)

    override suspend fun onPostFling(
        consumed: Velocity,
        available: Velocity,
    ): Velocity = interop.onPostFling(consumed, available)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun rememberBringIntoViewSpec(): BringIntoViewSpec {
    val context = LocalContext.current
    val default = LocalBringIntoViewSpec.current
    return remember(context, default) { AppAnimationsBringIntoViewSpec(context, default) }
}

private class AppAnimationsBringIntoViewSpec(
    private val context: Context,
    private val default: BringIntoViewSpec,
) : BringIntoViewSpec {
    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override val scrollAnimationSpec: AnimationSpec<Float>
        get() = if (Animations.areAnimationsEnabled(context)) default.scrollAnimationSpec else snap()

    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ) = default.calculateScrollDistance(offset, size, containerSize)
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

private class PagingDestination(
    val row: KeyboardPagingTargetNode,
    val top: Float,
    val bottom: Float,
)

private class KeyboardPagingNode(
    var scrollState: ScrollState,
    var onPaging: () -> Unit,
) : DelegatingNode(),
    KeyInputModifierNode,
    RotaryInputModifierNode,
    LayoutAwareModifierNode,
    CompositionLocalConsumerModifierNode {
    private val pageFocus: FocusTargetModifierNode =
        delegate(
            FocusTargetModifierNode(Focusability.Never) { _, current ->
                if (!current.isFocused) pageFocus.focusability = Focusability.Never
            },
        )
    private var pagingJob: Job? = null
    private var pagingTarget = 0
    private var page: Page? = null
    private var destination: PagingDestination? = null
    private var viewportHeight = 0

    override fun onPreKeyEvent(event: KeyEvent): Boolean {
        val page =
            when {
                event.key == Key.PageUp -> Page.UP
                event.key == Key.PageDown -> Page.DOWN
                event.key == Key.Spacebar && event.isShiftPressed -> Page.UP
                event.key == Key.Spacebar && !event.nativeKeyEvent.hasNoModifiers() -> Page.DOWN
                event.key == Key.MoveHome -> Page.TOP
                event.key == Key.MoveEnd -> Page.BOTTOM
                event.key == Key.DirectionUp && event.isAltPressed -> Page.TOP
                event.key == Key.DirectionDown && event.isAltPressed -> Page.BOTTOM
                else -> null
            }
        if (page != null) {
            if (event.type == KeyEventType.KeyDown) scroll(page)
            return true
        }
        if (event.type == KeyEventType.KeyDown && !AndroidKeyEvent.isModifierKey(event.nativeKeyEvent.keyCode)) finishPaging()
        return event.isConfirmKey && !event.nativeKeyEvent.hasNoModifiers()
    }

    override fun onKeyEvent(event: KeyEvent) = false

    override fun onRotaryScrollEvent(event: RotaryScrollEvent): Boolean {
        finishPaging()
        val consumed = scrollState.dispatchRawDelta(event.verticalScrollPixels)
        if (consumed != 0f) onPaging()
        return consumed != 0f
    }

    override fun onPreRotaryScrollEvent(event: RotaryScrollEvent) = false

    override fun onRemeasured(size: IntSize) {
        val shrunk = size.height < viewportHeight
        viewportHeight = size.height
        val page = page ?: return
        if (!shrunk || (pagingJob?.isActive != true && scrollState.value != pagingTarget)) return
        val target = if (page == Page.BOTTOM) scrollState.maxValue else pagingTarget
        scrollTo(destination.shownFrom(target, size.height))
    }

    private fun scroll(page: Page) {
        val height = requireLayoutCoordinates().size.height
        val from = if (pagingJob?.isActive == true) pagingTarget else scrollState.value
        val target =
            when (page) {
                Page.UP -> from - height
                Page.DOWN -> from + height
                Page.TOP -> 0
                Page.BOTTOM -> scrollState.maxValue
            }.coerceIn(0, scrollState.maxValue)
        onPaging()
        val destination = rowOnPage(target, height, up = page == Page.UP || page == Page.TOP)
        if (destination != null) {
            destination.row.requestFocus()
        } else {
            pageFocus.focusability = Focusability.Always
            pageFocus.requestFocus()
        }
        this.page = page
        this.destination = destination
        scrollTo(destination.shownFrom(target, height))
    }

    private fun scrollTo(target: Int) {
        val to = target.coerceIn(0, scrollState.maxValue)
        pagingTarget = to
        pagingJob?.cancel()
        val spec = if (Animations.areAnimationsEnabled(currentValueOf(LocalContext))) spring<Float>() else snap()
        pagingJob =
            coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                scrollState.scroll(MutatePriority.UserInput) {
                    var scrolled = scrollState.value.toFloat()
                    animate(scrolled, to.toFloat(), animationSpec = spec) { value, _ ->
                        scrolled += scrollBy(value - scrolled)
                    }
                }
            }
    }

    private fun finishPaging() {
        if (pagingJob?.isActive == true) {
            pagingJob?.cancel()
            scrollState.dispatchRawDelta((pagingTarget - scrollState.value).toFloat())
        }
        page = null
        destination = null
    }

    private fun PagingDestination?.shownFrom(
        target: Int,
        height: Int,
    ): Int =
        when {
            this == null || bottom - top > height -> target
            top < target -> floor(top).toInt()
            bottom > target + height -> ceil(bottom - height).toInt()
            else -> target
        }

    private fun rowOnPage(
        target: Int,
        height: Int,
        up: Boolean,
    ): PagingDestination? {
        val viewport = requireLayoutCoordinates()
        val shift = (target - scrollState.value).toFloat()
        var next: KeyboardPagingTargetNode? = null
        var nextBounds = Rect.Zero
        var nextIsFullyShown = false
        traverseDescendants(KeyboardPagingTargetNode.TraverseKey) { row ->
            val bounds = viewport.localBoundingBoxOf(row.requireLayoutCoordinates(), clipBounds = false).translate(0f, -shift)
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
        return next?.let { PagingDestination(it, nextBounds.top + target, nextBounds.bottom + target) }
    }
}

private val KeyEvent.isConfirmKey: Boolean
    get() = key == Key.Enter || key == Key.NumPadEnter || key == Key.DirectionCenter || key == Key.Spacebar

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
