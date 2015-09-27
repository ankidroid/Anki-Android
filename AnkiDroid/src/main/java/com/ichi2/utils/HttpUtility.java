
package com.ichi2.utils;



import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.NameValuePair;
import cz.msebera.android.httpclient.client.ClientProtocolException;
import cz.msebera.android.httpclient.client.HttpClient;
import cz.msebera.android.httpclient.client.entity.UrlEncodedFormEntity;
import cz.msebera.android.httpclient.client.methods.HttpPost;
import cz.msebera.android.httpclient.impl.client.DefaultHttpClient;

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
        } catch (ClientProtocolException ex) {
            Timber.e(ex.toString());
        } catch (IOException ex) {
            Timber.e(ex.toString());
        }

        return false;
    }
}
