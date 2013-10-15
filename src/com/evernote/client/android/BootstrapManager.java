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

import android.util.Log;
import com.evernote.edam.userstore.BootstrapInfo;
import com.evernote.edam.userstore.BootstrapProfile;
import com.evernote.thrift.TException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * A class that provides access to check the current
 * {@link com.evernote.edam.userstore.Constants#EDAM_VERSION_MAJOR} and
 * the {@link com.evernote.edam.userstore.Constants#EDAM_VERSION_MINOR} against the Evernote Web serivice for API
 * Compatibility
 *
 * It provides access to the {@link List} of {@link BootstrapProfile} representing the possible server connections
 * for the user.  This list must be requested from the server on any type of authentication attempt
 *
 * @author @tylersmithnet
 */
public class BootstrapManager {

  private static final String LOGTAG = "EvernoteSession";

  /**
   * List of locales that match china
   */
  private static List<Locale> sChinaLocales = Arrays.asList(new Locale[]{
      Locale.TRADITIONAL_CHINESE,
      Locale.CHINESE,
      Locale.CHINA,
      Locale.SIMPLIFIED_CHINESE});

  /**
   * Server matched name for BootstrapProfile that matches china
   */
  public static final String CHINA_PROFILE = "Evernote-China";

  /**
   * Display names for Yinxiang and Evernote
   */
  public static final String DISPLAY_YXBIJI = "印象笔记";
  public static final String DISPLAY_EVERNOTE = "Evernote";
  public static final String DISPLAY_EVERNOTE_INTL = "Evernote International";

  private ArrayList<String> mBootstrapServerUrls = new ArrayList<String>();
  private AsyncUserStoreClient mUserStoreClient;
  private Locale mLocale;
  private ClientFactory mClientProducer;
  private String mBootstrapServerUsed;

  /**
   * private constructor
   */
  private BootstrapManager() {};

  /**
   * package-scope constructor
   */
  BootstrapManager(EvernoteSession.EvernoteService service, ClientFactory producer) {
    this(service, producer, Locale.getDefault());
  }

  /**
   * package-scope constructor
   *
   * @param service {@link com.evernote.client.android.EvernoteSession.EvernoteService#PRODUCTION} when using
   * production and {@link com.evernote.client.android.EvernoteSession.EvernoteService#SANDBOX} when using sandbox
   * @param producer Client producer used to create clients
   * @param locale Used to detect if the china servers need to be checked
   */
  BootstrapManager(EvernoteSession.EvernoteService service, ClientFactory producer, Locale locale) {
    mLocale = locale;
    mClientProducer = producer;

    mBootstrapServerUrls.clear();
    switch(service) {
      case PRODUCTION:
        if (sChinaLocales.contains(mLocale)) {
          mBootstrapServerUrls.add(EvernoteSession.HOST_CHINA);
        }
        mBootstrapServerUrls.add(EvernoteSession.HOST_PRODUCTION);
        break;

      case SANDBOX:
        mBootstrapServerUrls.add(EvernoteSession.HOST_SANDBOX);
        break;
    }
  }

  /**
   * Initialized the User Store to check for supported version of the API
   *
   * @throws ClientUnsupportedException on unsupported version
   * @throws Exception on generic errors
   */
  private void initializeUserStoreAndCheckVersion() throws Exception {

    int i = 0;
    String version = com.evernote.edam.userstore.Constants.EDAM_VERSION_MAJOR + "."
        + com.evernote.edam.userstore.Constants.EDAM_VERSION_MINOR;

    for (String url : mBootstrapServerUrls) {
      i++;
      try {
        mUserStoreClient =  mClientProducer.createUserStoreClient(url);

        if (!mUserStoreClient.getClient().checkVersion(mClientProducer.getUserAgent(),
            com.evernote.edam.userstore.Constants.EDAM_VERSION_MAJOR,
            com.evernote.edam.userstore.Constants.EDAM_VERSION_MINOR)) {
          mUserStoreClient = null;
          throw new ClientUnsupportedException(version);
        }

        mBootstrapServerUsed = url;
        return;
      } catch (ClientUnsupportedException cue) {

        Log.e(LOGTAG, "Invalid Version", cue);
        throw cue;
      } catch (Exception e) {
        mUserStoreClient = null;
        if (i < mBootstrapServerUrls.size()) {
          Log.e(LOGTAG, "Error contacting bootstrap server=" + url, e);
          continue;
        } else {
          throw e;
        }
      }
    }
  }

  /**
   * Makes a web request to get the latest bootstrap information
   * This is a requirement during the oauth process
   *
   * @return {@link BootstrapInfoWrapper}
   * @throws Exception
   */
  BootstrapInfoWrapper getBootstrapInfo() throws Exception {
    Log.d(LOGTAG, "getBootstrapInfo()");
    BootstrapInfo bsInfo = null;
    try {
      if (mUserStoreClient == null) {
        initializeUserStoreAndCheckVersion();
      }

      bsInfo = mUserStoreClient.getClient().getBootstrapInfo(mLocale.toString());
      printBootstrapInfo(bsInfo);

    } catch (ClientUnsupportedException cue) {
      throw cue;
    } catch (TException e) {
      Log.e(LOGTAG, "error getting bootstrap info", e);
    }

    BootstrapInfoWrapper wrapper = new BootstrapInfoWrapper(mBootstrapServerUsed, bsInfo);
    return wrapper;
  }

  /**
   * Log the {@link BootstrapProfile} list
   * @param bsInfo
   */
  void printBootstrapInfo(BootstrapInfo bsInfo) {
    if (bsInfo == null) return;

    Log.d(LOGTAG, "printBootstrapInfo");
    List<BootstrapProfile> profiles = bsInfo.getProfiles();
    if (profiles != null) {
      for (BootstrapProfile profile : profiles) {
        Log.d(LOGTAG, profile.toString());
      }
    } else {
      Log.d(LOGTAG, "Profiles are null");
    }
  }

  /**
   * Wrapper class to hold the Evernote API server URL and the {@link BootstrapProfile} object
   */
  static class BootstrapInfoWrapper {
    private String mServerUrl;
    private BootstrapInfo mBootstrapInfo;

    BootstrapInfoWrapper(String serverUrl, BootstrapInfo info) {
      mServerUrl = serverUrl;
      mBootstrapInfo = info;
    }

    String getServerUrl() {
      return mServerUrl;
    }

    BootstrapInfo getBootstrapInfo() {
      return mBootstrapInfo;
    }
  }

  public static class ClientUnsupportedException extends Exception {
    public ClientUnsupportedException(String version) {
      super("Client version " + version + " not supported.");
    }
  };
}
