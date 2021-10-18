/****************************************************************************************
 * Copyright (c) 2021 Piyush Goel <piyushgoel2008@gmail.com>                            *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/
package com.ichi2.utils

import java.io.File
import java.io.RandomAccessFile
import java.util.*

class FileOperation {
    companion object {
        @JvmStatic
        fun getFileResource(name: String): String {
            val resource = Objects.requireNonNull(FileOperation::class.java.classLoader).getResource(name)
            return (File(resource.path).path)
        }

        @JvmStatic
        fun getFileContentsBytes(exportedFile: File): ByteArray {
            val f = RandomAccessFile(exportedFile, "r")
            val b = ByteArray(f.length().toInt())
            f.readFully(b)
            return b
        }

        @JvmStatic
        fun getFileContents(exportedFile: File): String {
            return String(getFileContentsBytes(exportedFile))
        }
    }
}
