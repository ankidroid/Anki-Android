/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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
import com.ichi2.anki.BackupManager.Companion.enoughDiscSpace
import com.ichi2.anki.CollectionHelper
import com.ichi2.utils.KotlinCleanup
import org.junit.Assert.assertTrue
import java.io.File
import java.lang.IllegalStateException

object BackupManagerTestUtilities {
    @JvmStatic
    @KotlinCleanup("make context non-null")
    fun setupSpaceForBackup(context: Context?) {
        val currentAnkiDroidDirectory = CollectionHelper.getCurrentAnkiDroidDirectory(context)

        val path = File(currentAnkiDroidDirectory).parentFile
            ?: throw IllegalStateException("currentAnkiDroidDirectory had no parent")
        ShadowStatFs.markAsNonEmpty(path)

        assertTrue(enoughDiscSpace(currentAnkiDroidDirectory))
    }

    @JvmStatic
    fun reset() {
        ShadowStatFs.reset()
    }
}
