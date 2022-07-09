/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.libanki.sync;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import com.ichi2.anki.AnkiDroidApp;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@RunWith(AndroidJUnit4.class)
public class RemoteMediaServerTest {
    //COULD_BE_BETTER: We currently fail on a trailing flash in these variables.
    private static final String sCustomServerWithNoFormatting = "https://sync.example.com/msync";
    private static final String sCustomServerWithFormatting   = "https://sync%s.example.com/msync";
    private static final String sDefaultUrlNoHostNum    = "https://sync.ankiweb.net/msync/";
    private static final String sDefaultUrlWithHostNum  = "https://sync1.ankiweb.net/msync/";

    @Test
    public void getDefaultMediaUrlWithNoHostNum() {
        RemoteMediaServer underTest = getServerWithHostNum(null);

        String syncUrl = underTest.syncURL();

        assertThat(syncUrl, is(sDefaultUrlNoHostNum));
    }


    @Test
    public void getDefaultMediaUrlWithHostNum() {
        RemoteMediaServer underTest = getServerWithHostNum(1);

        String syncUrl = underTest.syncURL();

        assertThat(syncUrl, is(sDefaultUrlWithHostNum));
    }


    @Test
    @Ignore("Not yet supported")
    public void getCustomMediaUrlWithNoHostNum() {
        RemoteMediaServer underTest = getServerWithHostNum(null);
        setCustomMediaServer(sCustomServerWithFormatting);

        String syncUrl = underTest.syncURL();

        assertThat(syncUrl, is("https://sync.example.com/msync"));
    }


    @Test
    @Ignore("Not yet supported")
    public void getCustomMediaUrlWithHostNum() {
        RemoteMediaServer underTest = getServerWithHostNum(1);
        setCustomMediaServer(sCustomServerWithFormatting);

        String syncUrl = underTest.syncURL();

        assertThat(syncUrl, is("https://sync1.example.com/msync"));
    }

    @Test
    public void getUnformattedCustomMediaUrlWithHostNum() {
        RemoteMediaServer underTest = getServerWithHostNum(null);
        setCustomMediaServer(sCustomServerWithNoFormatting);

        String syncUrl = underTest.syncURL();

        assertThat(syncUrl, is("https://sync.example.com/msync"));
    }

    @Test
    public void getUnformattedCustomMediaUrlWithNoHostNum() {
        RemoteMediaServer underTest = getServerWithHostNum(1);
        setCustomMediaServer(sCustomServerWithNoFormatting);

        String syncUrl = underTest.syncURL();

        assertThat(syncUrl, is("https://sync.example.com/msync"));
    }

    @Test
    public void invalidSettingReturnsCorrectResultWithNoHostNum() {
        RemoteMediaServer underTest = getServerWithHostNum(null);
        setCustomServerWithNoUrl();

        String syncUrl = underTest.syncURL();

        assertThat(syncUrl, is(sDefaultUrlNoHostNum));
    }

    @Test
    public void invalidSettingReturnsCorrectResultWithHostNum() {
        RemoteMediaServer underTest = getServerWithHostNum(1);
        setCustomServerWithNoUrl();

        String syncUrl = underTest.syncURL();

        assertThat(syncUrl, is(sDefaultUrlWithHostNum));
    }

    private void setCustomServerWithNoUrl() {
        SharedPreferences userPreferences = AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance());
        userPreferences.edit().putBoolean("useCustomSyncServer", true).apply();
    }

    private void setCustomMediaServer(String s) {

        SharedPreferences userPreferences = AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance());
        Editor e = userPreferences.edit();
        e.putBoolean("useCustomSyncServer", true);
        e.putString("syncMediaUrl", s);
        e.apply();
    }

    @NonNull
    private RemoteMediaServer getServerWithHostNum(Integer hostNum) {
        return new RemoteMediaServer(null, null, null, new HostNum(hostNum));
    }


}
