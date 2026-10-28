// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ichi2.anki.CommonString
import com.ichi2.anki.common.android.Animations
import com.ichi2.compose.ui.components.LinearProgressIndicator
import kotlinx.coroutines.flow.first

/** Displays search and the animated tag hierarchy, including loading, empty, and busy states. */
@Composable
internal fun ManageTagsContent(
    state: ManageTagsState,
    searchQuery: String,
    onSearch: (String) -> Unit,
    onToggleCollapsed: (TagName) -> Unit,
    onSelectTag: (TagName) -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = state as? ManageTagsState.Content
    val actionsEnabled = content != null && !content.isWorking
    val listState = rememberLazyListState()
    var expandedTag by remember { mutableStateOf<TagName?>(null) }
    var previousQuery by remember { mutableStateOf(searchQuery) }
    LaunchedEffect(searchQuery) {
        if (previousQuery != searchQuery) {
            expandedTag = null
            listState.scrollToItem(0)
            previousQuery = searchQuery
        }
    }
    val animateRows = !LocalInspectionMode.current && Animations.areAnimationsEnabled(LocalContext.current)
    LaunchedEffect(expandedTag, content?.visibleNodes) {
        val tag = expandedTag ?: return@LaunchedEffect
        val nodes = content?.visibleNodes ?: return@LaunchedEffect
        val index = nodes.indexOfFirst { it.fullTag == tag }
        if (index < 0 || nodes[index].collapsed) return@LaunchedEffect
        val children = nodes.drop(index + 1).takeWhile { it.level > nodes[index].level }.size
        val layout = snapshotFlow { listState.layoutInfo }.first { it.totalItemsCount == nodes.size }
        val parent = layout.visibleItemsInfo.firstOrNull { it.key == tag.value }
        if (parent != null && children > 0) {
            // Rows have the same single-line height. Reveal the branch when it fits;
            // otherwise keep its parent at the top so its first children are reachable.
            val height = minOf((children + 1L) * parent.size, (layout.viewportEndOffset - layout.viewportStartOffset).toLong())
            val distance = (parent.offset + height - layout.viewportEndOffset).coerceAtLeast(0).toFloat()
            if (distance > 0) {
                if (animateRows) listState.animateScrollBy(distance) else listState.scrollBy(distance)
            }
        }
        expandedTag = null
    }
    Column(modifier) {
        TagSearchBar(
            query = searchQuery,
            onQueryChange = onSearch,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        when {
            state is ManageTagsState.Loading ->
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            content != null ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    // Retain the list when empty so the final removed rows can finish fading out.
                    LazyColumn(Modifier.fillMaxSize().testTag("tag_list"), state = listState) {
                        items(content.visibleNodes, key = { it.fullTag.value }) { item ->
                            TagRow(
                                item = item,
                                actionsEnabled = actionsEnabled,
                                onToggleCollapsed = {
                                    expandedTag = item.fullTag.takeIf { item.collapsed }
                                    onToggleCollapsed(item.fullTag)
                                },
                                onOverflowClick = { onSelectTag(item.fullTag) },
                                modifier = if (animateRows) Modifier.animateItem() else Modifier,
                            )
                        }
                    }
                    if (content.visibleNodes.isEmpty()) {
                        Text(stringResource(if (searchQuery.isBlank()) CommonString.no_tags else CommonString.manage_tags_no_matches))
                    }
                    LinearProgressIndicator(content.isWorking, Modifier.fillMaxWidth().align(Alignment.TopCenter))
                }
        }
    }
}
