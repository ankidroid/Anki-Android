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
package com.ichi2.compat.customtabs

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import androidx.annotation.CheckResult
import androidx.annotation.VisibleForTesting
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession
import timber.log.Timber

/**
 * This is a helper class to manage the connection to the Custom Tabs Service.
 */
class CustomTabActivityHelper : ServiceConnectionCallback {
    private var mCustomTabsSession: CustomTabsSession? = null
    private var mClient: CustomTabsClient? = null
    private var mConnection: CustomTabsServiceConnection? = null

    /**
     * Unbinds the Activity from the Custom Tabs Service.
     * @param activity the activity that is connected to the service.
     */
    fun unbindCustomTabsService(activity: Activity) {
        if (mConnection == null) return
        mConnection.let { activity.unbindService(it!!) }
        mClient = null
        mCustomTabsSession = null
        mConnection = null
    }

    /**
     * Creates or retrieves an exiting CustomTabsSession.
     *
     * @return a CustomTabsSession.
     */
    val session: CustomTabsSession?
        get() {
            if (mClient == null) {
                mCustomTabsSession = null
            } else if (mCustomTabsSession == null) {
                mCustomTabsSession = mClient!!.newSession(null)
            }
            return mCustomTabsSession
        }

    /**
     * Binds the Activity to the Custom Tabs Service.
     * @param activity the activity to be bound to the service.
     */
    fun bindCustomTabsService(activity: Activity) {
        if (mClient != null) return
        val packageName = CustomTabsHelper.getPackageNameToUse(activity) ?: return
        mConnection = ServiceConnection(this)
        try {
            CustomTabsClient.bindCustomTabsService(activity, packageName, mConnection!!)
        } catch (e: SecurityException) {
            Timber.w(e, "CustomTabsService bind attempt failed, using fallback")
            disableCustomTabHandler()
        }
    }

    private fun disableCustomTabHandler() {
        Timber.i("Disabling custom tab handler and using fallback")
        sCustomTabsFailed = true
        mClient = null
        mCustomTabsSession = null
        mConnection = null
    }

    /**
     * @see CustomTabsSession.mayLaunchUrl
     * @return true if call to mayLaunchUrl was accepted.
     */
    fun mayLaunchUrl(uri: Uri?, extras: Bundle?, otherLikelyBundles: List<Bundle?>?): Boolean {
        if (mClient == null) return false
        val session = session ?: return false
        return session.mayLaunchUrl(uri, extras, otherLikelyBundles)
    }

    override fun onServiceConnected(client: CustomTabsClient) {
        try {
            mClient = client
            try {
                mClient!!.warmup(0L)
            } catch (e: IllegalStateException) {
                // Issue 5337 - some browsers like TorBrowser don't adhere to Android 8 background limits
                // They will crash as they attempt to start services. warmup failure shouldn't be fatal though.
                Timber.w(e, "Ignoring CustomTabs implementation that doesn't conform to Android 8 background limits")
            }
            session
        } catch (e: SecurityException) {
            // #6142 - A securityException here means that we're not able to load the CustomTabClient at all, whereas
            // the IllegalStateException was a failure, but could be continued from
            Timber.w(e, "CustomTabsService bind attempt failed, using fallback")
            disableCustomTabHandler()
        }
    }

    override fun onServiceDisconnected() {
        mClient = null
        mCustomTabsSession = null
    }

    /**
     * To be used as a fallback to open the Uri when Custom Tabs is not available.
     */
    interface CustomTabFallback {
        /**
         *
         * @param activity The Activity that wants to open the Uri.
         * @param uri The uri to be opened by the fallback.
         */
        fun openUri(activity: Activity, uri: Uri)
    }

    @get:CheckResult
    @get:VisibleForTesting(otherwise = VisibleForTesting.NONE)
    val isFailed: Boolean
        get() = sCustomTabsFailed && mClient == null

    companion object {
        private var sCustomTabsFailed = false

        /**
         * Opens the URL on a Custom Tab if possible. Otherwise falls back to opening it on a WebView.
         *
         * @param activity The host activity.
         * @param customTabsIntent a CustomTabsIntent to be used if Custom Tabs is available.
         * @param uri the Uri to be opened.
         * @param fallback a CustomTabFallback to be used if Custom Tabs is not available.
         */
        fun openCustomTab(
            activity: Activity,
            customTabsIntent: CustomTabsIntent,
            uri: Uri,
            fallback: CustomTabFallback?
        ) {
            val packageName = CustomTabsHelper.getPackageNameToUse(activity)

            // If we cant find a package name or there was a serious failure during init, we don't support
            // Chrome Custom Tabs. So, we fallback to the webview
            if (packageName == null || sCustomTabsFailed) {
                if (fallback != null) {
                    fallback.openUri(activity, uri)
                } else {
                    Timber.e("A version of Chrome supporting custom tabs was not available, and the fallback was null")
                }
            } else {
                customTabsIntent.intent.setPackage(packageName)
                customTabsIntent.launchUrl(activity, uri)
            }
        }

        @VisibleForTesting(otherwise = VisibleForTesting.NONE)
        fun resetFailed() {
            sCustomTabsFailed = false
        }
    }
}
