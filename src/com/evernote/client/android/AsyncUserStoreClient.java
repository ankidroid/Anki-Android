/*
 * Copyright 2012 Evernote Corporation
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, mClient
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    mClient list of conditions and the following disclaimer in the documentation
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


import com.evernote.edam.error.EDAMSystemException;
import com.evernote.edam.error.EDAMUserException;
import com.evernote.edam.type.PremiumInfo;
import com.evernote.edam.type.User;
import com.evernote.edam.userstore.AuthenticationResult;
import com.evernote.edam.userstore.BootstrapInfo;
import com.evernote.edam.userstore.PublicUserInfo;
import com.evernote.edam.userstore.UserStore;
import com.evernote.thrift.TException;
import com.evernote.thrift.protocol.TProtocol;

/**
 * An Async wrapper for {@link UserStore.Client}
 * Use these methods with a {@link OnClientCallback} to get make network requests
 *
 * @author @tylersmithnet
 */
public class AsyncUserStoreClient {

  private final String mAuthenticationToken;
  private final UserStore.Client mClient;

  AsyncUserStoreClient(TProtocol prot, String authenticationToken) {
    mClient = new UserStore.Client(prot);
    mAuthenticationToken = authenticationToken;
  }

  AsyncUserStoreClient(TProtocol iprot, TProtocol oprot, String authenticationToken) {
    mClient = new UserStore.Client(iprot, oprot);
    mAuthenticationToken = authenticationToken;
  }

  /**
   * If direct access to the Note Store is needed, all of these calls are synchronous
   * @return {@link UserStore.Client}
   */
  public UserStore.Client getClient() {
    return mClient;
  }

  /**
   * @return authToken inserted into calls
   */
  String getAuthenticationToken() {
    return mAuthenticationToken;
  }

  /**
   *
   * Asynchronous call
   *
   */
  public void isBusinessUserAsync(final OnClientCallback<Boolean> callback) {
    AsyncReflector.execute(this, callback, "isBusinessUser");
  }

  /**
   *
   * Synchronous call
   *
   *
   * @return the result of a user belonging to a business account
   */
  public boolean isBusinessUser() throws TException, EDAMUserException, EDAMSystemException {
    return getClient().getUser(getAuthenticationToken()).getAccounting().isSetBusinessId();
  }

  /**
   * Asynchronous wrapper
   *
   * @param {@link OnClientCallback} providing an interface to the calling code
   * @see UserStore.Client#checkVersion(String, short, short)
   */
  public void checkVersion(final String clientName, final short edamVersionMajor, final short edamVersionMinor, final OnClientCallback<Boolean> callback) {
    AsyncReflector.execute(mClient, callback, "checkVersion", clientName, edamVersionMajor, edamVersionMinor);
  }

  /**
   * Asynchronous wrapper
   *
   * @param {@link OnClientCallback} providing an interface to the calling code
   * @see UserStore.Client#getBootstrapInfo(String)
   */
  public void getBootstrapInfo(final String locale, final OnClientCallback<BootstrapInfo> callback) {
    AsyncReflector.execute(mClient, callback, "getBootstrapInfo", locale);
  }

  /**
   * Asynchronous wrapper
   *
   * @param {@link OnClientCallback} providing an interface to the calling code
   * @see UserStore.Client#authenticate(String, String, String, String, boolean)
   */
  public void authenticate(final String username, final String password, final String consumerKey, final String consumerSecret, boolean supportsTwoFactor, final OnClientCallback<AuthenticationResult> callback) {
    AsyncReflector.execute(mClient, callback, "authenticate", username, password, consumerKey, consumerSecret, supportsTwoFactor);
  }

  /**
   * Asynchronous wrapper
   *
   * @param {@link OnClientCallback} providing an interface to the calling code
   * @see UserStore.Client#authenticateLongSession(String, String, String, String, String, String, boolean)
   */
  public void authenticateLongSession(final String username, final String password, final String consumerKey, final String consumerSecret, final String deviceIdentifier, final String deviceDescription, final boolean supportsTwoFactor, final OnClientCallback<AuthenticationResult> callback) {
    AsyncReflector.execute(mClient, callback, "authenticateLongSession", username, password, consumerKey, consumerSecret, deviceIdentifier, deviceDescription, supportsTwoFactor);
  }

  /**
   * Asynchronous wrapper
   *
   * @param {@link OnClientCallback} providing an interface to the calling code
   * @see UserStore.Client#authenticateToBusiness(String)
   */
  public void authenticateToBusiness(final String authenticationToken, final OnClientCallback<AuthenticationResult> callback) {
    AsyncReflector.execute(mClient, callback, "authenticateToBusiness", authenticationToken);
  }

  /**
   * Asynchronous wrapper
   *
   * @param {@link OnClientCallback} providing an interface to the calling code
   * @see UserStore.Client#refreshAuthentication(String)
   */
  public void refreshAuthentication(final String authenticationToken, final OnClientCallback<AuthenticationResult> callback) {
    AsyncReflector.execute(mClient, callback, "refreshAuthentication", authenticationToken);
  }

  /**
   * Asynchronous wrapper
   *
   * @param {@link OnClientCallback} providing an interface to the calling code
   * @see UserStore.Client#getUser(String)
   */
  public void getUser(final OnClientCallback<User> callback) {
    AsyncReflector.execute(mClient, callback, "getUser", mAuthenticationToken);
  }

  /**
   * Asynchronous wrapper
   *
   * @param {@link OnClientCallback} providing an interface to the calling code
   * @see UserStore.Client#getPublicUserInfo(String)
   */
  public void getPublicUserInfo(final String username, final OnClientCallback<PublicUserInfo> callback) {
    AsyncReflector.execute(mClient, callback, "getPublicUserInfo", username);
  }

  /**
   * Asynchronous wrapper
   *
   * @param {@link OnClientCallback} providing an interface to the calling code
   * @see UserStore.Client#getPremiumInfo(String)
   */
  public void getPremiumInfo(final OnClientCallback<PremiumInfo> callback) {
    AsyncReflector.execute(mClient, callback, "getPremiumInfo", mAuthenticationToken);
  }

  /**
   * Asynchronous wrapper
   *
   * @param {@link OnClientCallback} providing an interface to the calling code
   * @see UserStore.Client#getNoteStoreUrl(String)
   */
  public void getNoteStoreUrl(final OnClientCallback<String> callback) {
    AsyncReflector.execute(mClient, callback, "getNoteStoreUrl", mAuthenticationToken);
  }

  /**
   * Asynchronous wrapper
   *
   * @see UserStore.Client#revokeLongSession(String)
   */
  public void revokeLongSession(final OnClientCallback<Void> callback) throws EDAMUserException,
      EDAMSystemException, TException {
    AsyncReflector.execute(mClient, callback, "revokeLongSession", mAuthenticationToken);
  }

  /**
   * Asynchronous wrapper
   *
   * @see UserStore.Client#completeTwoFactorAuthentication(String, String,
   *      String, String)
   */
  public void completeTwoFactorAuthentication(final String oneTimeCode, final String deviceIdentifier,
                                              final String deviceDescription, final OnClientCallback<AuthenticationResult> callback) throws EDAMUserException,
      EDAMSystemException, TException {
    AsyncReflector.execute(mClient, callback, "completeTwoFactorAuthentication", mAuthenticationToken, oneTimeCode, deviceIdentifier, deviceDescription);
  }
}

