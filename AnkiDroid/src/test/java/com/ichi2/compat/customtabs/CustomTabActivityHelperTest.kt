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

package com.ichi2.compat.customtabs;

import android.app.Activity;
import android.content.pm.PackageManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Collections;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.browser.customtabs.CustomTabsClient;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class CustomTabActivityHelperTest {

    @Before
    public void before() {
        CustomTabActivityHelper.resetFailed();
    }

    @Test
    public void ensureInvalidClientWithSecurityExceptionDoesNotCrash() {
        CustomTabsClient badClient  = getClientThrowingSecurityException();
        CustomTabActivityHelper customTabActivityHelper = getValidTabHandler();

        customTabActivityHelper.onServiceConnected(badClient);

        assertThat("Should be failed after call", customTabActivityHelper.isFailed());
    }


    @Test
    public void invalidClientMeansFallbackIsCalled() {
        CustomTabsClient badClient  = getClientThrowingSecurityException();
        CustomTabActivityHelper customTabActivityHelper = getValidTabHandler();

        customTabActivityHelper.onServiceConnected(badClient);

        CustomTabActivityHelper.CustomTabFallback fallback = mock(CustomTabActivityHelper.CustomTabFallback.class);
        Activity activity = mock(Activity.class);
        PackageManager packageManager = mock(PackageManager.class);

        when(activity.getPackageManager()).thenReturn(packageManager);
        when(packageManager.queryIntentActivities(any(), anyInt())).thenReturn(Collections.emptyList());

        CustomTabActivityHelper.openCustomTab(activity, null, null, fallback);

        verify(fallback, times(1)).openUri(any(), any());
    }

    @NonNull @CheckResult
    private CustomTabActivityHelper getValidTabHandler() {
        CustomTabActivityHelper customTabActivityHelper = new CustomTabActivityHelper();
        assertThat("Should not be failed before call", !customTabActivityHelper.isFailed());
        return customTabActivityHelper;
    }

    @NonNull @CheckResult
    private CustomTabsClient getClientThrowingSecurityException() {
        SecurityException exceptionToThrow = new SecurityException("Binder invocation to an incorrect interface");

        CustomTabsClient invalidClient = mock(CustomTabsClient.class);
        doThrow(exceptionToThrow).when(invalidClient).warmup(anyLong());
        doThrow(exceptionToThrow).when(invalidClient).extraCommand(anyString(), any());
        doThrow(exceptionToThrow).when(invalidClient).newSession(any());
        return invalidClient;
    }
}
