package com.ichi2.anki.web;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;

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
            if (! response.getStatusLine().toString().contains("OK"))
            {
                return "FAILED";
            }
            String result = "";
             
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                  response.getEntity().getContent()
                )
              );
             
            String line = null;
            while ((line = reader.readLine()) != null){
              result += line + "\n";
            }
            
            return result;
    
        }
        catch (Exception e)
        {
            return "FAILED with exception: " + e.getMessage();
        }
    
    }

}
