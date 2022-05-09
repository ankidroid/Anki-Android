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
package com.ichi2.compat.customtabs

import android.app.Activity
import android.content.pm.PackageManager
import androidx.annotation.CheckResult
import androidx.annotation.NonNull
import androidx.browser.customtabs.CustomTabsClient
import com.ichi2.utils.KotlinCleanup
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner

@KotlinCleanup("`when` -> whenever")
@KotlinCleanup("remove @NonNull")
@RunWith(RobolectricTestRunner::class)
class CustomTabActivityHelperTest {
    @Before
    fun before() {
        CustomTabActivityHelper.resetFailed()
    }

    @Test
    fun ensureInvalidClientWithSecurityExceptionDoesNotCrash() {
        val badClient = getClientThrowingSecurityException()
        val customTabActivityHelper = getValidTabHandler()

        customTabActivityHelper.onServiceConnected(badClient)

        assertThat("Should be failed after call", customTabActivityHelper.isFailed)
    }

    @Test
    fun invalidClientMeansFallbackIsCalled() {
        val badClient = getClientThrowingSecurityException()
        val customTabActivityHelper = getValidTabHandler()

        customTabActivityHelper.onServiceConnected(badClient)

        val fallback = mock(CustomTabActivityHelper.CustomTabFallback::class.java)
        val activity = mock(Activity::class.java)
        val packageManager = mock(PackageManager::class.java)

        `when`(activity.packageManager).thenReturn(packageManager)
        `when`(packageManager.queryIntentActivities(any(), anyInt())).thenReturn(emptyList())

        CustomTabActivityHelper.openCustomTab(activity, mock(), mock(), fallback)

        verify(fallback, times(1)).openUri(any(), any())
    }

    @NonNull @CheckResult
    @KotlinCleanup("Use not() matcher")
    private fun getValidTabHandler(): CustomTabActivityHelper {
        val customTabActivityHelper = CustomTabActivityHelper()
        assertThat("Should not be failed before call", !customTabActivityHelper.isFailed)
        return customTabActivityHelper
    }

    @NonNull @CheckResult
    @KotlinCleanup("Try to use mock { }")
    private fun getClientThrowingSecurityException(): CustomTabsClient {
        val exceptionToThrow = SecurityException("Binder invocation to an incorrect interface")

        val invalidClient = mock(CustomTabsClient::class.java)
        doThrow(exceptionToThrow).`when`(invalidClient).warmup(anyLong())
        doThrow(exceptionToThrow).`when`(invalidClient).extraCommand(anyString(), any())
        doThrow(exceptionToThrow).`when`(invalidClient).newSession(any())
        return invalidClient
    }
}
