package com.ichi2.libanki.sync;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

import org.json.JSONException;
import org.json.JSONObject;



public class RemoteMediaServer extends HttpSyncer {

	public RemoteMediaServer(String hkey) {
		super(hkey);
	} 
}
