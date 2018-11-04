/****************************************************************************************
 * Copyright (c) 2013 Bibek Shrestha <bibekshrestha@gmail.com>                          *
 * Copyright (c) 2013 Zaur Molotnikov <qutorial@gmail.com>                              *
 * Copyright (c) 2013 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>                                   *
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

import com.ichi2.compat.CompatHelper;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

/**
 * Helper class to download from web.
 * <p>
 * Used in AsyncTasks in Translation and Pronunciation activities, and more...
 */
@SuppressWarnings("deprecation") // tracking HTTP transport change in github already
public class HttpFetcher {

    public static String fetchThroughHttp(String address) {
        return fetchThroughHttp(address, "utf-8");
    }


    public static String fetchThroughHttp(String address, String encoding) {

        try {
            org.apache.http.client.HttpClient httpClient = new org.apache.http.impl.client.DefaultHttpClient();
            org.apache.http.params.HttpParams params = httpClient.getParams();
            org.apache.http.params.HttpConnectionParams.setConnectionTimeout(params, 10000);
            org.apache.http.params.HttpConnectionParams.setSoTimeout(params, 60000);
            org.apache.http.protocol.HttpContext localContext = new org.apache.http.protocol.BasicHttpContext();
            org.apache.http.client.methods.HttpGet httpGet = new org.apache.http.client.methods.HttpGet(address);
            org.apache.http.HttpResponse response = httpClient.execute(httpGet, localContext);
            if (response.getStatusLine().getStatusCode() != 200) {
                return "FAILED";
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent(),
                    Charset.forName(encoding)));

            StringBuilder stringBuilder = new StringBuilder();

            String line = null;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }

            return stringBuilder.toString();

        } catch (Exception e) {
            return "FAILED with exception: " + e.getMessage();
        }

    }


    // public static String downloadFileToCache(String UrlToFile, Context context, String prefix)
    // {
    // try
    // {
    // URL url = new URL(UrlToFile);
    //
    // String extension = UrlToFile.substring(UrlToFile.length() - 4);
    //
    // HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
    // urlConnection.setRequestMethod("GET");
    // urlConnection.setDoOutput(true);
    // urlConnection.connect();
    //
    // File outputDir = context.getCacheDir();
    // File file = File.createTempFile(prefix, extension, outputDir);
    //
    // FileOutputStream fileOutput = new FileOutputStream(file);
    // InputStream inputStream = urlConnection.getInputStream();
    //
    // byte[] buffer = new byte[1024];
    // int bufferLength = 0;
    //
    // while ((bufferLength = inputStream.read(buffer)) > 0)
    // {
    // fileOutput.write(buffer, 0, bufferLength);
    // }
    // fileOutput.close();
    //
    // return file.getAbsolutePath();
    //
    // }
    // catch (Exception e)
    // {
    // return "FAILED " + e.getMessage();
    // }
    // }

    public static String downloadFileToSdCard(String UrlToFile, Context context, String prefix) {
        String str = downloadFileToSdCardMethod(UrlToFile, context, prefix, "GET");
        if (str.startsWith("FAIL")) {
            str = downloadFileToSdCardMethod(UrlToFile, context, prefix, "POST");
        }

        return str;
    }


    public static String downloadFileToSdCardMethod(String UrlToFile, Context context, String prefix, String method) {
        try {
            URL url = new URL(UrlToFile);

            String extension = UrlToFile.substring(UrlToFile.length() - 4);

            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod(method);
            urlConnection.setRequestProperty("Referer", "com.ichi2.anki");
            urlConnection.setRequestProperty("User-Agent", "Mozilla/5.0 ( compatible ) ");
            urlConnection.setRequestProperty("Accept", "*/*");
			urlConnection.setConnectTimeout(10000);
			urlConnection.setReadTimeout(60000);
            urlConnection.connect();

            File file = File.createTempFile(prefix, extension, context.getCacheDir());
            InputStream inputStream = urlConnection.getInputStream();
            CompatHelper.getCompat().copyFile(inputStream, file.getCanonicalPath());
            inputStream.close();
            return file.getAbsolutePath();

        } catch (Exception e) {
            return "FAILED " + e.getMessage();
        }
    }

}
