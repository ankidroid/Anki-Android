/***************************************************************************************
 * Copyright (c) 2016 Houssam Salem <houssam.salem.au@gmail.com>                        *
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

package com.ichi2.libanki.importer

import android.text.format.Formatter
import com.fasterxml.jackson.core.JsonToken
import com.ichi2.anki.AnkiSerialization.factory
import com.ichi2.anki.BackupManager.Companion.removeDir
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.CrashReportService
import com.ichi2.anki.R
import com.ichi2.anki.exception.ImportExportException
import com.ichi2.annotations.NeedsTest
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Storage
import com.ichi2.libanki.Utils
import com.ichi2.utils.HashUtil.HashMapInit
import com.ichi2.utils.KotlinCleanup
import org.apache.commons.compress.archivers.zip.ZipFile
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.*

class AnkiPackageImporter(col: Collection?, file: String?) : Anki2Importer(col!!, file!!) {
    @KotlinCleanup("lateinit")
    private var mZip: ZipFile? = null
    private var mNameToNum: MutableMap<String, String>? = null

    @Throws(ImportExportException::class)
    @NeedsTest("test this on our lowest SDK")
    override fun run() {
        publishProgress(0, 0, 0)
        val tempDir = File(File(mCol.path).parent, "tmpzip")
        val tmpCol: Collection? // self.col into Anki.
        Timber.d("Attempting to import package %s", file)
        try {
            // We extract the zip contents into a temporary directory and do a little more
            // validation than the desktop client to ensure the extracted collection is an apkg.
            var colname = "collection.anki21"
            try {
                // extract the deck from the zip file
                mZip = try {
                    ZipFile(File(file))
                } catch (fileNotFound: FileNotFoundException) {
                    Timber.w(fileNotFound)
                    // The cache can be cleared between copying the file in and importing. This is temporary
                    if (fileNotFound.message!!.contains("ENOENT")) {
                        log.add(res.getString(R.string.import_log_file_cache_cleared))
                        return
                    }
                    throw fileNotFound // displays: failed to unzip
                }
                // v2 scheduler?
                if (mZip!!.getEntry(colname) == null) {
                    colname = CollectionHelper.COLLECTION_FILENAME
                }

                // Make sure we have sufficient free space
                val uncompressedSize = Utils.calculateUncompressedSize(mZip!!)
                val availableSpace = Utils.determineBytesAvailable(mCol.path)
                Timber.d("Total uncompressed size will be: %d", uncompressedSize)
                Timber.d("Total available size is:         %d", availableSpace)
                if (uncompressedSize > availableSpace) {
                    Timber.e("Not enough space to unzip, need %d, available %d", uncompressedSize, availableSpace)
                    log.add(res.getString(R.string.import_log_insufficient_space_error, Formatter.formatFileSize(context, uncompressedSize), Formatter.formatFileSize(context, availableSpace)))
                    return
                }
                // The filename that we extract should be collection.anki2
                // Importing collection.anki21 fails due to some media regexes expecting collection.anki2.
                // We follow how Anki does it and fix the problem here.
                val mediaToFileNameMap = HashMapInit<String, String>(1)
                mediaToFileNameMap[colname] = CollectionHelper.COLLECTION_FILENAME
                Utils.unzipFiles(mZip!!, tempDir.absolutePath, arrayOf(colname, "media"), mediaToFileNameMap)
                colname = CollectionHelper.COLLECTION_FILENAME
            } catch (e: IOException) {
                Timber.e(e, "Failed to unzip apkg.")
                CrashReportService.sendExceptionReport(e, "AnkiPackageImporter::run() - unzip")
                log.add(res.getString(R.string.import_log_failed_unzip, e.localizedMessage))
                return
            }
            val colpath = File(tempDir, colname).absolutePath
            if (!File(colpath).exists()) {
                log.add(res.getString(R.string.import_log_failed_copy_to, colpath))
                return
            }
            tmpCol = Storage.collection(context, colpath)
            try {
                if (!tmpCol.validCollection()) {
                    log.add(res.getString(R.string.import_log_failed_validate))
                    return
                }
            } finally {
                tmpCol.close()
            }
            file = colpath
            // we need the media dict in advance, and we'll need a map of fname ->
            // number to use during the import
            val mediaMapFile = File(tempDir, "media")
            mNameToNum = HashMap() // Number of file in mediaMapFile as json. Not knowable
            val dirPath = tmpCol.media.dir()
            val dir = File(dirPath)
            // We need the opposite mapping in AnkiDroid since our extraction method requires it.
            val numToName: MutableMap<String, String> = HashMap() // Number of file in mediaMapFile as json. Not knowable
            try {
                factory.createParser(mediaMapFile).use { jp ->
                    var name: String // v in anki
                    var num: String // k in anki
                    check(jp.nextToken() == JsonToken.START_OBJECT) { "Expected content to be an object" }
                    while (jp.nextToken() != JsonToken.END_OBJECT) {
                        num = jp.currentName()
                        name = jp.nextTextValue()
                        val file = File(dir, name)
                        if (!Utils.isInside(file, dir)) {
                            throw RuntimeException("Invalid file")
                        }
                        Utils.nfcNormalized(num)
                        (mNameToNum as HashMap<String, String>)[name] = num
                        numToName[num] = name
                    }
                }
            } catch (e: FileNotFoundException) {
                Timber.e("Apkg did not contain a media dict. No media will be imported.")
            } catch (e: IOException) {
                Timber.e("Malformed media dict. Media import will be incomplete.")
            }
            // run anki2 importer
            super.run()
            // import static media
            for ((file, c) in mNameToNum!!) {
                if (!file.startsWith("_") && !file.startsWith("latex-")) {
                    continue
                }
                val path = File(mCol.media.dir(), Utils.nfcNormalized(file))
                if (!path.exists()) {
                    try {
                        Utils.unzipFiles(mZip!!, mCol.media.dir(), arrayOf(c), numToName)
                    } catch (e: IOException) {
                        Timber.e("Failed to extract static media file. Ignoring.")
                    }
                }
            }
        } finally {
            val availableSpace = Utils.determineBytesAvailable(mCol.path)
            Timber.d("Total available size is: %d", availableSpace)

            // Clean up our temporary files
            if (tempDir.exists()) {
                removeDir(tempDir)
            }
        }
        publishProgress(100, 100, 100)
    }

    override fun _srcMediaData(fname: String): BufferedInputStream? {
        if (mNameToNum!!.containsKey(fname)) {
            try {
                return BufferedInputStream(mZip!!.getInputStream(mZip!!.getEntry(mNameToNum!![fname])))
            } catch (e: IOException) {
                Timber.e("Could not extract media file %s from zip file.", fname)
            } catch (e: NullPointerException) {
                Timber.e("Could not extract media file %s from zip file.", fname)
            }
        }
        return null
    }
}
