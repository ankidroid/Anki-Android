/*
 Copyright (c) 2021 Tarek Mohamed Abdalla <tarekkma@gmail.com>
 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.
 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.
 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.libanki

import com.ichi2.utils.KotlinCleanup
import java.io.BufferedWriter
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

class TextNoteExporter(
    col: Collection,
    did: DeckId?,
    val includeID: Boolean,
    val includedTags: Boolean,
    includeHTML: Boolean
) : Exporter(col, did) {
    init {
        this.includeHTML = includeHTML
    }

    constructor(
        col: Collection,
        includeID: Boolean,
        includedTags: Boolean,
        includeHTML: Boolean
    ) : this(col, null, includeID, includedTags, includeHTML)

    @Throws(IOException::class)
    fun doExport(path: String?) {
        val queryStr = String.format(
            "SELECT guid, flds, tags from notes " +
                "WHERE id in " +
                "(SELECT nid from cards WHERE cards.id in %s)",
            Utils.ids2str(cardIds())
        )
        val data: MutableList<String?> = ArrayList()
        col.db.query(queryStr).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val flds = cursor.getString(1)
                val tags = cursor.getString(2)
                val row: MutableList<String?> = ArrayList()
                if (includeID) {
                    row.add(id)
                }
                for (field in Utils.splitFields(flds)) {
                    row.add(processText(field))
                }
                if (includedTags) {
                    row.add(tags.trim())
                }
                @KotlinCleanup("use kotlin joinToString function")
                data.add(row.joinToString("\t"))
            }
        }
        count = data.size
        val out = data.joinToString("\n")
        BufferedWriter(
            OutputStreamWriter(
                FileOutputStream(path),
                StandardCharsets.UTF_8
            )
        ).use { writer -> writer.write(out.toString()) }
    }

    companion object {
        private const val EXT = ".txt"
    }
}
