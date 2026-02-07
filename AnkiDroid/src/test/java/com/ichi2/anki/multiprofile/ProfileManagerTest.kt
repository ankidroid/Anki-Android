/*
 * Copyright (c) 2025 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.multiprofile

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.multiprofile.ProfileManager.Companion.KEY_LAST_ACTIVE_PROFILE_ID
import com.ichi2.anki.multiprofile.ProfileManager.Companion.PROFILE_REGISTRY_FILENAME
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
class ProfileManagerTest {
    private lateinit var context: Context
    private lateinit var profileManager: ProfileManager

    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(PROFILE_REGISTRY_FILENAME, Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        prefs.edit(commit = true) { clear() }
        profileManager = ProfileManager(context)
    }

    @Test
    fun `initializeAndLoadActiveProfile sets up Default profile`() {
        profileManager.initializeAndLoadActiveProfile()

        assertEquals("default", prefs.getString(KEY_LAST_ACTIVE_PROFILE_ID, null))
    }

    @Test
    fun `Existing Legacy user is automatically migrated to Default profile`() {
        File(context.filesDir, "old_user_data.txt").apply { writeText("Important Data") }

        profileManager.initializeAndLoadActiveProfile()

        assertEquals("default", prefs.getString(KEY_LAST_ACTIVE_PROFILE_ID, null))

        val activeContext = profileManager.activeProfileContext as ProfileContextWrapper
        val currentFile = File(activeContext.filesDir, "old_user_data.txt")

        assertTrue("Default profile must see legacy files", currentFile.exists())
        assertEquals("Important Data", currentFile.readText())
        assertEquals(context.filesDir.absolutePath, activeContext.filesDir.absolutePath)
    }

    @Test
    fun `Manager loads existing previously active profile on restart`() {
        val aliceId = "p_alice"
        prefs.edit(commit = true) {
            putString(KEY_LAST_ACTIVE_PROFILE_ID, aliceId)
            putString(aliceId, "Alice")
        }

        profileManager.initializeAndLoadActiveProfile()

        val activeContext = profileManager.activeProfileContext
        assertTrue(activeContext.filesDir.absolutePath.contains(aliceId))
        assertEquals(aliceId, prefs.getString(KEY_LAST_ACTIVE_PROFILE_ID, ""))
    }

    @Test
    @Config(minSdk = Build.VERSION_CODES.P)
    fun `New profile sets WebView directory suffix (API 28+)`() {
        val newId = "p_webview_test"
        prefs.edit(commit = true) { putString(KEY_LAST_ACTIVE_PROFILE_ID, newId) }

        mockWebViewStatic { mockedWebView ->
            profileManager.initializeAndLoadActiveProfile()
            mockedWebView.verify { WebView.setDataDirectorySuffix(newId) }
        }
    }

    @Test
    @Config(minSdk = Build.VERSION_CODES.P)
    fun `Default profile does NOT set WebView directory suffix (API 28+)`() {
        mockWebViewStatic { mockedWebView ->
            profileManager.initializeAndLoadActiveProfile()
            mockedWebView.verify({ WebView.setDataDirectorySuffix(any()) }, Mockito.never())
        }
    }

    @Test
    @Config(minSdk = Build.VERSION_CODES.P)
    fun `Swallows exception if WebView is already initialized`() {
        val newId = "p_error_test"
        prefs.edit(commit = true) { putString(KEY_LAST_ACTIVE_PROFILE_ID, newId) }

        mockWebViewStatic { mockedWebView ->
            mockedWebView
                .`when`<Unit> { WebView.setDataDirectorySuffix(newId) }
                .thenThrow(IllegalStateException("WebView cannot change suffix after initialization"))

            try {
                profileManager.initializeAndLoadActiveProfile()
            } catch (e: Exception) {
                throw AssertionError("Manager should have caught the WebView exception but didn't", e)
            }
        }
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.O_MR1])
    fun `Legacy device clears cookies on init`() {
        mockCookieManagerStatic { mockInstance ->
            profileManager.initializeAndLoadActiveProfile()
            Mockito.verify(mockInstance).removeAllCookies(null)
        }
    }

    @Test
    fun `DEFAULT_PROFILE_ID must be strictly 'default' to preserve legacy compatibility`() {
        assertEquals("default", ProfileManager.DEFAULT_PROFILE_ID)
    }

    private inline fun mockWebViewStatic(block: (org.mockito.MockedStatic<WebView>) -> Unit) {
        Mockito.mockStatic(WebView::class.java).use { block(it) }
    }

    private inline fun mockCookieManagerStatic(block: (CookieManager) -> Unit) {
        val mockInstance = Mockito.mock(CookieManager::class.java)
        Mockito.mockStatic(CookieManager::class.java).use { staticMock ->
            staticMock.`when`<CookieManager> { CookieManager.getInstance() }.thenReturn(mockInstance)
            block(mockInstance)
        }
    }
}
