/****************************************************************************************
 * Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
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

import android.util.Log;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.async.Connection;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

public class RemoteMediaServer extends BasicHttpSyncer {

    public RemoteMediaServer(String hkey, Connection con) {
        super(hkey, con);
    }


    public JSONArray remove(List<String> fnames, long minUsn) {
        JSONObject data = new JSONObject();

        try {
            data.put("fnames", new JSONArray(fnames));
            data.put("minUsn", minUsn);
            HttpResponse ret = super.req("remove", getInputStream(data.toString()));
            if (ret == null) {
                return null;
            }
            String s;
            int resultType = ret.getStatusLine().getStatusCode();
            if (resultType == 200) {
                s = super.stream2String(ret.getEntity().getContent());
                if (s != null && !s.equalsIgnoreCase("null") && s.length() != 0) {
                    return new JSONArray(s);
                }
            }
            Log.e(AnkiDroidApp.TAG, "Error in RemoteMediaServer.remove(): " + ret.getStatusLine().getReasonPhrase());
            return new JSONArray();
        } catch (IllegalStateException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    public File files(long minUsn, String tmpMediaZip) {
        JSONObject data = new JSONObject();
        try {
            data.put("minUsn", minUsn);
            HttpResponse ret = super.req("files", getInputStream(data.toString()));
            if (ret == null) {
                Log.e(AnkiDroidApp.TAG, "RemoteMediaServer.files: Exception during request");
                return null;
            }
            int resultType = ret.getStatusLine().getStatusCode();
            if (resultType == 200) {
                super.writeToFile(ret.getEntity().getContent(), tmpMediaZip);
                return new File(tmpMediaZip);
            } else {
                Log.e(AnkiDroidApp.TAG, "RemoteMediaServer.files: Request returned " + resultType);
                return null;
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        } catch (IllegalStateException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public long addFiles(File zip) {
        try {
            HttpResponse ret = super.req("addFiles", 0, new FileInputStream(zip));
            if (ret == null) {
                return 0;
            }
            StatusLine sl = ret.getStatusLine();
            HttpEntity ent = ret.getEntity();
            String s;
            if (sl != null && sl.getStatusCode() == 200 && ent != null) {
                s = super.stream2String(ent.getContent());
                if (s != null && !s.equalsIgnoreCase("null") && s.length() != 0) {
                    try {
                        return Long.parseLong(s);
                    } catch (NumberFormatException e) {
                        AnkiDroidApp.saveExceptionReportFile(e, "RemoteMediaServerAddFiles:" + s);
                        return 0;
                    }
                }
            }
            Log.e(AnkiDroidApp.TAG, "Error in RemoteMediaServer.addFiles(): " + ret.getStatusLine().getReasonPhrase());
            return 0;
        } catch (IllegalStateException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public long mediaSanity() {
        HttpResponse ret = super.req("mediaSanity");
        if (ret == null) {
            return 0;
        }
        String s;
        int resultType = ret.getStatusLine().getStatusCode();
        if (resultType == 200) {
            try {
                s = super.stream2String(ret.getEntity().getContent());
            } catch (IllegalStateException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if (s != null && !s.equalsIgnoreCase("null") && s.length() != 0) {
                return Long.parseLong(s);
            }
        }
        Log.e(AnkiDroidApp.TAG, "Error in RemoteMediaServer.mediaSanity(): " + ret.getStatusLine().getReasonPhrase());
        return 0;
    }
}
