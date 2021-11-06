/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.utils

import android.content.ContentResolver
import android.net.Uri
import android.os.StatFs
import com.ichi2.compat.CompatHelper
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.lang.Exception
import java.lang.IllegalArgumentException
import java.util.AbstractMap
import kotlin.Throws

object FileUtil {
    /** Gets the free disk space given a file  */
    @JvmStatic
    fun getFreeDiskSpace(file: File, defaultValue: Long): Long {
        return try {
            StatFs(file.parentFile?.path).availableBytes
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "Free space could not be retrieved")
            defaultValue
        }
    }

    /**
     *
     * @param uri               uri to the content to be internalized, used if filePath not real/doesn't work.
     * @param internalFile      an internal cache temp file that the data is copied/internalized into.
     * @param contentResolver   this is needed to open an inputStream on the content uri.
     * @return the internal file after copying the data across.
     * @throws IOException
     */
    @JvmStatic
    @Throws(IOException::class)
    @KotlinCleanup("nonnull uri")
    fun internalizeUri(uri: Uri?, internalFile: File, contentResolver: ContentResolver): File {
        // If we got a real file name, do a copy from it
        val inputStream: InputStream?
        inputStream = try {
            contentResolver.openInputStream(uri!!)
        } catch (e: Exception) {
            Timber.w(e, "internalizeUri() unable to open input stream from content resolver for Uri %s", uri)
            throw e
        }
        try {
            CompatHelper.getCompat().copyFile(inputStream, internalFile.absolutePath)
        } catch (e: Exception) {
            Timber.w(e, "internalizeUri() unable to internalize file from Uri %s to File %s", uri, internalFile.absolutePath)
            throw e
        }
        return internalFile
    }

    /**
     * @return Key: Filename; Value: extension including dot
     */
    @JvmStatic
    fun getFileNameAndExtension(fileName: String?): Map.Entry<String, String>? {
        if (fileName == null) {
            return null
        }
        val index = fileName.lastIndexOf(".")
        return if (index < 1) {
            null
        } else AbstractMap.SimpleEntry(fileName.substring(0, index), fileName.substring(index))
    }

    /**
     * Calculates the size of a directory by recursively exploring the directory tree and summing the length of each
     * file. The time taken to calculate directory size is proportional to the number of files in the directory
     * and all of its sub-directories.
     * It is assumed that directory contains no symbolic links.
     *
     * @throws IOException if the directory argument doesn't denote a directory
     * @param directory Abstract representation of the file/directory whose size needs to be calculated
     * @return Size of the directory in bytes
     */
    @JvmStatic
    @Throws(IOException::class)
    fun getDirectorySize(directory: File): Long {
        var directorySize: Long = 0
        val files = listFiles(directory)
        for (file in files) {
            directorySize += if (file.isDirectory) {
                getDirectorySize(file)
            } else {
                file.length()
            }
        }
        return directorySize
    }

    /**
     * If dir exists, it must be a directory.
     * If not, it is created, along with any necessary parent directories (see [File.mkdirs]).
     * @param dir Abstract representation of a directory
     * @throws IOException if dir is not a directory or could not be created
     */
    @JvmStatic
    @Throws(IOException::class)
    fun ensureFileIsDirectory(dir: File) {
        if (dir.exists()) {
            if (!dir.isDirectory) {
                throw IOException("$dir exists but is not a directory")
            }
        } else if (!dir.mkdirs() && !dir.isDirectory) {
            throw IOException("$dir directory cannot be created")
        }
    }

    /**
     * Wraps [File.listFiles] and throws an exception instead of returning `null` if dir does not
     * denote an actual directory.
     *
     * @throws IOException if the contents of dir cannot be listed
     * @param dir Abstract representation of a directory
     * @return An array of abstract representations of the files / directories present in the directory represented
     * by dir
     */
    @JvmStatic
    @Throws(IOException::class)
    fun listFiles(dir: File): Array<File> {
        return dir.listFiles()
            ?: throw IOException("Failed to list the contents of '$dir'")
    }
}
