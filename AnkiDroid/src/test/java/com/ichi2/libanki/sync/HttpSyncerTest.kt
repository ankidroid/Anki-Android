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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.utils.KotlinCleanup
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

@KotlinCleanup("`is` => equalTo")
@RunWith(AndroidJUnit4::class)
class HttpSyncerTest {

    @Test
    fun defaultMediaUrlWithNoHostNum() {
        val underTest = getServerWithHostNum(null)
        val syncUrl = underTest.syncURL()
        assertThat(syncUrl, `is`(sDefaultUrlNoHostNum))
    }

    @Test
    fun defaultMediaUrlWithHostNum() {
        val underTest = getServerWithHostNum(1)
        val syncUrl = underTest.syncURL()
        assertThat(syncUrl, `is`(sDefaultUrlWithHostNum))
    }

    @Ignore("Not yet supported")
    @Test
    fun customMediaUrlWithNoHostNum() {
        val underTest = getServerWithHostNum(null)
        setCustomServer(sCustomServerWithFormatting)
        val syncUrl = underTest.syncURL()
        assertThat(syncUrl, `is`("https://sync.example.com/sync/"))
    }

    @Ignore("Not yet supported")
    @Test
    fun customMediaUrlWithHostNum() {
        val underTest = getServerWithHostNum(1)
        setCustomServer(sCustomServerWithFormatting)
        val syncUrl = underTest.syncURL()
        assertThat(syncUrl, `is`("https://sync1.example.com/sync/"))
    }

    @Test
    fun unformattedCustomMediaUrlWithHostNum() {
        val underTest = getServerWithHostNum(null)
        setCustomServer(sCustomServerWithNoFormatting)
        val syncUrl = underTest.syncURL()
        assertThat(syncUrl, `is`("https://sync.example.com/sync/"))
    }

    @Test
    fun unformattedCustomMediaUrlWithNoHostNum() {
        val underTest = getServerWithHostNum(1)
        setCustomServer(sCustomServerWithNoFormatting)
        val syncUrl = underTest.syncURL()
        assertThat(syncUrl, `is`("https://sync.example.com/sync/"))
    }

    @Test
    fun invalidSettingReturnsCorrectResultWithNoHostNum() {
        val underTest = getServerWithHostNum(null)
        setCustomServerWithNoUrl()
        val syncUrl = underTest.syncURL()
        assertThat(syncUrl, `is`(sDefaultUrlNoHostNum))
    }

    @Test
    fun invalidSettingReturnsCorrectResultWithHostNum() {
        val underTest = getServerWithHostNum(1)
        setCustomServerWithNoUrl()
        val syncUrl = underTest.syncURL()
        assertThat(syncUrl, `is`(sDefaultUrlWithHostNum))
    }

    @KotlinCleanup("use edit{} extension function")
    private fun setCustomServerWithNoUrl() {
        val userPreferences = AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance())
        userPreferences.edit().putBoolean("useCustomSyncServer", true).apply()
    }

    @KotlinCleanup("use edit{} extension function")
    private fun setCustomServer(s: String) {
        val userPreferences = AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance())
        val e = userPreferences.edit()
        e.putBoolean("useCustomSyncServer", true)
        e.putString("syncBaseUrl", s)
        e.apply()
    }

    private fun getServerWithHostNum(hostNum: Int?): HttpSyncer {
        return HttpSyncer(null, null, HostNum(hostNum))
    }

    @KotlinCleanup("rename all")
    companion object {
        private const val sCustomServerWithNoFormatting = "https://sync.example.com/"
        private const val sCustomServerWithFormatting = "https://sync%s.example.com/"
        private const val sDefaultUrlNoHostNum = "https://sync.ankiweb.net/sync/"
        private const val sDefaultUrlWithHostNum = "https://sync1.ankiweb.net/sync/"
    }
}
