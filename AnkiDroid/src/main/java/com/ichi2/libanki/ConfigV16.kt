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

import com.ichi2.libanki.backend.RustConfigBackend
import org.json.JSONArray
import org.json.JSONObject

class ConfigV16(val backend: RustConfigBackend) : ConfigManager() {

    override fun has(key: String): Boolean = backend.has(key)

    override fun isNull(key: String): Boolean {
        return backend.not_has_or_is_null(key)
    }

    override fun getString(key: String): String {
        val getString = backend.get_string(key)
        // remove the quotes
        return getString.substring(1, getString.length - 1)
    }

    override fun getBoolean(key: String): Boolean {
        return backend.get_string(key).toBoolean()
    }

    override fun getDouble(key: String): Double {
        return backend.get_string(key).toDouble()
    }

    override fun getInt(key: String): Int {
        return backend.get_string(key).toInt()
    }

    override fun getLong(key: String): Long {
        return backend.get_string(key).toLong()
    }

    override fun getJSONArray(key: String): JSONArray {
        return backend.get_array(key)
    }

    override fun getJSONObject(key: String): JSONObject {
        return backend.get_object(key)
    }

    override fun put(key: String, value: Boolean) {
        backend.set(key, value)
    }

    override fun put(key: String, value: Long) {
        backend.set(key, value)
    }

    override fun put(key: String, value: Int) {
        backend.set(key, value)
    }

    override fun put(key: String, value: Double) {
        backend.set(key, value)
    }

    override fun put(key: String, value: String) {
        backend.set(key, "\"" + value + "\"")
    }

    override fun put(key: String, value: JSONArray) {
        backend.set(key, value)
    }

    override fun put(key: String, value: JSONObject) {
        backend.set(key, value)
    }

    override fun put(key: String, value: Any?) {
        backend.set(key, value)
    }

    override fun remove(key: String) {
        backend.remove(key)
    }

    override var json: JSONObject
        get() = backend.getJson() as JSONObject
        set(@Suppress("UNUSED_PARAMETER") value) { TODO("not implemented; use backend syncing") }
}
