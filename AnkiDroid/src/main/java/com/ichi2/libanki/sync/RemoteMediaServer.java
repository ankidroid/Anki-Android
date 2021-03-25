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

import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.exception.MediaSyncException;
import com.ichi2.anki.exception.UnknownHttpResponseException;
import com.ichi2.anki.web.CustomSyncServer;
import com.ichi2.async.Connection;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Utils;
import com.ichi2.utils.HashUtil;
import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONObject;
import com.ichi2.utils.VersionUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipFile;

import okhttp3.Response;
import timber.log.Timber;

@SuppressWarnings({"PMD.AvoidThrowingRawExceptionTypes","PMD.MethodNamingConventions"})
public class RemoteMediaServer extends HttpSyncer {

    private final Collection mCol;


    public RemoteMediaServer(Collection col, String hkey, Connection con, HostNum hostNum) {
        super(hkey, con, hostNum);
        mCol = col;
    }


    @Override
    public String syncURL() {
        // Allow user to specify custom sync server
        SharedPreferences userPreferences = AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance());
        if (isUsingCustomSyncServer(userPreferences)) {
            String mediaSyncBase = CustomSyncServer.getMediaSyncUrl(userPreferences);
            if (mediaSyncBase == null) {
                return getDefaultAnkiWebUrl();
            }
            //Note: the preference did not necessarily contain /msync/, so we can't concat with the default as done in
            // getDefaultAnkiWebUrl
            return Uri.parse(mediaSyncBase).toString();
        }
        // Usual case
        return getDefaultAnkiWebUrl();
    }


    public JSONObject begin() throws UnknownHttpResponseException, MediaSyncException {
        try {
            mPostVars = HashUtil.HashMapInit(2);
            mPostVars.put("k", mHKey);
            mPostVars.put("v",
                    String.format(Locale.US, "ankidroid,%s,%s", VersionUtils.getPkgVersionName(), Utils.platDesc()));

            Response resp = super.req("begin", HttpSyncer.getInputStream(Utils.jsonToString(new JSONObject())));
            JSONObject jresp = new JSONObject(resp.body().string());
            JSONObject ret = _dataOnly(jresp, JSONObject.class);
            mSKey = ret.getString("sk");
            return ret;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    // args: lastUsn
    public JSONArray mediaChanges(int lastUsn) throws UnknownHttpResponseException, MediaSyncException {
        try {
            mPostVars = HashUtil.HashMapInit(1);
            mPostVars.put("sk", mSKey);

            Response resp = super.req("mediaChanges",
                    HttpSyncer.getInputStream(Utils.jsonToString(new JSONObject().put("lastUsn", lastUsn))));
            JSONObject jresp = new JSONObject(resp.body().string());
            return _dataOnly(jresp, JSONArray.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * args: files
     * <br>
     * This method returns a ZipFile with the OPEN_DELETE flag, ensuring that the file on disk will
     * be automatically deleted when the stream is closed.
     */
    public ZipFile downloadFiles(List<String> top) throws UnknownHttpResponseException {
        Response resp = null;
        try {
            resp = super.req("downloadFiles",
                    HttpSyncer.getInputStream(Utils.jsonToString(new JSONObject().put("files", new JSONArray(top)))));
            String zipPath = mCol.getPath().replaceFirst("collection\\.anki2$", "tmpSyncFromServer.zip");
            // retrieve contents and save to file on disk:
            super.writeToFile(resp.body().byteStream(), zipPath);
            return new ZipFile(new File(zipPath), ZipFile.OPEN_READ | ZipFile.OPEN_DELETE);
        } catch (IOException | NullPointerException e) {
            Timber.e(e, "Failed to download requested media files");
            throw new RuntimeException(e);
        } finally {
            if (resp != null && resp.body() != null) {
                resp.body().close();
            }
        }
    }


    public JSONArray uploadChanges(File zip) throws UnknownHttpResponseException, MediaSyncException {
        try {
            // no compression, as we compress the zip file instead
            Response resp = super.req("uploadChanges", new FileInputStream(zip), 0);
            JSONObject jresp = new JSONObject(resp.body().string());
            return _dataOnly(jresp, JSONArray.class);
        } catch (IOException | NullPointerException e) {
            throw new RuntimeException(e);
        }
    }


    // args: local
    public String mediaSanity(int lcnt) throws UnknownHttpResponseException, MediaSyncException {
        try {
            Response resp = super.req("mediaSanity",
                    HttpSyncer.getInputStream(Utils.jsonToString(new JSONObject().put("local", lcnt))));
            JSONObject jresp = new JSONObject(resp.body().string());
            return _dataOnly(jresp, String.class);
        } catch (IOException | NullPointerException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Returns the "data" element from the JSON response from the server, or throws an exception if there is a value in
     * the "err" element.
     * <p>
     * The python counterpart to this method is flexible with type coercion; the type of object returned is decided by
     * the content of the "data" element, and there are several such types in the various server responses. Java
     * requires us to specifically choose a type to convert to, so we need an additional parameter (returnType) to
     * specify the type we expect.
     * 
     * @param resp The JSON response from the server
     * @param returnType The type to coerce the 'data' element to.
     * @return The "data" element from the HTTP response from the server. The type of object returned is determined by
     *         returnType.
     */
    @SuppressWarnings("unchecked")
    private <T> T _dataOnly(JSONObject resp, Class<T> returnType) throws MediaSyncException {
        if (!TextUtils.isEmpty(resp.optString("err"))) {
            String err = resp.getString("err");
            mCol.log("error returned: " + err);
            throw new MediaSyncException("SyncError:" + err);
        }
        if (returnType == String.class) {
            return (T) resp.getString("data");
        } else if (returnType == JSONObject.class) {
            return (T) resp.getJSONObject("data");
        } else if (returnType == JSONArray.class) {
            return (T) resp.getJSONArray("data");
        }
        throw new RuntimeException("Did not specify a valid type for the 'data' element in resopnse");
    }

    // Difference from libAnki: we allow a custom URL to specify a different prefix, so this is only used with the
    // default URL
    @Override
    protected String getUrlPrefix() {
        return "msync";
    }
}
