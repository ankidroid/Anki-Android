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
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import com.ichi2.compat.CompatHelper
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.InputStream

object FileUtil {
    /**
     * Determine available storage space
     *
     * @param path the filesystem path you need free space information on
     * @return long indicating the bytes available for that path
     */
    fun determineBytesAvailable(path: String): Long = StatFs(path).availableBytes

    /** Gets the free disk space given a file  */
    fun getFreeDiskSpace(
        file: File,
        defaultValue: Long,
    ): Long =
        try {
            StatFs(file.parentFile?.path).availableBytes
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "Free space could not be retrieved")
            defaultValue
        }

    /** Returns the current download Directory */
    fun getDownloadDirectory(): String = Environment.DIRECTORY_DOWNLOADS

    /**
     * Returns a string representing the path to a private cache directory,
     * or optionally a sub-directory of with the provided name
     *
     * @param context the context to use to get directory paths from
     * @param subdirectoryName  if the caller wants a sub-directory instead of the main directory
     * @return String file path to cache directory or null if error
     */
    fun getAnkiCacheDirectory(
        context: Context,
        subdirectoryName: String? = null,
    ): String? {
        val cacheDirRoot = context.cacheDir
        if (cacheDirRoot == null) {
            Timber.e("createUI() unable to get cache directory")
            return null
        }
        var cacheDir = cacheDirRoot
        if (subdirectoryName != null) {
            cacheDir = File(cacheDir.absolutePath + "/" + subdirectoryName)
            if (!cacheDir.exists() && !cacheDir.mkdir()) {
                Timber.e("$subdirectoryName did not exist in cache dir and could not be created")
                return null
            }
        }
        return cacheDir.absolutePath
    }

    /**
     *
     * @param uri               uri to the content to be internalized, used if filePath not real/doesn't work.
     * @param internalFile      an internal cache temp file that the data is copied/internalized into.
     * @param contentResolver   this is needed to open an inputStream on the content uri.
     * @return the internal file after copying the data across.
     * @throws IOException
     */
    @Throws(IOException::class)
    fun internalizeUri(
        uri: Uri,
        internalFile: File,
        contentResolver: ContentResolver,
    ): File {
        // If we got a real file name, do a copy from it
        val inputStream: InputStream =
            try {
                contentResolver.openInputStream(uri)!!
            } catch (e: Exception) {
                Timber.w(e, "internalizeUri() unable to open input stream from content resolver for Uri %s", uri)
                throw e
            }
        try {
            CompatHelper.compat.copyFile(inputStream, internalFile.absolutePath)
        } catch (e: Exception) {
            Timber.w(e, "internalizeUri() unable to internalize file from Uri %s to File %s", uri, internalFile.absolutePath)
            throw e
        }
        return internalFile
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
    @Throws(IOException::class)
    fun listFiles(dir: File): Array<File> =
        dir.listFiles()
            ?: throw IOException("Failed to list the contents of '$dir'")

    /**
     * Returns a sequence containing the provided file, and its parents
     * up to the root of the filesystem.
     */
    fun File.getParentsAndSelfRecursive() =
        sequence {
            var currentPath: File? = this@getParentsAndSelfRecursive.canonicalFile
            while (currentPath != null) {
                yield(currentPath)
                currentPath = currentPath.parentFile?.canonicalFile
            }
        }

    fun File.isDescendantOf(ancestor: File) = this.getParentsAndSelfRecursive().drop(1).contains(ancestor)
}

/**
 * A filename without a path (e.g `collection.apkg`)
 *
 * @param fileName name of the file, before the '.'
 * @param extensionWithDot extension of the file, with a '.'
 */
@ConsistentCopyVisibility
data class FileNameAndExtension private constructor(
    val fileName: String,
    val extensionWithDot: String,
) {
    init {
        require(extensionWithDot.startsWith('.')) { "extension must start with '.'" }
    }

    /**
     * Ensures the filename is valid for [File.createTempFile], which requires `name.length() >= 3`
     */
    fun renameForCreateTempFile(): FileNameAndExtension = if (fileName.length >= 3) this else this.copy(fileName = "$fileName-name")

    /**
     * Returns a [FileNameAndExtension] with a custom extension
     */
    fun replaceExtension(extension: String): FileNameAndExtension {
        val withDot = if (extension.startsWith(".")) extension else ".$extension"
        return copy(extensionWithDot = withDot)
    }

    override fun toString() = "$fileName$extensionWithDot"

    companion object {
        /**
         * @return a valid [FileNameAndExtension]. `null` if [fileName] does not contain '.'
         */
        fun fromString(fileName: String): FileNameAndExtension? {
            val index = fileName.lastIndexOf(".")
            return if (index < 1) {
                null
            } else {
                FileNameAndExtension(
                    fileName = fileName.substring(0, index),
                    extensionWithDot = fileName.substring(index),
                )
            }
        }
    }
}
