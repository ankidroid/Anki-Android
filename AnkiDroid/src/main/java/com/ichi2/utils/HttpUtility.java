
package com.ichi2.utils;



import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.IOException;
import java.util.List;

import timber.log.Timber;

public class HttpUtility {
    public static Boolean postReport(String url, List<NameValuePair> values) {
        HttpClient httpClient = new DefaultHttpClient();
        HttpPost httpPost = new HttpPost(url);

        try {
            httpPost.setEntity(new UrlEncodedFormEntity(values));
            HttpResponse response = httpClient.execute(httpPost);

            switch (response.getStatusLine().getStatusCode()) {
                case 200:
                    Timber.e("feedback report posted to %s", url);
                    return true;

                default:
                    Timber.e("feedback report posted to %s message", url);
                    Timber.e("%d: %s", response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase());
                    break;
            }
        } catch (IOException ex) {
            Timber.e(ex.toString());
        }

        return false;
    }
}
