/****************************************************************************************
 * Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
 * Copyright (c) 2014 Houssam Salem <houssam.salem.au@gmail.com>                        *
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

import android.net.Uri
import android.text.TextUtils
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.exception.MediaSyncException
import com.ichi2.anki.exception.UnknownHttpResponseException
import com.ichi2.anki.web.CustomSyncServer.getMediaSyncUrl
import com.ichi2.async.Connection
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Utils
import com.ichi2.utils.HashUtil.HashMapInit
import com.ichi2.utils.JSONArray
import com.ichi2.utils.JSONObject
import com.ichi2.utils.KotlinCleanup
import com.ichi2.utils.VersionUtils.pkgVersionName
import okhttp3.Response
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.*
import java.util.zip.ZipFile

@KotlinCleanup("fix IDE lint issues")
class RemoteMediaServer(
    private val col: Collection?,
    hkey: String?,
    con: Connection?,
    hostNum: HostNum?
) : HttpSyncer(hkey, con, hostNum) {

    override fun syncURL(): String {
        // Allow user to specify custom sync server
        val userPreferences = AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance())
        if (isUsingCustomSyncServer(userPreferences)) {
            val mediaSyncBase = getMediaSyncUrl(userPreferences) ?: return defaultAnkiWebUrl
            // Note: the preference did not necessarily contain /msync/, so we can't concat with the default as done in
            // getDefaultAnkiWebUrl
            return Uri.parse(mediaSyncBase).toString()
        }
        // Usual case
        return defaultAnkiWebUrl
    }

    @Throws(UnknownHttpResponseException::class, MediaSyncException::class)
    fun begin(): JSONObject {
        return try {
            mPostVars = HashMapInit(2)
            mPostVars["k"] = mHKey
            mPostVars["v"] = String.format(Locale.US, "ankidroid,%s,%s", pkgVersionName, Utils.platDesc())
            val resp = super.req("begin", getInputStream(Utils.jsonToString(JSONObject())))
            val jresp = JSONObject(resp.body!!.string())
            val ret = _dataOnly(jresp, JSONObject::class.java)
            mSKey = ret.getString("sk")
            ret
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    // args: lastUsn
    @Throws(UnknownHttpResponseException::class, MediaSyncException::class)
    fun mediaChanges(lastUsn: Int): JSONArray {
        return try {
            mPostVars = HashMapInit(1)
            mPostVars["sk"] = mSKey
            val resp = super.req(
                "mediaChanges",
                getInputStream(Utils.jsonToString(JSONObject().put("lastUsn", lastUsn)))
            )
            val jresp = JSONObject(resp.body!!.string())
            _dataOnly(jresp, JSONArray::class.java)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    /**
     * args: files
     * This method returns a ZipFile with the OPEN_DELETE flag, ensuring that the file on disk will
     * be automatically deleted when the stream is closed.
     */
    @Throws(UnknownHttpResponseException::class)
    fun downloadFiles(top: List<String?>?): ZipFile {
        var resp: Response? = null
        return try {
            resp = super.req(
                "downloadFiles",
                getInputStream(Utils.jsonToString(JSONObject().put("files", JSONArray(top))))
            )
            val zipPath = col!!.path.replaceFirst("collection\\.anki2$".toRegex(), "tmpSyncFromServer.zip")
            // retrieve contents and save to file on disk:
            super.writeToFile(resp.body!!.byteStream(), zipPath)
            ZipFile(File(zipPath), ZipFile.OPEN_READ or ZipFile.OPEN_DELETE)
        } catch (e: IOException) {
            Timber.e(e, "Failed to download requested media files")
            throw RuntimeException(e)
        } catch (e: NullPointerException) {
            Timber.e(e, "Failed to download requested media files")
            throw RuntimeException(e)
        } finally {
            @KotlinCleanup("resp?.body?.close()")
            if (resp != null && resp.body != null) {
                resp.body!!.close()
            }
        }
    }

    @Throws(UnknownHttpResponseException::class, MediaSyncException::class)
    fun uploadChanges(zip: File?): JSONArray {
        return try {
            // no compression, as we compress the zip file instead
            val resp = super.req("uploadChanges", FileInputStream(zip), 0)
            val jresp = JSONObject(resp.body!!.string())
            _dataOnly(jresp, JSONArray::class.java)
        } catch (e: IOException) {
            throw RuntimeException(e)
        } catch (e: NullPointerException) {
            throw RuntimeException(e)
        }
    }

    // args: local
    @Throws(UnknownHttpResponseException::class, MediaSyncException::class)
    fun mediaSanity(lcnt: Int): String {
        return try {
            val resp = super.req(
                "mediaSanity",
                getInputStream(Utils.jsonToString(JSONObject().put("local", lcnt)))
            )
            val jresp = JSONObject(resp.body!!.string())
            _dataOnly(jresp, String::class.java)
        } catch (e: IOException) {
            throw RuntimeException(e)
        } catch (e: NullPointerException) {
            throw RuntimeException(e)
        }
    }

    /**
     * Returns the "data" element from the JSON response from the server, or throws an exception if there is a value in
     * the "err" element.
     *
     *
     * The python counterpart to this method is flexible with type coercion; the type of object returned is decided by
     * the content of the "data" element, and there are several such types in the various server responses. Java
     * requires us to specifically choose a type to convert to, so we need an additional parameter (returnType) to
     * specify the type we expect.
     *
     * @param resp The JSON response from the server
     * @param returnType The type to coerce the 'data' element to.
     * @return The "data" element from the HTTP response from the server. The type of object returned is determined by
     * returnType.
     */
    @Suppress("UNCHECKED_CAST")
    @Throws(MediaSyncException::class)
    private fun <T> _dataOnly(resp: JSONObject, returnType: Class<T>): T {
        if (!TextUtils.isEmpty(resp.optString("err"))) {
            val err = resp.getString("err")
            col?.log("error returned: $err")
            throw MediaSyncException("SyncError:$err")
        }
        @KotlinCleanup("replace if with when")
        if (returnType == String::class.java) {
            return resp.getString("data") as T
        } else if (returnType == JSONObject::class.java) {
            return resp.getJSONObject("data") as T
        } else if (returnType == JSONArray::class.java) {
            return resp.getJSONArray("data") as T
        }
        throw RuntimeException("Did not specify a valid type for the 'data' element in response")
    }

    // Difference from libAnki: we allow a custom URL to specify a different prefix, so this is only used with the
    // default URL
    override fun getUrlPrefix(): String {
        return "msync"
    }
}
