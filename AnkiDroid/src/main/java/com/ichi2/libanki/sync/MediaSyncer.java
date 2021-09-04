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

package com.ichi2.libanki.sync;

import android.database.SQLException;
import android.text.TextUtils;
import android.util.Pair;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;
import com.ichi2.anki.exception.MediaSyncException;
import com.ichi2.anki.exception.UnknownHttpResponseException;
import com.ichi2.async.Connection;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Consts;

import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipFile;

import timber.log.Timber;

import static com.ichi2.libanki.sync.Syncer.ConnectionResultType;
import static com.ichi2.libanki.sync.Syncer.ConnectionResultType.*;

/**
 * About conflicts:
 * - to minimize data loss, if both sides are marked for sending and one
 *   side has been deleted, favour the add
 * - if added/changed on both sides, favour the server version on the
 *   assumption other syncers are in sync with the server
 * 
 * A note about differences to the original python version of this class. We found that:
 *  1 - There is no reliable way to detect changes to the media directory on Android due to the
 *      file systems used (mainly FAT32 for sdcards) and the utilities available to probe them.
 *  2 - Scanning for media changes can take a very long time with thousands of files.
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
@SuppressWarnings({"PMD.AvoidThrowingRawExceptionTypes","PMD.AvoidReassigningParameters",
        "PMD.NPathComplexity","PMD.MethodNamingConventions","PMD.ExcessiveMethodLength","PMD.OneDeclarationPerLine",
        "PMD.SwitchStmtsShouldHaveDefault","PMD.EmptyIfStmt","PMD.SimplifyBooleanReturns","PMD.CollapsibleIfStatements"})
public class MediaSyncer {
    private final Collection mCol;
    private final RemoteMediaServer mServer;
    private int mDownloadCount;
    // Needed to update progress to UI
    private final Connection mCon;

    public MediaSyncer(Collection col, RemoteMediaServer server, Connection con) {
        mCol = col;
        mServer = server;
        mCon = con;
    }

    // Returned string may be null. ConnectionResultType and Pair are not null
    public Pair<ConnectionResultType, String> sync() throws UnknownHttpResponseException, MediaSyncException {
            // check if there have been any changes
            // If we haven't built the media db yet, do so on this sync. See note at the top
            // of this class about this difference to the original.
            if (mCol.getMedia().needScan()) {
                mCon.publishProgress(R.string.sync_media_find);
                mCol.log("findChanges");
                try {
                    mCol.getMedia().findChanges();
                } catch (SQLException ignored) {
                    Timber.w(ignored);
                    return new Pair<>(CORRUPT, null);
                }
            }

            // begin session and check if in sync
            int lastUsn = mCol.getMedia().lastUsn();
            JSONObject ret = mServer.begin();
            int srvUsn = ret.getInt("usn");
            if ((lastUsn == srvUsn) && !(mCol.getMedia().haveDirty())) {
                return new Pair<>(NO_CHANGES, null);
            }
            // loop through and process changes from server
            mCol.log("last local usn is " + lastUsn);
            mDownloadCount = 0;
            while (true) {
                // Allow cancellation (note: media sync has no finish command, so just throw)
                if (Connection.getIsCancelled()) {
                    Timber.i("Sync was cancelled");
                    throw new RuntimeException(USER_ABORTED_SYNC.toString());
                }
                JSONArray data = mServer.mediaChanges(lastUsn);
                mCol.log("mediaChanges resp count: ", data.length());
                if (data.length() == 0) {
                    break;
                }

                List<String> need = new ArrayList<>(data.length());
                lastUsn = data.getJSONArray(data.length()-1).getInt(1);
                for (int i = 0; i < data.length(); i++) {
                    // Allow cancellation (note: media sync has no finish command, so just throw)
                    if (Connection.getIsCancelled()) {
                        Timber.i("Sync was cancelled");
                        throw new RuntimeException(USER_ABORTED_SYNC.toString());
                    }
                    String fname = data.getJSONArray(i).getString(0);
                    int rusn = data.getJSONArray(i).getInt(1);
                    String rsum = null;
                    if (!data.getJSONArray(i).isNull(2)) {
                        // If `rsum` is a JSON `null` value, `.optString(2)` will
                        // return `"null"` as a string
                        rsum = data.getJSONArray(i).optString(2);
                    }
                    Pair<String, Integer> info = mCol.getMedia().syncInfo(fname);
                    String lsum = info.first;
                    int ldirty = info.second;
                    mCol.log(String.format(Locale.US,
                            "check: lsum=%s rsum=%s ldirty=%d rusn=%d fname=%s",
                            TextUtils.isEmpty(lsum) ? "" : lsum.subSequence(0, 4),
                            TextUtils.isEmpty(rsum) ? "" : rsum.subSequence(0, 4),
                            ldirty,
                            rusn,
                            fname));

                    if (!TextUtils.isEmpty(rsum)) {
                        // added/changed remotely
                        if (TextUtils.isEmpty(lsum) || !lsum.equals(rsum)) {
                            mCol.log("will fetch");
                            need.add(fname);
                        } else {
                            mCol.log("have same already");
                        }
                        mCol.getMedia().markClean(Collections.singletonList(fname));
                        
                    } else if (!TextUtils.isEmpty(lsum)) {
                        // deleted remotely
                        if (ldirty == 0) {
                            mCol.log("delete local");
                            mCol.getMedia().syncDelete(fname);
                        } else {
                            // conflict: local add overrides remote delete
                            mCol.log("conflict; will send");
                        }
                    } else {
                        // deleted both sides
                        mCol.log("both sides deleted");
                        mCol.getMedia().markClean(Collections.singletonList(fname));
                    }
                }
                _downloadFiles(need);

                mCol.log("update last usn to " + lastUsn);
                mCol.getMedia().setLastUsn(lastUsn); // commits
            }

            // at this point, we're all up to date with the server's changes,
            // and we need to send our own

            boolean updateConflict = false;
            int toSend = mCol.getMedia().dirtyCount();
            while (true) {
                Pair<File, List<String>> changesZip = mCol.getMedia().mediaChangesZip();
                File zip = changesZip.first;
                try {
                    List<String> fnames = changesZip.second;
                    if (fnames.isEmpty()) {
                        break;
                    }

                    mCon.publishProgress(String.format(
                                                       AnkiDroidApp.getAppResources().getString(R.string.sync_media_changes_count), toSend));

                    JSONArray changes = mServer.uploadChanges(zip);
                    int processedCnt = changes.getInt(0);
                    int serverLastUsn = changes.getInt(1);
                    mCol.getMedia().markClean(fnames.subList(0, processedCnt));

                    mCol.log(String.format(Locale.US,
                                           "processed %d, serverUsn %d, clientUsn %d",
                                           processedCnt, serverLastUsn, lastUsn));

                    if (serverLastUsn - processedCnt == lastUsn) {
                        mCol.log("lastUsn in sync, updating local");
                        lastUsn = serverLastUsn;
                        mCol.getMedia().setLastUsn(serverLastUsn); // commits
                    } else {
                        mCol.log("concurrent update, skipping usn update");
                        // commit for markClean
                        mCol.getMedia().getDb().commit();
                        updateConflict = true;
                    }

                    toSend -= processedCnt;
                } finally {
                    zip.delete();
                }
            }
            if (updateConflict) {
                mCol.log("restart sync due to concurrent update");
                return sync();
            }

            int lcnt = mCol.getMedia().mediacount();
            String sanityRet = mServer.mediaSanity(lcnt);
            if ("OK".equals(sanityRet)) {
                return new Pair<>(OK, null);
            } else {
                mCol.getMedia().forceResync();
                return new Pair<>(ARBITRARY_STRING, sanityRet);
        }
    }


    private void _downloadFiles(List<String> fnames) {
        mCol.log(fnames.size() + " files to fetch");
        while (!fnames.isEmpty()) {
            try {
                List<String> top = fnames.subList(0, Math.min(fnames.size(), Consts.SYNC_ZIP_COUNT));
                mCol.log("fetch " + top);
                ZipFile zipData = mServer.downloadFiles(top);
                int cnt = mCol.getMedia().addFilesFromZip(zipData);
                mDownloadCount += cnt;
                mCol.log("received " + cnt + " files");
                // NOTE: The python version uses slices which return an empty list when indexed beyond what
                // the list contains. Since we can't slice out an empty sublist in Java, we must check
                // if we've reached the end and clear the fnames list manually.
                if (cnt == fnames.size()) {
                    fnames.clear();
                } else {
                    fnames = fnames.subList(cnt, fnames.size());
                }
                mCon.publishProgress(String.format(
                        AnkiDroidApp.getAppResources().getString(R.string.sync_media_downloaded_count), mDownloadCount));
            } catch (IOException | UnknownHttpResponseException e) {
                Timber.e(e, "Error downloading media files");
                throw new RuntimeException(e);
            }
        }
    }
}
