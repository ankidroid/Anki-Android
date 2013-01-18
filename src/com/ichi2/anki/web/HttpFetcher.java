package com.ichi2.anki.web;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;

import android.content.Context;
import android.os.Environment;

public class HttpFetcher
{

    public static String fetchThroughHttp(String address)
    {

        try
        {
            HttpClient httpClient = new DefaultHttpClient();
            HttpContext localContext = new BasicHttpContext();
            HttpGet httpGet = new HttpGet(address);
            HttpResponse response = httpClient.execute(httpGet, localContext);
            if (!response.getStatusLine().toString().contains("OK"))
            {
                return "FAILED";
            }
            String result = "";

            BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));

            String line = null;
            while ((line = reader.readLine()) != null)
            {
                result += line + "\n";
            }

            return result;

        }
        catch (Exception e)
        {
            return "FAILED with exception: " + e.getMessage();
        }

    }

    public static String downloadFileToCache(String UrlToFile, Context context)
    {
        try
        {
            URL url = new URL(UrlToFile);
            
            String extension = UrlToFile.substring(UrlToFile.length() - 4);

            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.setDoOutput(true);
            urlConnection.connect();

            File outputDir = context.getCacheDir();
            File file = File.createTempFile("pronounciation", extension, outputDir);
            
            FileOutputStream fileOutput = new FileOutputStream(file);
            InputStream inputStream = urlConnection.getInputStream();

            byte[] buffer = new byte[1024];
            int bufferLength = 0; 

            while ((bufferLength = inputStream.read(buffer)) > 0)
            {
                fileOutput.write(buffer, 0, bufferLength);
            }
            fileOutput.close();
            
            return file.getAbsolutePath();

        }
        catch (Exception e)
        {
            return "FAILED " + e.getMessage();
        }
    }

}
