/****************************************************************************************
 * Copyright (c) 2011 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
 * Copyright (c) 2014 Houssam Salem <houssam.salem.au@gmail.com>                        *
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

package com.ichi2.libanki

import androidx.annotation.WorkerThread
import com.google.protobuf.kotlin.toByteString
import com.ichi2.libanki.exception.EmptyMediaException
import com.ichi2.utils.*
import timber.log.Timber
import java.io.*
import java.util.*

/**
 * Media manager - handles the addition and removal of media files from the media directory (collection.media) and
 * maintains the media database, which is used to determine the state of files for syncing.
 */
@KotlinCleanup("IDE Lint")
@WorkerThread
open class Media(private val col: Collection) {
    val dir = getCollectionMediaPath(col.path)

    init {
        Timber.e("dir %s", dir)
        val file = File(dir)
        if (!file.exists()) {
            file.mkdirs()
        }
    }

    /*
      Adding media
      ***********************************************************
     */

    fun addFile(oFile: File?): String {
        if (oFile == null || oFile.length() == 0L) {
            throw EmptyMediaException()
        }
        Timber.e("dir now %s", dir)
        return col.backend.addMediaFile(oFile.name, oFile.readBytes().toByteString())
    }

    /**
     * Extract media filenames from an HTML string.
     *
     * @param string The string to scan for media filenames ([sound:...] or <img...>).
     * @param includeRemote If true will also include external http/https/ftp urls.
     * @return A list containing all the sound and image filenames found in the input string.
     */
    /**
     * String manipulation
     * ***********************************************************
     */
    fun filesInStr(string: String): List<String> {
        return col.backend.extractAvTags(string, true).avTagsList.filter {
            it.hasSoundOrVideo()
        }.map {
            it.soundOrVideo
        }
    }

    fun findUnusedMediaFiles(): List<File> {
        return check().unusedFileNames.map { File(dir, it) }
    }

    @KotlinCleanup("fix 'string' as var")
    fun escapeImages(string: String, unescape: Boolean = false): String {
        return if (unescape) {
            col.backend.decodeIriPaths(string)
        } else {
            col.backend.encodeIriPaths(string)
        }
    }

    /*
      Rebuilding DB
      ***********************************************************
     */

    // FIXME: this also provides trash count, but UI can not handle it yet
    fun check(): MediaCheckResult {
        val out = col.backend.checkMedia()
        return MediaCheckResult(out.missingList, out.unusedList, listOf())
    }

    /**
     * Copying on import
     * ***********************************************************
     */
    open fun have(fname: String): Boolean = File(dir, fname).exists()

    open fun forceResync() {
        col.backend.removeMediaDb(colPath = col.path)
    }

    /**
     * Remove a file from the media directory if it exists and mark it as removed in the media database.
     */
    @Suppress("unused")
    open fun removeFile(fname: String) {
        removeFiles(listOf(fname))
    }

    // FIXME: this currently removes files immediately, as the UI does not expose a way
    // to empty the trash or restore media files yet
    fun removeFiles(files: Iterable<String>) {
        col.backend.trashMediaFiles(fnames = files)
        emptyTrash()
    }

    private fun emptyTrash() {
        col.backend.emptyTrash()
    }

    @Suppress("UNUSED")
    private fun restoreTrash() {
        col.backend.restoreTrash()
    }
}

fun getCollectionMediaPath(collectionPath: String): String {
    return collectionPath.replaceFirst("\\.anki2$".toRegex(), ".media")
}

data class MediaCheckResult(
    val missingFileNames: List<String>,
    val unusedFileNames: List<String>,
    val invalidFileNames: List<String>
)
