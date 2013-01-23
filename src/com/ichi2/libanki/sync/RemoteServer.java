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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import com.ichi2.libanki.Utils;
import org.apache.http.HttpResponse;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.ichi2.async.Connection;
import com.ichi2.libanki.Collection;

public class RemoteServer extends BasicHttpSyncer {

    public RemoteServer(Connection con, String hkey) {
        super(hkey, con);
    }


    /** Returns hkey or none if user/pw incorrect. */
    @Override
    public HttpResponse hostKey(String user, String pw) {
        try {
            JSONObject jo = new JSONObject();
            jo.put("u", user);
            jo.put("p", pw);
            return super.req("hostKey", super.getInputStream(Utils.jsonToString(jo)), false);
        } catch (JSONException e) {
            return null;
        }
    }


    @Override
    public HttpResponse register(String user, String pw) {
        try {
            JSONObject jo = new JSONObject();
            jo.put("u", URLEncoder.encode(user, "UTF-8"));
            jo.put("p", URLEncoder.encode(pw, "UTF-8"));
            return super.req("register", null, 6, false, jo);
        } catch (JSONException e) {
            return null;
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }


    @Override
    public HttpResponse meta() {
        try {
            JSONObject jo = new JSONObject();
            jo.put("v", Collection.SYNC_VER);
            return super.req("meta", super.getInputStream(Utils.jsonToString(jo)));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public JSONObject applyChanges(JSONObject kw) {
        return _run("applyChanges", kw);
    }


    @Override
    public JSONObject start(JSONObject kw) {
        return _run("start", kw);
    }


    @Override
    public JSONObject chunk() {
        JSONObject co = new JSONObject();
        return _run("chunk", co);
    }


    @Override
    public void applyChunk(JSONObject sech) {
        _run("applyChunk", sech);
    }


    @Override
    public JSONArray sanityCheck() {
        try {
            HttpResponse ret = super.req("sanityCheck", super.getInputStream("{}"));
            if (ret == null) {
                return null;
            }
            String s = super.stream2String(ret.getEntity().getContent());
            if (s.equals("null")) {
                return new JSONArray("[\"error\"]");
            }
            return new JSONArray(s);
        } catch (IllegalStateException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public long finish() {
        try {
            HttpResponse ret = super.req("finish", super.getInputStream("{}"));
            if (ret == null) {
                return 0;
            }
            String s = super.stream2String(ret.getEntity().getContent());
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0;
        } catch (IllegalStateException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    private JSONObject _run(String cmd, JSONObject data) {
        HttpResponse ret = super.req(cmd, super.getInputStream(Utils.jsonToString(data)));
        if (ret == null) {
            return null;
        }
        String s = "";
        try {
            int resultType = ret.getStatusLine().getStatusCode();
            if (resultType == 200) {
                s = super.stream2String(ret.getEntity().getContent());
                if (!s.equalsIgnoreCase("null") && s.length() != 0) {
                    return new JSONObject(s);
                }
            }
            JSONObject o = new JSONObject();
            o.put("errorType", resultType);
            o.put("errorReason", s.equals("null") ? "null result (" + cmd + ")" : ret.getStatusLine().getReasonPhrase());
            return o;
        } catch (IllegalStateException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }
}
