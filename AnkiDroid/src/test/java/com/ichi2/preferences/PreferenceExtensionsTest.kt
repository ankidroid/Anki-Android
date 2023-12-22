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
package com.ichi2.preferences

import android.content.SharedPreferences
import com.ichi2.testutils.AnkiAssert.assertDoesNotThrow
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import java.lang.RuntimeException
import java.util.function.Supplier

// Unknown issue: @CheckResult should provide warnings on this class when return value is unused, but doesn't.
class PreferenceExtensionsTest {
    @Mock
    lateinit var mockPreferences: SharedPreferences

    @Mock
    private val mMockEditor: SharedPreferences.Editor? = null

    private fun getOrSetString(
        key: String,
        supplier: Supplier<String>,
    ): String {
        return mockPreferences.getOrSetString(key, supplier)
    }

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Mockito.`when`(mockPreferences.contains(VALID_KEY)).thenReturn(true)
        Mockito.`when`(
            mockPreferences.getString(eq(VALID_KEY), anyString()),
        ).thenReturn(
            VALID_RESULT,
        )
        Mockito.`when`(mockPreferences.edit()).thenReturn(mMockEditor)
        Mockito.`when`(
            mMockEditor!!.putString(anyString(), anyString()),
        ).thenReturn(mMockEditor)
    }

    private val forMissingKey: String?
        get() = getOrSetString(MISSING_KEY) { LAMBDA_RETURN }

    @Test
    fun existingKeyReturnsMappedValue() {
        val ret = getOrSetString(VALID_KEY, UNUSED_SUPPLIER)
        assertEquals(ret, VALID_RESULT)
    }

    @Test
    fun missingKeyReturnsLambdaValue() {
        val ret = forMissingKey
        assertEquals(ret, LAMBDA_RETURN)
    }

    @Test
    fun missingKeySetsPreference() {
        forMissingKey
        Mockito.verify(mMockEditor)?.putString(MISSING_KEY, LAMBDA_RETURN)
        Mockito.verify(mMockEditor)?.apply()
    }

    @SuppressWarnings("unused")
    fun noLambdaExceptionIfKeyExists() {
        assertDoesNotThrow { getOrSetString(VALID_KEY, EXCEPTION_SUPPLIER) }
    }

    @Test(expected = ExpectedException::class)
    fun rethrowLambdaExceptionIfKeyIsMissing() {
        getOrSetString(MISSING_KEY, EXCEPTION_SUPPLIER)
    }

    private class ExpectedException : RuntimeException()

    private class UnexpectedException : RuntimeException()

    companion object {
        private val UNUSED_SUPPLIER = Supplier<String> { throw UnexpectedException() }
        private val EXCEPTION_SUPPLIER = Supplier<String> { throw ExpectedException() }
        private const val VALID_KEY = "VALID"
        private const val VALID_RESULT = "WAS VALID KEY"
        private const val MISSING_KEY = "INVALID"
        private const val LAMBDA_RETURN = "LAMBDA"
    }
}
