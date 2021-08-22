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

import android.os.Parcelable
import com.ichi2.libanki.did
import com.ichi2.libanki.ntid
import kotlinx.parcelize.Parcelize

/** The options that a user may select after they have selected a file */
@Parcelize
internal data class ImportOptions(
    val path: String,
    var noteTypeId: ntid,
    var deck: did,
    var delimiterChar: DelimiterChar,
    var importMode: ImportConflictMode,
    var allowHtml: AllowHtml,
    var importModeTag: String,
    var mapping: MutableList<CsvFieldMappingBehavior>
) : Parcelable
