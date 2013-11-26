/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.evernote.client.conn.mobile;

import com.evernote.thrift.transport.TTransport;
import com.evernote.thrift.transport.TTransportException;
import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * HTTP implementation of the TTransport interface modified by Evernote to work
 * on Android. Instead of caching in memory, large Thrift messages are cached on
 * disk before being sent to the Thrift server.
 */
public class TEvernoteHttpClient extends TTransport {
  /**
   * Keep requests in RAM if they are less than 512kb.
   */
  private static final int MEMORY_BUFFER_SIZE = 512 * 1024;
  private URL url_ = null;
  private String userAgent = null;
  private final DiskBackedByteStore requestBuffer_;
  private InputStream inputStream_ = null;
  private Map<String, String> customHeaders_ = null;
  private HttpRequestBase request = null;
  private HttpParams httpParameters = new BasicHttpParams();

  /**
   * Create a new TAndroidHttpClient.
   *
   * @param url       The Thrift server URL, for example, https://www.evernote.com/edam/user.
   * @param userAgent The User-Agent string to send, which should identify the
   *                  client application.
   * @param tempPath  A temp directory where Thrift messages should be cached
   *                  before they're sent.
   * @throws TTransportException If an error occurs creating the temporary
   *                             file that will be used to cache Thrift messages to disk before sending.
   */
  public TEvernoteHttpClient(String url, String userAgent, File tempDir)
      throws TTransportException {

    getHTTPClient();

    this.userAgent = userAgent;
    try {
      url_ = new URL(url);
      requestBuffer_ =
          new DiskBackedByteStore(tempDir, "http", MEMORY_BUFFER_SIZE);
    } catch (IOException iox) {
      throw new TTransportException(iox);
    }
  }

  public void setConnectTimeout(int timeout) {
    HttpConnectionParams.setConnectionTimeout(httpParameters, timeout);
  }

  public void setReadTimeout(int timeout) {
    HttpConnectionParams.setSoTimeout(httpParameters, timeout);
  }

  public void setCustomHeaders(Map<String, String> headers) {
    customHeaders_ = headers;
  }

  public void setCustomHeader(String key, String value) {
    if (customHeaders_ == null) {
      customHeaders_ = new HashMap<String, String>();
    }
    customHeaders_.put(key, value);
  }

  public void open() {
  }

  public void close() {
    if (null != inputStream_) {
      try {
        inputStream_.close();
      } catch (IOException ioe) {
        ;
      }
      inputStream_ = null;
    }

    if (mConnectionManager != null) {
      mConnectionManager.shutdown();
      mConnectionManager = null;
    }
  }

  public boolean isOpen() {
    return true;
  }

  public int read(byte[] buf, int off, int len) throws TTransportException {
    if (inputStream_ == null) {
      throw new TTransportException("Response buffer is empty, no request.");
    }

    try {
      int ret = inputStream_.read(buf, off, len);
      if (ret == -1) {
        throw new TTransportException("No more data available.");
      }
      return ret;
    } catch (IOException iox) {
      throw new TTransportException(iox);
    }
  }

  private ClientConnectionManager mConnectionManager;
  private DefaultHttpClient mHttpClient;

  private DefaultHttpClient getHTTPClient() {

    try {
      if (mConnectionManager != null) {
        mConnectionManager.closeExpiredConnections();
        mConnectionManager.closeIdleConnections(1, TimeUnit.SECONDS);
      } else {
        BasicHttpParams params = new BasicHttpParams();

        HttpConnectionParams.setConnectionTimeout(params, 10000);
        HttpConnectionParams.setSoTimeout(params, 20000);

        ConnManagerParams.setMaxTotalConnections(params, ConnManagerParams.DEFAULT_MAX_TOTAL_CONNECTIONS);
        ConnManagerParams.setTimeout(params, 10000);

        ConnPerRouteBean connPerRoute = new ConnPerRouteBean(18); // Giving 18 connections to Evernote
        ConnManagerParams.setMaxConnectionsPerRoute(params, connPerRoute);

        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(
            new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));

        schemeRegistry.register(
            new Scheme("https", SSLSocketFactory.getSocketFactory(), 443));

        mConnectionManager = new ThreadSafeClientConnManager(params, schemeRegistry);
        DefaultHttpClient httpClient = new DefaultHttpClient(mConnectionManager, params);
        httpClient.setKeepAliveStrategy(new ConnectionKeepAliveStrategy() {
          @Override
          public long getKeepAliveDuration(HttpResponse response,
                                           HttpContext context) {
            return 2 * 60 * 1000; // 2 minutes in millis
          }
        });

        httpClient.setReuseStrategy(new ConnectionReuseStrategy() {
          @Override
          public boolean keepAlive(HttpResponse response, HttpContext context) {
            return true;
          }
        });
        mHttpClient = httpClient;
      }
    } catch (Exception ex) {
      return null;
    }

    return mHttpClient;
  }

  public void write(byte[] buf, int off, int len) {
    requestBuffer_.write(buf, off, len);
  }

  public void flush() throws TTransportException {
    long timer = System.currentTimeMillis();

    HttpEntity httpEntity = null;

    // Extract request and reset buffer
    try {
      // Prepare http post request
      HttpPost request = new HttpPost(url_.toExternalForm());
      this.request = request;
      request.addHeader("Content-Type", "application/x-thrift");
      request.addHeader("Cache-Control", "no-transform");
      if (customHeaders_ != null) {
        for (Map.Entry<String, String> header : customHeaders_.entrySet()) {
          request.addHeader(header.getKey(), header.getValue());
        }
      }
      InputStreamEntity entity =
          new InputStreamEntity(requestBuffer_.getInputStream(), requestBuffer_
              .getSize());
      request.setEntity(entity);
      request.addHeader("Accept", "application/x-thrift");
      request.addHeader("User-Agent", userAgent == null ? "Java/THttpClient"
          : userAgent);
      request.getParams().setBooleanParameter(
          CoreProtocolPNames.USE_EXPECT_CONTINUE, false);

      DefaultHttpClient dHTTP = getHTTPClient();
      HttpResponse response = dHTTP.execute(request);
      httpEntity = response.getEntity();

      int responseCode = response.getStatusLine().getStatusCode();
      if (responseCode != 200) {
        if (httpEntity != null) {
          httpEntity.consumeContent();
        }
        throw new TTransportException("HTTP Response code: " + responseCode);
      }
      // Read the responses
      requestBuffer_.reset();
      inputStream_ = response.getEntity().getContent();
    } catch (IOException iox) {
      throw new TTransportException(iox);
    } catch (Exception ex) {
      throw new TTransportException(ex);
    } finally {
      try {
        requestBuffer_.reset();
      } catch (IOException e) {
      }
      this.request = null;
    }
  }

  public void cancel() {
    try {
      if (this.request != null) {
        this.request.abort();
      }
    } catch (Exception e) {
    }
    close();
  }

  public void reset() {

  }
}
