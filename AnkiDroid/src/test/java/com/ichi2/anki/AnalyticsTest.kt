/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.anki

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.preference.*
import com.brsanthu.googleanalytics.GoogleAnalytics
import com.brsanthu.googleanalytics.GoogleAnalyticsBuilder
import com.brsanthu.googleanalytics.request.ExceptionHit
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.utils.KotlinCleanup
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
@KotlinCleanup("when -> whenever")
class AnalyticsTest {
    @Mock
    private lateinit var mMockContext: Context

    @Mock
    private lateinit var mMockResources: Resources

    @Mock
    private lateinit var mMockSharedPreferences: SharedPreferences

    @Mock
    private lateinit var mMockSharedPreferencesEditor: SharedPreferences.Editor
    @Before
    fun setUp() {
        UsageAnalytics.resetForTests()
        MockitoAnnotations.openMocks(this)

        `when`((mMockResources.getBoolean(R.bool.ga_anonymizeIp))).thenReturn(true)
        `when`(mMockResources.getInteger(R.integer.ga_sampleFrequency))
            .thenReturn(10)
        `when`(mMockContext.resources)
            .thenReturn(mMockResources)
        `when`(mMockContext.getString(R.string.ga_trackingId))
            .thenReturn("Mock Tracking ID")
        `when`(mMockContext.getString(R.string.app_name))
            .thenReturn("Mock Application Name")
        `when`(mMockContext.packageName)
            .thenReturn("mock_context")
        `when`(mMockContext.getSharedPreferences("mock_context_preferences", Context.MODE_PRIVATE))
            .thenReturn(mMockSharedPreferences)
        `when`(mMockSharedPreferences.getBoolean(UsageAnalytics.ANALYTICS_OPTIN_KEY, false))
            .thenReturn(true)
        `when`(mMockSharedPreferencesEditor.putBoolean(UsageAnalytics.ANALYTICS_OPTIN_KEY, true))
            .thenReturn(mMockSharedPreferencesEditor)
        `when`(mMockSharedPreferences.edit())
            .thenReturn(mMockSharedPreferencesEditor)
    }

    private class SpyGoogleAnalyticsBuilder : GoogleAnalyticsBuilder() {
        override fun build(): GoogleAnalytics {
            val analytics = super.build()
            return spy(analytics)
        }
    }

    @After
    fun validate() {
        validateMockitoUsage()
    }

    @Test // TODO Tracked in https://github.com/ankidroid/Anki-Android/issues/5019
    @Suppress("DEPRECATION")
    fun testSendException() {
        mockStatic(PreferenceManager::class.java).use { _ ->
            mockStatic(GoogleAnalytics::class.java).use { _ ->
                `when`(PreferenceManager.getDefaultSharedPreferences(ArgumentMatchers.any()))
                    .thenReturn(mMockSharedPreferences)
                `when`(GoogleAnalytics.builder())
                    .thenReturn(SpyGoogleAnalyticsBuilder())

                // This is actually a Mockito Spy of GoogleAnalyticsImpl
                val analytics = mMockContext.let { UsageAnalytics.initialize(it) }

                // no root cause
                val exception = mock(Exception::class.java)
                `when`(exception.cause).thenReturn(null)
                val cause = UsageAnalytics.getCause(exception)
                verify(exception).cause
                Assert.assertEquals(exception, cause)

                // a 3-exception chain inside the actual analytics call
                val childException = mock(Exception::class.java)
                `when`(childException.cause).thenReturn(null)
                `when`(childException.toString()).thenReturn("child exception toString()")
                val parentException = mock(Exception::class.java)
                `when`(parentException.cause).thenReturn(childException)
                val grandparentException = mock(Exception::class.java)
                `when`(grandparentException.cause).thenReturn(parentException)

                // prepare analytics so we can inspect what happens
                val spyHit = spy(ExceptionHit())
                doReturn(spyHit).`when`(analytics)?.exception()
                try {
                    UsageAnalytics.sendAnalyticsException(grandparentException, false)
                } catch (e: Exception) {
                    // do nothing - this is expected because UsageAnalytics isn't fully initialized
                }
                verify(grandparentException).cause
                verify(parentException).cause
                verify(childException).cause
                verify(analytics)?.exception()
                verify(spyHit).exceptionDescription(ArgumentMatchers.anyString())
                verify(spyHit).sendAsync()
                Assert.assertEquals(spyHit.exceptionDescription(), "child exception toString()")
            }
        }
    }
}
