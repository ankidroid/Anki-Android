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

package com.ichi2.libanki.sync

import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.SyncPreferences
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemoteMediaServerTest {
    @Test
    fun defaultMediaUrlWithNoHostNum() {
        val underTest = getServerWithHostNum(null)
        val syncUrl = underTest.syncURL()
        assertThat(syncUrl, equalTo(defaultUrlNoHostNum))
    }

    @Test
    fun defaultMediaUrlWithHostNum() {
        val underTest = getServerWithHostNum(1)
        val syncUrl = underTest.syncURL()
        assertThat(syncUrl, equalTo(defaultUrlWithHostNum))
    }

    @Ignore("Not yet supported")
    @Test
    fun customMediaUrlWithNoHostNum() {
        val underTest = getServerWithHostNum(null)
        setCustomMediaServer(customServerWithFormatting)
        val syncUrl = underTest.syncURL()
        assertThat(syncUrl, equalTo("https://sync.example.com/msync"))
    }

    @Ignore("Not yet supported")
    @Test
    fun customMediaUrlWithHostNum() {
        val underTest = getServerWithHostNum(1)
        setCustomMediaServer(customServerWithFormatting)
        val syncUrl = underTest.syncURL()
        assertThat(syncUrl, equalTo("https://sync1.example.com/msync"))
    }

    @Test
    fun unformattedCustomMediaUrlWithHostNum() {
        val underTest = getServerWithHostNum(null)
        setCustomMediaServer(customServerWithNoFormatting)
        val syncUrl = underTest.syncURL()
        assertThat(syncUrl, equalTo("https://sync.example.com/msync/"))
    }

    @Test
    fun unformattedCustomMediaUrlWithNoHostNum() {
        val underTest = getServerWithHostNum(1)
        setCustomMediaServer(customServerWithNoFormatting)
        val syncUrl = underTest.syncURL()
        assertThat(syncUrl, equalTo("https://sync.example.com/msync/"))
    }

    @Test
    fun invalidSettingReturnsCorrectResultWithNoHostNum() {
        val underTest = getServerWithHostNum(null)
        setCustomServerWithNoUrl()
        val syncUrl = underTest.syncURL()
        assertThat(syncUrl, equalTo(defaultUrlNoHostNum))
    }

    @Test
    fun invalidSettingReturnsCorrectResultWithHostNum() {
        val underTest = getServerWithHostNum(1)
        setCustomServerWithNoUrl()
        val syncUrl = underTest.syncURL()
        assertThat(syncUrl, equalTo(defaultUrlWithHostNum))
    }

    private fun setCustomServerWithNoUrl() {
        AnkiDroidApp.getSharedPrefs(AnkiDroidApp.instance)
            .edit {
                putBoolean(SyncPreferences.CUSTOM_SYNC_ENABLED, true)
            }
    }

    private fun setCustomMediaServer(s: String) {
        AnkiDroidApp.getSharedPrefs(AnkiDroidApp.instance)
            .edit {
                putBoolean(SyncPreferences.CUSTOM_SYNC_ENABLED, true)
                putString(SyncPreferences.CUSTOM_SYNC_URI, s)
            }
    }

    private fun getServerWithHostNum(hostNum: Int?): RemoteMediaServer {
        return RemoteMediaServer(null, null, null, HostNum(hostNum))
    }

    companion object {
        // COULD_BE_BETTER: We currently fail on a trailing flash in these variables.
        private const val customServerWithNoFormatting = "https://sync.example.com/"
        private const val customServerWithFormatting = "https://sync%s.example.com/"
        private const val defaultUrlNoHostNum = "https://sync.ankiweb.net/msync/"
        private const val defaultUrlWithHostNum = "https://sync1.ankiweb.net/msync/"
    }
}
