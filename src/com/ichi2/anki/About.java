/***************************************************************************************
 * Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.SQLException;
import android.os.Bundle;
import android.util.Log;
import android.webkit.WebView;

/**
 * Shows an about box, which is a small HTML page.
 */

public class About extends Activity {
    /**
     * Tag for logging messages
     */
    private static final String TAG = "AnkiDroid";


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(getAboutTitle());

        setContentView(R.layout.about);

        WebView webview = (WebView) findViewById(R.id.about);
        webview.loadDataWithBaseURL("", getResources().getString(R.string.about_content), "text/html", "utf-8", null);
    }


    private String getAboutTitle() {
        StringBuilder appName = new StringBuilder();
        String pkgName = "AnkiDroid";
        String pkgVersion = "?";

        try {
            PackageInfo pInfo = this.getPackageManager().getPackageInfo(this.getPackageName(), 0);
            pkgName = getString(pInfo.applicationInfo.labelRes);
            pkgVersion = pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Couldn't find package named " + this.getPackageName(), e);
        }

        appName.append("About ");
        appName.append(pkgName);
        appName.append(" v");
        appName.append(pkgVersion);
        return appName.toString();
    }
}
