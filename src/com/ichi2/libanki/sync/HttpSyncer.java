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

import com.byarger.exchangeit.EasySSLSocketFactory;
import com.ichi2.async.Connection;
import com.ichi2.libanki.Utils;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnManagerPNames;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.SingleClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.zip.GZIPOutputStream;

public class HttpSyncer {

    private static final String BOUNDARY = "Anki-sync-boundary";
    public static final String ANKIWEB_STATUS_OK = "OK";

    public volatile long bytesSent=0;
    public volatile long bytesReceived=0;
    public volatile long mNextSendS = 1024;
    public volatile long mNextSendR = 1024;

    /**
     * Connection settings
     */
    public static final String SYNC_HOST = "beta.ankiweb.net";
    // TODO: correct https-address
    public static final String SYNC_URL = "http://" + SYNC_HOST + "/sync/"; // "http://219.108.60.108:6500/sync/";//
    public static final int SYNC_VER = 0;

    /**
     * Synchronization.
     */

    private String mHKey;
    private Connection mCon;

    public HttpSyncer(String hkey, Connection con) {
    	mHKey = hkey;
    	mCon = con;
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
	        OutputStreamProgress bos = new OutputStreamProgress(new ByteArrayOutputStream());
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

	        // body
	        httpPost.setEntity(entity);
	        httpPost.setHeader("Content-type", "multipart/form-data; boundary=" + BOUNDARY);

	        // https
	        SchemeRegistry schemeRegistry = new SchemeRegistry();
	        schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
	        schemeRegistry.register(new Scheme("https", new EasySSLSocketFactory(), 443));
	        HttpParams params = new BasicHttpParams();
	        params.setParameter(ConnManagerPNames.MAX_TOTAL_CONNECTIONS, 30);
	        params.setParameter(ConnManagerPNames.MAX_CONNECTIONS_PER_ROUTE, new ConnPerRouteBean(30));
	        params.setParameter(HttpProtocolParams.USE_EXPECT_CONTINUE, false);
	        HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);	         
	        ClientConnectionManager cm = new SingleClientConnManager(params, schemeRegistry);

	        DefaultHttpClient httpClient = new DefaultHttpClient(cm, params);
	        return httpClient.execute(httpPost);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			return null;
		}
    }


    public void writeToFile(InputStream source, String destination) throws IOException {
        new File(destination).createNewFile();

        OutputStream output = new BufferedOutputStream(new FileOutputStream(destination));

        byte[] buf = new byte[Utils.CHUNK_SIZE];
        int len;
        while ((len = source.read(buf)) > 0) {
            output.write(buf, 0, len);
            bytesReceived += len;
            publishProgress();
        }
        output.close();
    }

    public String stream2String(InputStream stream) {
        BufferedReader rd;
		try {
			rd = new BufferedReader(new InputStreamReader(stream, "UTF-8"), 4096);
	        String line;
	        StringBuilder sb = new StringBuilder();
	        while ((line = rd.readLine()) != null) {
	            sb.append(line);
	            bytesReceived += line.length();
	            publishProgress();
	        }
	        rd.close();
	        return sb.toString();
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
    }

	private void publishProgress() {
		if (mNextSendR <=  bytesReceived || mNextSendS <= bytesReceived) {
			long bR = bytesReceived / 1024;
			long bS = bytesSent / 1024;
			mNextSendR = (bR + 1) * 1024;
			mNextSendS = (bS / 1024 + 1) * 1024;
			mCon.publishProgress(0, bS, bR);
		}
	}

	public HttpResponse hostKey(String arg1, String arg2) {
		return null;
	}
	public JSONObject applyChanges(JSONObject kw) {
		return null;
	}
	public JSONObject start(JSONObject kw) {
		return null;
	}
	public JSONObject chunk(JSONObject kw) {
		return null;
	}
	public JSONObject chunk() {
		return null;
	}
	public long finish() {
		return 0;
	}
	public HttpResponse meta() {
		return null;
	}
	public JSONObject applyChunk(JSONObject kw) {
		return null;
	}
	public Object[] download() {
		return null;
	}
	public Object[] upload() {
		return null;
	}

	/*http://stackoverflow.com/questions/7057342/how-to-get-a-progress-bar-for-a-file-upload-with-apache-httpclient-4*/
	private class OutputStreamProgress extends OutputStream {

	    private final ByteArrayOutputStream outstream;

	    public OutputStreamProgress(ByteArrayOutputStream outstream) {
	        this.outstream = outstream;
	    }

	    @Override
	    public void write(int b) throws IOException {
	        outstream.write(b);
	        count(1);
	    }

	    @Override
	    public void write(byte[] b) throws IOException {
	        outstream.write(b);
	        count(b.length);
	    }

	    @Override
	    public void write(byte[] b, int off, int len) throws IOException {
	        outstream.write(b, off, len);
	        count(len);
	    }

	    @Override
	    public void flush() throws IOException {
	        outstream.flush();
	    }

	    @Override
	    public void close() throws IOException {
	        outstream.close();
	    }

	    public byte[] toByteArray() throws IOException {
	    	return outstream.toByteArray();
	    }

	    public void count(long count) throws IOException {
	        bytesSent += count;
	        publishProgress();
	    }
	}
}
