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

import com.ichi2.libanki.Utils
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File
import java.lang.Exception

class UnzipFile {
    companion object {
        fun unzip(exportedFile: File?, destDir: String) {
            try {
                val zipFile = ZipFile(exportedFile)
                Utils.unzipAllFiles(zipFile, destDir)
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }
    }
}
