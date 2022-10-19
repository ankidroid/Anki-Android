/***************************************************************************************
 * Copyright (c) 2012 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>                          *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.libanki.sync

import com.ichi2.anki.exception.UnknownHttpResponseException
import com.ichi2.async.Connection
import com.ichi2.libanki.Consts
import com.ichi2.libanki.Utils
import com.ichi2.utils.HashUtil.HashMapInit
import com.ichi2.utils.VersionUtils.pkgVersionName
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.util.*

class RemoteServer(
    con: Connection,
    hkey: String?,
    hostNum: HostNum
) : HttpSyncer(hkey, con, hostNum) {

    /** Returns hkey or null if user/pw incorrect.  */
    @Throws(UnknownHttpResponseException::class)
    fun hostKey(user: String?, pw: String?): Response? {
        return try {
            postVars = HashMapInit(0)
            val credentials = JSONObject()
            credentials.put("u", user)
            credentials.put("p", pw)
            req("hostKey", getInputStream(Utils.jsonToString(credentials)))
        } catch (e: JSONException) {
            Timber.w(e)
            null
        }
    }

    @Throws(UnknownHttpResponseException::class)
    fun meta(): Response {
        postVars = HashMapInit(2)
        postVars["k"] = hKey
        postVars["s"] = checksumKey
        val meta = JSONObject()
        meta.put("v", Consts.SYNC_VER)
        meta.put("cv", String.format(Locale.US, "ankidroid,%s,%s", pkgVersionName, Utils.platDesc()))
        return req("meta", getInputStream(Utils.jsonToString(meta)))
    }

    @Throws(UnknownHttpResponseException::class)
    fun applyChanges(kw: JSONObject): JSONObject {
        return parseDict(runCommand("applyChanges", kw))
    }

    @Throws(UnknownHttpResponseException::class)
    fun start(kw: JSONObject): JSONObject {
        return parseDict(runCommand("start", kw))
    }

    @Throws(UnknownHttpResponseException::class)
    fun chunk(): JSONObject {
        val co = JSONObject()
        return parseDict(runCommand("chunk", co))
    }

    @Throws(UnknownHttpResponseException::class)
    fun applyChunk(chunk: JSONObject) {
        runCommand("applyChunk", chunk)
    }

    @Throws(UnknownHttpResponseException::class)
    fun sanityCheck2(client: JSONObject): JSONObject {
        return parseDict(runCommand("sanityCheck2", client))
    }

    @Throws(UnknownHttpResponseException::class)
    fun finish(): Long {
        return parseLong(runCommand("finish", JSONObject()))
    }

    @Throws(UnknownHttpResponseException::class)
    fun abort() {
        runCommand("abort", JSONObject())
    }

    /** Python has dynamic type deduction, but we don't, so return String  */
    @Throws(UnknownHttpResponseException::class)
    private fun runCommand(cmd: String, data: JSONObject): String {
        val ret = req(cmd, getInputStream(Utils.jsonToString(data)))
        return try {
            ret.body!!.string()
        } catch (e: IllegalStateException) {
            throw RuntimeException(e)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    /** Note: these conversion helpers aren't needed in libanki as type deduction occurs automatically there  */
    private fun parseDict(s: String): JSONObject {
        return if (!"null".equals(s, ignoreCase = true) && s.isNotEmpty()) {
            JSONObject(s)
        } else {
            JSONObject()
        }
    }

    private fun parseLong(s: String): Long {
        return try {
            s.toLong()
        } catch (e: NumberFormatException) {
            Timber.w(e)
            0
        }
    }
}
