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

package com.ichi2.anki.jsaddons;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.utils.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;

import timber.log.Timber;

public class NpmPackageTgzExtract {

    private static final byte[] GZIP_SIGNATURE = {0x1f, (byte) 0x8b};


    /**
     * Determine whether a file is a gzip.
     *
     * @param file the file to check.
     * @return whether the file is a gzip.
     * @throws IOException if the file could not be read.
     */
    public static boolean isGzip(File file) throws IOException {
        byte[] signature = new byte[GZIP_SIGNATURE.length];
        try (InputStream stream = new FileInputStream(file)) {
            if (stream.read(signature) != signature.length) {
                return false;
            }
        }
        return Arrays.equals(GZIP_SIGNATURE, signature);
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
    public static void extractTarGzipToAddonFolder(File tarballFile, File addonsDir) throws Exception {
        if (!isGzip(tarballFile)) {
            throw new IllegalArgumentException(String.format("%s is not a valid .tgz file.", tarballFile.getAbsolutePath()));
        }

        if (!(addonsDir.exists())) {
            if (!(addonsDir.mkdirs())) {
                throw new IllegalStateException(String.format("Couldn't create directory %s.", addonsDir.getAbsolutePath()));
            }
        }

        File tarTempFile = unGzip(tarballFile, addonsDir);
        unTar(tarTempFile, addonsDir);
        tarTempFile.delete();
    }


    /**
     * UnGZip a file: a .tgz file will become a .tar file.
     *
     * @param inputFile The {@link File} to ungzip
     * @param outputDir The directory where to put the ungzipped file.
     * @return a {@link File} pointing to the ungzipped file.
     * @throws FileNotFoundException
     * @throws IOException
     */
    public static File unGzip(File inputFile, File outputDir) throws FileNotFoundException, IOException {
        Timber.i("Ungzipping %s to dir %s.", inputFile.getAbsolutePath(), outputDir.getAbsolutePath());

        // rename '-4' to remove the '.tgz' extension and add .tar extension
        File outputFile = new File(outputDir, inputFile.getName().substring(0, inputFile.getName().length() - 4) + ".tar");
        GZIPInputStream inputStream = new GZIPInputStream(new FileInputStream(inputFile));
        FileOutputStream outputStream = new FileOutputStream(outputFile);

        IOUtils.copy(inputStream, outputStream);

        inputStream.close();
        outputStream.close();

        return outputFile;
    }


    /**
     * Untar a tar file into a directory. tar.gz file needs to be {@link #unGzip(File, File)} first.
     *
     * @param inputFile The tar file to extract
     * @param outputDir the directory where to put the extracted files.
     * @throws ArchiveException
     * @throws IOException
     */
    public static void unTar(File inputFile, File outputDir) throws Exception {
        Timber.i("Untaring %s to dir %s.", inputFile.getAbsolutePath(), outputDir.getAbsolutePath());

        try (InputStream inputStream = new FileInputStream(inputFile);
             TarArchiveInputStream tarInputStream = (TarArchiveInputStream)
                     new ArchiveStreamFactory().createArchiveInputStream("tar", inputStream)) {

            TarArchiveEntry entry = null;
            while ((entry = (TarArchiveEntry) tarInputStream.getNextEntry()) != null) {
                File outputFile = new File(outputDir, entry.getName());

                // Zip Slip Vulnerability https://snyk.io/research/zip-slip-vulnerability
                zipPathSafety(outputFile, outputDir);

                if (entry.isDirectory()) {
                    unTarDir(inputFile, outputDir, outputFile);
                } else {
                    unTarFile(tarInputStream, entry, outputDir, outputFile);
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
    private static void unTarFile(TarArchiveInputStream tarInputStream, TarArchiveEntry entry, File outputDir, File outputFile) throws IOException {
        Timber.i("Creating output file %s.", outputFile.getAbsolutePath());
        File currentFile = new File(outputDir, entry.getName());
        File parent = currentFile.getParentFile();  // this line important otherwise FileNotFoundException
        if (parent != null && !parent.exists()) {
            if (!parent.mkdirs()) {
                throw new IOException(String.format("Couldn't create directory %s.", parent.getAbsolutePath()));
            }
        }

        final OutputStream outputFileStream = new FileOutputStream(outputFile);
        IOUtils.copy(tarInputStream, outputFileStream);
        outputFileStream.close();
    }


    /**
     * UnTar directory to output dir
     * @param inputFile archive input file
     * @param outputDir Output directory
     * @param outputFile Output file
     */
    private static void unTarDir(File inputFile, File outputDir, File outputFile) {
        Timber.i("Untaring %s to dir %s.", inputFile.getAbsolutePath(), outputDir.getAbsolutePath());
        if (!outputFile.exists()) {
            Timber.i("Attempting to create output directory %s.", outputFile.getAbsolutePath());
            if (!outputFile.mkdirs()) {
                throw new IllegalStateException(String.format("Couldn't create directory %s.", outputFile.getAbsolutePath()));
            }
        }
    }


    // Zip Slip Vulnerability https://snyk.io/research/zip-slip-vulnerability
    private static void zipPathSafety(File outputFile, File destDirectory) throws ArchiveException, IOException {
        String destDirCanonicalPath = destDirectory.getCanonicalPath();
        String outputFileCanonicalPath = outputFile.getCanonicalPath();
        if (!outputFileCanonicalPath.startsWith(destDirCanonicalPath)) {
            throw new ArchiveException(String.format("Entry is outside of the target dir: %s", outputFileCanonicalPath));
        }
    }
}
