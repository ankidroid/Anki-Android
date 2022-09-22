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

import androidx.annotation.CheckResult
import androidx.annotation.VisibleForTesting
import com.ichi2.utils.JSON.checkDouble
import com.ichi2.utils.JSON.toString

/*
  Each method similar to the methods in JSONObjects. Name changed to add a ,
  and it throws JSONException instead of JSONException.
  Furthermore, it returns JSONObject and JSONArray

 */
@KotlinCleanup("Simplify null comparison, !! -> ?.")
open class JSONObject : org.json.JSONObject, Iterable<String?> {
    constructor() : super()
    constructor(copyFrom: Map<out String, Any?>) : super(copyFrom)

    /**
     * Creates a new [JSONObject] by copying mappings for the listed names
     * from the given object. Names that aren't present in [copyFrom] will
     * be skipped.
     *
     * Code copied from upstream.
     */
    constructor(copyFrom: org.json.JSONObject, names: Array<String?>) : this() {
        try {
            names.filterNotNull()
                .associateWith { name -> copyFrom.opt(name) }
                .filterValues { it != null }
                .forEach { (name, value) -> super.put(name, value) }
        } catch (e: org.json.JSONException) {
            throw JSONException(e)
        }
    }

    // original code from https://github.com/stleary/JSON-java/blob/master/JSONObject.java
    // super() must be first instruction, thus it can't be in a try, and the exception can't be caught
    constructor(x: JSONTokener) : this() {
        try {
            var c: Char
            var key: String
            if (x.nextClean() != '{') {
                throw x.syntaxError("A JSONObject text must begin with '{'")
            }
            while (true) {
                c = x.nextClean()
                key = when (c) {
                    '\u0000' -> throw x.syntaxError("A JSONObject text must end with '}'")
                    '}' -> return
                    else -> {
                        x.back()
                        x.nextValue().toString()
                    }
                }

                // The key is followed by ':'.
                c = x.nextClean()
                if (c != ':') {
                    throw x.syntaxError("Expected a ':' after a key")
                }

                // Use syntaxError(..) to include error location
                // Check if key exists
                if (this.opt(key) != null) {
                    // key already exists
                    throw x.syntaxError("Duplicate key \"$key\"")
                }
                // Only add value if non-null
                val value = x.nextValue()
                this.put(key, value)
                when (x.nextClean()) {
                    ';', ',' -> {
                        if (x.nextClean() == '}') {
                            return
                        }
                        x.back()
                    }
                    '}' -> return
                    else -> throw x.syntaxError("Expected a ',' or '}'")
                }
            }
        } catch (e: org.json.JSONException) {
            throw JSONException(e)
        }
    }

    constructor(source: String) : this(JSONTokener(source))
    constructor(copyFrom: JSONObject) : this() {
        copyFrom.keys()
            .forEach { put(it, copyFrom[it]) }
    }

    /**
     * Iters on the keys. (Similar to iteration in Python's dictionary.
     */
    override fun iterator(): MutableIterator<String> = keys()

    override fun put(name: String, value: Boolean): JSONObject = try {
        super.put(name, value)
        this
    } catch (e: org.json.JSONException) {
        throw JSONException(e)
    }

    override fun put(name: String, value: Double): JSONObject = try {
        super.put(name, value)
        this
    } catch (e: org.json.JSONException) {
        throw JSONException(e)
    }

    override fun put(name: String, value: Int): JSONObject = try {
        super.put(name, value)
        this
    } catch (e: org.json.JSONException) {
        throw JSONException(e)
    }

    override fun put(name: String, value: Long): JSONObject = try {
        super.put(name, value)
        this
    } catch (e: org.json.JSONException) {
        throw JSONException(e)
    }

    final override fun put(name: String, value: Any?): JSONObject = try {
        super.put(name, value)
        this
    } catch (e: org.json.JSONException) {
        throw JSONException(e)
    }

    override fun putOpt(name: String?, value: Any?): JSONObject = try {
        super.putOpt(name, value)
        this
    } catch (e: org.json.JSONException) {
        throw JSONException(e)
    }

    /**
     * Appends [value] to the array already mapped to [name]. If
     * this object has no mapping for [name], this inserts a new mapping.
     * If the mapping exists but its value is not an array, the existing
     * and new values are inserted in order into a new array which is itself
     * mapped to [name]. In aggregate, this allows values to be added to a
     * mapping one at a time.
     *
     *
     *  Note that [append(String, Object)] provides better semantics.
     * In particular, the mapping for [name] will **always** be a
     * [JSONArray]. Using [accumulate] will result in either a
     * [JSONArray] or a mapping whose type is the type of [value]
     * depending on the number of calls to it.
     *
     * @param value a [JSONObject], [JSONArray], String, Boolean,
     * Integer, Long, Double, [NULL] or null. May not be [NaNs][Double.isNaN] or [infinities][Double.isInfinite].
     */
    // TODO: Change {@code append) to {@link #append} when append is
    // unhidden.
    // Copied from upstream.
    // It must be copied, otherwise it adds a org.json.JSONArray instead of a JSONArray
    // in the object
    override fun accumulate(name: String, value: Any?): JSONObject {
        val current = opt(name)
            ?: return put(name, value!!)

        // check in accumulate, since array.put(Object) doesn't do any checking
        if (value is Number) {
            checkDouble(value.toDouble())
        }
        if (current is JSONArray) {
            current.put(value)
        } else {
            val array = JSONArray().apply {
                put(current)
                put(value)
            }
            put(name, array)
        }
        return this
    }

    @CheckResult
    override fun get(name: String): Any = try {
        super.get(name)
    } catch (e: org.json.JSONException) {
        throw JSONException(e)
    }

    @CheckResult
    override fun getBoolean(name: String): Boolean = try {
        super.getBoolean(name)
    } catch (e: org.json.JSONException) {
        throw JSONException(e)
    }

    @CheckResult
    override fun getDouble(name: String): Double = try {
        super.getDouble(name)
    } catch (e: org.json.JSONException) {
        throw JSONException(e)
    }

    @CheckResult
    override fun getInt(name: String): Int = try {
        super.getInt(name)
    } catch (e: org.json.JSONException) {
        throw JSONException(e)
    }

    @CheckResult
    override fun getLong(name: String): Long = try {
        super.getLong(name)
    } catch (e: org.json.JSONException) {
        throw JSONException(e)
    }

    @CheckResult
    override fun getString(name: String): String = try {
        super.getString(name)
    } catch (e: org.json.JSONException) {
        throw JSONException(e)
    }

    @CheckResult
    override fun getJSONArray(name: String): JSONArray = try {
        JSONArray.arrayToArray(super.getJSONArray(name))!!
    } catch (e: org.json.JSONException) {
        throw JSONException(e)
    }

    @CheckResult
    override fun getJSONObject(name: String): JSONObject = try {
        objectToObject(super.getJSONObject(name))
    } catch (e: org.json.JSONException) {
        throw JSONException(e)
    }

    // Code copied from upstream
    @CheckResult
    fun toJSONArray(names: JSONArray?): JSONArray? = names
        ?.takeIf { it.length() > 0 }
        ?.let {
            JSONArray().apply {
                List(names.length()) { toString(names.opt(it)) }
                    .forEach { name -> put(opt(name)) }
            }
        }

    @CheckResult
    override fun names(): JSONArray? = super.names()
        ?.let { JSONArray(it) }

    @CheckResult
    override fun optJSONArray(name: String?): JSONArray? =
        JSONArray.arrayToArray(super.optJSONArray(name))

    @CheckResult
    override fun optJSONObject(name: String?): JSONObject? =
        super.optJSONObject(name)?.let { objectToObject(it) }

    @CheckResult
    open fun deepClone(): JSONObject = deepClonedInto(JSONObject())

    /** deep clone this into clone.
     *
     * Given a subtype [T] of JSONObject, and a JSONObject [clone], we could do
     * ```
     * T t = new T();
     * clone.deepClonedInto(t);
     * ```
     * in order to obtain a deep clone of [clone] of type [T].  */
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    fun <T : JSONObject> deepClonedInto(clone: T): T {
        for (key in this) {
            if (get(key) is JSONObject) {
                clone.put(key, getJSONObject(key).deepClone())
            } else if (get(key) is JSONArray) {
                clone.put(key, getJSONArray(key).deepClone())
            } else {
                clone.put(key, get(key))
            }
        }
        return clone
    }

    override fun toString(indentSpace: Int): String = try {
        super.toString(indentSpace)
    } catch (e: org.json.JSONException) {
        throw JSONException(e)
    }

    companion object {
        val NULL: Any? = org.json.JSONObject.NULL

        /**
         * Change type from JSONObject to JSONObject.
         *
         * Assuming the whole code use only JSONObject, JSONArray and JSONTokener,
         * there should be no instance of JSONObject or JSONArray which is not a JSONObject or JSONArray.
         *
         * In theory, it would be easy to create a JSONObject similar to a JSONObject. It would suffices to iterate over key and add them here. But this would create two distinct objects, and update here would not be reflected in the initial object. So we must avoid this method.
         * Since the actual map in JSONObject is private, the child class can't edit it directly and can't access it. Which means that there is no easy way to create a JSONObject with the same underlying map. Unless the JSONObject was saved in a variable here. Which would entirely defeat the purpose of inheritance.
         *
         * @param obj A json object
         * @return Exactly the same object, with a different type.
         */
        fun objectToObject(obj: org.json.JSONObject?): JSONObject = obj as JSONObject

        @CheckResult
        fun numberToString(number: Number): String = try {
            org.json.JSONObject.numberToString(number)
        } catch (e: org.json.JSONException) {
            throw JSONException(e)
        }

        fun fromMap(map: Map<String, Boolean>): JSONObject = JSONObject().apply {
            map.forEach { (k, v) -> put(k, v) }
        }
    }
}
