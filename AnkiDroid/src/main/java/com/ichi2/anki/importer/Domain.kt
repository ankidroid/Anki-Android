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

package com.ichi2.anki.importer

import android.content.Context
import androidx.annotation.StringRes
import com.ichi2.anki.R
import com.ichi2.anki.importer.AllowHtml.INCLUDE_HTML
import com.ichi2.anki.importer.AllowHtml.STRIP_HTML
import com.ichi2.libanki.importer.NoteImporter.ImportMode as libankiImportMode

/** The name of a field of a note type */
internal typealias FieldName = String

/** Whether HTML and newlines should be escaped.
 * @see [INCLUDE_HTML] and [STRIP_HTML]
 */
enum class AllowHtml {
    /** No escaping should take place. The exact value in the CSV should be copied to the field */
    INCLUDE_HTML,
    /**
     * HTML should be escaped and newlines should be replaced with `<br>` tags
     * @see [com.ichi2.utils.HtmlUtils.escape]
     */
    STRIP_HTML;

    fun asBoolean(): Boolean = this == INCLUDE_HTML

    companion object {
        fun fromBoolean(b: Boolean): AllowHtml = if (b) INCLUDE_HTML else STRIP_HTML
    }
}

/** How conflicts in the first field should be handled */
enum class ImportConflictMode(val constant: libankiImportMode, @StringRes val resourceId: Int) {
    /** update if first field matches existing note  */
    UPDATE(libankiImportMode.UPDATE_MODE, R.string.import_mode_update),
    /** ignore if first field matches existing note  */
    IGNORE(libankiImportMode.IGNORE_MODE, R.string.import_mode_ignore),
    /** import even if first field matches existing note  */
    DUPLICATE(libankiImportMode.ADD_MODE, R.string.import_mode_add_regardless)
}

/** A path to a file in the Android cache */
internal typealias CacheFilePath = String

/**
 * Override AnkiDroid's default character detection for the separate
 * Anki Desktop fails if this is not a character
 * */
internal typealias DelimiterChar = Char

internal fun DelimiterChar.toDisplayString(context: Context): String {
    return when (this) {
        ' ' -> context.getString(R.string.importer_delimiter_space)
        '\t' -> context.getString(R.string.importer_delimiter_tab)
        ',' -> context.getString(R.string.importer_delimiter_comma)
        ';' -> context.getString(R.string.importer_delimiter_semicolon)
        ':' -> context.getString(R.string.importer_delimiter_colon)
        else -> this.toString()
    }
}
