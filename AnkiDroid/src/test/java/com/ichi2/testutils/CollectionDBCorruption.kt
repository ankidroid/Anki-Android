/*
 *  Copyright (c) 2022 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.testutils

import android.content.Context
import com.ichi2.anki.CollectionHelper
import com.ichi2.annotations.KotlinCleanup
import com.ichi2.annotations.NeedsTest
import timber.log.Timber
import java.io.File
import java.io.RandomAccessFile
import kotlin.random.Random

/**
 * Corrupt the file at `path`.
 * Assumes the File at pas exists, is readable, writable, and at least 100 bytes.
 */
@KotlinCleanup("androidTest/DBTest has corruption but wasn't sufficient. Combine this with that")
@NeedsTest("test corruption")
object CollectionDBCorruption {
    // It writes 100 bytes at position 100 to 199
    private fun corrupt(path: String) {
        RandomAccessFile(File(path), "rw").use { col ->
            col.seek(100)
            col.write(Random.nextBytes(100))
        }

        assert(File(path).length() != 0L)
        Timber.w("Explicitly corrupted database file: $path")
    }

    /**
     * Closes and corrupts [CollectionHelper]'s collection
     */
    @NeedsTest("test with a new collection")
    fun closeAndCorrupt(context: Context): String {
        val col = CollectionHelper.instance.getColUnsafe(context)!!
        val path = col.path
        col.close()
        corrupt(path)
        return path
    }
}
