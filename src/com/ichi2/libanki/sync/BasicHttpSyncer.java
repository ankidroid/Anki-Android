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
import com.ichi2.anki2.R;

import android.content.SharedPreferences;
import android.util.Log;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.async.Connection;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Utils;
import com.tomgibara.android.veecheck.util.PrefSettings;

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
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.SingleClientConnManager;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.security.KeyStore;
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


    public HttpResponse req(String method, InputStream fobj, boolean hkey) {
        return req(method, fobj, 6, hkey);
    }


    public HttpResponse req(String method, InputStream fobj, int comp, boolean hkey) {
        return req(method, fobj, comp, hkey, null);
    }


    public HttpResponse req(String method, InputStream fobj, int comp, boolean hkey, JSONObject registerData) {
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
                bos.write(buf.toString().getBytes("UTF-8"));
                // write file into buffer, optionally compressing
                int len;
                BufferedInputStream bfobj = new BufferedInputStream(fobj);
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
                    tgt.close();
                }
                bos.write(("\r\n" + bdry + "--\r\n").getBytes("UTF-8"));
            } else {
                buf.close();
                bos.write(buf.toString().getBytes("UTF-8"));
            }
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
            HttpEntity entity = new ProgressByteEntity(bos.toByteArray());

            // body
            httpPost.setEntity(entity);
            httpPost.setHeader("Content-type", "multipart/form-data; boundary=" + BOUNDARY);

            SharedPreferences preferences = PrefSettings.getSharedPrefs(AnkiDroidApp.getInstance()
                    .getApplicationContext());

            SchemeRegistry schemeRegistry = new SchemeRegistry();
            schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
            if (preferences.getBoolean("sslAcceptAll", true)) {
                Log.e(AnkiDroidApp.TAG, "SSL certificate check is disabled");
                schemeRegistry.register(new Scheme("https", new EasySSLSocketFactory(), 443));
            } else {
                schemeRegistry.register(new Scheme("https",
                        newSslSocketFactory(SSLSocketFactory.STRICT_HOSTNAME_VERIFIER), 443));
            }

            HttpParams params = new BasicHttpParams();
            params.setParameter(ConnManagerPNames.MAX_TOTAL_CONNECTIONS, 30);
            params.setParameter(ConnManagerPNames.MAX_CONNECTIONS_PER_ROUTE, new ConnPerRouteBean(30));
            params.setParameter(HttpProtocolParams.USE_EXPECT_CONTINUE, false);
            HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);

            ThreadSafeClientConnManager cm = new ThreadSafeClientConnManager(params, schemeRegistry);
            try {
                DefaultHttpClient httpClient = new DefaultHttpClient(cm, params);
                return httpClient.execute(httpPost);
            } catch (SSLException e) {
                // SSL cert error: might be related to a bug. as a workaround we accept all certs, if necessary
                // prevent loop
                if (!preferences.getBoolean("sslAcceptAll", false)) {
                    Log.e(AnkiDroidApp.TAG, "workaround for android < 3.0: disabling ssl certificate check");
                    preferences.edit().putBoolean("sslAcceptAll", true).commit();
                    return req(method, fobj, comp, hkey, registerData);
                } else {
                    return null;
                }
            }
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            return null;
        } catch (JSONException e) {
            throw new RuntimeException(e);
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
            while ((len = source.read(buf)) > 0) {
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


        public ProgressByteEntity(byte[] byteArray) {
            super();
            mLength = byteArray.length;
            mInputStream = new ByteArrayInputStream(byteArray);
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


    private SSLSocketFactory newSslSocketFactory(X509HostnameVerifier ver) {
        try {
            KeyStore trusted = KeyStore.getInstance("BKS");
            InputStream in = AnkiDroidApp.getInstance().getApplicationContext().getResources()
                    .openRawResource(R.raw.ankiweb_cert);
            try {
                trusted.load(in, "mysecret".toCharArray());
            } finally {
                in.close();
            }
            SSLSocketFactory sf = new SSLSocketFactory(trusted);
            sf.setHostnameVerifier(ver);
            return sf;
        } catch (Exception e) {
            Log.e(AnkiDroidApp.TAG, "Certificate error");
            // to update the ankiweb.cert:
            // 1. get http://bouncycastle.org/download/bcprov-jdk16-145.jar
            // 2. keytool -importcert -v -trustcacerts -file "path_to_cert/interm_ca.cer" -alias IntermediateCA
            // -keystore "res/raw/ankiweb_cert" -provider org.bouncycastle.jce.provider.BouncyCastleProvider
            // -providerpath "path_to_bouncycastle/bcprov-jdk16-145.jar" -storetype BKS -storepass mysecret
            // 3. copy ankiweb_cert to res/raw/
            throw new AssertionError(e);
        }
    }

}
