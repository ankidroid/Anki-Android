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

import net.ankiweb.rsdroid.Backend

class RustTagsBackend(val backend: Backend) : TagsBackend {
    override fun all_tags(): List<String> {
        return backend.allTags()
    }

    override fun register_tags(tags: String, preserve_usn: Boolean, usn: Int, clear_first: Boolean) {
        TODO("no longer in backend")
    }

    override fun remove_note_tags(nids: List<Long>, tags: String): Int {
        return backend.removeNoteTags(nids, tags).count
    }

    override fun add_note_tags(nids: List<Long>, tags: String): Int {
        return backend.addNoteTags(nids, tags).count
    }
}
