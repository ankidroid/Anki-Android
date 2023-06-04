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

package com.ichi2.libanki

import androidx.annotation.CheckResult
import androidx.annotation.WorkerThread
import org.json.JSONArray
import org.json.JSONObject

@WorkerThread
abstract class ConfigManager {
    @CheckResult abstract fun has(key: String): Boolean

    /**
     * Returns true if this object has no mapping for [key]or if it has
     * a mapping whose value is [JSONObject.NULL].
     */
    @CheckResult abstract fun isNull(key: String): Boolean

    @CheckResult abstract fun getString(key: String): String

    @CheckResult abstract fun getBoolean(key: String): Boolean

    @CheckResult abstract fun getDouble(key: String): Double

    @CheckResult abstract fun getInt(key: String): Int

    @CheckResult abstract fun getLong(key: String): Long

    @CheckResult abstract fun getJSONArray(key: String): JSONArray

    @CheckResult abstract fun getJSONObject(key: String): JSONObject

    abstract fun put(key: String, value: Boolean)
    abstract fun put(key: String, value: Long)
    abstract fun put(key: String, value: Int)
    abstract fun put(key: String, value: Double)
    abstract fun put(key: String, value: String)
    abstract fun put(key: String, value: JSONArray)
    abstract fun put(key: String, value: JSONObject)
    abstract fun put(key: String, value: Any?)

    abstract fun remove(key: String)

    abstract var json: JSONObject
}
