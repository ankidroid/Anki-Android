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

import com.ichi2.utils.DiskUtil;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

/**
 * Helper class to donwload from web.
 * <p>
 * Used in AsyncTasks in Translation and Pronunication activities, and more...
 */
public class HttpFetcher {

    public static String fetchThroughHttp(String address) {
        return fetchThroughHttp(address, "utf-8");
    }


    public static String fetchThroughHttp(String address, String encoding) {

        try {
            HttpClient httpClient = new DefaultHttpClient();
            HttpContext localContext = new BasicHttpContext();
            HttpGet httpGet = new HttpGet(address);
            HttpResponse response = httpClient.execute(httpGet, localContext);
            if (!response.getStatusLine().toString().contains("OK")) {
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
            urlConnection.connect();

            File file = File.createTempFile(prefix, extension, DiskUtil.getStoringDirectory());

            FileOutputStream fileOutput = new FileOutputStream(file);
            InputStream inputStream = urlConnection.getInputStream();

            byte[] buffer = new byte[1024];
            int bufferLength = 0;

            while ((bufferLength = inputStream.read(buffer)) > 0) {
                fileOutput.write(buffer, 0, bufferLength);
            }
            fileOutput.close();

            return file.getAbsolutePath();

        } catch (Exception e) {
            return "FAILED " + e.getMessage();
        }
    }

}
