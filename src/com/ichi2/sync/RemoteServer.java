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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

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
			return super.req("hostKey", new ByteArrayInputStream(jo.toString().getBytes()), false);
		} catch (JSONException e) {
			return null;
		}
	}

	@Override
	public HttpResponse meta() {
		try {
			JSONObject jo = new JSONObject();
			jo.put("v", SYNC_VER);
			return super.req("meta", new ByteArrayInputStream(jo.toString().getBytes()));
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
		return _run("chunk", null);
	}

	@Override
	public JSONObject chunk(JSONObject kw) {
		return _run("chunk", kw);
	}

	@Override
	public JSONObject applyChunk(JSONObject kw) {
		return _run("applyChunk", kw);
	}

//	@Override
//	public JSONObject finish() {
//		return _run("finish", 0);
//	}
//
//	@Override
//	public JSONObject finish(long kw) {
//		return _run("finish", kw);
//	}

	private JSONObject _run(String cmd, JSONObject data) {
		HttpResponse ret;
		if (data != null) {
			ret = super.req(cmd, new ByteArrayInputStream(data.toString().getBytes()));
		} else {
			ret = super.req(cmd);
		}
		if (HttpSyncer.getReturnType(ret) == 200) {
			InputStream content;
			try {
				content = ret.getEntity().getContent();
				String s = Utils.convertStreamToString(content);
				if (s == null || s.equalsIgnoreCase("null") || s.length() == 0) {
					return null;
				} else {
					return new JSONObject(s);
				}
			} catch (IllegalStateException e) {
				throw new RuntimeException(e);
			} catch (IOException e) {
				throw new RuntimeException(e);
			} catch (JSONException e) {
				throw new RuntimeException(e);
			}
		} else {
			return null;
		}
	}
}
