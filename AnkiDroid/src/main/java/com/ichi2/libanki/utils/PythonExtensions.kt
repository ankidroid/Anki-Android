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

package com.ichi2.libanki.utils

import android.text.TextUtils
import com.ichi2.utils.JSONArray
import java.util.*

fun <T> MutableList<T>.append(value: T) {
    this.add(value)
}

fun <T> MutableList<T>.extend(elements: Iterable<T>) {
    this.addAll(elements)
}

fun <T> len(l: Sequence<T>): Long {
    return l.count().toLong()
}

fun <T> len(l: List<T>): Long {
    return l.size.toLong()
}

fun <E> MutableList<E>.pop(i: Int): E {
    return this.removeAt(i)
}

fun <K, V> HashMap<K, V>.items(): List<Pair<K, V>> {
    return this.entries.map {
        Pair(it.key, it.value)
    }
}

fun <T> List<T>?.isNullOrEmpty(): Boolean {
    return this == null || this.isEmpty()
}

fun <T> List<T>?.isNotNullOrEmpty(): Boolean {
    return !this.isNullOrEmpty()
}

fun <T> list(vararg elements: T) = mutableListOf(elements)

fun String.join(values: MutableList<String>): String {
    return TextUtils.join(this, values)
}

fun <E> MutableList<E>.toJsonArray(): JSONArray {
    val array = JSONArray()
    for (i in this) {
        array.put(i)
    }
    return array
}
