// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.libanki.Tags
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.tags.ManageTagsState.Error
import com.ichi2.anki.tags.UserMessage.ClearedUnusedTags
import com.ichi2.anki.tags.UserMessage.TagRemoved
import com.ichi2.anki.tags.UserMessage.TagRenamed
import com.ichi2.anki.utils.MessageQueue
import com.ichi2.anki.utils.MessageQueue.MessageId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import anki.tags.TagTreeNode as BackendTagTreeNode

/**
 * ViewModel for the Manage Tags screen.
 *
 * Handles display of hierarchical tags with expand/collapse and a search filter.
 * Tag operations (rename, delete) are applied to individual tags.
 *
 * This must handle a huge amount of tags, AnKing v11 contains ~17k tags.
 *
 * @see Tags for backend functions.
 * @see TagListItemState for display.
 * @see ManageTagsState for UI state.
 * @see pendingMessages for messages awaiting display.
 */
class ManageTagsViewModel : ViewModel() {
    val state: StateFlow<ManageTagsState>
        field = MutableStateFlow<ManageTagsState>(ManageTagsState.Loading)

    /** Search field contents, available even while tags are loading or failed to load. */
    val searchQuery: StateFlow<String>
        field = MutableStateFlow("")

    private val messageQueue = MessageQueue<UserMessage>()

    /** Messages in display order. Collecting does not consume them; acknowledge with [messageShown]. */
    val pendingMessages = messageQueue.messages

    /** Cached flat list of all tags, rebuilt when the backend tree changes */
    private var tagList: List<TagListItemState> = emptyList()

    private var tagOperation: Job? = null

    init {
        refreshTags()
    }

    /** Reloads the tag tree from the backend. Preserves the current search query if any */
    fun refreshTags() =
        launchTagOpAndRefresh {
            Timber.i("Refreshing tags")
        }

    /**
     * Filters visible tags by [query]. Ancestors of matches are shown to preserve hierarchy.
     *
     * WARN: Mutliselect was not implemented on this screen due to the confusing combination
     * of having a filtered tag selected.
     */
    fun filter(query: String) {
        searchQuery.value = query
        updateState { loaded ->
            loaded.copy(
                visibleNodes = computeVisibleNodes(query),
            )
        }
    }

    /** Acknowledges that the UI has finished displaying a message. */
    fun messageShown(id: MessageId) = messageQueue.acknowledge(id)

    /** Returns the visible node for [tag], or null with a warning if not found or not loaded */
    private fun findVisibleNode(tag: TagName): TagListItemState? {
        val loaded = state.value as? ManageTagsState.Content
        if (loaded == null) {
            Timber.w("findVisibleNode called while not loaded")
            return null
        }
        return loaded.visibleNodes.firstOrNull { it.fullTag == tag }.also {
            if (it == null) {
                Timber.w("Tag not found in visible nodes")
                Timber.d("Tag: %s", tag)
            }
        }
    }

    /**
     * Expands or collapses [tag] in the tree. Persists the state to the backend.
     *
     * PERF: ~55ms to process 10k+ tags on my M1; ~350ms to process 10k+ tags in CI
     *
     * @see Tags.setCollapsed
     */
    fun toggleCollapsed(tag: TagName) =
        launchTagOperation {
            val node =
                findVisibleNode(tag) ?: run {
                    messageQueue.enqueue(UserMessage.UnexpectedError)
                    return@launchTagOperation
                }
            val newCollapsed = !node.collapsed
            withCol { tags.setCollapsed(tag, newCollapsed) }
            // Update the in-memory list with the new collapsed state
            tagList =
                tagList.map {
                    if (it.fullTag == tag) it.copy(collapsed = newCollapsed) else it
                }
            updateState { it.copy(visibleNodes = computeVisibleNodes(searchQuery.value)) }
        }

    /**
     * Deletes [tag] and its children from all notes.
     * @see Tags.remove
     */
    fun removeTag(tag: TagName) =
        launchTagOpAndRefresh {
            val result = undoableOp { tags.remove(tag) }
            messageQueue.enqueue(TagRemoved(result.count))
        }

    /**
     * Renames [oldName] to [newName], updating all notes and child tags.
     * @see Tags.rename
     */
    fun renameTag(
        oldName: TagName,
        newName: TagName,
    ) = launchTagOpAndRefresh {
        val result = undoableOp { tags.rename(oldName, newName) }
        messageQueue.enqueue(TagRenamed(result.count))
    }

    /**
     * Removes tags not present on any note. Queues a [ClearedUnusedTags] message with the count.
     * @see Tags.clearUnusedTags
     */
    fun clearUnusedTags() =
        launchTagOpAndRefresh {
            val result = undoableOp { tags.clearUnusedTags() }
            Timber.i("Deleted %d unused tags", result.count)
            messageQueue.enqueue(ClearedUnusedTags(result.count))
        }

    private suspend fun loadTags() {
        Timber.i("Loading tags from collection")
        tagList = flattenTree(withCol { tags.tree() })
        state.value =
            ManageTagsState.Content(
                visibleNodes = computeVisibleNodes(searchQuery.value),
            )
    }

    /**
     * Runs [block], then [reloads][loadTags], keeping any existing content visible.
     */
    private fun launchTagOpAndRefresh(block: suspend () -> Unit = { }): Job =
        launchTagOperation {
            block()
            loadTags()
        }

    /** Starts an operation, or returns the unfinished operation's job without starting another. */
    private fun launchTagOperation(block: suspend () -> Unit): Job {
        val currentOperation = tagOperation
        if (currentOperation != null && !currentOperation.isCompleted) {
            return currentOperation
        }

        // set 'tagOperation' before executing
        val operation = viewModelScope.launch(start = CoroutineStart.LAZY) { runTagOperation(block) }
        tagOperation = operation
        // calls from observers will now find this job
        operation.start()
        return operation
    }

    /**
     * Updates progress and errors while keeping existing content visible.
     * Uses [ManageTagsState.Loading] and [Error] only when no content is available.
     */
    private suspend fun runTagOperation(block: suspend () -> Unit) {
        state.update { current ->
            when (current) {
                is ManageTagsState.Content -> current.copy(isWorking = true, error = null)
                is ManageTagsState.Loading, is Error -> ManageTagsState.Loading
            }
        }
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Tag operation failed")
            state.update { current ->
                when (current) {
                    is ManageTagsState.Content -> current.copy(isWorking = false, error = e)
                    is ManageTagsState.Loading, is Error -> Error(e)
                }
            }
        } finally {
            updateState { it.copy(isWorking = false) }
        }
    }

    private fun computeVisibleNodes(searchQuery: String): List<TagListItemState> =
        if (searchQuery.isBlank()) {
            // filter out the collapsed nodes from the results
            applyCollapsedVisibility(tagList)
        } else {
            // collapsed nodes can be re-expanded if searchQuery matches the leaf nodes
            applySearchFilter(tagList, searchQuery)
        }

    /** Applies [transform] only if the current state is [ManageTagsState.Content]; no-ops otherwise */
    private inline fun updateState(transform: (ManageTagsState.Content) -> ManageTagsState.Content) {
        state.update { current ->
            when (current) {
                is ManageTagsState.Content -> transform(current)
                else -> current
            }
        }
    }

    companion object {
        /**
         * Converts the backend's recursive [BackendTagTreeNode] into a flat list of all
         * [TagListItemState]s in pre-order. Always recurses into all children regardless of
         * collapsed state; the `collapsed` flag is preserved on each item for later filtering.
         */
        @VisibleForTesting
        fun flattenTree(root: BackendTagTreeNode): List<TagListItemState> {
            val result = mutableListOf<TagListItemState>()

            fun traverse(
                node: BackendTagTreeNode,
                parentFullTag: String,
            ) {
                for (child in node.childrenList) {
                    val fullTag = if (parentFullTag.isEmpty()) child.name else "$parentFullTag::${child.name}"
                    result.add(
                        TagListItemState(
                            fullTag = TagName.build(fullTag) ?: error("Invalid tag provided"),
                            displayName = child.name,
                            level = child.level - 1,
                            hasChildren = child.childrenList.isNotEmpty(),
                            collapsed = child.collapsed,
                        ),
                    )
                    if (child.childrenList.isNotEmpty()) {
                        traverse(child, fullTag)
                    }
                }
            }

            traverse(root, "")
            return result
        }

        /**
         * Filters a flat tag list to hide children of collapsed nodes.
         * Expects [allNodes] in pre-order (parent before children).
         */
        @VisibleForTesting
        fun applyCollapsedVisibility(allNodes: List<TagListItemState>): List<TagListItemState> =
            buildList {
                var skipBelowLevel: Int? = null
                for (item in allNodes) {
                    if (skipBelowLevel != null && item.level > skipBelowLevel) {
                        continue
                    }
                    skipBelowLevel = if (item.collapsed && item.hasChildren) item.level else null
                    add(item)
                }
            }

        /**
         * Filters a flat tag list to items matching [searchQuery] (case-insensitive substring),
         * plus their ancestors to preserve hierarchy.
         * Collapsed nodes are expanded if their children match, ensuring leaf matches are visible.
         *
         * O(n*d) where n = number of tags and d = maximum tag depth.
         */
        @VisibleForTesting
        fun applySearchFilter(
            allNodes: List<TagListItemState>,
            searchQuery: String,
        ): List<TagListItemState> {
            val queryLowercase = searchQuery.lowercase()
            val visibleTags = mutableSetOf<String>()
            val parentsWithVisibleChildren = mutableSetOf<String>()
            for (item in allNodes) {
                if (item.containsLowercase(queryLowercase)) {
                    visibleTags.add(item.fullTag.value)
                    for (ancestor in item.fullTag.ancestors) {
                        visibleTags.add(ancestor)
                        parentsWithVisibleChildren.add(ancestor)
                    }
                }
            }
            return allNodes
                .filter { it.fullTag.value in visibleTags }
                .map {
                    // expand a collapsed parent if a child matches the search
                    if (it.collapsed && it.fullTag.value in parentsWithVisibleChildren) {
                        it.copy(collapsed = false)
                    } else {
                        it
                    }
                }
        }
    }
}

/**
 * A flattened representation of a tag for display in a RecyclerView.
 *
 * @param fullTag full hierarchical tag path, e.g. "science::biology"
 * @param displayName leaf name only, e.g. "biology"
 * @param level tree depth (0 = top-level)
 */
data class TagListItemState(
    val fullTag: TagName,
    val displayName: String,
    val level: Int,
    val hasChildren: Boolean,
    val collapsed: Boolean,
) {
    // cache the tag name to support fast case-insensitive searches
    private val fullTagLower: String = fullTag.value.lowercase()

    /** Case-insensitive check against the full tag path. [query] must be pre-lowercased. */
    fun containsLowercase(query: String): Boolean = fullTagLower.contains(query)
}

sealed class ManageTagsState {
    data object Loading : ManageTagsState()

    data class Content(
        val visibleNodes: List<TagListItemState>,
        /** `true` while a tag operation runs, allowing the UI to show progress without hiding the list. */
        val isWorking: Boolean = false,
        /** Most recent operation failure, cleared when the next operation starts. */
        val error: Throwable? = null,
    ) : ManageTagsState()

    data class Error(
        val error: Throwable,
    ) : ManageTagsState()
}

sealed interface UserMessage {
    /** @param notesAffected number of notes the tag was removed from */
    data class TagRemoved(
        val notesAffected: Int,
    ) : UserMessage

    /** @param notesAffected number of notes whose tags were updated */
    data class TagRenamed(
        val notesAffected: Int,
    ) : UserMessage

    /** @param count number of unused tags removed from the collection */
    data class ClearedUnusedTags(
        val count: Int,
    ) : UserMessage

    /** A generic error message for unexpected failures */
    data object UnexpectedError : UserMessage
}
