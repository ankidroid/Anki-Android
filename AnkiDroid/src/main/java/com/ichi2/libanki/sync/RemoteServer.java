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

package com.ichi2.libanki.sync;

import com.ichi2.anki.exception.UnknownHttpResponseException;
import com.ichi2.async.Connection;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.Utils;
import com.ichi2.utils.HashUtil;
import com.ichi2.utils.VersionUtils;

import com.ichi2.utils.JSONException;
import com.ichi2.utils.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;

import okhttp3.Response;
import timber.log.Timber;

@SuppressWarnings({"PMD.AvoidThrowingRawExceptionTypes","PMD.MethodNamingConventions"})
public class RemoteServer extends HttpSyncer {

    public RemoteServer(Connection con, String hkey, HostNum hostNum) {
        super(hkey, con, hostNum);
    }


    /** Returns hkey or null if user/pw incorrect. */
    public Response hostKey(String user, String pw) throws UnknownHttpResponseException {
        try {
            mPostVars = HashUtil.HashMapInit(0);
            JSONObject credentials = new JSONObject();
            credentials.put("u", user);
            credentials.put("p", pw);
            return super.req("hostKey", HttpSyncer.getInputStream(Utils.jsonToString(credentials)));
        } catch (JSONException e) {
            Timber.w(e);
            return null;
        }
    }


    public Response meta() throws UnknownHttpResponseException {
        mPostVars = HashUtil.HashMapInit(2);
        mPostVars.put("k", mHKey);
        mPostVars.put("s", mSKey);
        JSONObject meta = new JSONObject();
        meta.put("v", Consts.SYNC_VER);
        meta.put("cv",
                String.format(Locale.US, "ankidroid,%s,%s", VersionUtils.getPkgVersionName(), Utils.platDesc()));
        return super.req("meta", HttpSyncer.getInputStream(Utils.jsonToString(meta)));
    }


    public JSONObject applyChanges(JSONObject kw) throws UnknownHttpResponseException {
        return parseDict(_run("applyChanges", kw));
    }


    public JSONObject start(JSONObject kw) throws UnknownHttpResponseException {
        return parseDict(_run("start", kw));
    }


    public JSONObject chunk() throws UnknownHttpResponseException {
        JSONObject co = new JSONObject();
        return parseDict(_run("chunk", co));
    }


    public void applyChunk(JSONObject sech) throws UnknownHttpResponseException {
        _run("applyChunk", sech);
    }

    public JSONObject sanityCheck2(JSONObject client) throws UnknownHttpResponseException {
        return parseDict(_run("sanityCheck2", client));
    }

    public long finish() throws UnknownHttpResponseException {
        return parseLong(_run("finish", new JSONObject()));
    }

    public void abort() throws UnknownHttpResponseException {
        _run("abort", new JSONObject());
    }

    /** Python has dynamic type deduction, but we don't, so return String **/
    private String _run(String cmd, JSONObject data) throws UnknownHttpResponseException {
        Response ret = super.req(cmd, HttpSyncer.getInputStream(Utils.jsonToString(data)));
        try {
            return ret.body().string();
        } catch (IllegalStateException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** Note: these conversion helpers aren't needed in libanki as type deduction occurs automatically there **/
    private JSONObject parseDict(String s) {
        if (!"null".equalsIgnoreCase(s) && s.length() != 0) {
            return new JSONObject(s);
        } else {
            return new JSONObject();
        }
    }

    private long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            Timber.w(e);
            return 0;
        }
    }
}
