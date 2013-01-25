/***************************************************************************************
 * Copyright (c) 2012 Norbert Nagold <norbert.nagold@gmail.com>                         *
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
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Utils;

import org.apache.commons.httpclient.contrib.ssl.EasySSLSocketFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.params.ConnManagerPNames;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.SSLException;

public class BasicHttpSyncer implements HttpSyncer {

    private static final String BOUNDARY = "Anki-sync-boundary";
    public static final String ANKIWEB_STATUS_OK = "OK";

    public volatile long bytesSent = 0;
    public volatile long bytesReceived = 0;
    public volatile long mNextSendS = 1024;
    public volatile long mNextSendR = 1024;

    /**
     * Synchronization.
     */

    private String mHKey;
    private Connection mCon;


    public BasicHttpSyncer(String hkey, Connection con) {
        mHKey = hkey;
        mCon = con;
    }


    public HttpResponse req(String method) {
        return req(method, null);
    }


    public HttpResponse req(String method, InputStream fobj) {
        return req(method, fobj, 6, true);
    }


    public HttpResponse req(String method, int comp, InputStream fobj) {
        return req(method, fobj, comp, true);
    }


    public HttpResponse req(String method, InputStream fobj, boolean hkey) {
        return req(method, fobj, 6, hkey);
    }


    public HttpResponse req(String method, InputStream fobj, int comp, boolean hkey) {
        return req(method, fobj, comp, hkey, null);
    }

    public HttpResponse req(String method, InputStream fobj, int comp, boolean hkey, JSONObject registerData) {
        return req(method, fobj, comp, hkey, registerData, null);
    }

    public HttpResponse req(String method, InputStream fobj, int comp, boolean hkey, JSONObject registerData,
                            Connection.CancelCallback cancelCallback) {
        File tmpFileBuffer = null;
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
            tmpFileBuffer = File.createTempFile("syncer", ".tmp", new File(AnkiDroidApp.getCacheStorageDirectory()));
            FileOutputStream fos = new FileOutputStream(tmpFileBuffer);
            BufferedOutputStream bos = new BufferedOutputStream(fos);
            GZIPOutputStream tgt;
            // payload as raw data or json
            if (fobj != null) {
                // header
                buf.write(bdry + "\r\n");
                buf.write("Content-Disposition: form-data; name=\"data\"; filename=\"data\"\r\nContent-Type: application/octet-stream\r\n\r\n");
                buf.close();
                bos.write(buf.toString().getBytes("UTF-8"));
                // write file into buffer, optionally compressing
                int len;
                BufferedInputStream bfobj = new BufferedInputStream(fobj);
                byte[] chunk = new byte[65536];
                if (comp != 0) {
                    tgt = new GZIPOutputStream(bos);
                    while ((len = bfobj.read(chunk)) >= 0) {
                        tgt.write(chunk, 0, len);
                    }
                    tgt.close();
                    bos = new BufferedOutputStream(new FileOutputStream(tmpFileBuffer, true));
                } else {
                    while ((len = bfobj.read(chunk)) >= 0) {
                        bos.write(chunk, 0, len);
                    }
                }
                bos.write(("\r\n" + bdry + "--\r\n").getBytes("UTF-8"));
            } else {
                buf.close();
                bos.write(buf.toString().getBytes("UTF-8"));
            }
            bos.flush();
            bos.close();
            // connection headers
            String url = Collection.SYNC_URL;
            if (method.equals("register")) {
                url = url + "account/signup" + "?username=" + registerData.getString("u") + "&password="
                        + registerData.getString("p");
            } else if (method.startsWith("upgrade")) {
                url = url + method;
            } else {
                url = url + "sync/" + method;
            }
            HttpPost httpPost = new HttpPost(url);
            HttpEntity entity = new ProgressByteEntity(tmpFileBuffer);

            // body
            httpPost.setEntity(entity);
            httpPost.setHeader("Content-type", "multipart/form-data; boundary=" + BOUNDARY);

            // HttpParams
            HttpParams params = new BasicHttpParams();
            params.setParameter(ConnManagerPNames.MAX_TOTAL_CONNECTIONS, 30);
            params.setParameter(ConnManagerPNames.MAX_CONNECTIONS_PER_ROUTE, new ConnPerRouteBean(30));
            params.setParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE, false);
            params.setParameter(CoreProtocolPNames.USER_AGENT, "AnkiDroid-" + AnkiDroidApp.getPkgVersion());
            HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
            HttpConnectionParams.setSoTimeout(params, Connection.CONN_TIMEOUT);

            // Registry
            SchemeRegistry registry = new SchemeRegistry();
            registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
            registry.register(new Scheme("https", new EasySSLSocketFactory(), 443));
            ThreadSafeClientConnManager cm = new ThreadSafeClientConnManager(params, registry);
            if (cancelCallback != null) {
                cancelCallback.setConnectionManager(cm);
            }

            try {
                HttpClient httpClient = new DefaultHttpClient(cm, params);
                return httpClient.execute(httpPost);
            } catch (SSLException e) {
                Log.e(AnkiDroidApp.TAG, "SSLException while building HttpClient", e);
                return null;
            }
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            Log.e(AnkiDroidApp.TAG, "BasicHttpSyncer.sync: IOException", e);
            return null;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        } finally {
            if (tmpFileBuffer != null && tmpFileBuffer.exists()) {
                tmpFileBuffer.delete();
            }
        }
    }


    public boolean writeToFile(InputStream source, String destination) {
        File file = new File(destination);
        OutputStream output = null;
        try {
            file.createNewFile();
            output = new BufferedOutputStream(new FileOutputStream(file));
            byte[] buf = new byte[Utils.CHUNK_SIZE];
            int len;
            while ((len = source.read(buf)) >= 0) {
                output.write(buf, 0, len);
                bytesReceived += len;
                publishProgress();
            }
            output.close();
            return true;
        } catch (IOException e) {
            try {
                output.close();
            } catch (IOException e1) {
                // do nothing
            }
            // no write access or sd card full
            file.delete();
            return false;
        }
    }


    public String stream2String(InputStream stream) {
    	return stream2String(stream, -1);
    }
    public String stream2String(InputStream stream, int maxSize) {
        BufferedReader rd;
        try {
            rd = new BufferedReader(new InputStreamReader(stream, "UTF-8"), maxSize == -1 ? 4096 : Math.min(4096, maxSize));
            String line;
            StringBuilder sb = new StringBuilder();
            while ((line = rd.readLine()) != null && (maxSize == -1 || sb.length() < maxSize)) {
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
        if (mCon != null && (mNextSendR <= bytesReceived || mNextSendS <= bytesSent)) {
            long bR = bytesReceived;
            long bS = bytesSent;
            mNextSendR = (bR / 1024 + 1) * 1024;
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


    public Object[] download() {
        return null;
    }


    public Object[] upload() {
        return null;
    }


    public JSONArray sanityCheck() {
        return null;
    }


    public void applyChunk(JSONObject sech) {
    }

    public class ProgressByteEntity extends AbstractHttpEntity {

        private InputStream mInputStream;
        private long mLength;


        public ProgressByteEntity(File file) {
            super();
            mLength = file.length();
            try {
                mInputStream = new BufferedInputStream(new FileInputStream(file));
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        }


        @Override
        public void writeTo(OutputStream outstream) throws IOException {
            try {
                byte[] tmp = new byte[4096];
                int len;
                while ((len = mInputStream.read(tmp)) != -1) {
                    outstream.write(tmp, 0, len);
                    bytesSent += len;
                    publishProgress();
                }
                outstream.flush();
            } finally {
                mInputStream.close();
            }
        }


        @Override
        public InputStream getContent() throws IOException, IllegalStateException {
            return mInputStream;
        }


        @Override
        public long getContentLength() {
            return mLength;
        }


        @Override
        public boolean isRepeatable() {
            return false;
        }


        @Override
        public boolean isStreaming() {
            return false;
        }
    }


    public static ByteArrayInputStream getInputStream(String string) {
        try {
            return new ByteArrayInputStream(string.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            Log.e(AnkiDroidApp.TAG, "HttpSyncer: error on getting bytes from string: " + e);
            return null;
        }
    }


    public HttpResponse register(String user, String pw) {
        return null;
    }
}
