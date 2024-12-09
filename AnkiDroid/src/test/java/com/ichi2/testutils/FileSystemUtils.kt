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

import androidx.annotation.CheckResult
import org.acra.util.IOUtils
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import timber.log.Timber
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.io.path.pathString

/** Utilities which assist testing changes to files/directories */
@Suppress("unused")
object FileSystemUtils {
    /**
     * Prints a directory structure using [Timber.d]
     * @param description The prefix to print before the tree is listed
     * @param file the root of the tree to print
     *
     * ```
     * D/FileSystemUtils: destination: C:\Users\User\AppData\Local\Temp\robolectric-Method_successfulMigration11404528269084729867\external-files\AnkiDroid-1
     * +--AnkiDroid-1/
     * |  +--backup/
     * |  |  +--collection-2020-08-07-08-00.colpkg
     * |  +--collection.media/
     * |  |  +--directory/
     * |  |  |  +--test.txt
     * |  |  +--test.txt
     * ```
     */
    fun printDirectoryTree(
        description: String,
        file: File,
    ) {
        Timber.d("$description: $file\n${printDirectoryTree(file)}")
    }

    /** from https://stackoverflow.com/a/13130974/ */
    @CheckResult
    private fun printDirectoryTree(directory: File): String {
        require(directory.isDirectory) { "directory is not a Directory" }
        val indent = 0
        val sb = StringBuilder()
        printDirectoryTree(directory, indent, sb)
        return sb.toString()
    }

    /** from https://stackoverflow.com/a/13130974/ */
    private fun printDirectoryTree(
        directory: File,
        indent: Int,
        sb: StringBuilder,
    ) {
        require(directory.isDirectory) { "directory is not a Directory" }
        sb.append(getIndentString(indent))
        sb.append("+--")
        sb.append(directory.name)
        sb.append("/")
        sb.append("\n")
        for (file in directory.listFiles() ?: emptyArray()) {
            if (file.isDirectory) {
                printDirectoryTree(file, indent + 1, sb)
            } else {
                printFile(file, indent + 1, sb)
            }
        }
    }

    /** from https://stackoverflow.com/a/13130974/ */
    private fun printFile(
        file: File,
        indent: Int,
        sb: StringBuilder,
    ) {
        sb.append(getIndentString(indent))
        sb.append("+--")
        sb.append(file.name)
        sb.append("\n")
    }

    /** from https://stackoverflow.com/a/13130974/ */
    private fun getIndentString(indent: Int): String {
        val sb = StringBuilder()
        for (i in 0 until indent) {
            sb.append("|  ")
        }
        return sb.toString()
    }
}

/**
 * Returns a new directory in the OS's default temp directory, using the given [prefix] to generate its name.
 * This directory is deleted on exit
 */
fun createTransientDirectory(prefix: String? = null): File =
    createTempDirectory(prefix = prefix).let {
        val file = File(it.pathString)
        file.deleteOnExit()
        return@let file
    }

/**
 * Returns a temp file with [content]. The file is deleted on exit.
 * @param extension The file extension. Do not include a "."
 */
fun createTransientFile(
    content: String = "",
    extension: String? = null,
): File =
    File(kotlin.io.path.createTempFile(suffix = if (extension == null) null else ".$extension").pathString).also {
        it.deleteOnExit()
        IOUtils.writeStringToFile(it, content)
    }

/** Creates a sub-directory with the given name which is deleted on exit */
fun File.createTransientDirectory(name: String): File {
    File(this, name).also { directory ->
        directory.deleteOnExit()
        Timber.d("test: creating $directory")
        MatcherAssert.assertThat("directory should have been created", directory.mkdirs(), CoreMatchers.equalTo(true))
        return directory
    }
}
