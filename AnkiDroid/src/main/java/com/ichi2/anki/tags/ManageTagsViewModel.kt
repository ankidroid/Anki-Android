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
import com.ichi2.anki.tags.UserMessage.TagMoved
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

    /** Explicit collapses override search expansion until the query changes. */
    private val searchCollapsedTags = mutableSetOf<TagName>()

    private var tagOperation: Job? = null
    private var expansionSave: Job? = null

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
        if (query != searchQuery.value) searchCollapsedTags.clear()
        searchQuery.value = query
        updateState { loaded ->
            loaded.copy(
                visibleNodes = computeVisibleNodes(query),
            )
        }
    }

    /** Acknowledges that the UI has finished displaying a message. */
    fun messageShown(id: MessageId) = messageQueue.acknowledge(id)

    /** Clears a dismissed operation error if it is still current. */
    fun errorDismissed(error: Throwable) {
        updateState { current ->
            if (current.error === error) current.copy(error = null) else current
        }
    }

    /** Checks the complete tag tree, including tags hidden by search or collapsed ancestors. */
    fun tagExists(tag: TagName): Boolean = tagList.any { it.fullTag == tag }

    /** Finds a tag using its displayed expansion state when visible. */
    private fun findTagNode(tag: TagName): TagListItemState? {
        val loaded = state.value as? ManageTagsState.Content
        if (loaded == null) {
            Timber.w("findTagNode called while not loaded")
            return null
        }
        return (loaded.visibleNodes.firstOrNull { it.fullTag == tag } ?: tagList.firstOrNull { it.fullTag == tag }).also {
            if (it == null) {
                Timber.w("Tag not found in hierarchy")
                Timber.d("Tag: %s", tag)
            }
        }
    }

    /**
     * Expands or collapses [tag] immediately, then saves the preference without a busy state.
     * Saves run in tap order; a save failure leaves the visible hierarchy intact and reports the error.
     *
     * PERF: ~55ms to process 10k+ tags on my M1; ~350ms to process 10k+ tags in CI
     *
     * @see Tags.setCollapsed
     */
    fun toggleCollapsed(tag: TagName): Job {
        tagOperation?.takeUnless { it.isCompleted }?.let { return it }
        val previousSave = expansionSave
        val operation =
            viewModelScope.launch(start = CoroutineStart.LAZY) {
                val node =
                    findTagNode(tag) ?: run {
                        messageQueue.enqueue(UserMessage.UnexpectedError)
                        return@launch
                    }
                val newCollapsed = !node.collapsed
                if (searchQuery.value.isNotBlank()) {
                    if (newCollapsed) searchCollapsedTags.add(node.fullTag) else searchCollapsedTags.remove(node.fullTag)
                }
                tagList =
                    tagList.map {
                        if (it.fullTag == tag) it.copy(collapsed = newCollapsed) else it
                    }
                updateState { it.copy(visibleNodes = computeVisibleNodes(searchQuery.value), error = null) }
                previousSave?.join()
                try {
                    withCol { tags.setCollapsed(node.fullTag, newCollapsed) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Saving tag expansion failed")
                    updateState { it.copy(error = e) }
                }
            }
        expansionSave = operation
        operation.start()
        return operation
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
     * Renames [oldName] to [newName] on notes, including matching child tags.
     * @see Tags.rename
     */
    fun renameTag(
        oldName: TagName,
        newName: TagName,
    ) = launchTagOpAndRefresh {
        val result = undoableOp { renameTagTree(oldName, newName) }
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

    /**
     * Reparents [tag] and its descendants on notes under [newParent]. An empty [newParent] moves
     * [tag] to the top level.
     * @see moveTagTree
     */
    fun moveTag(
        tag: TagName,
        newParent: TagName?,
    ) = launchTagOpAndRefresh {
        Timber.i("Moving tag")
        val result = undoableOp { moveTagTree(tag, newParent) }
        val currentParent = TagName.build(tag.value.substringBeforeLast("::", ""))
        messageQueue.enqueue(
            if (result.count == 0 && currentParent != newParent) {
                UserMessage.UnusedTagNotMoved
            } else {
                TagMoved(result.count)
            },
        )
    }

    private suspend fun loadTags() {
        Timber.i("Loading tags from collection")
        tagList = flattenTree(withCol { tags.tree() })
        state.value =
            ManageTagsState.Content(
                visibleNodes = computeVisibleNodes(searchQuery.value),
                totalTagCount = tagList.size,
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
        val operation =
            viewModelScope.launch(start = CoroutineStart.LAZY) {
                runTagOperation {
                    // Finish preference saves before a reload or a mutation can rename/remove their tags.
                    expansionSave?.join()
                    block()
                }
            }
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
            applySearchFilter(tagList, searchQuery, searchCollapsedTags)
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
                            fullTagName = fullTag,
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
         * Expands ancestors only when a match reaches a child's own name. A match inherited
         * from a parent's name preserves the subtree's existing collapsed state.
         * [manuallyCollapsedTags] prevents search from reopening tags collapsed by the user.
         *
         * O(n*d) where n = number of tags and d = maximum tag depth.
         */
        @VisibleForTesting
        fun applySearchFilter(
            allNodes: List<TagListItemState>,
            searchQuery: String,
            manuallyCollapsedTags: Set<TagName> = emptySet(),
        ): List<TagListItemState> {
            val queryLowercase = searchQuery.lowercase()
            val visibleTags = mutableSetOf<String>()
            val ancestorsToExpand = mutableSetOf<String>()
            for (item in allNodes) {
                if (item.containsLowercase(queryLowercase)) {
                    visibleTags.add(item.fullTag.value)
                    val expandAncestors = item.matchesOwnComponent(queryLowercase)
                    for (ancestor in item.fullTag.ancestors) {
                        visibleTags.add(ancestor)
                        if (expandAncestors) ancestorsToExpand.add(ancestor)
                    }
                }
            }
            val filteredNodes =
                allNodes
                    .filter { it.fullTag.value in visibleTags }
                    .map {
                        if (it.collapsed && it.fullTag.value in ancestorsToExpand && it.fullTag !in manuallyCollapsedTags) {
                            it.copy(collapsed = false)
                        } else {
                            it
                        }
                    }
            return applyCollapsedVisibility(filteredNodes)
        }
    }
}

/**
 * A flattened tag row. Equality includes the full spelling so UI updates reach Compose.
 *
 * @param fullTagName full hierarchical tag path, e.g. "science::biology"
 * @param displayName leaf name only, e.g. "biology"
 * @param level tree depth (0 = top-level)
 */
data class TagListItemState(
    val fullTagName: String,
    val displayName: String,
    val level: Int,
    val hasChildren: Boolean,
    val collapsed: Boolean,
) {
    val fullTag: TagName = requireNotNull(TagName.build(fullTagName))

    // cache the tag name to support fast case-insensitive searches
    private val fullTagLower: String = fullTagName.lowercase()

    /** Case-insensitive check against the full tag path. [query] must be pre-lowercased. */
    fun containsLowercase(query: String): Boolean = fullTagLower.contains(query)

    /** Whether pre-lowercased [query] reaches this tag's own name or its preceding separator. */
    fun matchesOwnComponent(query: String): Boolean {
        val matchStart = fullTagLower.lastIndexOf(query)
        // Include the separator so a query such as "parent::" reveals its immediate children.
        return matchStart >= 0 && matchStart + query.length > fullTagLower.lastIndexOf("::")
    }
}

sealed class ManageTagsState {
    data object Loading : ManageTagsState()

    /**
     * @param totalTagCount every tag in the collection, unaffected by search/collapse
     */
    data class Content(
        val visibleNodes: List<TagListItemState>,
        /** `true` while a tag operation runs, allowing the UI to show progress without hiding the list. */
        val isWorking: Boolean = false,
        /** Most recent operation failure, cleared on dismissal or when the next operation starts. */
        val error: Throwable? = null,
        val totalTagCount: Int = 0,
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

    /** @param notesAffected number of notes whose tags were reparented */
    data class TagMoved(
        val notesAffected: Int,
    ) : UserMessage

    /**
     * The backend leaves an entirely unused subtree in place when asked to move it.
     * Remove this message and its handling when [Anki issue 5870](https://github.com/ankitects/anki/issues/5870) is fixed.
     */
    data object UnusedTagNotMoved : UserMessage

    /** @param count number of unused tags removed from the collection */
    data class ClearedUnusedTags(
        val count: Int,
    ) : UserMessage

    /** A generic error message for unexpected failures */
    data object UnexpectedError : UserMessage
}
