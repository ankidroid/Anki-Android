/****************************************************************************************
 * Copyright (c) 2013 Bibek Shrestha <bibekshrestha@gmail.com>                          *
 * Copyright (c) 2013 Zaur Molotnikov <qutorial@gmail.com>                              *
 * Copyright (c) 2013 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>                                   *
 * Copyright (c) 2020 Mike Hardy <github@mikehardy.net>                                 *
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

package com.ichi2.anki.web;

import android.content.Context;

import com.ichi2.async.Connection;
import com.ichi2.compat.CompatHelper;
import com.ichi2.libanki.sync.Tls12SocketFactory;
import com.ichi2.utils.VersionUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import timber.log.Timber;

/**
 * Helper class to download from web.
 * <p>
 * Used in AsyncTasks in Translation and Pronunciation activities, and more...
 */
public class HttpFetcher {

    /**
     * Get an OkHttpClient configured with correct timeouts and headers
     *
     * @param fakeUserAgent true if we should issue "fake" User-Agent header 'Mozilla/5.0' for compatibility
     * @return OkHttpClient.Builder ready for use or further configuration
     */
    public static OkHttpClient.Builder getOkHttpBuilder(boolean fakeUserAgent) {
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();
        Tls12SocketFactory.enableTls12OnPreLollipop(clientBuilder)
                .connectTimeout(Connection.CONN_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(Connection.CONN_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(Connection.CONN_TIMEOUT, TimeUnit.SECONDS);

        if (fakeUserAgent) {
            clientBuilder.addNetworkInterceptor(chain -> chain.proceed(
                    chain.request()
                            .newBuilder()
                            .header("Referer", "com.ichi2.anki")
                            .header("User-Agent", "Mozilla/5.0 ( compatible ) ")
                            .header("Accept", "*/*")
                            .build()
            ));
        } else {
            clientBuilder.addNetworkInterceptor(chain -> chain.proceed(
                    chain.request()
                            .newBuilder()
                            .header("User-Agent", "AnkiDroid-" + VersionUtils.getPkgVersionName())
                            .build()
            ));
        }
        return clientBuilder;
    }

    public static String fetchThroughHttp(String address) {
        return fetchThroughHttp(address, "utf-8");
    }

    public static String fetchThroughHttp(String address, String encoding) {

        Timber.d("fetching %s", address);
        Response response = null;

        try {
            Request.Builder requestBuilder = new Request.Builder();
            requestBuilder.url(address).get();
            Request httpGet = requestBuilder.build();

            OkHttpClient client = getOkHttpBuilder(true).build();
            response = client.newCall(httpGet).execute();


            if (response.code() != 200) {
                Timber.d("Response code was %s, returning failure", response.code());
                return "FAILED";
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(response.body().byteStream(),
                    Charset.forName(encoding)));

            StringBuilder stringBuilder = new StringBuilder();

            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }

            return stringBuilder.toString();

        } catch (Exception e) {
            Timber.d(e, "Failed with an exception");
            return "FAILED with exception: " + e.getMessage();
        } finally {
            if (response != null && response.body() != null) {
                response.body().close();
            }
        }
    }


    public static String downloadFileToSdCard(String UrlToFile, Context context, String prefix) {
        String str = downloadFileToSdCardMethod(UrlToFile, context, prefix, "GET");
        if (str.startsWith("FAIL")) {
            str = downloadFileToSdCardMethod(UrlToFile, context, prefix, "POST");
        }

        return str;
    }


    public static String downloadFileToSdCardMethod(String UrlToFile, Context context, String prefix, String method) {

        Response response = null;

        try {
            URL url = new URL(UrlToFile);

            String extension = UrlToFile.substring(UrlToFile.length() - 4);

            Request.Builder requestBuilder = new Request.Builder();
            requestBuilder.url(url);
            if ("GET".equals(method)) {
                requestBuilder.get();
            } else {
                requestBuilder.post(RequestBody.create(new byte[0], null));
            }
            Request request = requestBuilder.build();
            OkHttpClient client = getOkHttpBuilder(true).build();
            response = client.newCall(request).execute();

            File file = File.createTempFile(prefix, extension, context.getCacheDir());
            InputStream inputStream = response.body().byteStream();
            CompatHelper.getCompat().copyFile(inputStream, file.getCanonicalPath());
            inputStream.close();
            return file.getAbsolutePath();

        } catch (Exception e) {
            Timber.w(e);
            return "FAILED " + e.getMessage();
        } finally {
            if (response != null && response.body() != null) {
                response.body().close();
            }
        }
    }

}
