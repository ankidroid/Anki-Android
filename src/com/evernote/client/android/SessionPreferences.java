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

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

/**
 *
 * A class to manage Evernote Session specific preferences
 *
 * @author @tylersmithnet
 */
class SessionPreferences {

  // Keys for values persisted in our shared preferences
  static final String KEY_AUTHTOKEN = "evernote.mAuthToken";
  static final String KEY_NOTESTOREURL = "evernote.notestoreUrl";
  static final String KEY_WEBAPIURLPREFIX = "evernote.webApiUrlPrefix";
  static final String KEY_USERID = "evernote.userId";
  static final String KEY_EVERNOTEHOST = "evernote.mEvernoteHost";
  static final String KEY_BUSINESSID = "evernote.businessId";

  static final String PREFERENCE_NAME = "evernote.preferences";

  /**
   *
   * @return the {@link SharedPreferences} object to the private space
   */
  static SharedPreferences getPreferences(Context ctx) {
    return ctx.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
  }

  /**
   * Saves {@link SharedPreferences.Editor} using a non-blocking method on Gingerbread and up
   * Saves {@link SharedPreferences.Editor} using a blocking method below Gingerbread
   */
  @TargetApi(9)
  static void save(SharedPreferences.Editor editor) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
      editor.apply();
    } else {
      editor.commit();
    }
  }

}
