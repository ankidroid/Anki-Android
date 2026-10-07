// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compat.customtabs

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import androidx.annotation.CheckResult
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsSession
import androidx.core.net.toUri
import com.ichi2.anki.compat.CompatHelper.Companion.queryIntentActivitiesCompat
import com.ichi2.anki.compat.ResolveInfoFlagsCompat
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
class CustomTabActivityHelperTest {
    @Before
    fun before() {
        CustomTabActivityHelper.resetFailed()
        ReflectionHelpers.setStaticField(CustomTabsHelper::class.java, "sPackageNameToUse", null)
    }

    @After
    fun after() {
        CustomTabActivityHelper.resetFailed()
        ReflectionHelpers.setStaticField(CustomTabsHelper::class.java, "sPackageNameToUse", null)
    }

    @Test
    fun `reading an unavailable session does not retry browser IPC`() {
        val client = mock<CustomTabsClient>()
        val helper = getValidTabHandler()
        helper.onServiceConnected(client)

        helper.session
        helper.session

        verify(client, times(1)).newSession(anyOrNull())
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
        getValidTabHandler().onServiceConnected(getClientThrowingSecurityException())

        val fallback = mock<CustomTabActivityHelper.CustomTabFallback>()
        val packageManager =
            mock<PackageManager> {
                on {
                    it.queryIntentActivitiesCompat(
                        Intent(Intent.ACTION_VIEW, "http://www.example.com".toUri()),
                        ResolveInfoFlagsCompat.EMPTY,
                    )
                } doReturn emptyList()
            }
        val activity =
            mock<Activity> {
                on { it.packageManager } doReturn packageManager
            }

        CustomTabActivityHelper.openCustomTab(activity, mock(), mock(), fallback)

        verify(fallback, times(1)).openUri(any(), any())
    }

    @Test
    fun ensureClientThrowingNullPointerExceptionFromWarmupDoesNotCrash() {
        val badClient = getClientThrowing(NullPointerException("Attempt to get length of null array"))
        val customTabActivityHelper = getValidTabHandler()

        customTabActivityHelper.onServiceConnected(badClient)

        assertThat("Should be failed after call", customTabActivityHelper.isFailed)
    }

    @Test
    fun ensureClientThrowingNullPointerExceptionFromNewSessionDoesNotCrash() {
        val exceptionToThrow = NullPointerException("Attempt to get length of null array")
        val badClient =
            mock<CustomTabsClient> {
                on { it.warmup(anyLong()) } doReturn true
                on { it.newSession(anyOrNull()) } doThrow exceptionToThrow
            }
        val customTabActivityHelper = getValidTabHandler()

        customTabActivityHelper.onServiceConnected(badClient)

        assertThat("Should be failed after call", customTabActivityHelper.isFailed)
    }

    @CheckResult
    private fun getValidTabHandler(): CustomTabActivityHelper =
        CustomTabActivityHelper().also {
            assertThat("Should not be failed before call", not(it.isFailed))
        }

    @Test
    fun `warmup illegal state still allows a session`() {
        val session = mock<CustomTabsSession>()
        val client =
            mock<CustomTabsClient> {
                on { warmup(anyLong()) } doThrow IllegalStateException("Background start restricted")
                on { newSession(anyOrNull()) } doReturn session
            }
        val helper = getValidTabHandler()
        helper.onServiceConnected(client)

        assertSame(session, helper.session)
        assertFalse(helper.isFailed)
    }

    @Test
    fun `failed initialization still releases the service binding`() {
        val activity = activityWithBrowser()
        whenever(activity.bindService(any(), any(), any<Int>())).thenReturn(true)
        val helper = getValidTabHandler()
        helper.bindCustomTabsService(activity)
        helper.bindCustomTabsService(activity)
        val connection = argumentCaptor<android.content.ServiceConnection>()
        verify(activity).bindService(any(), connection.capture(), any<Int>())

        helper.onServiceConnected(getClientThrowingSecurityException())
        assertTrue(helper.isFailed)
        helper.unbindCustomTabsService(activity)
        helper.unbindCustomTabsService(activity)

        verify(activity).unbindService(connection.firstValue)
    }

    @Test
    fun `unsuccessful bind still releases the connection`() {
        val activity = activityWithBrowser()
        // bindService returns false by default, but Android still requires unbinding.
        val helper = getValidTabHandler()
        helper.bindCustomTabsService(activity)
        val connection = argumentCaptor<android.content.ServiceConnection>()
        verify(activity).bindService(any(), connection.capture(), any<Int>())

        helper.unbindCustomTabsService(activity)

        verify(activity).unbindService(connection.firstValue)
    }

    private fun activityWithBrowser(): Activity {
        val browser =
            ResolveInfo().apply {
                activityInfo = ActivityInfo().apply { packageName = "test.browser" }
            }
        val packageManager =
            mock<PackageManager> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    on { queryIntentActivities(any(), any<PackageManager.ResolveInfoFlags>()) } doReturn listOf(browser)
                    on { resolveService(any(), any<PackageManager.ResolveInfoFlags>()) } doReturn ResolveInfo()
                } else {
                    on { queryIntentActivities(any(), any<Int>()) } doReturn listOf(browser)
                    on { resolveService(any(), any<Int>()) } doReturn ResolveInfo()
                }
            }
        return mock {
            on { it.packageManager } doReturn packageManager
        }
    }

    @CheckResult
    private fun getClientThrowingSecurityException(): CustomTabsClient =
        getClientThrowing(SecurityException("Binder invocation to an incorrect interface"))

    @CheckResult
    private fun getClientThrowing(exceptionToThrow: RuntimeException): CustomTabsClient =
        mock {
            on { it.warmup(anyLong()) } doThrow exceptionToThrow
            on { it.extraCommand(anyString(), any()) } doThrow exceptionToThrow
            on { it.newSession(anyOrNull()) } doThrow exceptionToThrow
        }
}
