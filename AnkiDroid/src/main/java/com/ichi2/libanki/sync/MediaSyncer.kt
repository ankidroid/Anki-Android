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

import android.database.SQLException
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.anki.exception.MediaSyncException
import com.ichi2.anki.exception.UnknownHttpResponseException
import com.ichi2.async.Connection
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Consts
import com.ichi2.libanki.sync.Syncer.ConnectionResultType
import timber.log.Timber
import java.io.IOException
import java.util.*

/**
 * About conflicts:
 * - to minimize data loss, if both sides are marked for sending and one
 * side has been deleted, favour the add
 * - if added/changed on both sides, favour the server version on the
 * assumption other syncers are in sync with the server
 *
 * A note about differences to the original python version of this class. We found that:
 * 1 - There is no reliable way to detect changes to the media directory on Android due to the
 * file systems used (mainly FAT32 for SD Cards) and the utilities available to probe them.
 * 2 - Scanning for media changes can take a very long time with thousands of files.
 *
 * Given these two points, we have decided to avoid the call to findChanges() on every sync and
 * only do it on the first sync to build the initial database. Changes to the media collection
 * made through AnkiDroid (e.g., multimedia note editor, media check) are recorded directly in
 * the media database as they are made. This allows us to skip finding media changes entirely
 * as the database already contains the changes.
 *
 * The downside to this approach is that changes made to the media directory externally (e.g.,
 * through a file manager) will not be recorded and will not be synced. In this case, the user
 * must issue a media check command through the UI to bring the database up-to-date.
 */
class MediaSyncer(
    private val col: Collection,
    private val server: RemoteMediaServer, // Needed to update progress to UI
    private val con: Connection
) {
    private var mDownloadCount = 0
    private var mUploadCount = 0

    fun getDownloadCount(): Int {
        return mDownloadCount
    }
    fun getUploadCount(): Int {
        return mUploadCount
    }

    // Returned string may be null. ConnectionResultType and Pair are not null
    @Throws(UnknownHttpResponseException::class, MediaSyncException::class)
    fun sync(): Pair<ConnectionResultType, String?> {
        // check if there have been any changes
        // If we haven't built the media db yet, do so on this sync. See note at the top
        // of this class about this difference to the original.
        if (col.media.needScan()) {
            con.publishProgress(R.string.sync_media_find)
            col.log("findChanges")
            try {
                col.media.findChanges()
            } catch (ignored: SQLException) {
                Timber.w(ignored)
                return Pair(ConnectionResultType.CORRUPT, null)
            }
        }

        // begin session and check if in sync
        var lastUsn = col.media.lastUsn()
        val ret = server.begin()
        val srvUsn = ret.getInt("usn")
        if (lastUsn == srvUsn && !col.media.haveDirty()) {
            return Pair(ConnectionResultType.NO_CHANGES, null)
        }
        // loop through and process changes from server
        col.log("last local usn is $lastUsn")
        mDownloadCount = 0
        while (true) {
            // Allow cancellation (note: media sync has no finish command, so just throw)
            if (Connection.isCancelled) {
                Timber.i("Sync was cancelled")
                throw RuntimeException(ConnectionResultType.USER_ABORTED_SYNC.toString())
            }
            val data = server.mediaChanges(lastUsn)
            col.log("mediaChanges resp count: ", data.length())
            if (data.length() == 0) {
                break
            }
            val need: MutableList<String> = ArrayList(data.length())
            lastUsn = data.getJSONArray(data.length() - 1).getInt(1)
            for (i in 0 until data.length()) {
                // Allow cancellation (note: media sync has no finish command, so just throw)
                if (Connection.isCancelled) {
                    Timber.i("Sync was cancelled")
                    throw RuntimeException(ConnectionResultType.USER_ABORTED_SYNC.toString())
                }
                val fname = data.getJSONArray(i).getString(0)
                val rusn = data.getJSONArray(i).getInt(1)
                var rsum: String? = null
                if (!data.getJSONArray(i).isNull(2)) {
                    // If `rsum` is a JSON `null` value, `.optString(2)` will
                    // return `"null"` as a string
                    rsum = data.getJSONArray(i).optString(2)
                }
                val info = col.media.syncInfo(fname)
                val lsum = info.first
                val ldirty = info.second
                col.log(
                    String.format(
                        Locale.US,
                        "check: lsum=%s rsum=%s ldirty=%d rusn=%d fname=%s",
                        if (lsum.isNullOrEmpty()) "" else lsum.subSequence(0, 4),
                        if (rsum.isNullOrEmpty()) "" else rsum.subSequence(0, 4),
                        ldirty,
                        rusn,
                        fname
                    )
                )
                if (!rsum.isNullOrEmpty()) {
                    // added/changed remotely
                    if (lsum.isNullOrEmpty() || lsum != rsum) {
                        col.log("will fetch")
                        need.add(fname)
                    } else {
                        col.log("have same already")
                    }
                    col.media.markClean(listOf(fname))
                } else if (!lsum.isNullOrEmpty()) {
                    // deleted remotely
                    if (ldirty == 0) {
                        col.log("delete local")
                        col.media.syncDelete(fname)
                    } else {
                        // conflict: local add overrides remote delete
                        col.log("conflict; will send")
                    }
                } else {
                    // deleted both sides
                    col.log("both sides deleted")
                    col.media.markClean(listOf(fname))
                }
            }
            _downloadFiles(need)
            col.log("update last usn to $lastUsn")
            col.media.setLastUsn(lastUsn) // commits
        }

        // at this point, we're all up to date with the server's changes,
        // and we need to send our own
        var updateConflict = false
        mUploadCount = 0
        var toSend = col.media.dirtyCount()
        while (true) {
            val changesZip = col.media.mediaChangesZip()
            val zip = changesZip.first
            try {
                val fnames = changesZip.second
                if (fnames.isEmpty()) {
                    break
                }
                con.publishProgress(
                    String.format(
                        AnkiDroidApp.appResources.getString(R.string.sync_media_changes_count),
                        toSend
                    )
                )
                val changes = server.uploadChanges(zip)
                val processedCnt = changes.getInt(0)
                mUploadCount += processedCnt
                val serverLastUsn = changes.getInt(1)
                col.media.markClean(fnames.subList(0, processedCnt))
                col.log(
                    String.format(
                        Locale.US,
                        "processed %d, serverUsn %d, clientUsn %d",
                        processedCnt,
                        serverLastUsn,
                        lastUsn
                    )
                )
                if (serverLastUsn - processedCnt == lastUsn) {
                    col.log("lastUsn in sync, updating local")
                    lastUsn = serverLastUsn
                    col.media.setLastUsn(serverLastUsn) // commits
                } else {
                    col.log("concurrent update, skipping usn update")
                    // commit for markClean
                    col.media.db!!.commit()
                    updateConflict = true
                }
                toSend -= processedCnt
            } finally {
                zip.delete()
            }
        }
        if (updateConflict) {
            col.log("restart sync due to concurrent update")
            return sync()
        }
        val lcnt = col.media.mediacount()
        val sanityRet = server.mediaSanity(lcnt)
        return if ("OK" == sanityRet) {
            Pair(ConnectionResultType.OK, null)
        } else {
            col.media.forceResync()
            Pair(ConnectionResultType.ARBITRARY_STRING, sanityRet)
        }
    }

    private fun _downloadFiles(_fnames: MutableList<String>) {
        var fnames = _fnames
        col.log(fnames.size.toString() + " files to fetch")
        while (!fnames.isEmpty()) {
            try {
                val top: List<String> = fnames.subList(0, Math.min(fnames.size, Consts.SYNC_MAX_FILES))
                col.log("fetch $top")
                val zipData = server.downloadFiles(top)
                val cnt = col.media.addFilesFromZip(zipData)
                mDownloadCount += cnt
                col.log("received $cnt files")
                // NOTE: The python version uses slices which return an empty list when indexed beyond what
                // the list contains. Since we can't slice out an empty sublist in Java, we must check
                // if we've reached the end and clear the fnames list manually.
                if (cnt == fnames.size) {
                    fnames.clear()
                } else {
                    fnames = fnames.subList(cnt, fnames.size)
                }
                con.publishProgress(
                    String.format(
                        AnkiDroidApp.appResources.getString(R.string.sync_media_downloaded_count),
                        mDownloadCount
                    )
                )
            } catch (e: IOException) {
                Timber.e(e, "Error downloading media files")
                throw RuntimeException(e)
            } catch (e: UnknownHttpResponseException) {
                Timber.e(e, "Error downloading media files")
                throw RuntimeException(e)
            }
        }
    }
}
