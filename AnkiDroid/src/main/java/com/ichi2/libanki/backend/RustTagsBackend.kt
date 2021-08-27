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

package com.ichi2.libanki.backend

import com.ichi2.libanki.backend.model.TagUsnTuple
import net.ankiweb.rsdroid.BackendV1

class RustTagsBackend(val backend: BackendV1) : TagsBackend {
    override fun all_tags(): List<TagUsnTuple> {
        return backend.allTags().tagsList.map {
            TagUsnTuple(it.tag, it.usn)
        }
    }

    override fun register_tags(tags: String, preserve_usn: Boolean, usn: Int, clear_first: Boolean) {
        backend.registerTags(tags, preserve_usn, usn, clear_first)
    }

    override fun update_note_tags(nids: List<Long>, tags: String, replacement: String, regex: Boolean): Int {
        return backend.updateNoteTags(nids, tags, replacement, regex).`val`
    }

    override fun add_note_tags(nids: List<Long>, tags: String): Int {
        return backend.addNoteTags(nids, tags).`val`
    }
}
