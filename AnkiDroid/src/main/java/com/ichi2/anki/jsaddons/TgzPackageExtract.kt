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

import org.apache.commons.compress.archivers.ArchiveException
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.utils.IOUtils
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
*/

object TgzPackageExtract {
    private val GZIP_SIGNATURE = byteArrayOf(0x1f, 0x8b.toByte())

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
        require(isGzip(tarballFile)) { String.format("%s is not a valid .tgz file.", tarballFile.absolutePath) }
        if (!addonsDir.exists()) {
            check(addonsDir.mkdirs()) { String.format("Couldn't create directory %s.", addonsDir.absolutePath) }
        }
        val tarTempFile = unGzip(tarballFile, addonsDir)
        unTar(tarTempFile, addonsDir)
        tarTempFile.delete()
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
        val inputStream = GZIPInputStream(FileInputStream(inputFile))
        val outputStream = FileOutputStream(outputFile)

        IOUtils.copy(inputStream, outputStream)
        inputStream.close()
        outputStream.close()
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

        FileInputStream(inputFile).use { inputStream ->
            ArchiveStreamFactory().createArchiveInputStream("tar", inputStream).use { tarInputStream ->
                val tarInputStream1 = tarInputStream as TarArchiveInputStream
                var entry: TarArchiveEntry? = tarInputStream1.nextEntry as TarArchiveEntry

                while (entry != null) {
                    val outputFile = File(outputDir, entry.name)

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
                throw IOException(String.format("Couldn't create directory %s.", parent.absolutePath))
            }
        }

        val outputFileStream: OutputStream = FileOutputStream(outputFile)
        IOUtils.copy(tarInputStream, outputFileStream)
        outputFileStream.close()
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
            check(outputFile.mkdirs()) { String.format("Couldn't create directory %s.", outputFile.absolutePath) }
        }
    }

    // Zip Slip Vulnerability https://snyk.io/research/zip-slip-vulnerability
    @Throws(ArchiveException::class, IOException::class)
    private fun zipPathSafety(outputFile: File, destDirectory: File) {
        val destDirCanonicalPath = destDirectory.canonicalPath
        val outputFileCanonicalPath = outputFile.canonicalPath

        if (!outputFileCanonicalPath.startsWith(destDirCanonicalPath)) {
            throw ArchiveException(String.format("Entry is outside of the target dir: %s", outputFileCanonicalPath))
        }
    }
}
