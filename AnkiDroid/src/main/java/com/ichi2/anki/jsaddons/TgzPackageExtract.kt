/****************************************************************************************
 * Copyright (c) 2021 Mani <infinyte01@gmail.com>                                       *
 *                                                                                      *
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
 *                                                                                      *
 * This file incorporates work covered by the following copyright and permission        *
 * notice:                                                                              *
 *                                                                                      *
 *      Copyright (C) 2016 The Android Open Source Project                              *
 *      <p>                                                                             *
 *      Licensed under the Apache License, Version 2.0 (the "License");                 *
 *      you may not use this file except in compliance with the License.                *
 *      You may obtain a copy of the License at                                         *
 *      <p>                                                                             *
 *      http://www.apache.org/licenses/LICENSE-2.0                                      *
 *      <p>                                                                             *
 *      Unless required by applicable law or agreed to in writing, software             *
 *      distributed under the License is distributed on an "AS IS" BASIS,               *
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.        *
 *      See the License for the specific language governing permissions and             *
 *      limitations under the License.                                                  *
 ****************************************************************************************/

package com.ichi2.anki.jsaddons

import android.content.Context
import android.text.format.Formatter
import com.ichi2.anki.R
import com.ichi2.libanki.Utils
import org.apache.commons.compress.archivers.ArchiveException
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import timber.log.Timber
import java.io.*
import java.util.zip.GZIPInputStream

/**
 * In JS Addons the addon packages are downloaded from npm registry.
 * The file format of downloaded file is tgz, so I created this class to extract tgz file to provided directory.
 *
 * This file in kotlin is used for extracting tgz file.
 * https://android.googlesource.com/platform/tools/tradefederation/+/master/src/com/android/tradefed/util/TarUtil.java
 *
 * Also added zip slip safety
 * https://snyk.io/research/zip-slip-vulnerability
 *
 * Also extracted gzip files with file size limit 100MB and number of entries in the tar file
 */

class TgzPackageExtract(private val context: Context) {
    private val GZIP_SIGNATURE = byteArrayOf(0x1f, 0x8b.toByte())
    private var requiredMinSpace: Long = 0
    private var availableSpace: Long = 0

    val BUFFER = 512
    val TOO_BIG_SIZE: Long = 0x6400000 // max size of unzipped data, 100MB
    val TOO_MANY_FILES = 1024 // max number of files

    var count = 0
    var total: Long = 0
    var data = ByteArray(BUFFER)

    /**
     * Determine whether a file is a gzip.
     *
     * @param file the file to check.
     * @return whether the file is a gzip.
     * @throws IOException if the file could not be read.
     */
    @Throws(IOException::class)
    fun isGzip(file: File?): Boolean {
        val signature = ByteArray(GZIP_SIGNATURE.size)
        FileInputStream(file).use { stream ->
            if (stream.read(signature) != signature.size) {
                return false
            }
        }
        return GZIP_SIGNATURE.contentEquals(signature)
    }

    /**
     * Untar and ungzip a tar.gz file to a AnkiDroid/addons directory.
     *
     * @param tarballFile the .tgz file to extract
     * @param addonsDir   the AnkiDroid addons directory
     * @return the temp directory.
     * @throws FileNotFoundException if .tgz file or ungzipped file i.e. .tar file not found
     * @throws IOException
     */
    @Throws(Exception::class)
    fun extractTarGzipToAddonFolder(tarballFile: File, addonsDir: File) {

        require(isGzip(tarballFile)) { context.getString(R.string.not_valid_js_addon, tarballFile.absolutePath) }

        if (!addonsDir.exists()) {
            context.getString(R.string.could_not_create_dir, addonsDir.absolutePath)
        }

        // Make sure we have 2x the tar file size in free space (1x for tar file, 1x for unarchived tar file contents
        requiredMinSpace = tarballFile.length() * 2
        availableSpace = Utils.determineBytesAvailable(addonsDir.canonicalPath)
        if (requiredMinSpace > availableSpace) {
            Timber.e("Not enough space to extract file, need %d, available %d", requiredMinSpace, availableSpace)
            throw IOException(context.getString(R.string.import_log_insufficient_space_error, Formatter.formatFileSize(context, requiredMinSpace), Formatter.formatFileSize(context, availableSpace)))
        }

        // If space available then unGZip it
        val tarTempFile = unGzip(tarballFile, addonsDir)

        // Make sure we have sufficient free space
        val unTarSize = calculateUnTarSize(tarTempFile)
        if (unTarSize > availableSpace) {
            Timber.e("Not enough space to untar, need %d, available %d", unTarSize, availableSpace)
            throw IOException(context.getString(R.string.import_log_insufficient_space_error, Formatter.formatFileSize(context, unTarSize), Formatter.formatFileSize(context, availableSpace)))
        }

        try {
            // If space available then unTar it
            unTar(tarTempFile, addonsDir)
        } catch (e: IOException) {
            // directory where tar archive extracted
            addonsDir.deleteRecursively()
            Timber.w("Failed to unTar file")
        } finally {
            tarTempFile.delete()
        }
    }

    /**
     * UnGZip a file: a .tgz file will become a .tar file.
     *
     * @param inputFile The [File] to ungzip
     * @param outputDir The directory where to put the ungzipped file.
     * @return a [File] pointing to the ungzipped file.
     * @throws FileNotFoundException
     * @throws IOException
     */
    @Throws(FileNotFoundException::class, IOException::class)
    fun unGzip(inputFile: File, outputDir: File): File {
        Timber.i("Ungzipping %s to dir %s.", inputFile.absolutePath, outputDir.absolutePath)

        // rename '-4' to remove the '.tgz' extension and add .tar extension
        val outputFile = File(outputDir, inputFile.name.substring(0, inputFile.name.length - 4) + ".tar")

        count = 0
        total = 0
        data = ByteArray(BUFFER)

        // file being unGzipped must not be greater than 100MB
        GZIPInputStream(FileInputStream(inputFile)).use { inputStream ->
            FileOutputStream(outputFile).use { outputStream ->
                BufferedOutputStream(outputStream, BUFFER).use { bufferOutput ->

                    while (total + BUFFER <= TOO_BIG_SIZE &&
                        inputStream.read(data, 0, BUFFER).also { count = it } != -1
                    ) {

                        bufferOutput.write(data, 0, count)
                        total += count
                    }

                    if (total + BUFFER > TOO_BIG_SIZE) {
                        throw IllegalStateException("File being unGzip is too big")
                    }
                }
            }
        }

        return outputFile
    }

    /**
     * Untar a tar file into a directory. tar.gz file needs to be [.unGzip] first.
     *
     * @param inputFile The tar file to extract
     * @param outputDir the directory where to put the extracted files.
     * @throws ArchiveException
     * @throws IOException
     */
    @Throws(Exception::class)
    fun unTar(inputFile: File, outputDir: File) {
        Timber.i("Untaring %s to dir %s.", inputFile.absolutePath, outputDir.absolutePath)
        var newAvailableSpace: Long

        count = 0
        total = 0
        data = ByteArray(BUFFER)

        FileInputStream(inputFile).use { inputStream ->
            ArchiveStreamFactory().createArchiveInputStream("tar", inputStream).use { tarInputStream ->
                val tarInputStream1 = tarInputStream as TarArchiveInputStream
                var entry: TarArchiveEntry? = tarInputStream1.nextEntry as TarArchiveEntry

                while (entry != null) {
                    val outputFile = File(outputDir, entry.name)

                    // check if space increased to 50% of total space
                    newAvailableSpace = Utils.determineBytesAvailable(outputDir.canonicalPath)
                    if (newAvailableSpace <= availableSpace / 2) {
                        throw ArchiveException(context.getString(R.string.file_extract_exceeds_storage_space))
                    }

                    // Zip Slip Vulnerability https://snyk.io/research/zip-slip-vulnerability
                    zipPathSafety(outputFile, outputDir)
                    if (entry.isDirectory) {
                        unTarDir(inputFile, outputDir, outputFile)
                    } else {
                        unTarFile(tarInputStream, entry, outputDir, outputFile)
                    }

                    entry = tarInputStream.nextEntry as? TarArchiveEntry?
                }
            }
        }
    }

    /**
     * UnTar file from archive using input stream, entry to output dir
     * @param tarInputStream TarArchiveInputStream
     * @param entry TarArchiveEntry
     * @param outputDir Output directory
     * @param outputFile Output file
     * @throws IOException
     */
    @Throws(IOException::class)
    private fun unTarFile(tarInputStream: TarArchiveInputStream, entry: TarArchiveEntry?, outputDir: File, outputFile: File) {
        Timber.i("Creating output file %s.", outputFile.absolutePath)
        val currentFile = File(outputDir, entry!!.name)
        val parent = currentFile.parentFile // this line important otherwise FileNotFoundException

        if (parent != null && !parent.exists()) {
            if (!parent.mkdirs()) {
                throw IOException(context.getString(R.string.could_not_create_dir, parent.absolutePath))
            }
        }

        // file being untar must not be greater than 100MB
        // here total is global so the total will be size of all files inside tar combined
        FileOutputStream(outputFile).use { outputFileStream ->
            BufferedOutputStream(outputFileStream, BUFFER).use { bufferOutput ->

                while (total + BUFFER <= TOO_BIG_SIZE &&
                    tarInputStream.read(data, 0, BUFFER).also { count = it } != -1
                ) {

                    bufferOutput.write(data, 0, count)
                    total += count
                }

                if (total + BUFFER > TOO_BIG_SIZE) {
                    throw IllegalStateException("File being untar is too big")
                }
            }
        }
    }

    /**
     * UnTar directory to output dir
     * @param inputFile archive input file
     * @param outputDir Output directory
     * @param outputFile Output file
     */
    private fun unTarDir(inputFile: File, outputDir: File, outputFile: File) {
        Timber.i("Untaring %s to dir %s.", inputFile.absolutePath, outputDir.absolutePath)
        if (!outputFile.exists()) {
            Timber.i("Attempting to create output directory %s.", outputFile.absolutePath)
            check(outputFile.mkdirs()) { context.getString(R.string.could_not_create_dir, outputFile.absolutePath) }
        }
    }

    // Zip Slip Vulnerability https://snyk.io/research/zip-slip-vulnerability
    @Throws(ArchiveException::class, IOException::class)
    private fun zipPathSafety(outputFile: File, destDirectory: File) {
        val destDirCanonicalPath = destDirectory.canonicalPath
        val outputFileCanonicalPath = outputFile.canonicalPath

        if (!outputFileCanonicalPath.startsWith(destDirCanonicalPath)) {
            throw ArchiveException(context.getString(R.string.malicious_archive_entry_outside, outputFileCanonicalPath))
        }
    }

    /**
     * Given a tar file, iterate through the entries to determine the total untar size
     * TODO warning: vulnerable to resource exhaustion attack if entries contain spoofed sizes
     *
     * @param tarFile File of unknown total uncompressed size
     * @return total untar size of tar file
     */
    private fun calculateUnTarSize(tarFile: File): Long {
        var unTarSize: Long = 0

        FileInputStream(tarFile).use { inputStream ->
            ArchiveStreamFactory().createArchiveInputStream("tar", inputStream).use { tarInputStream ->
                val tarInputStream1 = tarInputStream as TarArchiveInputStream
                var entry: TarArchiveEntry? = tarInputStream1.nextEntry as TarArchiveEntry
                var numOfEntries = 0

                while (entry != null) {
                    numOfEntries++
                    unTarSize += entry.size
                    entry = tarInputStream.nextEntry as? TarArchiveEntry?
                }

                if (numOfEntries > TOO_MANY_FILES) {
                    throw IllegalStateException("Too many files to untar")
                }
            }
        }

        return unTarSize
    }
}
