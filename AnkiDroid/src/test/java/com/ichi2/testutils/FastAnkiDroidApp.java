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

package com.ichi2.testutils;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.AnkiDroidAppBase;

import net.ankiweb.rsdroid.database.NotImplementedException;

import org.acra.config.CoreConfigurationBuilder;

import timber.log.Timber;

public class FastAnkiDroidApp extends AnkiDroidAppBase {

    @Override
    public void onCreate() {
        super.onCreate();
        Timber.plant(new Timber.DebugTree());

        Timber.tag(AnkiDroidApp.TAG);
    }


    @Override
    protected Throwable getWebViewError() {
        throw new NotImplementedException("FastAnkiDroidApp does not support getWebViewError");
    }


    @Override
    public CoreConfigurationBuilder getAcraCoreConfigBuilder() {
        throw new NotImplementedException("FastAnkiDroidApp does not support getAcraCoreConfigBuilder");
    }


    @Override
    public void setAcraReportingMode(String value) {
        throw new NotImplementedException("FastAnkiDroidApp does not support setAcraReportingMode");
    }
}
