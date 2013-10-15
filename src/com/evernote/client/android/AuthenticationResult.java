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

import android.content.SharedPreferences;
import android.util.Log;
import com.evernote.edam.type.User;

/**
 * A container class for the results of a successful OAuth authorization with
 * the Evernote service.
 *
 * @author @tylersmithnet
 */
public class AuthenticationResult {

  private static final String LOGTAG = "AuthenticatonResult";

  private String mAuthToken;
  private String mNoteStoreUrl;
  private String mWebApiUrlPrefix;
  private String mEvernoteHost;
  private int mUserId;

  private String mBusinessNoteStoreUrl;
  private String mBusinessAuthToken;
  private long mBusinessAuthTokenExpiration;
  private User mBusinessUser;


  public AuthenticationResult(SharedPreferences pref) {
    restore(pref);
  }

  /**
   * Create a new AuthenticationResult.
   *
   * @param authToken An Evernote authentication token.
   * @param noteStoreUrl The URL of the Evernote NoteStore for the authenticated user.
   * @param webApiUrlPrefix The URL of misc. Evernote web APIs for the authenticated user.
   * @param evernoteHost the Evernote Web URL provided from the bootstrap process
   * @param userId The numeric ID of the Evernote user.
   *
   */

  public AuthenticationResult(String authToken, String noteStoreUrl, String webApiUrlPrefix, String evernoteHost, int userId) {
    this.mAuthToken = authToken;
    this.mNoteStoreUrl = noteStoreUrl;
    this.mWebApiUrlPrefix = webApiUrlPrefix;
    this.mEvernoteHost = evernoteHost;
    this.mUserId = userId;
  }

  void persist(SharedPreferences pref) {
    Log.d(LOGTAG, "persisting Authentication results to SharedPreference");
    SharedPreferences.Editor editor = pref.edit();

    editor.putString(SessionPreferences.KEY_AUTHTOKEN, mAuthToken);
    editor.putString(SessionPreferences.KEY_NOTESTOREURL, mNoteStoreUrl);
    editor.putString(SessionPreferences.KEY_WEBAPIURLPREFIX, mWebApiUrlPrefix);
    editor.putString(SessionPreferences.KEY_EVERNOTEHOST, mEvernoteHost);
    editor.putInt(SessionPreferences.KEY_USERID, mUserId);

    SessionPreferences.save(editor);
  }

  void restore(SharedPreferences pref) {
    Log.d(LOGTAG, "restoring Authentication results from SharedPreference");
    mAuthToken = pref.getString(SessionPreferences.KEY_AUTHTOKEN, null);
    mNoteStoreUrl = pref.getString(SessionPreferences.KEY_NOTESTOREURL, null);
    mWebApiUrlPrefix = pref.getString(SessionPreferences.KEY_WEBAPIURLPREFIX, null);
    mEvernoteHost = pref.getString(SessionPreferences.KEY_EVERNOTEHOST, null);
    mUserId = pref.getInt(SessionPreferences.KEY_USERID, -1);
  }

  void clear(SharedPreferences pref) {
    Log.d(LOGTAG, "clearing Authentication results from SharedPreference");
    SharedPreferences.Editor editor = pref.edit();

    editor.remove(SessionPreferences.KEY_AUTHTOKEN);
    editor.remove(SessionPreferences.KEY_NOTESTOREURL);
    editor.remove(SessionPreferences.KEY_WEBAPIURLPREFIX);
    editor.remove(SessionPreferences.KEY_EVERNOTEHOST);
    editor.remove(SessionPreferences.KEY_USERID);

    SessionPreferences.save(editor);
  }



  /**
   * @return the authentication token that will be used to make authenticated API requests.
   */
  public String getAuthToken() {
    return mAuthToken;
  }

  /**
   * @return the URL that will be used to access the NoteStore service.
   */
  public String getNoteStoreUrl() {
    return mNoteStoreUrl;
  }

  /**
   * @return the URL prefix that can be used to access non-Thrift API endpoints.
   */
  public String getWebApiUrlPrefix() {
    return mWebApiUrlPrefix;
  }

  /**
   *
   * @return the Evernote Web URL provided from the bootstrap process
   */
  public String getEvernoteHost() {
    return mEvernoteHost;
  }

  /**
   * @return the numeric user ID of the user who authorized access to their Evernote account.
   */
  public int getUserId() {
    return mUserId;
  }

  /**
   * @return the URL that will be used to access the BusinessNoteStore service.
   */
  public String getBusinessNoteStoreUrl() {
    return mBusinessNoteStoreUrl;
  }

  /**
   * Set the BusinessNoteStore Url
   */
  void setBusinessNoteStoreUrl(String businessNoteStoreUrl) {
    this.mBusinessNoteStoreUrl = businessNoteStoreUrl;
  }

  /**
   * @return the {@link User} that references the business account user object
   */
  public User getBusinessUser() {
    return mBusinessUser;
  }

  /**
   * Set the BusinessNoteStore Url
   */
  void setBusinessUser(User user) {
    this.mBusinessUser = user;
  }

  /**
   * @return the Business Auth token
   */
  public String getBusinessAuthToken() {
    return mBusinessAuthToken;
  }

  /**
   * Set the BusinessNoteStore Authorizaton token's expiration time  (epoch)
   */
  void setBusinessAuthToken(String authToken) {
    this.mBusinessAuthToken = authToken;
  }

  /**
   * @return the BusinessNoteStore Authorizaton token's expiration time (epoch)
   */
  public long getBusinessAuthTokenExpiration() {
    return mBusinessAuthTokenExpiration;
  }

  /**
   * Set the BusinessNoteStore Authorizaton token's expiration time  (epoch)
   */
  void setBusinessAuthTokenExpiration(long businessAuthTokenExpiration) {
    this.mBusinessAuthTokenExpiration = businessAuthTokenExpiration;
  }
}
