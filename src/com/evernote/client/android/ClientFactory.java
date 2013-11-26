/*
 * Copyright 2012 Evernote Corporation
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.evernote.client.android;

import com.evernote.client.conn.mobile.TEvernoteHttpClient;
import com.evernote.edam.error.EDAMNotFoundException;
import com.evernote.edam.error.EDAMSystemException;
import com.evernote.edam.error.EDAMUserException;
import com.evernote.edam.type.LinkedNotebook;
import com.evernote.edam.userstore.AuthenticationResult;
import com.evernote.thrift.TException;
import com.evernote.thrift.protocol.TBinaryProtocol;
import com.evernote.thrift.transport.TTransportException;

import java.io.File;
import java.util.Map;

/**
 * A class to produce User and Note store clients.
 *
 *
 * @author @briangriffey
 * @author @tylersmithnet
 */

public class ClientFactory {
  private static final String LOGTAG = "ClientFactory";
  private static final String USER_AGENT_KEY = "User-Agent";

  private String mUserAgent;
  private Map<String, String> mCustomHeaders;
  private File mTempDir;


  /**
   * Private constructor
   */
  private ClientFactory() {}

  /**
   * This should always be requested through an {@link com.evernote.client.android.EvernoteSession}
   */
  ClientFactory(String userAgent, File tempDir) {
    mUserAgent = userAgent;
    mTempDir = tempDir;
  }

  /**
   * Create a new NoteStore client. Each call to this method will return
   * a new NoteStore.Client instance. The returned client can be used for any
   * number of API calls, but is NOT thread safe.
   *
   * @throws IllegalStateException if @link #isLoggedIn() is false.
   * @throws TTransportException if an error occurs setting up the
   * connection to the Evernote service.
   */
  public AsyncNoteStoreClient createNoteStoreClient() throws TTransportException {
    if(EvernoteSession.getOpenSession() == null || EvernoteSession.getOpenSession().getAuthenticationResult() == null) {
      throw new IllegalStateException();
    }

    TEvernoteHttpClient transport =
        new TEvernoteHttpClient(EvernoteSession.getOpenSession().getAuthenticationResult().getNoteStoreUrl(), mUserAgent, mTempDir);
    TBinaryProtocol protocol = new TBinaryProtocol(transport);
    return new AsyncNoteStoreClient(protocol, protocol, EvernoteSession.getOpenSession().getAuthenticationResult().getAuthToken());
  }

  /**
   * This is an async call to retrieve a business note store.
   *
   * @param callback to receive results from creating NoteStore
   */
  public void createBusinessNoteStoreClientAsync(final OnClientCallback<AsyncBusinessNoteStoreClient> callback) {
    AsyncReflector.execute(this, callback, "createBusinessNoteStoreClient");
  }

  /**
   *
   * Create a new Business NoteStore client. Each call to this method will return
   * a new NoteStore.Client instance. The returned client can be used for any
   * number of API calls, but is NOT thread safe.
   *
   * This method will check expiration time for the business authorization token, this is a network request
   *
   * This method is synchronous
   *
   * @throws TException
   * @throws EDAMUserException
   * @throws EDAMSystemException User is not part of a business
   */
  public AsyncBusinessNoteStoreClient createBusinessNoteStoreClient() throws TException, EDAMUserException, EDAMSystemException {
    com.evernote.client.android.AuthenticationResult authResult =
        EvernoteSession.getOpenSession().getAuthenticationResult();

    if(authResult.getBusinessAuthToken() == null ||
        authResult.getBusinessAuthTokenExpiration() < System.currentTimeMillis()) {

      AuthenticationResult businessAuthResult = createUserStoreClient().getClient().authenticateToBusiness(authResult.getAuthToken());

      authResult.setBusinessAuthToken(businessAuthResult.getAuthenticationToken());
      authResult.setBusinessAuthTokenExpiration(businessAuthResult.getExpiration());
      authResult.setBusinessNoteStoreUrl(businessAuthResult.getNoteStoreUrl());
      authResult.setBusinessUser(businessAuthResult.getUser());
    }

    TEvernoteHttpClient transport =
        new TEvernoteHttpClient(authResult.getBusinessNoteStoreUrl(), mUserAgent, mTempDir);
    TBinaryProtocol protocol = new TBinaryProtocol(transport);
    return new AsyncBusinessNoteStoreClient(protocol, protocol, authResult.getBusinessAuthToken(), this);
  }


  /**
   * Creates a LinkedNoteStoreClient from a {@link LinkedNotebook} asynchronously
   *
   * @param notebook
   * @param callback
   */
  public void createLinkedNoteStoreClientAsync(LinkedNotebook notebook, OnClientCallback<AsyncLinkedNoteStoreClient> callback) {
    AsyncReflector.execute(this, callback, "createLinkedNoteStoreClient", notebook);
  }

  /**
   * Creates a LinkedNoteStoreClient from a {@link LinkedNotebook} synchronously
   *
   * @param linkedNotebook
   */
  public AsyncLinkedNoteStoreClient createLinkedNoteStoreClient(LinkedNotebook linkedNotebook) throws EDAMUserException, EDAMSystemException, TException, EDAMNotFoundException {
    com.evernote.client.android.AuthenticationResult authResult =
        EvernoteSession.getOpenSession().getAuthenticationResult();

    TEvernoteHttpClient transport =
        new TEvernoteHttpClient(linkedNotebook.getNoteStoreUrl(), mUserAgent, mTempDir);
    TBinaryProtocol protocol = new TBinaryProtocol(transport);

    AsyncLinkedNoteStoreClient sharedNoteStore = new AsyncLinkedNoteStoreClient(protocol, protocol, authResult.getAuthToken(), this);
    AuthenticationResult sharedAuthKey = sharedNoteStore.getAsyncClient().getClient().authenticateToSharedNotebook(linkedNotebook.getShareKey(), authResult.getAuthToken());
    sharedNoteStore.setAuthToken(sharedAuthKey.getAuthenticationToken());
    return sharedNoteStore;
  }

  /**
   * Create a new UserStore client. Each call to this method will return
   * a new UserStore.Client instance. The returned client can be used for any
   * number of API calls, but is NOT thread safe.
   *
   * @throws IllegalStateException if @link #isLoggedIn() is false.
   * @throws TTransportException if an error occurs setting up the
   * connection to the Evernote service.
   *
   */
  public AsyncUserStoreClient createUserStoreClient()  throws IllegalStateException, TTransportException {
    if(EvernoteSession.getOpenSession() == null || EvernoteSession.getOpenSession().getAuthenticationResult() == null) {
      throw new IllegalStateException();
    }
    return createUserStoreClient(EvernoteSession.getOpenSession().getAuthenticationResult().getEvernoteHost());
  }

  /**
   * Creates a UserStore client interface that can be used to send requests to a
   * particular UserStore server. For example, the following would provide a
   * handle to make requests from the "MyClient" application to talk to the
   * Evernote server at "www.evernote.com" :
   * <p/>
   * <pre>
   * UserStore.Iface userStore = factory.createUserStoreClient(&quot;www.evernote.com&quot;,
   *     &quot;MyClient (Java)&quot;);
   * </pre>
   * <p/>
   * This call does not actually initiate any communications with the UserStore,
   * it only creates the handle that will be used.
   *
   * @param url the hostname (or numeric IP address) for the server that we should
   *            communicate with. This will attempt to use HTTPS to talk to that
   *            server unless the hostname contains a port number component, in
   *            which case we'll use plaintext HTTP.
   * @param url the hostname (or numeric IP address) for the server that we should
   *            communicate with. This will attempt to use HTTPS to talk to that
   *            server unless the hostname contains a port number component, in
   *            which case we'll use plaintext HTTP.
   * @return
   * @throws TTransportException
   */
  AsyncUserStoreClient createUserStoreClient(String url) throws TTransportException {
    return createUserStoreClient(url, 0);
  }

  /**
   * Create a new UserStore client. Each call to this method will return
   * a new UserStore.Client instance. The returned client can be used for any
   * number of API calls, but is NOT thread safe.
   *
   * @param url to connect to
   * @param port to connect on
   *
   * @throws IllegalStateException if @link #isLoggedIn() is false.
   * @throws TTransportException if an error occurs setting up the
   * connection to the Evernote service.
   *
   */
  AsyncUserStoreClient createUserStoreClient(String url, int port) throws TTransportException {
    String serviceUrl = getFullUrl(url, port);

    TEvernoteHttpClient transport =
        new TEvernoteHttpClient(serviceUrl, mUserAgent, mTempDir);

    if (mCustomHeaders != null) {
      for (Map.Entry<String, String> header : mCustomHeaders.entrySet()) {
        transport.setCustomHeader(header.getKey(), header.getValue());
      }
    }
    if (mUserAgent != null) {
      transport.setCustomHeader(USER_AGENT_KEY, mUserAgent);
    }
    TBinaryProtocol protocol = new TBinaryProtocol(transport);
    String authToken = null;
    if(EvernoteSession.getOpenSession().isLoggedIn()) {
      authToken = EvernoteSession.getOpenSession().getAuthenticationResult().getAuthToken();
    }

    return new AsyncUserStoreClient(protocol, protocol, authToken);
  }

  private String getFullUrl(String serviceUrl, int port) {
    String url = "";

    if (port != 0)
      serviceUrl += ":" + port;
    if (!serviceUrl.startsWith("http")) {
      url = serviceUrl.contains(":") ? "http://" : "https://";
    }

    url += serviceUrl + "/edam/user";

    return url;
  }


  /**
   * The user agent defined for the connection
   */
  public String getUserAgent() {
    return mUserAgent;
  }

  /**
   * Set a custom UserAgent String for the client connection
   *
   * @param mUserAgent
   */
  public void setUserAgent(String mUserAgent) {
    this.mUserAgent = mUserAgent;
  }

  /**
   * if non-null, this is a mapping of HTTP headers to values which
   * will be included in the request.
   */
  public Map<String, String> getCustomHeaders() {
    return mCustomHeaders;
  }

  /**
   * Allows custom headers to be defined for the Client connection
   *
   * @param mCustomHeaders
   */
  public void setCustomHeaders(Map<String, String> mCustomHeaders) {
    this.mCustomHeaders = mCustomHeaders;
  }

  /**
   * a temporary directory in which large outgoing Thrift messages will
   * be cached to disk before they are sent
   */
  public File getTempDir() {
    return mTempDir;
  }

  /**
   * sets the temporary directory in which large outgoing Thrift messages will
   * be cached to disk before they are sent
   *
   * @param mTempDir
   */
  public void setTempDir(File mTempDir) {
    this.mTempDir = mTempDir;
  }

}
