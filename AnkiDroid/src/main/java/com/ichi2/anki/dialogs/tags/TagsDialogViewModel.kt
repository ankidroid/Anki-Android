/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.dialogs.tags

import androidx.lifecycle.ViewModel
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.asyncIO
import com.ichi2.anki.libanki.NoteId
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * @param noteIds IDs of notes whose tags should bfe retrieved and marked as "checked"
 * @param checkedTags additional list of checked tags.
 * These tags coming from EXTRAS are treated as absolute checked and cannot be indeterminate.
 * @param isCustomStudying true if all inputs are to be handled as unchecked tags, false otherwise(
 * this is a temporary parameter until custom study by tags is modified)
 *  They are joined with the tags retrieved from noteIds
 *
 *  @see <a href="https://github.com/ankidroid/Anki-Android/pull/19499#discussion_r2532184695">Extra checked tags</>
 */
class TagsDialogViewModel(
    noteIds: Collection<NoteId> = emptyList(),
    checkedTags: Collection<String> = emptyList(),
    isCustomStudying: Boolean = false,
) : ViewModel() {
    val tags: Deferred<TagsList>

    val initProgress: StateFlow<InitProgress>
        field = MutableStateFlow<InitProgress>(InitProgress.Processing)

    init {
        tags =
            asyncIO {
                val allTagsNode = withCol { tags.tree() }
                val allTags = mutableListOf<String>()

                fun traverse(
                    node: anki.tags.TagTreeNode,
                    parentFullTag: String,
                ) {
                    for (child in node.childrenList) {
                        val fullTag = if (parentFullTag.isEmpty()) child.name else "$parentFullTag::${child.name}"
                        allTags.add(fullTag)
                        traverse(child, fullTag)
                    }
                }
                traverse(allTagsNode, "")

                val allCheckedTags = mutableSetOf<String>()
                val uncheckedTags = mutableSetOf<String>()

                // Fetch all tags for the given notes
                val notesTagsList =
                    noteIds.mapIndexed { index, nid ->
                        initProgress.emit(InitProgress.FetchingNoteTags(index + 1, noteIds.size))
                        withCol { getNote(nid) }.tags
                    }

                // For each tag check if it's present in the notes to determine if it's checked or unchecked
                if (notesTagsList.isNotEmpty()) {
                    for (tag in allTags) {
                        var isChecked = false
                        var isUnchecked = false
                        for (noteTags in notesTagsList) {
                            if (noteTags.any { it.equals(tag, ignoreCase = true) }) {
                                isChecked = true
                            } else {
                                isUnchecked = true
                            }
                            if (isChecked && isUnchecked) break
                        }
                        if (isChecked) allCheckedTags.add(tag)
                        if (isUnchecked) uncheckedTags.add(tag)
                    }
                }

                // add the extra checked tags, these are to be shown as `checked` and cannot be indeterminate
                val extraCheckedTags = checkedTags.toSet()
                allCheckedTags.addAll(extraCheckedTags)
                uncheckedTags.removeAll(extraCheckedTags)
                initProgress.emit(InitProgress.Processing)
                if (isCustomStudying) {
                    val customStudyTags = allTags.filter { allCheckedTags.contains(it) }
                    TagsList(
                        allTags = customStudyTags,
                        checkedTags = emptyList(),
                        uncheckedTags = customStudyTags,
                    )
                } else {
                    TagsList(
                        allTags = allTags,
                        checkedTags = allCheckedTags,
                        uncheckedTags = uncheckedTags,
                    )
                }.also {
                    initProgress.emit(InitProgress.Finished)
                }
            }
    }

    sealed interface InitProgress {
        data object Processing : InitProgress

        class FetchingNoteTags(
            val noteNumber: Int,
            val noteCount: Int,
        ) : InitProgress

        data object Finished : InitProgress
    }
}
