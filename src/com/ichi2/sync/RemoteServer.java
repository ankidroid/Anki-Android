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

package com.ichi2.sync;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.http.HttpResponse;
import org.json.JSONException;
import org.json.JSONObject;

import com.ichi2.libanki.Utils;

public class RemoteServer extends HttpSyncer {

	public RemoteServer(String hkey) {
		super(hkey);
	}

	/** Returns hkey or none if user/pw incorrect. */
	@Override
	public HttpResponse hostKey(String user, String pw) {
		try {
			JSONObject jo = new JSONObject();
			jo.put("u", user);
			jo.put("p", pw);
			return super.req("hostKey", new ByteArrayInputStream(jo.toString()
					.getBytes()), false);
		} catch (JSONException e) {
			return null;
		}
	}

	@Override
	public HttpResponse meta() {
		try {
			JSONObject jo = new JSONObject();
			jo.put("v", SYNC_VER);
			return super.req("meta", new ByteArrayInputStream(jo.toString()
					.getBytes()));
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
	public JSONObject chunk(JSONObject kw) {
		return _run("chunk", kw);
	}

	@Override
	public JSONObject applyChunk(ByteArrayInputStream kw) {
		return _run("applyChunk", kw);
	}

	@Override
	public long finish() {
		try {
			HttpResponse ret = super.req("finish", new ByteArrayInputStream(
					"{}".getBytes()));
			if (HttpSyncer.getReturnType(ret) == 200) {
				InputStream content;
				content = ret.getEntity().getContent();
				String s = Utils.convertStreamToString(content);
				if (s == null || s.equalsIgnoreCase("null") || s.length() == 0) {
					return 0;
				} else {
					return Long.parseLong(s);
				}
			} else {
				return 0;
			}
		} catch (IllegalStateException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private JSONObject _run(String cmd, JSONObject data) {
		return _run(cmd, new ByteArrayInputStream(data.toString().getBytes()));
	}
	private JSONObject _run(String cmd, ByteArrayInputStream data) {
		HttpResponse ret;
		if (data != null) {
			ret = super.req(cmd, data);
		} else {
			ret = super.req(cmd);
		}
		try {
			int resultType = HttpSyncer.getReturnType(ret);
			String s = "";
			if (resultType == 200) {
	            BufferedReader rd = new BufferedReader(new InputStreamReader(ret.getEntity().getContent(), "UTF-8"), 4096);
	            String line;
	            StringBuilder sb = new StringBuilder();
	            long size = 0;
	            while ((line = rd.readLine()) != null) {
	                sb.append(line);
	                size += line.length();
	            }
	            rd.close();
	            s = sb.toString();
				if (!s.equalsIgnoreCase("null") && s.length() != 0) {
					JSONObject o = new JSONObject(s);
					if (cmd.equals("chunk")) {
						o.put("rSize", size);						
					}
					return o;
				}
			}
			JSONObject o = new JSONObject();
			o.put("errorType", HttpSyncer.getReturnType(ret));
			o.put("errorReason", s.equals("null") ? "null result" : HttpSyncer.getReason(ret));
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
