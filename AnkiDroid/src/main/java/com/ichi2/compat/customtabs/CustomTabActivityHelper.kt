//noinspection MissingCopyrightHeader #8659
// Copyright 2015 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.ichi2.compat.customtabs;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.browser.customtabs.CustomTabsClient;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.browser.customtabs.CustomTabsServiceConnection;
import androidx.browser.customtabs.CustomTabsSession;

import java.util.List;

import timber.log.Timber;


/**
 * This is a helper class to manage the connection to the Custom Tabs Service.
 */
public class CustomTabActivityHelper implements ServiceConnectionCallback {
    private static boolean sCustomTabsFailed = false;
    @Nullable
    private CustomTabsSession mCustomTabsSession;
    @Nullable
    private CustomTabsClient mClient;
    @Nullable
    private CustomTabsServiceConnection mConnection;

    /**
     * Opens the URL on a Custom Tab if possible. Otherwise fallsback to opening it on a WebView.
     *
     * @param activity The host activity.
     * @param customTabsIntent a CustomTabsIntent to be used if Custom Tabs is available.
     * @param uri the Uri to be opened.
     * @param fallback a CustomTabFallback to be used if Custom Tabs is not available.
     */
    public static void openCustomTab(@NonNull Activity activity,
                                     CustomTabsIntent customTabsIntent,
                                     Uri uri,
                                     CustomTabFallback fallback) {
        String packageName = CustomTabsHelper.getPackageNameToUse(activity);

        //If we cant find a package name or there was a serious failure during init, we don't support
        //Chrome Custom Tabs. So, we fallback to the webview
        if (packageName == null || sCustomTabsFailed) {
            if (fallback != null) {
                fallback.openUri(activity, uri);
            } else {
                Timber.e("A version of Chrome supporting custom tabs was not available, and the fallback was null");
            }
        } else {
            customTabsIntent.intent.setPackage(packageName);
            customTabsIntent.launchUrl(activity, uri);
        }
    }

    /**
     * Unbinds the Activity from the Custom Tabs Service.
     * @param activity the activity that is connected to the service.
     */
    public void unbindCustomTabsService(Activity activity) {
        if (mConnection == null) return;
        activity.unbindService(mConnection);
        mClient = null;
        mCustomTabsSession = null;
        mConnection = null;
    }

    /**
     * Creates or retrieves an exiting CustomTabsSession.
     *
     * @return a CustomTabsSession.
     */
    public CustomTabsSession getSession() {
        if (mClient == null) {
            mCustomTabsSession = null;
        } else if (mCustomTabsSession == null) {
            mCustomTabsSession = mClient.newSession(null);
        }
        return mCustomTabsSession;
    }

    /**
     * Binds the Activity to the Custom Tabs Service.
     * @param activity the activity to be binded to the service.
     */
    public void bindCustomTabsService(Activity activity) {
        if (mClient != null) return;

        String packageName = CustomTabsHelper.getPackageNameToUse(activity);
        if (packageName == null) return;

        mConnection = new ServiceConnection(this);
        try {
            CustomTabsClient.bindCustomTabsService(activity, packageName, mConnection);
        } catch (SecurityException e) {
            Timber.w(e, "CustomTabsService bind attempt failed, using fallback");
            disableCustomTabHandler();
        }
    }


    private void disableCustomTabHandler() {
        Timber.i("Disabling custom tab handler and using fallback");
        sCustomTabsFailed = true;
        mClient = null;
        mCustomTabsSession = null;
        mConnection = null;
    }

    /**
     * @see {@link CustomTabsSession#mayLaunchUrl(Uri, Bundle, List)}.
     * @return true if call to mayLaunchUrl was accepted.
     */
    public boolean mayLaunchUrl(Uri uri, Bundle extras, List<Bundle> otherLikelyBundles) {
        if (mClient == null) return false;

        CustomTabsSession session = getSession();
        if (session == null) return false;

        return session.mayLaunchUrl(uri, extras, otherLikelyBundles);
    }


    @Override
    public void onServiceConnected(CustomTabsClient client) {
        try {
            mClient = client;
            try {
                mClient.warmup(0L);
            } catch (IllegalStateException e) {
                // Issue 5337 - some browsers like TorBrowser don't adhere to Android 8 background limits
                // They will crash as they attempt to start services. warmup failure shouldn't be fatal though.
                Timber.w(e, "Ignoring CustomTabs implementation that doesn't conform to Android 8 background limits");
            }
            getSession();
        } catch (SecurityException e) {
            //#6142 - A securityException here means that we're not able to load the CustomTabClient at all, whereas
            //the IllegalStateException was a failure, but could be continued from
            Timber.w(e, "CustomTabsService bind attempt failed, using fallback");
            disableCustomTabHandler();
        }
    }

    @Override
    public void onServiceDisconnected() {
        mClient = null;
        mCustomTabsSession = null;
    }


    /**
     * To be used as a fallback to open the Uri when Custom Tabs is not available.
     */
    public interface CustomTabFallback {
        /**
         *
         * @param activity The Activity that wants to open the Uri.
         * @param uri The uri to be opened by the fallback.
         */
        void openUri(Activity activity, Uri uri);
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE) @CheckResult
    boolean isFailed() {
        return sCustomTabsFailed && mClient == null;
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    public static void resetFailed() {
        sCustomTabsFailed = false;
    }
}