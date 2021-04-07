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

package com.ichi2.anki;

import android.app.Application;

import org.acra.config.CoreConfigurationBuilder;

import timber.log.Timber;

/** Base class for AnkiDroidApp to allow for skipping init in tests, but allowing static methods: getInstance() to work */
public abstract class AnkiDroidAppBase extends Application {

    // Singleton instance of this class.
    protected static AnkiDroidAppBase sInstance;


    @Override
    public void onCreate() {
        super.onCreate();
        if (sInstance != null) {
            Timber.i("onCreate() called multiple times");
            //5887 - fix crash.
            if (sInstance.getResources() == null) {
                Timber.w("Skipping re-initialisation - no resources. Maybe uninstalling app?");
                return;
            }
        }
        sInstance = this;
    }


    protected abstract Throwable getWebViewError();

    public abstract CoreConfigurationBuilder getAcraCoreConfigBuilder();

    public abstract void setAcraReportingMode(String value);
}
