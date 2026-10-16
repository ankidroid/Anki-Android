// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

import anki.collection.OpChangesWithCount
import anki.collection.copy
import com.ichi2.anki.libanki.Collection

/**
 * Renames tags on notes, repairing duplicates when subtrees merge.
 *
 * Works around [Anki issue 5857](https://github.com/ankitects/anki/issues/5857).
 *
 * Follows Anki's backend behavior: an entirely unused subtree is left unchanged. When
 * renaming a subtree used by notes, unused cached descendants are removed, not renamed.
 * See https://github.com/ankitects/anki/blob/main/rslib/src/tags/rename.rs.
 */
internal fun Collection.renameTagTree(
    oldName: TagName,
    newName: TagName,
): OpChangesWithCount {
    val affectedNotes = findNotes(oldName.literalSubtreeSearch())
    val result = tags.rename(oldName, newName)
    if (result.count == 0) return result

    // Renaming into an existing tag can leave duplicate tags on a note. Saving only those
    // notes canonifies their tags and keeps unrelated notes untouched.
    val duplicates = affectedNotes.map(::getNote).filter { note -> note.tags.distinctBy(TagName::build).size != note.tags.size }
    if (duplicates.isEmpty()) return result

    val undoStep = undoStatus().lastStep
    duplicates.forEach { it.tags = it.tags.distinctBy(TagName::build).toMutableList() }
    // The combined changes from mergeUndoEntries below include these note updates.
    @Suppress("CheckResult")
    updateNotes(duplicates)
    return result.copy { changes = mergeUndoEntries(undoStep) }
}

/** A literal regex avoids tag-search wildcards and reserved names such as "none". */
private fun TagName.literalSubtreeSearch(): String {
    val escaped =
        buildString {
            for (char in value) {
                if (char in "\\.^$|?*+()[]{}") append('\\')
                append(char)
            }
        }.replace("\"", "\\\"")
    return "\"tag:re:^$escaped(?:::|$)\""
}
