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

// BuiltinSortKind - is a valid choice

package com.ichi2.libanki.backend.model

import com.ichi2.libanki.SortOrder

// Conversion functions from SortOrder to anki.search.SortOrder

fun SortOrder.toProtoBuf(): anki.search.SortOrder {
    val builder = anki.search.SortOrder.newBuilder()
    return when (this) {
        is SortOrder.NoOrdering -> {
            builder.setNone(anki.generic.Empty.getDefaultInstance())
        }
        is SortOrder.AfterSqlOrderBy ->
            builder.setCustom(this.customOrdering)
        is SortOrder.BuiltinSortKind ->
            builder.setBuiltin(this.toProtoBuf())
        else -> throw IllegalStateException(this.toString())
    }.build()
}

fun SortOrder.BuiltinSortKind.toProtoBuf(): anki.search.SortOrder.Builtin {
    return anki.search.SortOrder.Builtin.newBuilder()
        .setColumn(value)
        .setReverse(reverse)
        .build()
}
