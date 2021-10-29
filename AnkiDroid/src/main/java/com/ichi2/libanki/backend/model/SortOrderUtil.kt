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

@file:Suppress("deprecation") // BuiltinSortKind - is a valid choice

package com.ichi2.libanki.backend.model

import BackendProto.Backend
import com.ichi2.libanki.SortOrder
import com.ichi2.libanki.SortOrder.BuiltinSortKind.BuiltIn.*
import BackendProto.Backend.BuiltinSearchOrder.BuiltinSortKind as BackendSortKind

// Conversion functions from SortOrder to Backend.SortOrder

fun SortOrder.toProtoBuf(): Backend.SortOrder {
    val builder = Backend.SortOrder.newBuilder()
    return when (this) {
        is SortOrder.NoOrdering -> {
            builder.setNone(Backend.Empty.getDefaultInstance())
        }
        is SortOrder.UseCollectionOrdering ->
            builder.setFromConfig(Backend.Empty.getDefaultInstance())

        is SortOrder.AfterSqlOrderBy ->
            builder.setCustom(this.customOrdering)
        is SortOrder.BuiltinSortKind ->
            builder.setBuiltin(this.toProtoBuf())
        else -> throw IllegalStateException(this.toString())
    }.build()
}

fun SortOrder.BuiltinSortKind.toProtoBuf(): Backend.BuiltinSearchOrder {
    val enumValue = when (this.value) {
        NOTE_CREATION -> BackendSortKind.NOTE_CREATION
        NOTE_MOD -> BackendSortKind.NOTE_MOD
        NOTE_FIELD -> BackendSortKind.NOTE_FIELD
        NOTE_TAGS -> BackendSortKind.NOTE_TAGS
        NOTE_TYPE -> BackendSortKind.NOTE_TYPE
        CARD_MOD -> BackendSortKind.CARD_MOD
        CARD_REPS -> BackendSortKind.CARD_REPS
        CARD_DUE -> BackendSortKind.CARD_DUE
        CARD_EASE -> BackendSortKind.CARD_EASE
        CARD_LAPSES -> BackendSortKind.CARD_LAPSES
        CARD_INTERVAL -> BackendSortKind.CARD_INTERVAL
        CARD_DECK -> BackendSortKind.CARD_DECK
        CARD_TEMPLATE -> BackendSortKind.CARD_TEMPLATE
        UNRECOGNIZED -> BackendSortKind.UNRECOGNIZED
    }

    return Backend.BuiltinSearchOrder.newBuilder()
        .setKind(enumValue)
        .setReverse(this.reverse)
        .build()
}
