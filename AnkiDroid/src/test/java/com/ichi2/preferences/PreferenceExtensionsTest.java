package com.ichi2.preferences;

import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.modules.junit4.PowerMockRunner;

import static com.ichi2.testutils.AnkiAssert.assertDoesNotThrow;
import static com.ichi2.utils.FunctionalInterfaces.Supplier;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

//Unknown issue: @CheckResult should provide warnings on this class when return value is unused, but doesn't.
//TODO: The preference mock is messy
@RunWith(PowerMockRunner.class)
public class PreferenceExtensionsTest {

    private static Supplier<String> UNUSED_SUPPLIER = () -> { throw new UnexpectedException();};
    private static Supplier<String> EXCEPTION_SUPPLIER = () -> { throw new ExpectedException();};

    private static final String VALID_KEY = "VALID";
    private static final String VALID_RESULT = "WAS VALID KEY";
    private static final String MISSING_KEY = "INVALID";
    private static final String LAMBDA_RETURN = "LAMBDA";

    @Mock
    private SharedPreferences mMockReferences;

    @Mock
    private SharedPreferences.Editor mockEditor;

    private String getOrSetString(String key, Supplier<String> supplier) {
        return PreferenceExtensions.getOrSetString(mMockReferences, key, supplier);
    }

    @Before
    public void setUp() {
        Mockito.when(mMockReferences.contains(VALID_KEY)).thenReturn(true);
        Mockito.when(mMockReferences.getString(eq(VALID_KEY), anyString())).thenReturn(VALID_RESULT);
        Mockito.when(mMockReferences.edit()).thenReturn(mockEditor);
        Mockito.when(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor);
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
        Mockito.verify(mockEditor).putString(MISSING_KEY, LAMBDA_RETURN);
        Mockito.verify(mockEditor).apply();
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
