/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.servicelayer;

import android.content.Context;
import android.os.Build;
import android.webkit.WebView;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.libanki.Collection;
import com.ichi2.utils.VersionUtils;

import org.acra.util.Installation;

import java.util.function.Supplier;

import androidx.annotation.NonNull;
import timber.log.Timber;

public class DebugInfoService {
    @NonNull
    public static String getDebugInfo(Context info, Supplier<Collection> col) {
        String schedName = "Not found";
        try {
            schedName = col.get().getSched().getName();
        } catch (Throwable e) {
            Timber.e(e, "Sched name not found");
        }

        Boolean dbV2Enabled = null;
        try {
            dbV2Enabled = col.get().isUsingRustBackend();
        } catch (Throwable e) {
            Timber.w(e, "Unable to detect Rust Backend");
        }

        String webviewUserAgent = getWebviewUserAgent(info);
        return "AnkiDroid Version = " + VersionUtils.getPkgVersionName() + "\n\n" +
                "Android Version = " + Build.VERSION.RELEASE + "\n\n" +
                "Manufacturer = " + Build.MANUFACTURER + "\n\n" +
                "Model = " + Build.MODEL + "\n\n" +
                "Hardware = " + Build.HARDWARE + "\n\n" +
                "Webview User Agent = " + webviewUserAgent + "\n\n" +
                "ACRA UUID = " + Installation.id(info) + "\n\n" +
                "Scheduler = " + schedName + "\n\n" +
                "Crash Reports Enabled = " + isSendingCrashReports(info) + "\n\n" +
                "DatabaseV2 Enabled = " + dbV2Enabled + "\n";
    }


    private static String getWebviewUserAgent(Context context) {
        try {
            return new WebView(context).getSettings().getUserAgentString();
        } catch (Throwable e) {
            AnkiDroidApp.sendExceptionReport(e, "Info::copyDebugInfo()", "some issue occured while extracting webview user agent");
        }
        return null;
    }

    private static boolean isSendingCrashReports(Context context) {
        return AnkiDroidApp.isAcraEnabled(context, false);
    }

}
