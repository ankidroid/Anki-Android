// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.multiprofile

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class AppRestartTest : RobolectricTest() {
    @Test
    fun `only the ProcessPhoenix process is recognised as it`() {
        assertTrue(isPhoenixProcessName("com.ichi2.anki:phoenix"))
        assertFalse(isPhoenixProcessName("com.ichi2.anki"))
        assertFalse(isPhoenixProcessName("com.ichi2.anki:acra"))
        assertFalse(isPhoenixProcessName(null))
    }
}
