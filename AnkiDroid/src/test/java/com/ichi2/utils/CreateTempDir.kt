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

class CreateTempDir {
    companion object {
        @JvmStatic
        fun tempDir(dirName: String): File {
            val path: String? = System.getProperty("java.io.tmpdir")
            val tempDir = path + dirName + System.nanoTime()
            val temp = File(tempDir)
            temp.mkdir()
            return temp
        }
    }
}
