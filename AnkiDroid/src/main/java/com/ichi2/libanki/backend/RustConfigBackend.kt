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

package com.ichi2.libanki.backend

import BackendProto.Backend
import com.ichi2.libanki.backend.BackendUtils.from_json_bytes
import com.ichi2.libanki.backend.BackendUtils.to_json_bytes
import com.ichi2.libanki.str
import com.ichi2.utils.JSONArray
import com.ichi2.utils.JSONObject
import net.ankiweb.rsdroid.BackendV1
import net.ankiweb.rsdroid.exceptions.BackendNotFoundException

class RustConfigBackend(private val backend: BackendV1) {

    fun getJson(): Any {
        return from_json_bytes(backend.allConfig)
    }

    fun setJson(value: JSONObject) {
        val builder = Backend.Json.newBuilder()
        builder.json = to_json_bytes(value)
        backend.allConfig = builder.build()
    }

    fun get_string(key: str): String {
        try {
            return BackendUtils.jsonToString(backend.getConfigJson(key))
        } catch (ex: BackendNotFoundException) {
            throw IllegalStateException("'$key' not found", ex)
        }
    }

    fun get_array(key: str): JSONArray {
        try {
            return BackendUtils.jsonToArray(backend.getConfigJson(key))
        } catch (ex: BackendNotFoundException) {
            throw IllegalStateException("'$key' not found", ex)
        }
    }

    fun get_object(key: str): JSONObject {
        try {
            return from_json_bytes(backend.getConfigJson(key))
        } catch (ex: BackendNotFoundException) {
            throw IllegalStateException("'$key' not found", ex)
        }
    }

    fun set(key: str, value: Any) {
        backend.setConfigJson(key, to_json_bytes(value))
    }

    fun remove(key: str) {
        backend.removeConfig(key)
    }

    fun has(key: str): Boolean {
        return try {
            this.get_string(key)
            true
        } catch (ex: IllegalStateException) {
            false
        }
    }

    fun not_has_or_is_null(key: str): Boolean {
        return try {
            val ret = this.get_string(key)
            return ret == "null"
        } catch (ex: IllegalStateException) {
            true
        }
    }
}
