/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
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
 *
 * ----
 * (https://commons.apache.org/proper/commons-io/javadocs/api-2.4/org/apache/commons/io/FileUtils.html#contentEquals(java.io.File,%20java.io.File))
 * **CHANGES**
 * * Auto convert code to Kotlin, make `File` parameters non-null.
 * * Change `contentEquals` to `throwIfContentUnequal`, extract variables and throw
 * IllegalStateException if file contents are not equal.
 * * inline `requireFile`
 *
 * This file incorporates code under the following license
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ichi2.anki.servicelayer.scopedstorage

import android.os.Build
import androidx.annotation.RequiresApi
import org.apache.commons.io.IOUtils
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * Tests whether the contents of two files are equal.
 *
 * This method checks to see if the two files are different lengths or if they point to the same file, before
 * resorting to byte-by-byte comparison of the contents.
 *
 * Code origin: Avalon
 *
 * @param file1 the first file
 * @param file2 the second file
 * @throws IllegalStateException if the two files were unequal
 * @throws IllegalArgumentException when an input is not a file.
 * @throws IOException If an I/O error occurs.
 * @see org.apache.commons.io.file.PathUtils.fileContentEquals
 */
@RequiresApi(Build.VERSION_CODES.O)
@Throws(IOException::class)
internal fun throwIfContentUnequal(
    file1: File,
    file2: File,
) {
    val file1Exists = file1.exists()
    val file2Exists = file2.exists()
    if (file1Exists != file2Exists) {
        throw IllegalStateException("Only one file existed. file1: $file1Exists; file2: $file2Exists. $file1 $file2")
    }
    if (!file1Exists) {
        // two not existing files are equal
        return
    }
    require(file1.isFile) { "Parameter 'file1' is not a file: $file1" }
    require(file2.isFile) { "Parameter 'file2' is not a file: $file2" }

    val file1Length = file1.length()
    val file2Length = file2.length()
    if (file1Length != file2Length) {
        // lengths differ, cannot be equal
        throw IllegalStateException("File lengths differed. file1: $file1Length; file2: $file2Length. $file1 $file2")
    }
    if (file1.canonicalFile == file2.canonicalFile) {
        // same file
        return
    }
    Files.newInputStream(file1.toPath()).use { input1 ->
        Files.newInputStream(file2.toPath()).use { input2 ->
            if (!IOUtils.contentEquals(input1, input2)) {
                throw IllegalStateException("files had same lengths ($file1Length), but different content. $file1 $file2")
            }
        }
    }
}
