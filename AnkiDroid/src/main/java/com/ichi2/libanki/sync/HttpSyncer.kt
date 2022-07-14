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
import com.ichi2.anki.web.CustomSyncServer;
import com.ichi2.anki.web.HttpFetcher;
import com.ichi2.async.Connection;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.Utils;
import com.ichi2.utils.HashUtil;


import com.ichi2.utils.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.SSLException;

import androidx.annotation.Nullable;
import okhttp3.HttpUrl;
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

    public final AtomicLong bytesSent = new AtomicLong();
    public final AtomicLong bytesReceived = new AtomicLong();
    public volatile long mNextSendS = 1024;
    public volatile long mNextSendR = 1024;

    /**
     * Synchronization.
     */

    protected final String mHKey;
    protected String mSKey;
    protected final Connection mCon;
    protected Map<String, Object> mPostVars;
    private volatile OkHttpClient mHttpClient;
    private final HostNum mHostNum;

    public HttpSyncer(String hkey, Connection con, HostNum hostNum) {
        mHKey = hkey;
        mSKey = Utils.checksum(Float.toString(new Random().nextFloat())).substring(0, 8);
        mCon = con;
        mPostVars = HashUtil.HashMapInit(0); // New map is created each time it is filled. No need to allocate room
        mHostNum = hostNum;
    }

    private OkHttpClient getHttpClient() {
        if (this.mHttpClient != null) {
            return mHttpClient;
        }
        return setupHttpClient();
    }

    //PERF: Thread safety isn't required for the current implementation
    private synchronized OkHttpClient setupHttpClient() {
        if (mHttpClient != null) {
            return mHttpClient;
        }
        mHttpClient = HttpFetcher.getOkHttpBuilder(false)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .cache(null)
                .build();
        return mHttpClient;
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

    /** Note: Return value must be closed */
    public Response req(String method) throws UnknownHttpResponseException {
        return req(method, null);
    }

    /** Note: Return value must be closed */
    public Response req(String method, InputStream fobj) throws UnknownHttpResponseException {
        return req(method, fobj, 6);
    }

    /** Note: Return value must be closed */
    @SuppressWarnings("CharsetObjectCanBeUsed")
    public Response req(String method, InputStream fobj, int comp) throws UnknownHttpResponseException {
        File tmpFileBuffer = null;
        try {
            String bdry = "--" + BOUNDARY;
            StringWriter buf = new StringWriter();
            // post vars
            mPostVars.put("c", comp != 0 ? 1 : 0);
            for (Map.Entry<String, Object> entry : mPostVars.entrySet()) {
                buf.write(bdry + "\r\n");
                buf.write(String.format(Locale.US, "Content-Disposition: form-data; name=\"%s\"\r\n\r\n%s\r\n", entry.getKey(),
                        entry.getValue()));
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

            String url = Uri.parse(syncURL()).buildUpon().appendPath(method).toString();

            Request.Builder requestBuilder = new Request.Builder();
            requestBuilder.url(parseUrl(url));

            // Set our request up to count upstream traffic including headers
            requestBuilder.post(new CountingFileRequestBody(tmpFileBuffer, ANKI_POST_TYPE.toString(), num -> {
                bytesSent.addAndGet(num);
                publishProgress();
            }));
            Request httpPost = requestBuilder.build();
            bytesSent.addAndGet(httpPost.headers().byteCount());
            publishProgress();

            try {
                OkHttpClient httpClient = getHttpClient();
                Response httpResponse = httpClient.newCall(httpPost).execute();

                // we assume badAuthRaises flag from Anki Desktop always False
                // so just throw new RuntimeException if response code not 200 or 403
                Timber.d("TLSVersion in use is: %s",
                        (httpResponse.handshake() != null ? httpResponse.handshake().tlsVersion() : "unknown"));


                // Count downstream traffic including headers
                bytesReceived.addAndGet(httpResponse.headers().byteCount());
                try {
                    bytesReceived.addAndGet(httpResponse.body().contentLength());
                } catch (NullPointerException npe) {
                    Timber.d(npe, "Unexpected null response body");
                }
                publishProgress();

                assertOk(httpResponse);
                return httpResponse;
            } catch (SSLException e) {
                Timber.e(e, "SSLException while building HttpClient");
                throw new RuntimeException("SSLException while building HttpClient", e);
            }
        } catch (UnsupportedEncodingException e) {
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


    private HttpUrl parseUrl(String url) {
        // #5843 - show better exception if the URL is invalid
        try {
            return HttpUrl.get(url);
        } catch (IllegalArgumentException ex) {
            if (isUsingCustomSyncServer(AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance()))) {
                throw new CustomSyncServerUrlException(url, ex);
            } else {
                throw ex;
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
                bytesReceived.addAndGet(len);
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


    @SuppressWarnings("CharsetObjectCanBeUsed")
    public String stream2String(InputStream stream, int maxSize) {
        BufferedReader rd;
        try {
            rd = new BufferedReader(new InputStreamReader(stream, "UTF-8"), maxSize == -1 ? 4096 : Math.min(4096,
                    maxSize));
            String line;
            StringBuilder sb = new StringBuilder();
            while ((line = rd.readLine()) != null && (maxSize == -1 || sb.length() < maxSize)) {
                sb.append(line);
                bytesReceived.addAndGet(line.length());
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
        if (mCon != null && (mNextSendR <= bytesReceived.get() || mNextSendS <= bytesSent.get())) {
            long bR = bytesReceived.get();
            long bS = bytesSent.get();
            Timber.d("Current progress: %d, %d", bR, bS);
            mNextSendR = (bR / 1024 + 1) * 1024;
            mNextSendS = (bS / 1024 + 1) * 1024;
            mCon.publishProgress(0, bS, bR);
        }
    }


    @SuppressWarnings("CharsetObjectCanBeUsed")
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
        if (isUsingCustomSyncServer(userPreferences)) {
            String syncBaseString = CustomSyncServer.getSyncBaseUrl(userPreferences);
            if (syncBaseString == null) {
                return getDefaultAnkiWebUrl();
            }
            return Uri.parse(syncBaseString).buildUpon().appendPath(getUrlPrefix()).toString() + "/";
        }
        // Usual case
        return getDefaultAnkiWebUrl();
    }

    protected String getUrlPrefix() {
        return "sync";
    }

    protected Integer getHostNum() {
        return mHostNum.getHostNum();
    }

    protected boolean isUsingCustomSyncServer(@Nullable SharedPreferences userPreferences) {
        return userPreferences != null && CustomSyncServer.isEnabled(userPreferences);
    }

    protected String getDefaultAnkiWebUrl() {
        String hostNumAsStringFormat = "";
        Integer hostNum = getHostNum();
        if (hostNum != null) {
            hostNumAsStringFormat = hostNum.toString();
        }
        return String.format(Consts.SYNC_BASE, hostNumAsStringFormat) + getUrlPrefix() + "/";
    }
}


