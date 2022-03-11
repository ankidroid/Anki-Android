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

import com.ichi2.utils.JSON.toString
import java.lang.reflect.Array

@KotlinCleanup(
    "lint issues;" +
        "consider iterator => sequence" +
        "helper for try..catch" +
        "fix bare variables: `object`" +
        "constructors accepting null collections/strings, and Any are ambiguous"
)
class JSONArray : org.json.JSONArray {
    constructor() : super() {}
    constructor(copyFrom: org.json.JSONArray) {
        try {
            for (i in 0 until copyFrom.length()) {
                put(i, copyFrom[i])
            }
        } catch (e: org.json.JSONException) {
            throw JSONException(e)
        }
    }

    constructor(x: JSONTokener) : this() {
        try {
            if (x.nextClean() != '[') {
                throw x.syntaxError("A JSONArray text must start with '['")
            }
            var nextChar = x.nextClean()
            if (nextChar.code == 0) {
                // array is unclosed. No ']' found, instead EOF
                throw x.syntaxError("Expected a ',' or ']'")
            }
            if (nextChar != ']') {
                x.back()
                while (true) {
                    if (x.nextClean() == ',') {
                        x.back()
                        put(JSONObject.NULL)
                    } else {
                        x.back()
                        put(x.nextValue())
                    }
                    when (x.nextClean()) {
                        '\u0000' -> throw x.syntaxError("Expected a ',' or ']'")
                        ',' -> {
                            nextChar = x.nextClean()
                            if (nextChar.code == 0) {
                                // array is unclosed. No ']' found, instead EOF
                                throw x.syntaxError("Expected a ',' or ']'")
                            }
                            if (nextChar == ']') {
                                return
                            }
                            x.back()
                        }
                        ']' -> return
                        else -> throw x.syntaxError("Expected a ',' or ']'")
                    }
                }
            }
        } catch (e: org.json.JSONException) {
            throw JSONException(e)
        }
    }

    @KotlinCleanup("null is not permitted - check the conversion commit as this will revert some test changes")
    constructor(source: String?) : this(JSONTokener(source)) {}
    @KotlinCleanup("for (i in 0 until length) {")
    constructor(array: Any) : this() {
        if (array.javaClass.isArray) {
            val length = Array.getLength(array)
            var i = 0
            while (i < length) {
                this.put(Array.get(array, i))
                i += 1
            }
        } else {
            throw JSONException(
                "JSONArray initial value should be a string or collection or array."
            )
        }
    }

    constructor(copyFrom: Collection<*>?) : this() {
        if (copyFrom != null) {
            for (o in copyFrom) {
                put(o)
            }
        }
    }

    override fun put(value: Double): JSONArray {
        return try {
            super.put(value)
            this
        } catch (e: org.json.JSONException) {
            throw JSONException(e)
        }
    }

    override fun put(index: Int, value: Boolean): JSONArray {
        return try {
            super.put(index, value)
            this
        } catch (e: org.json.JSONException) {
            throw JSONException(e)
        }
    }

    override fun put(index: Int, value: Double): JSONArray {
        return try {
            super.put(index, value)
            this
        } catch (e: org.json.JSONException) {
            throw JSONException(e)
        }
    }

    override fun put(index: Int, value: Int): JSONArray {
        return try {
            super.put(index, value)
            this
        } catch (e: org.json.JSONException) {
            throw JSONException(e)
        }
    }

    override fun put(index: Int, value: Long): JSONArray {
        return try {
            super.put(index, value)
            this
        } catch (e: org.json.JSONException) {
            throw JSONException(e)
        }
    }

    override fun put(index: Int, value: Any): JSONArray {
        return try {
            super.put(index, value)
            this
        } catch (e: org.json.JSONException) {
            throw JSONException(e)
        }
    }

    override fun get(index: Int): Any {
        return try {
            super.get(index)
        } catch (e: org.json.JSONException) {
            throw JSONException(e)
        }
    }

    override fun getBoolean(index: Int): Boolean {
        return try {
            super.getBoolean(index)
        } catch (e: org.json.JSONException) {
            throw JSONException(e)
        }
    }

    override fun getDouble(index: Int): Double {
        return try {
            super.getDouble(index)
        } catch (e: org.json.JSONException) {
            throw JSONException(e)
        }
    }

    override fun getInt(index: Int): Int {
        return try {
            super.getInt(index)
        } catch (e: org.json.JSONException) {
            throw JSONException(e)
        }
    }

    override fun getLong(index: Int): Long {
        return try {
            super.getLong(index)
        } catch (e: org.json.JSONException) {
            throw JSONException(e)
        }
    }

    override fun getString(index: Int): String {
        return try {
            super.getString(index)
        } catch (e: org.json.JSONException) {
            throw JSONException(e)
        }
    }

    override fun getJSONArray(pos: Int): JSONArray {
        return try {
            arrayToArray(super.getJSONArray(pos))!!
        } catch (e: org.json.JSONException) {
            throw RuntimeException(e)
        }
    }

    override fun getJSONObject(pos: Int): JSONObject {
        return try {
            JSONObject.objectToObject(super.getJSONObject(pos))
        } catch (e: org.json.JSONException) {
            throw RuntimeException(e)
        }
    }

    override fun join(separator: String): String {
        return try {
            super.join(separator)
        } catch (e: org.json.JSONException) {
            throw JSONException(e)
        }
    }

    override fun toString(indentSpace: Int): String {
        return try {
            super.toString(indentSpace)
        } catch (e: org.json.JSONException) {
            throw JSONException(e)
        }
    }

    fun deepClone(): JSONArray {
        val clone = JSONArray()
        for (i in 0 until length()) {
            if (get(i) is JSONObject) {
                clone.put(getJSONObject(i).deepClone())
            } else if (get(i) is JSONArray) {
                clone.put(getJSONArray(i).deepClone())
            } else {
                clone.put(get(i))
            }
        }
        return clone
    }

    fun jsonArrayIterable(): Iterable<JSONArray> {
        return Iterable { jsonArrayIterator() }
    }

    fun jsonArrayIterator(): Iterator<JSONArray> {
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

    fun jsonObjectIterable(): Iterable<JSONObject> {
        return Iterable { jsonObjectIterator() }
    }

    fun jsonObjectIterator(): Iterator<JSONObject> {
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

    fun stringIterable(): Iterable<String> {
        return Iterable { stringIterator() }
    }

    fun stringIterator(): Iterator<String> {
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

    fun longIterable(): Iterable<Long> {
        return Iterable { longIterator() }
    }

    fun longIterator(): Iterator<Long> {
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

    fun toJSONObjectList(): List<JSONObject> {
        val l: MutableList<JSONObject> = ArrayList(length())
        for (`object` in jsonObjectIterable()) {
            l.add(`object`)
        }
        return l
    }

    fun toLongList(): List<Long> {
        val l: MutableList<Long> = ArrayList(length())
        for (`object` in longIterable()) {
            l.add(`object`)
        }
        return l
    }

    fun toStringList(): List<String> {
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
    fun toStringList(key: String): List<String> {
        val l: MutableList<String> = ArrayList(length())
        for (`object` in jsonObjectIterable()) {
            l.add(`object`.getString(key))
        }
        return l
    }

    /**
     * Returns a new object whose values are the values in this array, and whose
     * names are the values in `names`. Names and values are paired up by
     * index from 0 through to the shorter array's length. Names that are not
     * strings will be coerced to strings. This method returns null if either
     * array is empty.
     */
    @KotlinCleanup("`names` to [names]")
    fun toJSONObject(names: JSONArray): JSONObject? {
        // copied from upstream
        val result = JSONObject()
        val length = Math.min(names.length(), length())
        if (length == 0) {
            return null
        }
        for (i in 0 until length) {
            val name = toString(names.opt(i))!!
            result.put(name, opt(i))
        }
        return result
    }

    companion object {
        /**
         * This method simply change the type.
         *
         * See the comment of objectToObject to read about the problems met here.
         *
         * @param ar Actually a JSONArray
         * @return the same element as input. But considered as a JSONArray.
         */
        @JvmStatic
        fun arrayToArray(ar: org.json.JSONArray?): JSONArray? {
            return ar as JSONArray?
        }
    }
}
