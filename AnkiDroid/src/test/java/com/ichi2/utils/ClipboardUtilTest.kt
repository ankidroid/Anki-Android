//noinspection MissingCopyrightHeader #8659

package com.ichi2.utils

import android.content.ClipDescription
import android.content.ClipboardManager
import com.ichi2.utils.ClipboardUtil.hasImage
import org.junit.Test
import kotlin.test.assertFalse

class ClipboardUtilTest {
    @Test
    fun hasImageClipboardManagerNullTest() {
        val clipboardManager: ClipboardManager? = null
        assertFalse(hasImage(clipboardManager))
    }

    @Test
    fun hasImageDescriptionNullTest() {
        val clipDescription: ClipDescription? = null
        assertFalse(hasImage(clipDescription))
    }
}
