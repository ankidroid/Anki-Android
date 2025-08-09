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
import kotlinx.coroutines.flow.asStateFlow

/**
 * @param noteIds IDs of notes whose tags should bfe retrieved and marked as "checked"
 * @param checkedTags additional list of checked tags.
 * @param isCustomStudying true if all inputs are to be handled as unchecked tags, false otherwise(
 * this is a temporary parameter until custom study by tags is modified)
 *  They are joined with the tags retrieved from noteIds
 */
class TagsDialogViewModel(
    noteIds: Collection<NoteId> = emptyList(),
    checkedTags: Collection<String> = emptyList(),
    isCustomStudying: Boolean = false,
) : ViewModel() {
    val tags: Deferred<TagsList>

    private val _initProgress = MutableStateFlow<InitProgress>(InitProgress.Processing)
    val initProgress = _initProgress.asStateFlow()

    init {
        tags =
            asyncIO {
                val allTags = withCol { tags.all() }.toSet()
                val allCheckedTags =
                    noteIds
                        .flatMapIndexedTo(mutableSetOf()) { index, nid ->
                            _initProgress.emit(InitProgress.FetchingNoteTags(index + 1, noteIds.size))
                            withCol { getNote(nid) }.tags
                        }.apply {
                            addAll(checkedTags)
                        }
                _initProgress.emit(InitProgress.Processing)
                val uncheckedTags = allTags - allCheckedTags
                if (isCustomStudying) {
                    TagsList(
                        allTags = allCheckedTags,
                        checkedTags = emptyList(),
                        uncheckedTags = allCheckedTags,
                    )
                } else {
                    TagsList(
                        allTags = allTags,
                        checkedTags = allCheckedTags,
                        uncheckedTags = uncheckedTags,
                    )
                }.also {
                    _initProgress.emit(InitProgress.Finished)
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
