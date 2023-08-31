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
import android.os.Environment
import android.os.StatFs
import com.ichi2.compat.CompatHelper
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.*

object FileUtil {
    /** Gets the free disk space given a file  */
    fun getFreeDiskSpace(file: File, defaultValue: Long): Long {
        return try {
            StatFs(file.parentFile?.path).availableBytes
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "Free space could not be retrieved")
            defaultValue
        }
    }

    /** Returns the current download Directory */
    fun getDownloadDirectory(): String {
        return Environment.DIRECTORY_DOWNLOADS
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
    fun internalizeUri(uri: Uri, internalFile: File, contentResolver: ContentResolver): File {
        // If we got a real file name, do a copy from it
        val inputStream: InputStream = try {
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
     * @return Key: Filename; Value: extension including dot
     */
    fun getFileNameAndExtension(fileName: String?): Map.Entry<String, String>? {
        if (fileName == null) {
            return null
        }
        val index = fileName.lastIndexOf(".")
        return if (index < 1) {
            null
        } else {
            AbstractMap.SimpleEntry(fileName.substring(0, index), fileName.substring(index))
        }
    }

    /**
     * Calculates the size of a [File].
     * If it is a file, returns the size.
     * If the file does not exist, returns 0
     * If the file is a directory, recursively explore the directory tree and summing the length of each
     * file. The time taken to calculate directory size is proportional to the number of files in the directory
     * and all of its sub-directories. See: [DirectoryContentInformation.fromDirectory]
     * It is assumed that directory contains no symbolic links.
     *
     * @param file Abstract representation of the file/directory whose size needs to be calculated
     * @return Size of the File/Directory in bytes. 0 if the [File] does not exist
     */
    fun getSize(file: File): Long {
        if (file.isFile) {
            return file.length()
        } else if (!file.exists()) {
            return 0L
        }
        return DirectoryContentInformation.fromDirectory(file).totalBytes
    }

    /**
     * Information about the content of a directory `d`.
     */
    data class DirectoryContentInformation(
        /**
         * Size of all files contained in `d` directly or indirectly.
         * Ignore the extra size taken by file system.
         */
        val totalBytes: Long,
        /**
         * Number of subdirectories of `d`, directly or indirectly. Not counting `d`.
         */
        val numberOfSubdirectories: Int,
        /**
         * Number of files contained in `d` directly or indirectly.
         */
        val numberOfFiles: Int
    ) {
        companion object {
            /**
             * @throws IOException [root] does not exist
             */
            fun fromDirectory(root: File): DirectoryContentInformation {
                var totalBytes = 0L
                var numberOfDirectories = 0
                var numberOfFiles = 0
                val directoriesToProcess = mutableListOf(root)
                while (directoriesToProcess.isNotEmpty()) {
                    val dir = directoriesToProcess.removeLast()
                    listFiles(dir).forEach {
                        if (it.isDirectory) {
                            numberOfDirectories++
                            directoriesToProcess.add(it)
                        } else {
                            numberOfFiles++
                            totalBytes += it.length()
                        }
                    }
                }

                return DirectoryContentInformation(totalBytes, numberOfDirectories, numberOfFiles)
            }
        }
    }

    /**
     * If dir exists, it must be a directory.
     * If not, it is created, along with any necessary parent directories (see [File.mkdirs]).
     * @param dir Abstract representation of a directory
     * @throws IOException if dir is not a directory or could not be created
     */
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
    @Throws(IOException::class)
    fun listFiles(dir: File): Array<File> {
        return dir.listFiles()
            ?: throw IOException("Failed to list the contents of '$dir'")
    }

    /**
     * Returns a sequence containing the provided file, and its parents
     * up to the root of the filesystem.
     */
    fun File.getParentsAndSelfRecursive() = sequence {
        var currentPath: File? = this@getParentsAndSelfRecursive.canonicalFile
        while (currentPath != null) {
            yield(currentPath)
            currentPath = currentPath.parentFile?.canonicalFile
        }
    }

    fun File.isDescendantOf(ancestor: File) = this.getParentsAndSelfRecursive().drop(1).contains(ancestor)
    fun File.isAncestorOf(descendant: File) = descendant.isDescendantOf(this)

    fun getDepth(fileParam: File): Int {
        var file: File? = fileParam
        var depth = 0
        while (file != null) {
            file = file.parentFile
            depth++
        }
        return depth
    }
    enum class FilePrefix {
        EQUAL,
        STRICT_PREFIX,
        STRICT_SUFFIX,
        NOT_PREFIX
    }

    /**
     * @return whether how [potentialPrefixFile] related to [fullFile] as far as prefix goes
     * @throws FileNotFoundException if a file is not found
     * @throws IOException If an I/O error occurs
     */
    fun isPrefix(potentialPrefixFile: File, fullFile: File): FilePrefix {
        var potentialPrefixBuffer: FileInputStream? = null
        var fullFileBuffer: FileInputStream? = null
        try {
            potentialPrefixBuffer = FileInputStream(potentialPrefixFile)
            fullFileBuffer = FileInputStream(fullFile)
            while (true) {
                val prefixContent = potentialPrefixBuffer.read()
                val fullFileContent = fullFileBuffer.read()
                val prefixFileEnded = prefixContent == -1
                val fullFileEnded = fullFileContent == -1
                if (prefixFileEnded && fullFileEnded) {
                    return FilePrefix.EQUAL
                }
                if (prefixFileEnded) {
                    return FilePrefix.STRICT_PREFIX
                }
                if (fullFileEnded) {
                    return FilePrefix.STRICT_SUFFIX
                }
                if (prefixContent != fullFileContent) {
                    return FilePrefix.NOT_PREFIX
                }
            }
        } finally {
            potentialPrefixBuffer?.close()
            fullFileBuffer?.close()
        }
    }
}
