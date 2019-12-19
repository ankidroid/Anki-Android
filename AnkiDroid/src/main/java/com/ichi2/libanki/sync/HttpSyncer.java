/***************************************************************************************
 * Copyright (c) 2012 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
 * Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>                          *
 * Copyright (c) 2019 Mike Hardy <github@mikehardy.net>                                 *
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


import android.content.SharedPreferences;
import android.net.Uri;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.exception.UnknownHttpResponseException;
import com.ichi2.async.Connection;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.Utils;
import com.ichi2.utils.VersionUtils;

import org.apache.http.entity.AbstractHttpEntity;
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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.SSLException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import timber.log.Timber;

/**
 * # HTTP syncing tools
 * Calling code should catch the following codes:
 * - 501: client needs upgrade
 * - 502: ankiweb down
 * - 503/504: server too busy
 */
@SuppressWarnings( {"PMD.AvoidThrowingRawExceptionTypes", "PMD.NPathComplexity"})
public class HttpSyncer {

    private static final String BOUNDARY = "Anki-sync-boundary";
    private static final MediaType ANKI_POST_TYPE = MediaType.get("multipart/form-data; boundary=" + BOUNDARY);

    public static final String ANKIWEB_STATUS_OK = "OK";

    public volatile long bytesSent = 0;
    public volatile long bytesReceived = 0;
    public volatile long mNextSendS = 1024;
    public volatile long mNextSendR = 1024;

    /**
     * Synchronization.
     */

    protected String mHKey;
    protected String mSKey;
    protected Connection mCon;
    protected Map<String, Object> mPostVars;


    public HttpSyncer(String hkey, Connection con) {
        mHKey = hkey;
        mSKey = Utils.checksum(Float.toString(new Random().nextFloat())).substring(0, 8);
        mCon = con;
        mPostVars = new HashMap<>();
    }


    public void assertOk(Response resp) throws UnknownHttpResponseException {
        // Throw RuntimeException if HTTP error
        if (resp == null) {
            throw new UnknownHttpResponseException("Null HttpResponse", -2);
        }
        int resultCode = resp.code();
        if (!(resultCode == 200 || resultCode == 403)) {
            String reason = resp.message();
            throw new UnknownHttpResponseException(reason, resultCode);
        }
    }


    public Response req(String method) throws UnknownHttpResponseException {
        return req(method, null);
    }


    public Response req(String method, InputStream fobj) throws UnknownHttpResponseException {
        return req(method, fobj, 6);
    }


    public Response req(String method, int comp, InputStream fobj) throws UnknownHttpResponseException {
        return req(method, fobj, comp);
    }


    public Response req(String method, InputStream fobj, int comp) throws UnknownHttpResponseException {
        return req(method, fobj, comp, null);
    }


    private Response req(String method, InputStream fobj, int comp, JSONObject registerData) throws UnknownHttpResponseException {
        File tmpFileBuffer = null;
        try {
            String bdry = "--" + BOUNDARY;
            StringWriter buf = new StringWriter();
            // post vars
            mPostVars.put("c", comp != 0 ? 1 : 0);
            for (String key : mPostVars.keySet()) {
                buf.write(bdry + "\r\n");
                buf.write(String.format(Locale.US, "Content-Disposition: form-data; name=\"%s\"\r\n\r\n%s\r\n", key,
                        mPostVars.get(key)));
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
                bos.write((bdry + "--\r\n").getBytes("UTF-8"));
            }
            bos.flush();
            bos.close();
            // connection headers
            String url = Consts.SYNC_BASE;
            if ("register".equals(method)) {
                url = url + "account/signup" + "?username=" + registerData.getString("u") + "&password="
                        + registerData.getString("p");
            } else if (method.startsWith("upgrade")) {
                url = url + method;
            } else {
                url = syncURL() + method;
            }

            Request.Builder requestBuilder = new Request.Builder();
            requestBuilder.url(url);

            requestBuilder.post(new CountingFileRequestBody(tmpFileBuffer, ANKI_POST_TYPE.toString(), num -> {
                bytesSent += num;
                publishProgress();
            }));
            Request httpPost = requestBuilder.build();

            try {
                OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder().addNetworkInterceptor(chain -> chain.proceed(
                        chain.request()
                                .newBuilder()
                                .header("User-Agent", "AnkiDroid-" + VersionUtils.getPkgVersionName())
                                .build()
                ));
                Tls12SocketFactory.enableTls12OnPreLollipop(clientBuilder)
                        .followRedirects(true)
                        .followSslRedirects(true)
                        .retryOnConnectionFailure(true)
                        .cache(null)
                        .connectTimeout(Connection.CONN_TIMEOUT, TimeUnit.SECONDS)
                        .writeTimeout(Connection.CONN_TIMEOUT, TimeUnit.SECONDS)
                        .readTimeout(Connection.CONN_TIMEOUT, TimeUnit.SECONDS);
                OkHttpClient httpClient = clientBuilder.build();
                Response httpResponse = httpClient.newCall(httpPost).execute();

                // we assume badAuthRaises flag from Anki Desktop always False
                // so just throw new RuntimeException if response code not 200 or 403
                Timber.d("TLSVersion in use is: %s",
                        (httpResponse.handshake() != null ? httpResponse.handshake().tlsVersion() : "unknown"));


                assertOk(httpResponse);
                return httpResponse;
            } catch (SSLException e) {
                Timber.e(e, "SSLException while building HttpClient");
                throw new RuntimeException("SSLException while building HttpClient");
            }
        } catch (UnsupportedEncodingException | JSONException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            Timber.e(e, "BasicHttpSyncer.sync: IOException");
            throw new RuntimeException(e);
        } finally {
            if (tmpFileBuffer != null && tmpFileBuffer.exists()) {
                tmpFileBuffer.delete();
            }
        }
    }


    // Could be replaced by Compat copy method if that method took listener for bytesReceived/publishProgress()
    public void writeToFile(InputStream source, String destination) throws IOException {
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
        } catch (IOException e) {
            if (file.exists()) {
                // Don't keep the file if something went wrong. It'll be corrupt.
                file.delete();
            }
            // Re-throw so we know what the error was.
            throw e;
        } finally {
            if (output != null) {
                output.close();
            }
        }
    }


    public String stream2String(InputStream stream, int maxSize) {
        BufferedReader rd;
        try {
            rd = new BufferedReader(new InputStreamReader(stream, "UTF-8"), maxSize == -1 ? 4096 : Math.min(4096,
                    maxSize));
            String line;
            StringBuilder sb = new StringBuilder();
            while ((line = rd.readLine()) != null && (maxSize == -1 || sb.length() < maxSize)) {
                sb.append(line);
                bytesReceived += line.length();
                publishProgress();
            }
            rd.close();
            return sb.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    private void publishProgress() {
        Timber.d("Publishing progress");
        if (mCon != null && (mNextSendR <= bytesReceived || mNextSendS <= bytesSent)) {
            long bR = bytesReceived;
            long bS = bytesSent;
            Timber.d("Current progress: %d, %d", bytesReceived, bytesSent);
            mNextSendR = (bR / 1024 + 1) * 1024;
            mNextSendS = (bS / 1024 + 1) * 1024;
            mCon.publishProgress(0, bS, bR);
        }
    }


    public Response hostKey(String arg1, String arg2) throws UnknownHttpResponseException {
        return null;
    }


    public JSONObject applyChanges(JSONObject kw) throws UnknownHttpResponseException {
        return null;
    }


    public JSONObject start(JSONObject kw) throws UnknownHttpResponseException {
        return null;
    }


    public JSONObject chunk() throws UnknownHttpResponseException {
        return null;
    }


    public long finish() throws UnknownHttpResponseException {
        return 0;
    }


    public void abort() throws UnknownHttpResponseException {
        // do nothing
    }


    public Response meta() throws UnknownHttpResponseException {
        return null;
    }


    public Object[] download() throws UnknownHttpResponseException {
        return null;
    }


    public Object[] upload() throws UnknownHttpResponseException {
        return null;
    }


    public JSONObject sanityCheck2(JSONObject client) throws UnknownHttpResponseException {
        return null;
    }


    public void applyChunk(JSONObject sech) throws UnknownHttpResponseException {
        // do nothing
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
        public InputStream getContent() throws IllegalStateException {
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
            Timber.e(e, "HttpSyncer: error on getting bytes from string");
            return null;
        }
    }


    public String syncURL() {
        // Allow user to specify custom sync server
        SharedPreferences userPreferences = AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance());
        if (userPreferences != null && userPreferences.getBoolean("useCustomSyncServer", false)) {
            Uri syncBase = Uri.parse(userPreferences.getString("syncBaseUrl", Consts.SYNC_BASE));
            return syncBase.buildUpon().appendPath("sync").toString() + "/";
        }
        // Usual case
        return Consts.SYNC_BASE + "sync/";
    }
}


