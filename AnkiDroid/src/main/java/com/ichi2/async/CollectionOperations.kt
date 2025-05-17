/****************************************************************************************
 * Copyright (c) 2022 Divyansh Kushwaha <kushwaha.divyansh.dxn@gmail.com>               *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.async

import com.ichi2.anki.CardTemplateNotetype
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.libanki.Collection
import com.ichi2.libanki.NotetypeJson
import timber.log.Timber

/**
 * Takes a list of media file names and removes them from the [Collection]
 * @param unused List of media names to be deleted
 */
fun deleteMedia(
    col: Collection,
    unused: List<String>,
): Int {
    col.media.trashFiles(unused)
    return unused.size
}

/**
 * Trashes the specified media files and immediately empties the trash in the given [Collection].
 *
 * @param col The [Collection] from which media files should be removed.
 * @param unused A list of media file names to be deleted.
 */
fun clearMediaAndTrash(
    col: Collection,
    unused: List<String>,
) {
    col.media.trashFiles(unused)
    col.media.emptyTrash()
}

/**
 * Handles everything for a note type change at once - template add / deletes as well as content updates
 */
@KotlinCleanup("strongly type templateChanges")
fun saveNoteType(
    col: Collection,
    notetype: NotetypeJson,
    templateChanges: ArrayList<Array<Any>>,
) {
    Timber.d("saveNoteType")
    val oldNoteType = col.notetypes.get(notetype.id)

    // TODO: make undoable
    val newTemplates = notetype.templates
    for (change in templateChanges) {
        val oldTemplates = oldNoteType!!.templates
        when (change[1] as CardTemplateNotetype.ChangeType) {
            CardTemplateNotetype.ChangeType.ADD -> {
                Timber.d("saveNoteType() adding template %s", change[0])
                col.notetypes.addTemplate(oldNoteType, newTemplates[change[0] as Int])
            }
            CardTemplateNotetype.ChangeType.DELETE -> {
                Timber.d("saveNoteType() deleting template currently at ordinal %s", change[0])
                col.notetypes.remTemplate(oldNoteType, oldTemplates[change[0] as Int])
            }
        }
    }

    // required for Rust: the modified time can't go backwards, and we updated the note type by adding fields
    // This could be done better
    notetype.mod = oldNoteType!!.mod
    col.notetypes.save(notetype)
    col.notetypes.update(notetype)
}
