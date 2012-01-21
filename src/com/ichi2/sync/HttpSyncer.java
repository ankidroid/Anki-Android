/***************************************************************************************
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
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
import com.ichi2.async.Connection;
import com.ichi2.libanki.Utils;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;

public class HttpSyncer {

	private static final String BOUNDARY = "Anki-sync-boundary";
	
    // The possible values for the status response from the AnkiWeb server.
    public static final String ANKIWEB_STATUS_OK = "OK";
//    private static final String ANKIWEB_STATUS_INVALID_USER_PASS = "invalidUserPass";
//    private static final String ANKIWEB_STATUS_OLD_VERSION = "oldVersion";
//    private static final String ANKIWEB_STATUS_TOO_BUSY =
//        "AnkiWeb is too busy right now. Please try again later.";
    /**
     * Connection settings
     */
    public static final String SYNC_HOST = "beta.ankiweb.net";
    public static final String SYNC_URL = "http://" + SYNC_HOST + "/sync/";
    public static final String SYNC_SEARCH = "http://" + SYNC_HOST + "/file/search";
    public static final int SYNC_VER = 0;
    
    /**
     * Synchronization.
     */

    private String mHKey;

    public HttpSyncer(String hkey) {
    	mHKey = hkey;
    }


    public HttpResponse req(String method) {
    	return req(method, null);
    }
    public HttpResponse req(String method, InputStream fobj) {
    	return req(method, fobj, 6, true);
    }
    public HttpResponse req(String method, InputStream fobj, boolean hkey) {
    	return req(method, fobj, 6, hkey);
    }
    public HttpResponse req(String method, InputStream fobj, int comp, boolean hkey) {
		try {
	    	String bdry = "--" + BOUNDARY;
	    	StringWriter buf = new StringWriter();
	    	// compression flag and session key as post vars
	        buf.write(bdry + "\r\n");
	        buf.write("Content-Disposition: form-data; name=\"c\"\r\n\r\n" + (comp != 0 ? 1 : 0) + "\r\n");
	        if (hkey) {
	            buf.write(bdry + "\r\n");
	            buf.write("Content-Disposition: form-data; name=\"k\"\r\n\r\n" + mHKey + "\r\n");        	
	        }
	        ByteArrayOutputStream bos = new ByteArrayOutputStream();
	        // payload as raw data or json
	        if (fobj != null) {
	        	// header
	        	buf.write(bdry + "\r\n");
	        	buf.write("Content-Disposition: form-data; name=\"data\"; filename=\"data\"\r\nContent-Type: application/octet-stream\r\n\r\n");
		        buf.close();
		        bos.write(buf.toString().getBytes());
	        	// write file into buffer, optionally compressing
	        	int len;
	        	BufferedInputStream bfobj = new BufferedInputStream (fobj);
		        byte[] chunk = new byte[65536];
	        	if (comp != 0) {
	        		GZIPOutputStream tgt = new GZIPOutputStream(new BufferedOutputStream(bos));
		            while ((len = bfobj.read(chunk)) > 0) {
		               	tgt.write(chunk, 0, len);
		            }
		            tgt.close();
	        	} else {
	        		BufferedOutputStream tgt = new BufferedOutputStream(bos);
		            while ((len = bfobj.read(chunk)) > 0) {
		               	tgt.write(chunk, 0, len);
		            }
	        	}
	            bos.write(("\r\n" + bdry + "--\r\n").getBytes());
	        } else {
	        	buf.close();
	        	bos.write(buf.toString().getBytes());	        	
	        }
            bos.close();

            // connection headers
	        HttpPost httpPost = new HttpPost(SYNC_URL + method);
	        HttpEntity entity = new ByteArrayEntity(bos.toByteArray());
	        httpPost.setEntity(entity);
	        httpPost.setHeader("Content-type", "multipart/form-data; boundary=" + BOUNDARY);
	        DefaultHttpClient httpClient = new DefaultHttpClient();
	        return httpClient.execute(httpPost);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
    }


    public static String getDataString(HttpResponse response) {
    	try {
			return Utils.convertStreamToString(response.getEntity().getContent());
		} catch (IllegalStateException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
    }

    public static JSONObject getDataJSONObject(HttpResponse response) {
    	try {
			return new JSONObject(getDataString(response));
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
    }

    public static JSONArray getDataJSONArray(HttpResponse response) {
    	try {
			return new JSONArray(getDataString(response));
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
    }

    public static int getReturnType(HttpResponse response) {
    	return response.getStatusLine().getStatusCode();
    }


	public HttpResponse hostKey(String arg1, String arg2) {
		// TODO Auto-generated method stub
		return null;
	}


	public JSONObject applyChanges(JSONObject kw) {
		// TODO Auto-generated method stub
		return null;
	}


	public JSONObject start(JSONObject kw) {
		// TODO Auto-generated method stub
		return null;
	}


	public JSONObject chunk(JSONObject kw) {
		// TODO Auto-generated method stub
		return null;
	}


	public JSONObject applyChunk(JSONObject kw) {
		// TODO Auto-generated method stub
		return null;
	}


	public HttpResponse meta() {
		// TODO Auto-generated method stub
		return null;
	}


	public HttpResponse upload(Connection connection) {
		// TODO Auto-generated method stub
		return null;
	}


	public HttpResponse download(Connection connection) {
		// TODO Auto-generated method stub
		return null;
	}


	public JSONObject chunk() {
		// TODO Auto-generated method stub
		return null;
	}


	public JSONObject finish() {
		// TODO Auto-generated method stub
		return null;
	}


	public JSONObject finish(long kw) {
		// TODO Auto-generated method stub
		return null;
	}

}
