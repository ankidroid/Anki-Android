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

import com.google.protobuf.ByteString
import com.ichi2.libanki.backend.BackendUtils
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.exceptions.BackendNotFoundException
import org.json.JSONArray
import org.json.JSONObject

class Config(val backend: Backend) {
    fun isNull(key: String): Boolean {
        return try {
            val ret = this.getJsonString(key)
            return ret == "null"
        } catch (ex: IllegalStateException) {
            true
        }
    }

    fun getString(key: String): String {
        val string = getJsonString(key)
        // remove the quotes
        return string.substring(1, string.length - 1)
    }

    fun getBoolean(key: String): Boolean {
        return getJsonString(key).toBoolean()
    }

    fun getDouble(key: String): Double {
        return getJsonString(key).toDouble()
    }

    fun getInt(key: String): Int {
        return getJsonString(key).toInt()
    }

    fun getLong(key: String): Long {
        return getJsonString(key).toLong()
    }

    fun getJSONArray(key: String): JSONArray {
        return BackendUtils.jsonToArray(getJsonBytes(key))
    }

    fun getJSONObject(key: String): JSONObject {
        return BackendUtils.from_json_bytes(getJsonBytes(key))
    }

    fun put(key: String, value: Boolean) {
        set(key, value)
    }

    fun put(key: String, value: Long) {
        set(key, value)
    }

    fun put(key: String, value: Int) {
        set(key, value)
    }

    fun put(key: String, value: Double) {
        set(key, value)
    }

    fun put(key: String, value: String) {
        set(key, "\"" + value + "\"")
    }

    fun put(key: String, value: JSONArray) {
        set(key, value)
    }

    fun put(key: String, value: JSONObject) {
        set(key, value)
    }

    fun put(key: String, value: Any?) {
        set(key, value)
    }

    // / True if key exists (even if null)
    fun has(key: String): Boolean {
        return try {
            getJsonBytes(key)
            true
        } catch (ex: java.lang.IllegalStateException) {
            false
        }
    }

    fun remove(key: str) {
        backend.removeConfig(key)
    }

    private fun getJsonBytes(key: str): ByteString {
        try {
            return backend.getConfigJson(key)
        } catch (ex: BackendNotFoundException) {
            throw IllegalStateException("'$key' not found", ex)
        }
    }

    private fun getJsonString(key: str): String {
        return BackendUtils.jsonToString(getJsonBytes(key))
    }

    private fun set(key: str, value: Any?) {
        backend.setConfigJson(key, BackendUtils.to_json_bytes(value), false)
    }
}
