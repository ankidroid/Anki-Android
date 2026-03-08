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
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
class ProfileManagerTest {
    private lateinit var context: Context

    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(PROFILE_REGISTRY_FILENAME, Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs.edit(commit = true) { clear() }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `initialize sets up Default profile if none exists`() {
        ProfileManager.create(context)

        assertEquals("default", prefs.getString(KEY_LAST_ACTIVE_PROFILE_ID, null))
    }

    @Test
    fun `Existing Legacy user is automatically migrated to Default profile`() {
        File(context.filesDir, "old_user_data.txt").apply { writeText("Important Data") }

        val manager = ProfileManager.create(context)

        assertEquals("default", prefs.getString(KEY_LAST_ACTIVE_PROFILE_ID, null))

        val activeContext = manager.activeProfileContext as ProfileContextWrapper
        val currentFile = File(activeContext.filesDir, "old_user_data.txt")

        assertTrue("Default profile must see legacy files", currentFile.exists())
        assertEquals("Important Data", currentFile.readText())
        assertEquals("Path must match root filesDir", context.filesDir.absolutePath, activeContext.filesDir.absolutePath)
    }

    @Test
    fun `Manager loads existing previously active profile on restart`() {
        val aliceId = "p_alice"

        prefs.edit(commit = true) {
            putString(KEY_LAST_ACTIVE_PROFILE_ID, aliceId)
            putString(aliceId, "Alice")
        }

        val manager = ProfileManager.create(context)

        val activeContext = manager.activeProfileContext
        assertTrue(activeContext.filesDir.absolutePath.contains(aliceId))
    }

    @Test
    @Config(minSdk = Build.VERSION_CODES.P)
    fun `New profile sets WebView directory suffix (API 28+)`() {
        val newId = "p_webview_test"
        prefs.edit(commit = true) { putString(KEY_LAST_ACTIVE_PROFILE_ID, newId) }

        mockkStatic(WebView::class)
        every { WebView.setDataDirectorySuffix(any()) } just runs

        ProfileManager.create(context)

        verify(exactly = 1) { WebView.setDataDirectorySuffix(newId) }
    }

    @Test
    @Config(minSdk = Build.VERSION_CODES.P)
    fun `Default profile does not set WebView directory suffix (API 28+)`() {
        mockkStatic(WebView::class)
        every { WebView.setDataDirectorySuffix(any()) } just runs

        ProfileManager.create(context)

        verify(exactly = 0) { WebView.setDataDirectorySuffix(any()) }
    }

    @Test
    @Config(minSdk = Build.VERSION_CODES.P)
    fun `Swallows exception if WebView is already initialized`() {
        val newId = "p_error_test"
        prefs.edit(commit = true) { putString(KEY_LAST_ACTIVE_PROFILE_ID, newId) }

        mockkStatic(WebView::class)
        every { WebView.setDataDirectorySuffix(any()) } throws IllegalStateException("Already initialized")

        try {
            ProfileManager.create(context)
        } catch (e: Exception) {
            throw AssertionError("Manager should have caught the WebView exception but didn't", e)
        }

        verify(exactly = 1) { WebView.setDataDirectorySuffix(newId) }
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.O_MR1])
    fun `Legacy device clears cookies on init (Pre-API 28)`() {
        mockkStatic(CookieManager::class)
        val mockCookies = mockk<CookieManager>(relaxed = true)
        every { CookieManager.getInstance() } returns mockCookies
        ProfileManager.create(context)

        verify(exactly = 1) { mockCookies.removeAllCookies(null) }
    }

    @Test
    fun `DEFAULT_PROFILE_ID must be strictly 'default' to preserve legacy compatibility`() {
        assertEquals("default", ProfileManager.DEFAULT_PROFILE_ID)
    }

    @Test
    fun `ProfileMetadata JSON round-trip preserves all fields`() {
        val original =
            ProfileManager.ProfileMetadata(
                displayName = "Test User",
                version = 5,
                createdTimestamp = "2025-12-31T23:59:59Z",
            )

        val jsonString = original.toJson()
        val reconstructed = ProfileManager.ProfileMetadata.fromJson(jsonString)

        assertEquals("Serialization round-trip failed!", original, reconstructed)
    }
}
