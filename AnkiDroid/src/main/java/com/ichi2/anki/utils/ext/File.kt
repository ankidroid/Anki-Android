/*
 *  Copyright (c) 2026 Shaan Narendran <shaannaren06@gmail.com>
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

package com.ichi2.anki.utils.ext

import com.ichi2.anki.common.annotations.NeedsTest
import java.io.File

/**
 * @param name Relative file name or path inside this directory.
 * @return `true` if the named file exists within this directory
 *
 * @throws SecurityException If [SecurityManager.checkRead] is used and access is denied.
 */
@NeedsTest("../ handling")
@NeedsTest("file exists")
@NeedsTest("file does not exist")
@NeedsTest("name points to a directory")
@NeedsTest("file exists in subdirectory")
@NeedsTest("empty string handling")
fun File.containsFile(name: String): Boolean {
    if (!isDirectory) return false

    val maybeFile = File(this, name).canonicalFile

    // guard against path traversal
    if (!maybeFile.path.startsWith(this.canonicalPath + File.separator)) return false

    // ensure the file exists, and is not a directory
    return maybeFile.isFile
}
