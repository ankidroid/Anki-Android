/****************************************************************************************
 * Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>                                   *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;

import com.brsanthu.googleanalytics.GoogleAnalytics;
import com.brsanthu.googleanalytics.GoogleAnalyticsBuilder;
import com.brsanthu.googleanalytics.request.ExceptionHit;
import com.ichi2.anki.analytics.UsageAnalytics;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AnalyticsTest {

    @Mock
    private Context mMockContext;

    @Mock
    private Resources mMockResources;

    @Mock
    private SharedPreferences mMockSharedPreferences;

    @Mock
    private SharedPreferences.Editor mMockSharedPreferencesEditor;

    @Before
    public void setUp() {
        UsageAnalytics.resetForTests();

        MockitoAnnotations.openMocks(this);

        when(mMockResources.getBoolean(R.bool.ga_anonymizeIp))
                .thenReturn(true);
        when(mMockResources.getInteger(R.integer.ga_sampleFrequency))
                .thenReturn(10);
        when(mMockContext.getResources())
                .thenReturn(mMockResources);

        when(mMockContext.getString(R.string.ga_trackingId))
                .thenReturn("Mock Tracking ID");
        when(mMockContext.getString(R.string.app_name))
                .thenReturn("Mock Application Name");
        when(mMockContext.getPackageName())
                .thenReturn("mock_context");
        when(mMockContext.getSharedPreferences("mock_context_preferences", Context.MODE_PRIVATE))
                .thenReturn(mMockSharedPreferences);

        when(mMockSharedPreferences.getBoolean(UsageAnalytics.ANALYTICS_OPTIN_KEY, false))
                .thenReturn(true);

        when(mMockSharedPreferencesEditor.putBoolean(UsageAnalytics.ANALYTICS_OPTIN_KEY, true))
                .thenReturn(mMockSharedPreferencesEditor);

        when(mMockSharedPreferences.edit())
                .thenReturn(mMockSharedPreferencesEditor);

    }


    private static class SpyGoogleAnalyticsBuilder extends GoogleAnalyticsBuilder {
        public GoogleAnalytics build() {
            GoogleAnalytics analytics = super.build();
            return spy(analytics);
        }
    }


    @After
    public void validate() {
        validateMockitoUsage();
    }


    @Test
    @SuppressWarnings("deprecation") // TODO Tracked in https://github.com/ankidroid/Anki-Android/issues/5019
    public void testSendException() {

        try (
                MockedStatic<android.preference.PreferenceManager> ignored = mockStatic(android.preference.PreferenceManager.class);
                MockedStatic<GoogleAnalytics> ignored1 = mockStatic(GoogleAnalytics.class)) {

            when(android.preference.PreferenceManager.getDefaultSharedPreferences(ArgumentMatchers.any()))
                    .thenReturn(mMockSharedPreferences);

            when(GoogleAnalytics.builder())
                    .thenReturn(new SpyGoogleAnalyticsBuilder());

            // This is actually a Mockito Spy of GoogleAnalyticsImpl
            GoogleAnalytics analytics = UsageAnalytics.initialize(mMockContext);

            // no root cause
            Exception exception = mock(Exception.class);
            when(exception.getCause()).thenReturn(null);
            Throwable cause = UsageAnalytics.getCause(exception);
            verify(exception).getCause();
            Assert.assertEquals(exception, cause);

            // a 3-exception chain inside the actual analytics call
            Exception childException = mock(Exception.class);
            when(childException.getCause()).thenReturn(null);
            when(childException.toString()).thenReturn("child exception toString()");
            Exception parentException = mock(Exception.class);
            when(parentException.getCause()).thenReturn(childException);
            Exception grandparentException = mock(Exception.class);
            when(grandparentException.getCause()).thenReturn(parentException);

            // prepare analytics so we can inspect what happens
            ExceptionHit spyHit = spy(new ExceptionHit());
            doReturn(spyHit).when(analytics).exception();

            try {
                UsageAnalytics.sendAnalyticsException(grandparentException, false);
            } catch (Exception e) {
                // do nothing - this is expected because UsageAnalytics isn't fully initialized
            }
            verify(grandparentException).getCause();
            verify(parentException).getCause();
            verify(childException).getCause();
            verify(analytics).exception();
            verify(spyHit).exceptionDescription(ArgumentMatchers.anyString());
            verify(spyHit).sendAsync();
            Assert.assertEquals(spyHit.exceptionDescription(), "child exception toString()");
        }
    }
}
