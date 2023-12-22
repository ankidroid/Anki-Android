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

import com.ichi2.compat.CompatHelper
import java.io.File
import java.io.IOException
import java.nio.file.NotDirectoryException
import kotlin.jvm.Throws

/**
 * A directory which is assumed to exist (existed when class was instantiated)
 *
 * @see [DiskFile]
 */
class Directory private constructor(val directory: File) {
    /** @see [File.renameTo] */
    fun renameTo(destination: File): Boolean = directory.renameTo(destination)

    /** List of files in this directory. If this is not a directory or no longer exists, then an empty array. */
    fun listFiles(): Array<out File> = directory.listFiles() ?: emptyArray()

    /**
     * Whether this directory has at least files
     * @return Whether the directory has file.
     * @throws [SecurityException] If a security manager exists and its SecurityManager.checkRead(String)
     * method denies read access to the directory
     * @throws [java.io.FileNotFoundException] if the file do not exists
     * @throws [NotDirectoryException] if the file could not otherwise be opened because it is not
     * a directory (optional specific exception), (starting at API 26)
     * @throws [IOException] – if an I/O error occurs.
     * This also occurred on an existing directory because of permission issue
     * that we could not reproduce. See https://github.com/ankidroid/Anki-Android/issues/10358
     */
    @Throws(IOException::class, SecurityException::class, NotDirectoryException::class)
    fun hasFiles(): Boolean = CompatHelper.compat.hasFiles(directory)

    /** The [canonical path][java.io.File.getCanonicalPath] for the file */
    override fun toString(): String = directory.canonicalPath

    companion object {
        /**
         * Returns a [Directory] from [path] if `Directory` precondition holds; i.e. [path] is an existing directory.
         * Otherwise returns `null`.
         */
        fun createInstance(path: String): Directory? = createInstance(File(path))

        /**
         * Returns a [Directory] from [file] if `Directory` precondition holds; i.e. [file] is an existing directory.
         * Otherwise returns `null`.
         */
        fun createInstance(file: File): Directory? {
            if (!file.exists() || !file.isDirectory) {
                return null
            }
            return Directory(file)
        }

        /** Creates an instance. Only call it if [Directory] preconditions are known to be true */
        fun createInstanceUnsafe(file: File) = Directory(file)
    }
}
