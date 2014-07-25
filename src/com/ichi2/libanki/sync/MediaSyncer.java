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

import android.text.TextUtils;
import android.util.Log;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.Pair;
import com.ichi2.anki.R;
import com.ichi2.anki.exception.APIVersionException;
import com.ichi2.anki.exception.UnsupportedSyncException;
import com.ichi2.async.Connection;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Consts;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipFile;

/**
 * About conflicts:
 * - to minimize data loss, if both sides are marked for sending and one
 *   side has been deleted, favour the add
 * - if added/changed on both sides, favour the server version on the
 *   assumption other syncers are in sync with the server
 */
public class MediaSyncer {
    Collection mCol;
    RemoteMediaServer mServer;


    public MediaSyncer(Collection col, RemoteMediaServer server) {
        mCol = col;
        mServer = server;
    }


    public String sync(Connection con) throws UnsupportedSyncException {
        try {
            // check if there have been any changes
            con.publishProgress(R.string.sync_media_find);
            Log.i(AnkiDroidApp.TAG, "MediaSyncer: finding changed media");
            mCol.getMedia().findChanges();
    
            // begin session and check if in sync
            int lastUsn = mCol.getMedia().lastUsn();
            JSONObject ret = mServer.begin();
            int srvUsn = ret.getInt("usn");
            if ((lastUsn == srvUsn) && !(mCol.getMedia().haveDirty())) {
                return "noChanges";
            }
            // loop through and process changes from server
            Log.i(AnkiDroidApp.TAG, "MediaSyncer: Last local usn is: " + lastUsn);
            while (true) {
                JSONArray data = mServer.mediaChanges(lastUsn);
                Log.i(AnkiDroidApp.TAG, "MediaSyncer: mediaChanges resp count: " + data.length());
                if (data.length() == 0) {
                    break;
                }
    
                List<String> need = new ArrayList<String>();
                lastUsn = data.getJSONArray(data.length()-1).getInt(1);
                for (int i = 0; i < data.length(); i++) {
                    String fname = data.getJSONArray(i).getString(0);
                    int rusn = data.getJSONArray(i).getInt(1);
                    String rsum = data.getJSONArray(i).optString(2);
                    Pair<String, Integer> info = mCol.getMedia().syncInfo(fname);
                    String lsum = info.first;
                    int ldirty = info.second;
                    Log.v(AnkiDroidApp.TAG, String.format("check: lsum=%s rsum=%s ldirty=%d rusn=%d fname=%s",
                            TextUtils.isEmpty(lsum) ? "" : lsum.subSequence(0, 4),
                            TextUtils.isEmpty(rsum) ? "" : rsum.subSequence(0, 4),
                            ldirty,
                            rusn,
                            fname));
                    
                    if (!TextUtils.isEmpty(rsum)) {
                        // added/changed remotely
                        if (TextUtils.isEmpty(lsum) || !lsum.equals(rsum)) {
                            Log.v(AnkiDroidApp.TAG, "will fetch");
                            need.add(fname);
                        } else {
                            Log.v(AnkiDroidApp.TAG, "have same already");
                        }
                        mCol.getMedia().markClean(Arrays.asList(fname));
                        
                    } else if (!TextUtils.isEmpty(lsum)) {
                        // deleted remotely
                        if (ldirty != 0) {
                            Log.v(AnkiDroidApp.TAG, "delete local");
                            mCol.getMedia().syncDelete(fname);
                        } else {
                            // conflict: local add overrides remote delte
                            Log.v(AnkiDroidApp.TAG, "conflict; will send");
                        }
                    } else {
                        // deleted both sides
                        Log.v(AnkiDroidApp.TAG, "both sides deleted");
                        mCol.getMedia().markClean(Arrays.asList(fname));
                    }
                }
                _downloadFiles(need);
                
                Log.v(AnkiDroidApp.TAG, "update last usn to " + lastUsn);
                mCol.getMedia().setLastUsn(lastUsn); // commits
            }
            
            // at this point, we're all up to date with the server's changes,
            // and we need to send our own
            
            boolean updateConflict = false;
            while (true) {
                Pair<File, List<String>> changesZip = mCol.getMedia().mediaChangesZip();
                File zip = changesZip.first;
                List<String> fnames = changesZip.second;
                if (fnames.size() == 0) {
                    break;
                }
                JSONArray changes = mServer.uploadChanges(zip);
                int processedCnt = changes.getInt(0);
                int serverLastUsn = changes.getInt(1);
                mCol.getMedia().markClean(fnames.subList(0, processedCnt));
                Log.v(AnkiDroidApp.TAG, String.format("processed %d, serverUsn %d, clientUsn %d", processedCnt,
                        serverLastUsn, lastUsn));

                if (serverLastUsn - processedCnt == lastUsn) {
                    Log.v(AnkiDroidApp.TAG, "lastUsn in sync, updating local");
                    lastUsn = serverLastUsn;
                    mCol.getMedia().setLastUsn(serverLastUsn); // commits
                } else {
                    Log.v(AnkiDroidApp.TAG, "concurrent update, skipping usn update");
                    // commit for markClean
                    mCol.getMedia().getDb().commit();
                    updateConflict = true;
                }
            }
            if (updateConflict) {
                Log.v(AnkiDroidApp.TAG, "restart sync due to concurrent update");
                return sync(con);
            }
            
            int lcnt = mCol.getMedia().mediacount();
            String sRet = mServer.mediaSanity(lcnt);
            if (sRet.equals("OK")) {
                return "OK";
            } else {
                mCol.getMedia().forceResync();
                return sRet;
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        } catch (APIVersionException e) {
            UnsupportedSyncException ee = new UnsupportedSyncException("Cannot sync media on this version of Android");
            Log.e(AnkiDroidApp.TAG, e.getMessage());
            throw ee;
        } catch (Exception e) {
            Log.e(AnkiDroidApp.TAG, "Syncing error: ", e);
            throw new RuntimeException(e);
        }
    }

    // TODO: I think this method should be responsible for the life cycle of the zip file
    // It can be safely deleted after this method has finished using it. Right now it is
    // left in the AnkiDroid directory (harmless but takes up space).
    private void _downloadFiles(List<String> fnames) throws APIVersionException {
        Log.v(AnkiDroidApp.TAG, fnames.size() + " files to fetch");
        while (fnames.size() > 0) {
            try {
                List<String> top = fnames.subList(0, Math.min(fnames.size(), Consts.SYNC_ZIP_COUNT));
                Log.v(AnkiDroidApp.TAG, "fetch " + top);
                ZipFile zipData = mServer.downloadFiles(top);
                int cnt = mCol.getMedia().addFilesFromZip(zipData);
                Log.v(AnkiDroidApp.TAG, "received " + cnt + " files");
                // NOTE: The python version uses slices which return an empty list when indexed beyond what
                // the list contains. Since we can't slice out an empty sublist in Java, we must check
                // if we've reached the end and clear the fnames list manually.
                if (cnt == fnames.size()) {
                    fnames.clear();
                } else {
                    fnames = fnames.subList(cnt, fnames.size());
                }
            } catch (Exception e) {
                Log.e(AnkiDroidApp.TAG, "Error downloading media files", e);
                throw new RuntimeException(e);
            }
        }
    }
}
