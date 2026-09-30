// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class IdsFileTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `parcelled snapshot can be restored repeatedly`() {
        val ids = listOf(9L, 1L, Long.MAX_VALUE)
        val file = IdsFile(temporaryFolder.root, ids)
        val parcel = Parcel.obtain()
        try {
            file.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            val restored = IdsFile.CREATOR.createFromParcel(parcel)
            assertEquals(ids, restored.getIds())
            assertEquals(ids, restored.getIds())
            assertTrue(file.exists())
        } finally {
            parcel.recycle()
        }
    }

    @Test
    fun `empty selection is distinct from a missing file`() {
        val file = IdsFile(temporaryFolder.root, emptyList())
        assertEquals(emptyList(), file.getIds())
        assertTrue(file.delete())
        assertFailsWith<IOException> { file.getIds() }
    }

    @Test
    fun `incomplete header cannot be read`() {
        val file = IdsFile(temporaryFolder.root, emptyList())
        file.writeBytes(byteArrayOf(0, 0))
        assertFailsWith<IOException> { file.getIds() }
    }
}
