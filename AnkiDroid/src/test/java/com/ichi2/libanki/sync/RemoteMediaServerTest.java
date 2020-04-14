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
    private static String sCustomServerWithNoFormatting = "https://sync.example.com/msync";
    private static String sCustomServerWithFormatting   = "https://sync%s.example.com/msync";

    @Test
    public void getDefaultMediaUrlWithNoHostNum() {
        RemoteMediaServer underTest = getServerWithHostNum("");

        String syncUrl = underTest.syncURL();

        assertThat(syncUrl, is("https://sync.ankiweb.net/msync/"));
    }


    @Test
    @Ignore("Not yet supported")
    public void getDefaultMediaUrlWithHostNum() {
        RemoteMediaServer underTest = getServerWithHostNum("1");

        String syncUrl = underTest.syncURL();

        assertThat(syncUrl, is("https://sync1.ankiweb.net/msync/"));
    }


    @Test
    @Ignore("Not yet supported")
    public void getCustomMediaUrlWithNoHostNum() {
        RemoteMediaServer underTest = getServerWithHostNum("");
        setCustomMediaServer(sCustomServerWithFormatting);

        String syncUrl = underTest.syncURL();

        assertThat(syncUrl, is("https://sync.example.com/msync/"));
    }


    @Test
    @Ignore("Not yet supported")
    public void getCustomMediaUrlWithHostNum() {
        RemoteMediaServer underTest = getServerWithHostNum("1");
        setCustomMediaServer(sCustomServerWithFormatting);

        String syncUrl = underTest.syncURL();

        assertThat(syncUrl, is("https://sync1.example.com/msync/"));
    }

    @Test
    public void getUnformattedCustomMediaUrlWithHostNum() {
        RemoteMediaServer underTest = getServerWithHostNum("");
        setCustomMediaServer(sCustomServerWithNoFormatting);

        String syncUrl = underTest.syncURL();

        assertThat(syncUrl, is("https://sync.example.com/msync/"));
    }

    @Test
    public void getUnformattedCustomMediaUrlWithNoHostNum() {
        RemoteMediaServer underTest = getServerWithHostNum("1");
        setCustomMediaServer(sCustomServerWithNoFormatting);

        String syncUrl = underTest.syncURL();

        assertThat(syncUrl, is("https://sync.example.com/msync/"));
    }

    private void setCustomMediaServer(String s) {

        SharedPreferences userPreferences = AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance());
        Editor e = userPreferences.edit();
        e.putBoolean("useCustomSyncServer", true);
        e.putString("syncMediaUrl", s);
        e.commit();
    }

    @NonNull
    private RemoteMediaServer getServerWithHostNum(String hostNum) {
        return new RemoteMediaServer(null, null, null, hostNum);
    }
}
