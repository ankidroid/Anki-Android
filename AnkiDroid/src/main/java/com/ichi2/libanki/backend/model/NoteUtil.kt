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

import BackendProto.Backend
import com.ichi2.libanki.Note

fun Note.to_backend_note(): Backend.Note {

    return Backend.Note.newBuilder()
        .setId(this.id)
        .setGuid(this.guId)
        .setNotetypeId(this.mid)
        .setMtimeSecs(this.mod.toInt()) // this is worrying, 2038 problem
        .setUsn(this.usn)
        .setTagsList(this.tags)
        .setFieldList(this.fields)
        .build()
}

private fun Backend.Note.Builder.setFieldList(fields: Array<String>): Backend.Note.Builder {
    for (t in fields.withIndex()) {
        this.setFields(t.index, t.value)
    }
    return this
}

private fun Backend.Note.Builder.setTagsList(tags: ArrayList<String>): Backend.Note.Builder {
    for (t in tags.withIndex()) {
        this.setTags(t.index, t.value)
    }
    return this
}
