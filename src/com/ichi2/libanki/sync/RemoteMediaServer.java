package com.ichi2.libanki.sync;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

import org.json.JSONException;
import org.json.JSONObject;

import com.ichi2.async.Connection;



public class RemoteMediaServer extends HttpSyncer {

	public RemoteMediaServer(String hkey, Connection con) {
		super(hkey, con);
	} 
}
