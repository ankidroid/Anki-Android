// SPDX-FileCopyrightText: 2026 Aryan Jaiswal <aryanjaiswal123123@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.utils

import android.graphics.Color
import android.text.Spanned
import android.text.style.TypefaceSpan
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.testutils.EmptyApplication
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class ErrorMessageFormatterTest {
    @Test
    fun `plain message is returned unchanged`() {
        val input = "Invalid search: an `and` was found but it is not connecting two search terms."
        assertThat(formatErrorMessage(input, Color.GRAY).toString(), equalTo(input))
    }

    @Test
    fun `pre tags are replaced with a monospace span`() {
        val input = "<pre>regex parse error:\n    (?i)i[\n        ^\nerror: unclosed character class</pre>"

        val output = formatErrorMessage(input, Color.GRAY) as Spanned

        assertThat(output.toString(), not(containsString("<pre>")))
        assertThat(output.toString(), not(containsString("</pre>")))
        assertThat(output.toString(), equalTo("regex parse error:\n    (?i)i[\n        ^\nerror: unclosed character class"))

        val typefaceSpans = output.getSpans(0, output.length, TypefaceSpan::class.java)
        assertThat(typefaceSpans.size, equalTo(1))
        assertThat(typefaceSpans[0].family, equalTo("monospace"))
    }
}
