// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.progress

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class ProgressTextTest : RobolectricTest() {
    @Test
    fun `Raw resolves to its text`() {
        assertEquals("Loading", ProgressText.Raw("Loading").resolve(targetContext))
    }

    @Test
    fun `Res resolves the resource without arguments`() {
        val expected = targetContext.getString(R.string.dialog_processing)
        assertEquals(expected, ProgressText.Res(R.string.dialog_processing).resolve(targetContext))
    }

    @Test
    fun `Res resolves the resource with its arguments`() {
        val text = ProgressText.Res(R.string.progress_amount_bytes, listOf("1 MB", "2 MB")).resolve(targetContext)
        assertEquals("1 MB/2 MB", text)
    }
}
