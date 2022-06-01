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
import java.util.*

@KotlinCleanup("add a default constructor for the class")
class TextCardExporter : Exporter {
    constructor(col: Collection, includeHTML: Boolean) : super(col) {
        mIncludeHTML = includeHTML
    }

    constructor(col: Collection, did: Long, includeHTML: Boolean) : super(col, did) {
        mIncludeHTML = includeHTML
    }

    /**
     * Exports into a csv(tsv) file
     *
     * @param path path of the file
     * @throws IOException encountered an error while writing the csv file
     */
    @Throws(IOException::class)
    fun doExport(path: String) {
        val ids = cardIds()
        Arrays.sort(ids)
        val out = StringBuilder()
        for (cid in ids) {
            val c = mCol.getCard(cid)
            @KotlinCleanup("use a string template to reduce to a single append() call")
            out.append(esc(c.q()))
            out.append("\t")
            out.append(esc(c.a()))
            out.append("\n")
        }
        BufferedWriter(
            OutputStreamWriter(
                FileOutputStream(path),
                StandardCharsets.UTF_8
            )
        ).use { writer -> writer.write(out.toString()) }
    }

    /**
     * Strip off the repeated question in answer if exists
     *
     * @param s answer
     * @return stripped answer
     */
    private fun esc(s: String): String {
        val str = s.replace("(?si)^.*<hr id=answer>\\n*".toRegex(), "")
        return processText(str)
    }

    companion object {
        const val EXT = ".txt"
    }
}
