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
import android.preference.PreferenceManager;

import com.brsanthu.googleanalytics.GoogleAnalytics;
import com.brsanthu.googleanalytics.GoogleAnalyticsBuilder;
import com.brsanthu.googleanalytics.request.ExceptionHit;
import com.ichi2.anki.analytics.UsageAnalytics;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@PowerMockIgnore("javax.net.ssl.*")
@PrepareForTest({PreferenceManager.class, GoogleAnalytics.class})
@RunWith(PowerMockRunner.class)
public class AnalyticsTest {

    @Mock
    private Context mMockContext;

    @Mock
    private Resources mMockResources;

    @Mock
    private SharedPreferences mMockSharedPreferences;

    @Mock
    private SharedPreferences.Editor mMockSharedPreferencesEditor;

    // This is actually a Mockito Spy of GoogleAnalyticsImpl
    private GoogleAnalytics mAnalytics;

    @Before
    public void setUp() {
        PowerMockito.mockStatic(PreferenceManager.class);
        PowerMockito.mockStatic(GoogleAnalytics.class);

        MockitoAnnotations.initMocks(this);

        Mockito.when(mMockResources.getBoolean(R.bool.ga_anonymizeIp))
                .thenReturn(true);
        Mockito.when(mMockResources.getInteger(R.integer.ga_sampleFrequency))
                .thenReturn(10);
        Mockito.when(mMockContext.getResources())
                .thenReturn(mMockResources);

        Mockito.when(mMockContext.getString(R.string.ga_trackingId))
                .thenReturn("Mock Tracking ID");
        Mockito.when(mMockContext.getString(R.string.app_name))
                .thenReturn("Mock Application Name");
        Mockito.when(mMockContext.getPackageName())
                .thenReturn("mock_context");
        Mockito.when(mMockContext.getSharedPreferences("mock_context_preferences", Context.MODE_PRIVATE))
                .thenReturn(mMockSharedPreferences);

        Mockito.when(mMockSharedPreferences.getBoolean(UsageAnalytics.ANALYTICS_OPTIN_KEY, false))
                .thenReturn(true);
        Mockito.when(PreferenceManager.getDefaultSharedPreferences(ArgumentMatchers.any()))
                .thenReturn(mMockSharedPreferences);


        Mockito.when(mMockSharedPreferencesEditor.putBoolean(UsageAnalytics.ANALYTICS_OPTIN_KEY, true))
                .thenReturn(mMockSharedPreferencesEditor);

        Mockito.when(mMockSharedPreferences.edit())
                .thenReturn(mMockSharedPreferencesEditor);

        Mockito.when(GoogleAnalytics.builder())
                .thenReturn(new SpyGoogleAnalyticsBuilder());

        mAnalytics = UsageAnalytics.initialize(mMockContext);
    }

    private class SpyGoogleAnalyticsBuilder extends GoogleAnalyticsBuilder {
        public GoogleAnalytics build() {
            GoogleAnalytics analytics = super.build();
            return Mockito.spy(analytics);
        }
    }

    @After
    public void validate() {
        Mockito.validateMockitoUsage();
    }

    @Test
    public void testSendException() {

        // no root cause
        Exception exception = Mockito.mock(Exception.class);
        Mockito.when(exception.getCause()).thenReturn(null);
        Throwable cause = UsageAnalytics.getCause(exception);
        Mockito.verify(exception).getCause();
        Assert.assertEquals(exception, cause);

        // a 3-exception chain inside the actual analytics call
        Exception childException = Mockito.mock(Exception.class);
        Mockito.when(childException.getCause()).thenReturn(null);
        Mockito.when(childException.toString()).thenReturn("child exception toString()");
        Exception parentException = Mockito.mock(Exception.class);
        Mockito.when(parentException.getCause()).thenReturn(childException);
        Exception grandparentException = Mockito.mock(Exception.class);
        Mockito.when(grandparentException.getCause()).thenReturn(parentException);

        // prepare analytics so we can inspect what happens
        ExceptionHit spyHit = Mockito.spy(new ExceptionHit());
        Mockito.doReturn(spyHit).when(mAnalytics).exception();

        try {
            UsageAnalytics.sendAnalyticsException(grandparentException, false);
        } catch (Exception e) {
            // do nothing - this is expected because UsageAnalytics isn't fully initialized
        }
        Mockito.verify(grandparentException).getCause();
        Mockito.verify(parentException).getCause();
        Mockito.verify(childException).getCause();
        Mockito.verify(mAnalytics).exception();
        Mockito.verify(spyHit).exceptionDescription(ArgumentMatchers.anyString());
        Mockito.verify(spyHit).sendAsync();
        Assert.assertEquals(spyHit.exceptionDescription(), "child exception toString()");
    }
}
