/***************************************************************************************
 * Copyright (c) 2012 Norbert Nagold <norbert.nagold@gmail.com>                         *
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

import android.database.sqlite.SQLiteDatabaseCorruptException
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.R
import com.ichi2.anki.exception.UnknownHttpResponseException
import com.ichi2.async.Connection
import com.ichi2.libanki.Collection
import com.ichi2.libanki.DB
import com.ichi2.libanki.Utils
import com.ichi2.libanki.sync.Syncer.ConnectionResultType
import com.ichi2.libanki.sync.Syncer.ConnectionResultType.ARBITRARY_STRING
import com.ichi2.libanki.sync.Syncer.ConnectionResultType.OVERWRITE_ERROR
import com.ichi2.libanki.sync.Syncer.ConnectionResultType.SUCCESS
import com.ichi2.utils.HashUtil
import com.ichi2.utils.KotlinCleanup
import com.ichi2.utils.VersionUtils.pkgVersionName
import okhttp3.Response
import okhttp3.ResponseBody
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.net.SocketException
import java.util.Locale

class FullSyncer(col: Collection?, hkey: String?, con: Connection, hostNum: HostNum?) :
    HttpSyncer(hkey, con, hostNum!!) {
    private var mCol: Collection?
    private val mCon: Connection

    init {
        postVars = HashUtil.HashMapInit(2)
        postVars["k"] = hkey
        postVars["v"] = String.format(Locale.US, "ankidroid,%s,%s", pkgVersionName, Utils.platDesc())
        @KotlinCleanup("move to constructor")
        mCol = col
        mCon = con
    }

    @Throws(UnknownHttpResponseException::class)
    fun download(): ConnectionResultType? {
        val cont: InputStream
        var body: ResponseBody? = null
        try {
            val ret = req("download")
            if (ret.body == null) {
                return null
            }
            body = ret.body
            cont = body!!.byteStream()
        } catch (e1: IllegalArgumentException) {
            body?.close()
            throw RuntimeException(e1)
        }
        val path: String
        if (mCol != null) {
            Timber.i("Closing collection for full sync")
            // Usual case where collection is non-null
            path = mCol!!.path
            mCol!!.close()
            mCol = null
        } else {
            // Allow for case where collection is completely unreadable
            Timber.w("Collection was unexpectedly null when doing full sync download")
            path = CollectionHelper.getCollectionPath(AnkiDroidApp.instance)
        }
        val tpath = "$path.tmp"
        try {
            super.writeToFile(cont, tpath)
            Timber.d("Full Sync - Downloaded temp file")
            val fis = FileInputStream(tpath)
            if ("upgradeRequired" == super.stream2String(fis, 15)) {
                Timber.w("Full Sync - 'Upgrade Required' message received")
                return ConnectionResultType.UPGRADE_REQUIRED
            }
        } catch (e: FileNotFoundException) {
            Timber.e(e, "Failed to create temp file when downloading collection.")
            throw RuntimeException(e)
        } catch (e: SocketException) {
            Timber.e(e, "Full sync failed to download collection.")
            return ConnectionResultType.SOCKET_ERROR
        } catch (e: IOException) {
            Timber.e(e, "Full sync failed to save or download collection.")
            return ConnectionResultType.SD_ACCESS_ERROR
        } finally {
            body.close()
        }

        // check the received file is ok
        mCon.publishProgress(R.string.sync_check_download_file)
        var tempDb: DB? = null
        try {
            tempDb = DB.withAndroidFramework(AnkiDroidApp.instance, tpath)
            if (!"ok".equals(tempDb.queryString("PRAGMA integrity_check"), ignoreCase = true)) {
                Timber.e("Full sync - downloaded file corrupt")
                return ConnectionResultType.REMOTE_DB_ERROR
            }
        } catch (e: SQLiteDatabaseCorruptException) {
            Timber.e("Full sync - downloaded file corrupt")
            return ConnectionResultType.REMOTE_DB_ERROR
        } finally {
            tempDb?.close()
        }
        Timber.d("Full Sync: Downloaded file was not corrupt")
        // overwrite existing collection
        val newFile = File(tpath)
        return if (newFile.renameTo(File(path))) {
            Timber.i("Full Sync Success: Overwritten collection with downloaded file")
            SUCCESS
        } else {
            Timber.w("Full Sync: Error overwriting collection with downloaded file")
            OVERWRITE_ERROR
        }
    }

    @Throws(UnknownHttpResponseException::class)
    fun upload(): Pair<ConnectionResultType, Array<Any?>?>? {
        // make sure it's ok before we try to upload
        mCon.publishProgress(R.string.sync_check_upload_file)
        if (!"ok".equals(mCol!!.db.queryString("PRAGMA integrity_check"), ignoreCase = true)) {
            return Pair(ConnectionResultType.DB_ERROR, null)
        }
        if (!mCol!!.basicCheck()) {
            return Pair(ConnectionResultType.DB_ERROR, null)
        }
        // apply some adjustments, then upload
        mCol!!.beforeUpload()
        val filePath = mCol!!.path
        val ret: Response
        mCon.publishProgress(R.string.sync_uploading_message)
        return try {
            ret = req("upload", FileInputStream(filePath))
            if (ret.body == null) {
                return null
            }
            val status = ret.code
            if (status != 200) {
                // error occurred
                Pair(ConnectionResultType.ERROR, arrayOf(status, ret.message))
            } else {
                Pair(ARBITRARY_STRING, arrayOf(ret.body!!.string()))
            }
        } catch (e: IllegalStateException) {
            throw RuntimeException(e)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }
}
