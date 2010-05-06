/*
 * Copyright 2008 Tom Gibara
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tomgibara.android.veecheck;

import static com.tomgibara.android.veecheck.Veecheck.LOG_TAG;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;
import org.xml.sax.SAXException;

import android.net.Uri;
import android.util.Log;
import android.util.Xml;
import android.util.Xml.Encoding;

/**
 * Instances of these threads are launched by a {@link VeecheckService} to do
 * the work of retrieving and processing a versions document to extract intent
 * information. The thread reports its completion back to the {@link VeecheckService}
 * that started it.
 * 
 * @author Tom Gibara
 */

public class VeecheckThread extends Thread {

	//timeouts are plucked out of thin air
	//not sure what to expect on a mobile network
	
	private static final int CONNECTION_TIMEOUT = 10 * 1000;

	private static final int SO_TIMEOUT = 10 * 1000;
	
	private static final Encoding DEFAULT_ENCODING = Encoding.UTF_8;

	private static final Pattern CHARSET = Pattern.compile("charset\\s*=\\s*([-_a-zA-Z0-9]+)");
	
	/**
	 * The service on whose behalf the thread is operating.
	 */
	
	private final VeecheckService service;
	
	/**
	 * The (unsubstituted) URI that was supplied to the service for checking.
	 */
	
	private final String uri;
	
	/**
	 * Constructs a new {@link Thread} that will check the specified URI for
	 * application updates on behalf of a {@link VeecheckService}.
	 * 
	 * @param service the service requesting the check
	 * @param uri the http URI from which to obtain the versions document
	 */
	
	public VeecheckThread(VeecheckService service, Uri uri) {
		this.service = service;
		this.uri = uri.toString();
	}
	
	@Override
	public void run() {
		VeecheckResult result = null;
		try {
			VeecheckVersion version = new VeecheckVersion(service);
			try {
				result = performRequest(version, uri);
			} catch (Exception e) {
				Log.w(LOG_TAG, "Failed to process versions.", e);
				return;
			} finally {
			}
			
			if (result.matched) {
				Log.d(LOG_TAG, "Matching intent found.");
			} else {
				result = null;
				Log.d(LOG_TAG, "No matching intent found.");
			}
			
		} finally {
			service.notifyAndStop(result);
		}
	}
	
	private VeecheckResult performRequest(VeecheckVersion version, String uri) throws ClientProtocolException, IOException, IllegalStateException, SAXException {
		HttpClient client = new DefaultHttpClient();
		//TODO ideally it should be possible to adjust these constants
		HttpParams params = client.getParams();
		HttpConnectionParams.setConnectionTimeout(params, CONNECTION_TIMEOUT);
		HttpConnectionParams.setSoTimeout(params, SO_TIMEOUT);
		HttpGet request = new HttpGet( version.substitute(uri) );
		HttpResponse response = client.execute(request);
		HttpEntity entity = response.getEntity();
		try {
			StatusLine line = response.getStatusLine();
			//TODO this is lazy, we should consider other codes here
			if (line.getStatusCode() != 200) throw new IOException("Request failed: " + line.getReasonPhrase());
			Header header = response.getFirstHeader(HTTP.CONTENT_TYPE);
			Encoding encoding = identityEncoding(header);
			VeecheckResult handler = new VeecheckResult(version);
			Xml.parse(entity.getContent(), encoding, handler);
			return handler;
		} finally {
			entity.consumeContent();
		}
	}

	private Encoding identityEncoding(Header header) {
		if (header == null) return DEFAULT_ENCODING;
		String value = header.getValue();
		Matcher matcher = CHARSET.matcher(value);
		if (!matcher.find()) return DEFAULT_ENCODING;
		String charset = matcher.group(1).replace("_", "").replace("-", "").toUpperCase();
		//we don't construct a static map for these
		//it will only get torn down when the application terminates
		if (charset.equals("UTF8")) return Encoding.UTF_8;
		if (charset.equals("USASCII")) return Encoding.US_ASCII;
		if (charset.equals("ASCII")) return Encoding.US_ASCII;
		if (charset.equals("ISO88591")) return Encoding.ISO_8859_1;
		if (charset.equals("UTF16")) return Encoding.UTF_16;
		return DEFAULT_ENCODING;
	}
	
}
