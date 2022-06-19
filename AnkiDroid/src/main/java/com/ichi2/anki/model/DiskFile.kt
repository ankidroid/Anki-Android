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

package com.ichi2.anki.model

import androidx.annotation.VisibleForTesting
import org.apache.commons.io.FileUtils
import java.io.File

/**
 * A file which exists (at time of instantiation) and is not a directory.
 * [java.io.File] could be a directory. A [DiskFile] is definitely a file
 *
 * Note: The file could be deleted manually, but this now represents an edge case
 * This allows us to assume that a file exists for future operations
 */
class DiskFile private constructor(val file: File) {
    /** @see [File.renameTo] */
    fun renameTo(destination: File): Boolean = file.renameTo(destination)

    /** @see [FileUtils.contentEquals] */
    fun contentEquals(f2: File): Boolean = FileUtils.contentEquals(file, f2)
    override fun toString(): String = file.canonicalPath

    companion object {
        /**
         * Returns a [DiskFile] from [file] if `DiskFile` precondition holds; i.e. [file] is an existing file.
         * Otherwise returns `null`.
         */
        fun createInstance(file: File): DiskFile? {
            if (!file.exists() || !file.isFile) {
                return null
            }
            return DiskFile(file)
        }

        /** Creates an instance. It must only be called when the preconditions are known to be true */
        @VisibleForTesting
        fun createInstanceUnsafe(file: File) = DiskFile(file)
    }
}
