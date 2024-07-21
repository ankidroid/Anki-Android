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
import com.ichi2.libanki.Collection
import com.ichi2.libanki.NotetypeJson
import com.ichi2.utils.KotlinCleanup
import timber.log.Timber

/**
 * Takes a list of media file names and removes them from the [Collection]
 * @param unused List of media names to be deleted
 */
fun deleteMedia(
    col: Collection,
    unused: List<String>,
): Int {
    // FIXME: this provides progress info that is not currently used
    col.media.removeFiles(unused)
    return unused.size
}

/**
 * Handles everything for a model change at once - template add / deletes as well as content updates
 */
@KotlinCleanup("strongly type templateChanges")
fun saveModel(
    col: Collection,
    notetype: NotetypeJson,
    templateChanges: ArrayList<Array<Any>>,
) {
    Timber.d("doInBackgroundSaveModel")
    val oldModel = col.notetypes.get(notetype.getLong("id"))

    // TODO: make undoable
    val newTemplates = notetype.tmpls
    for (change in templateChanges) {
        val oldTemplates = oldModel!!.tmpls
        when (change[1] as CardTemplateNotetype.ChangeType) {
            CardTemplateNotetype.ChangeType.ADD -> {
                Timber.d("doInBackgroundSaveModel() adding template %s", change[0])
                col.notetypes.addTemplate(oldModel, newTemplates[change[0] as Int])
            }
            CardTemplateNotetype.ChangeType.DELETE -> {
                Timber.d("doInBackgroundSaveModel() deleting template currently at ordinal %s", change[0])
                col.notetypes.remTemplate(oldModel, oldTemplates[change[0] as Int])
            }
        }
    }

    // required for Rust: the modified time can't go backwards, and we updated the model by adding fields
    // This could be done better
    notetype.put("mod", oldModel!!.getLong("mod"))
    col.notetypes.save(notetype)
    col.notetypes.update(notetype)
}
