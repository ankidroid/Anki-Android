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

import com.google.protobuf.ByteString
import com.ichi2.libanki.utils.LibAnkiAlias
import net.ankiweb.rsdroid.RustCleanup
import org.json.JSONArray
import org.json.JSONObject

object BackendUtils {
    @LibAnkiAlias("from_json_bytes")
    fun fromJsonBytes(json: ByteString): JSONObject {
        return JSONObject(json.toStringUtf8())
    }

    fun jsonToArray(json: ByteString): JSONArray {
        return JSONArray(json.toStringUtf8())
    }

    @RustCleanup("Confirm edge cases")
    fun toByteString(conf: Any?): ByteString {
        val asString: String = conf.toString()
        return ByteString.copyFromUtf8(asString)
    }

    @RustCleanup("Confirm edge cases")
    @LibAnkiAlias("to_json_bytes")
    fun toJsonBytes(json: Any?): ByteString = toByteString(json)
}
