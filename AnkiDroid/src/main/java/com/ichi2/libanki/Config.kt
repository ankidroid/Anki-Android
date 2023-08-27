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
import com.ichi2.utils.deepClone
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.exceptions.BackendNotFoundException
import org.jetbrains.annotations.Contract
import org.json.JSONArray
import org.json.JSONException
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

    fun has_config_not_null(key: String): Boolean {
        // not in libAnki
        return has(key) && !isNull(key)
    }

    @Contract("_, !null -> !null")
    fun get(key: String, defaultValue: Boolean?): Boolean? {
        return if (isNull(key)) {
            defaultValue
        } else {
            getBoolean(key)
        }
    }

    @Contract("_, !null -> !null")
    fun get(key: String, defaultValue: Long?): Long? {
        return if (isNull(key)) {
            defaultValue
        } else {
            getLong(key)
        }
    }

    @Contract("_, !null -> !null")
    fun get(key: String, defaultValue: Int?): Int? {
        return if (isNull(key)) {
            defaultValue
        } else {
            getInt(key)
        }
    }

    @Contract("_, !null -> !null")
    fun get(key: String, defaultValue: Double?): Double? {
        return if (isNull(key)) {
            defaultValue
        } else {
            getDouble(key)
        }
    }

    @Contract("_, !null -> !null")
    fun get(key: String, defaultValue: String?): String? {
        return if (isNull(key)) {
            defaultValue
        } else {
            getString(key)
        }
    }

    /** Edits to the config are not persisted to the preferences  */
    @Contract("_, !null -> !null")
    fun get(key: String, defaultValue: JSONObject?): JSONObject? {
        return if (isNull(key)) {
            if (defaultValue == null) null else defaultValue.deepClone()
        } else {
            getJSONObject(key).deepClone()
        }
    }

    /** Edits to the array are not persisted to the preferences  */
    @Contract("_, !null -> !null")
    fun get(key: String, defaultValue: JSONArray?): JSONArray? {
        return if (isNull(key)) {
            if (defaultValue == null) null else JSONArray(defaultValue)
        } else {
            JSONArray(getJSONArray(key))
        }
    }

    /**
     * If the value is null in the JSON, a string of "null" will be returned
     * @throws JSONException object does not exist, or can't be cast
     */
    fun getString(key: String): String {
        val string = getJsonString(key)
        if (string == "null") {
            return string
        } else {
            // remove the quotes
            return string.substring(1, string.length - 1)
        }
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

    fun set(key: str, value: Any?) {
        val adjustedValue = if (value is String) {
            "\"" + value + "\""
        } else {
            value
        }
        backend.setConfigJson(key, BackendUtils.to_json_bytes(adjustedValue), false)
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
}
