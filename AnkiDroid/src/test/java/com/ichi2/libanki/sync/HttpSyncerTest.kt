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
import com.ichi2.anki.preferences.sharedPrefs
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HttpSyncerTest {

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
        setCustomServer(customServerWithFormatting)
        val syncUrl = underTest.syncURL()
        assertThat(syncUrl, equalTo("https://sync.example.com/sync/"))
    }

    @Ignore("Not yet supported")
    @Test
    fun customMediaUrlWithHostNum() {
        val underTest = getServerWithHostNum(1)
        setCustomServer(customServerWithFormatting)
        val syncUrl = underTest.syncURL()
        assertThat(syncUrl, equalTo("https://sync1.example.com/sync/"))
    }

    @Test
    fun unformattedCustomMediaUrlWithHostNum() {
        val underTest = getServerWithHostNum(null)
        setCustomServer(customServerWithNoFormatting)
        val syncUrl = underTest.syncURL()
        assertThat(syncUrl, equalTo("https://sync.example.com/sync/"))
    }

    @Test
    fun unformattedCustomMediaUrlWithNoHostNum() {
        val underTest = getServerWithHostNum(1)
        setCustomServer(customServerWithNoFormatting)
        val syncUrl = underTest.syncURL()
        assertThat(syncUrl, equalTo("https://sync.example.com/sync/"))
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
        val userPreferences = AnkiDroidApp.instance.sharedPrefs()
        userPreferences.edit {
            putBoolean(SyncPreferences.CUSTOM_SYNC_ENABLED, true)
        }
    }

    private fun setCustomServer(s: String) {
        val userPreferences = AnkiDroidApp.instance.sharedPrefs()
        userPreferences.edit {
            putBoolean(SyncPreferences.CUSTOM_SYNC_ENABLED, true)
            putString(SyncPreferences.CUSTOM_SYNC_URI, s)
        }
    }

    private fun getServerWithHostNum(hostNum: Int?): HttpSyncer {
        return HttpSyncer(null, null, HostNum(hostNum))
    }

    companion object {
        private const val customServerWithNoFormatting = "https://sync.example.com/"
        private const val customServerWithFormatting = "https://sync%s.example.com/"
        private const val defaultUrlNoHostNum = "https://sync.ankiweb.net/sync/"
        private const val defaultUrlWithHostNum = "https://sync1.ankiweb.net/sync/"
    }
}
