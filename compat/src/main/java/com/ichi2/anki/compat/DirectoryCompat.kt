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

package com.ichi2.anki.compat

import com.ichi2.anki.model.Directory
import java.io.IOException
import java.nio.file.NotDirectoryException

/**
 * Whether this directory has at least one file
 * @return Whether the directory has a file.
 * @throws [SecurityException] If a security manager exists and its SecurityManager.checkRead(String)
 * method denies read access to the directory
 * @throws [java.io.FileNotFoundException] if the file does not exist
 * @throws [NotDirectoryException] if the file could not otherwise be opened because it is not
 * a directory (optional specific exception), (starting at API 26)
 * @throws [IOException] if an I/O error occurs.
 * This also occurred on an existing directory because of permission issue
 * that we could not reproduce. See https://github.com/ankidroid/Anki-Android/issues/10358
 */
@Throws(IOException::class, SecurityException::class, NotDirectoryException::class)
fun Directory.hasFiles(): Boolean = CompatHelper.compat.hasFiles(directory)
