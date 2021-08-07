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

@file:Suppress("FunctionName")

package com.ichi2.libanki.backend

import BackendProto.Backend
import com.google.protobuf.ByteString
import com.ichi2.utils.JSONObject
import java.io.UnsupportedEncodingException

object BackendUtils {
    fun from_json_bytes(json: Backend.Json): JSONObject {
        val str = jsonToString(json)
        return JSONObject(str)
    }

    fun jsonToString(json: Backend.Json): String {
        return try {
            json.json.toString("UTF-8")
        } catch (e: UnsupportedEncodingException) {
            throw IllegalStateException("Could not deserialize JSON", e)
        }
    }

    fun toByteString(conf: JSONObject): ByteString {
        val asString: String = conf.toString()
        return ByteString.copyFromUtf8(asString)
    }

    fun to_json_bytes(json: JSONObject): ByteString = toByteString(json)
}
