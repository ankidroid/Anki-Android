package com.ichi2.anki.utils

import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.utils.KeyUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyUtilsTest {

    @Test
    fun testIsDigitWithValidDigitShouldReturnTrue() {
        val keyEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_5)
        assertTrue(KeyUtils.isDigit(keyEvent))
    }

    @Test
    fun testIsDigitWithUnValidDigitShouldReturnFalse() {
        val keyEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A)
        assertFalse(KeyUtils.isDigit(keyEvent))
    }

    @Test
    fun testIsDigitWithNonLanguageKeyShouldReturnFalse() {
        val keyEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)
        assertFalse(KeyUtils.isDigit(keyEvent))
    }

    @Test
    fun testGetDigitWithValidDigitShouldReturnThisDigit() {
        val keyEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_7)
        assertEquals(7, KeyUtils.getDigit(keyEvent))
    }

    @Test
    fun testGetDigitWithNonDigitShouldReturnItsCode() {
        val keyEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A)
        assertEquals(49, KeyUtils.getDigit(keyEvent))
    }

    @Test
    fun testGetDigitWithNonLanguageKeyShouldReturnItsCode() {
        val keyEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)
        assertEquals(-48, KeyUtils.getDigit(keyEvent))
    }
}
