/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.libanki.backend.model

import com.ichi2.libanki.Note

fun Note.to_backend_note(): anki.notes.Note {

    return anki.notes.Note.newBuilder()
        .setId(this.id)
        .setGuid(this.guId)
        .setNotetypeId(this.mid)
        .setMtimeSecs(this.mod.toInt()) // this is worrying, 2038 problem
        .setUsn(this.usn)
        .setTagsList(this.tags)
        .setFieldList(this.fields.requireNoNulls())
        .build()
}

private fun anki.notes.Note.Builder.setFieldList(fields: Array<String>): anki.notes.Note.Builder {
    for (t in fields.withIndex()) {
        this.setFields(t.index, t.value)
    }
    return this
}

private fun anki.notes.Note.Builder.setTagsList(tags: ArrayList<String>): anki.notes.Note.Builder {
    for (t in tags.withIndex()) {
        this.setTags(t.index, t.value)
    }
    return this
}
