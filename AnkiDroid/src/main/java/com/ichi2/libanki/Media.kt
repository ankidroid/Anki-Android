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
import anki.media.CheckMediaResponse
import com.google.protobuf.kotlin.toByteString
import com.ichi2.libanki.exception.EmptyMediaException
import com.ichi2.libanki.utils.LibAnkiAlias
import timber.log.Timber
import java.io.File

/**
 * Media manager - handles the addition and removal of media files from the media directory (collection.media) and
 * maintains the media database, which is used to determine the state of files for syncing.
 */
@WorkerThread
open class Media(
    private val col: Collection,
) {
    val dir = col.collectionFiles.mediaFolder

    init {
        Timber.v("dir %s", dir)
        val file = dir
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
        Timber.v("dir now %s", dir)
        return col.backend.addMediaFile(oFile.name, oFile.readBytes().toByteString())
    }

    /*
     * String manipulation
     * ***********************************************************
     */

    /**
     * Extract media filenames from an HTML string.
     *
     * @param string The string to scan for media filenames ([sound:...] or <img...>).
     * @return A list containing all the sound and image filenames found in the input string.
     */
    fun filesInStr(string: String): List<String> =
        col.backend
            .extractAvTags(string, true)
            .avTagsList
            .filter {
                it.hasSoundOrVideo()
            }.map {
                it.soundOrVideo
            }

    fun findUnusedMediaFiles(): List<File> = check().unusedList.map { File(dir, it) }

    /**
     * [IRI](https://en.wikipedia.org/wiki/Internationalized_Resource_Identifier) encodes media
     *
     * `foo bar` -> `foo%20bar`
     */
    fun escapeMediaFilenames(
        string: String,
        unescape: Boolean = false,
    ): String =
        if (unescape) {
            col.backend.decodeIriPaths(string)
        } else {
            col.backend.encodeIriPaths(string)
        }

    /*
      Rebuilding DB
     ***********************************************************
     */
    fun check(): CheckMediaResponse = col.backend.checkMedia()

    @LibAnkiAlias("empty_trash")
    fun emptyTrash() = col.backend.emptyTrash()

    /**
     * Copying on import
     * ***********************************************************
     */
    open fun have(fname: String): Boolean = File(dir, fname).exists()

    open fun forceResync() {
        col.backend.removeMediaDb(colPath = col.colDb.absolutePath)
    }

    // FIXME: this currently removes files immediately, as the UI does not expose a way

    /** Move provided files to the trash. */
    @LibAnkiAlias("trash_files")
    fun trashFiles(fnames: Iterable<String>) {
        col.backend.trashMediaFiles(fnames = fnames)
    }

    @LibAnkiAlias("restore_trash")
    fun restoreTrash() = col.backend.restoreTrash()
}
