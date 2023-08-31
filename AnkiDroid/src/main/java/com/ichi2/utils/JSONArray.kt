/*
 *  Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>
 *
 *  This file is free software: you may copy, redistribute and/or modify it
 *  under the terms of the GNU General Public License as published by the
 *  Free Software Foundation, either version 3 of the License, or (at your
 *  option) any later version.
 *
 *  This file is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *  This file incorporates work covered by the following copyright and
 *  permission notice:
 *
 *    Copyright (c) 2002 JSON.org
 *
 *    Permission is hereby granted, free of charge, to any person obtaining a copy
 *    of this software and associated documentation files (the "Software"), to deal
 *    in the Software without restriction, including without limitation the rights
 *    to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *    copies of the Software, and to permit persons to whom the Software is
 *    furnished to do so, subject to the following conditions:
 *
 *    The above copyright notice and this permission notice shall be included in all
 *    copies or substantial portions of the Software.
 *
 *    The Software shall be used for Good, not Evil.
 *
 *    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *    LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *    OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *    SOFTWARE.
 */
package com.ichi2.utils

import org.json.JSONArray
import org.json.JSONObject

fun JSONArray.deepClone(): JSONArray {
    val clone = JSONArray()
    for (i in 0 until length()) {
        clone.put(
            when (get(i)) {
                is JSONObject -> getJSONObject(i).deepClone()
                is JSONArray -> getJSONArray(i).deepClone()
                else -> get(i)
            }
        )
    }
    return clone
}

fun JSONArray.jsonArrayIterable(): Iterable<JSONArray> {
    return Iterable { jsonArrayIterator() }
}

fun JSONArray.jsonArrayIterator(): Iterator<JSONArray> {
    return object : Iterator<JSONArray> {
        private var mIndex = 0
        override fun hasNext(): Boolean {
            return mIndex < length()
        }

        override fun next(): JSONArray {
            val array = getJSONArray(mIndex)
            mIndex++
            return array
        }
    }
}

fun JSONArray.jsonObjectIterable(): Iterable<JSONObject> {
    return Iterable { jsonObjectIterator() }
}

@KotlinCleanup("see if jsonObject/string/longIterator() methods can be combined into one")
fun JSONArray.jsonObjectIterator(): Iterator<JSONObject> {
    return object : Iterator<JSONObject> {
        private var mIndex = 0
        override fun hasNext(): Boolean {
            return mIndex < length()
        }

        override fun next(): JSONObject {
            val `object` = getJSONObject(mIndex)
            mIndex++
            return `object`
        }
    }
}

fun JSONArray.stringIterable(): Iterable<String> {
    return Iterable { stringIterator() }
}

fun JSONArray.stringIterator(): Iterator<String> {
    return object : Iterator<String> {
        private var mIndex = 0
        override fun hasNext(): Boolean {
            return mIndex < length()
        }

        override fun next(): String {
            val string = getString(mIndex)
            mIndex++
            return string
        }
    }
}

fun JSONArray.longIterable(): Iterable<Long> {
    return Iterable { longIterator() }
}

fun JSONArray.longIterator(): Iterator<Long> {
    return object : Iterator<Long> {
        private var mIndex = 0
        override fun hasNext(): Boolean {
            return mIndex < length()
        }

        override fun next(): Long {
            val long_ = getLong(mIndex)
            mIndex++
            return long_
        }
    }
}

@KotlinCleanup("simplify fun with apply and forEach")
fun JSONArray.toJSONObjectList(): List<JSONObject> {
    val l: MutableList<JSONObject> = ArrayList(length())
    for (`object` in jsonObjectIterable()) {
        l.add(`object`)
    }
    return l
}

@KotlinCleanup("simplify fun with apply and forEach")
fun JSONArray.toLongList(): List<Long> {
    val l: MutableList<Long> = ArrayList(length())
    for (`object` in longIterable()) {
        l.add(`object`)
    }
    return l
}

@KotlinCleanup("simplify fun with apply and forEach")
fun JSONArray.toStringList(): List<String> {
    val l: MutableList<String> = ArrayList(length())
    for (`object` in stringIterable()) {
        l.add(`object`)
    }
    return l
}

/**
 * @return Given an array of objects, return the array of the value with `key`, assuming that they are String.
 * E.g. templates, fields are a JSONArray whose objects have name
 */
@KotlinCleanup("simplify fun with apply and forEach")
fun JSONArray.toStringList(key: String?): List<String> {
    val l: MutableList<String> = ArrayList(length())
    for (`object` in jsonObjectIterable()) {
        l.add(`object`.getString(key!!))
    }
    return l
}
