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

package com.ichi2.libanki.sync;

import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabaseCorruptException;


import com.ichi2.anki.AnkiDatabaseManager;
import com.ichi2.anki.AnkiDb;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;
import com.ichi2.anki.exception.UnknownHttpResponseException;
import com.ichi2.async.Connection;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.Utils;
import com.ichi2.utils.VersionUtils;

import org.apache.http.HttpResponse;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;

import timber.log.Timber;

public class FullSyncer extends HttpSyncer {

    Collection mCol;
    Connection mCon;


    public FullSyncer(Collection col, String hkey, Connection con) {
        super(hkey, con);
        mPostVars = new HashMap<String, Object>();
        mPostVars.put("k", hkey);
        mPostVars.put("v",
                String.format(Locale.US, "ankidroid,%s,%s", VersionUtils.getPkgVersionName(), Utils.platDesc()));
        mCol = col;
        mCon = con;
    }


    @Override
    public String syncURL() {
        // Allow user to specify custom sync server
        SharedPreferences userPreferences = AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance());
        if (userPreferences!= null && userPreferences.getBoolean("useCustomSyncServer", false)) {
            File syncBase = new File(userPreferences.getString("syncBaseUrl", Consts.SYNC_BASE));
            return new File(syncBase, "/sync").toString() + "/";
        }
        // Usual case
        return Consts.SYNC_BASE + "sync/";
    }


    @Override
    public Object[] download() throws UnknownHttpResponseException {
        InputStream cont;
        try {
            HttpResponse ret = super.req("download");
            if (ret == null) {
                return null;
            }
            cont = ret.getEntity().getContent();
        } catch (IllegalStateException e1) {
            throw new RuntimeException(e1);
        } catch (IOException e1) {
            return null;
        }
        if (mCol == null) {
            Timber.e("Collection was unexpectedly null");
            return null;
        }
        String path = mCol.getPath();
        mCol.close(false);
        mCol = null;
        String tpath = path + ".tmp";
        try {
            super.writeToFile(cont, tpath);
        } catch (IOException e) {
            Timber.e(e, "Full sync failed to download collection.");
            return new Object[] { "sdAccessError" };
        }
        // first check, if account needs upgrade (from 1.2)
        try {
            FileInputStream fis = new FileInputStream(tpath);
            if (super.stream2String(fis, 15).equals("upgradeRequired")) {
                return new Object[] { "upgradeRequired" };
            }
        } catch (FileNotFoundException e1) {
            throw new RuntimeException(e1);
        }
        // check the received file is ok
        mCon.publishProgress(R.string.sync_check_download_file);
        try {
            AnkiDb d = AnkiDatabaseManager.getDatabase(tpath);
            if (!d.queryString("PRAGMA integrity_check").equalsIgnoreCase("ok")) {
                Timber.e("Full sync - downloaded file corrupt");
                return new Object[] { "remoteDbError" };
            }
        } catch (SQLiteDatabaseCorruptException e) {
            Timber.e("Full sync - downloaded file corrupt");
            return new Object[] { "remoteDbError" };
        } finally {
            AnkiDatabaseManager.closeDatabase(tpath);
        }
        // overwrite existing collection
        File newFile = new File(tpath);
        if (newFile.renameTo(new File(path))) {
            return new Object[] { "success" };
        } else {
            return new Object[] { "overwriteError" };
        }
    }


    @Override
    public Object[] upload() throws UnknownHttpResponseException {
        // make sure it's ok before we try to upload
        mCon.publishProgress(R.string.sync_check_upload_file);
        if (!mCol.getDb().queryString("PRAGMA integrity_check").equalsIgnoreCase("ok")) {
            return new Object[] { "dbError" };
        }
        if (!mCol.basicCheck()) {
            return new Object[] { "dbError" };
        }
        // apply some adjustments, then upload
        mCol.beforeUpload();
        String filePath = mCol.getPath();
        mCol.close();
        HttpResponse ret;
        mCon.publishProgress(R.string.sync_uploading_message);
        try {
            ret = super.req("upload", new FileInputStream(filePath));
            if (ret == null) {
                return null;
            }
            int status = ret.getStatusLine().getStatusCode();
            if (status != 200) {
                // error occurred
                return new Object[] { "error", status, ret.getStatusLine().getReasonPhrase() };
            } else {
                return new Object[] { super.stream2String(ret.getEntity().getContent()) };
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IllegalStateException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
