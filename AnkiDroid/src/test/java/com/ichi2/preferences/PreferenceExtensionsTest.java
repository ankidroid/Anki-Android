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

package com.ichi2.preferences;

import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.function.Supplier;

import static com.ichi2.testutils.AnkiAssert.assertDoesNotThrow;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

//Unknown issue: @CheckResult should provide warnings on this class when return value is unused, but doesn't.
public class PreferenceExtensionsTest {

    private static final Supplier<String> UNUSED_SUPPLIER = () -> { throw new UnexpectedException();};
    private static final Supplier<String> EXCEPTION_SUPPLIER = () -> { throw new ExpectedException();};

    private static final String VALID_KEY = "VALID";
    private static final String VALID_RESULT = "WAS VALID KEY";
    private static final String MISSING_KEY = "INVALID";
    private static final String LAMBDA_RETURN = "LAMBDA";

    @Mock
    private SharedPreferences mMockPreferences;

    @Mock
    private SharedPreferences.Editor mMockEditor;

    private String getOrSetString(String key, Supplier<String> supplier) {
        return PreferenceExtensions.getOrSetString(mMockPreferences, key, supplier);
    }

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        Mockito.when(mMockPreferences.contains(VALID_KEY)).thenReturn(true);
        Mockito.when(mMockPreferences.getString(eq(VALID_KEY), anyString())).thenReturn(VALID_RESULT);
        Mockito.when(mMockPreferences.edit()).thenReturn(mMockEditor);
        Mockito.when(mMockEditor.putString(anyString(), anyString())).thenReturn(mMockEditor);
    }

    private String getForMissingKey() {
        return getOrSetString(MISSING_KEY, () -> LAMBDA_RETURN);
    }

    @Test
    public void existingKeyReturnsMappedValue() {
        String ret = getOrSetString(VALID_KEY, UNUSED_SUPPLIER);
        assertEquals(ret, VALID_RESULT);
    }

    @Test
    public void missingKeyReturnsLambdaValue() {
        String ret = getForMissingKey();
        assertEquals(ret, LAMBDA_RETURN);
    }

    @SuppressWarnings("unused")
    @Test
    public void missingKeySetsPreference() {
        getForMissingKey();
        Mockito.verify(mMockEditor).putString(MISSING_KEY, LAMBDA_RETURN);
        Mockito.verify(mMockEditor).apply();
    }

    @SuppressWarnings("unused")
    public void noLambdaExceptionIfKeyExists() {
         assertDoesNotThrow(() -> getOrSetString(VALID_KEY, EXCEPTION_SUPPLIER));
    }

    @SuppressWarnings("unused")
    @Test(expected = ExpectedException.class)
    public void rethrowLambdaExceptionIfKeyIsMissing() {
        getOrSetString(MISSING_KEY, EXCEPTION_SUPPLIER);
    }

    private static class ExpectedException extends RuntimeException {
    }
    private static class UnexpectedException extends RuntimeException {
    }
}
