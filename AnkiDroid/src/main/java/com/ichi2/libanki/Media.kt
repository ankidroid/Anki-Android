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
import com.ichi2.libanki.template.TemplateFilters
import timber.log.Timber
import java.io.File
import java.util.Locale
import java.util.TreeSet
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Media manager - handles the addition and removal of media files from the media directory (collection.media) and
 * maintains the media database, which is used to determine the state of files for syncing.
 */
@WorkerThread
open class Media(
    private val col: Collection,
) {
    val dir = getCollectionMediaPath(col.path)

    init {
        Timber.v("dir %s", dir)
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
    fun filesInStr(
        currentCard: Card,
        includeRemote: Boolean = false,
    ): List<String> {
        val l: MutableList<String> = ArrayList()
        val model = currentCard.noteType(col)
        val renderOutput = currentCard.renderOutput(col)
        val string = renderOutput.questionText + renderOutput.answerText

        val strings: MutableList<String?> =
            if (model!!.isCloze && string.contains("{{c")) {
                // Expand clozes if necessary
                expandClozes(string)
            } else {
                mutableListOf(string)
            }

        for (s in strings) {
            var s = s
            // Handle LaTeX
            val svg = model.optBoolean("latexsvg", false)
            s = LaTeX.mungeQA(s!!, col, svg)

            // Extract filenames from the strings using regex patterns
            var m: Matcher
            for (p in REGEXPS) {
                val fnameIdx =
                    when (p) {
                        fSoundRegexps -> 2
                        fImgAudioRegExpU -> 2
                        else -> 3
                    }
                m = p.matcher(s)
                while (m.find()) {
                    val fname = m.group(fnameIdx)!!
                    val isLocal = !fRemotePattern.matcher(fname.lowercase(Locale.getDefault())).find()
                    if (isLocal || includeRemote) {
                        l.add(fname)
                    }
                }
            }

            val ankiPlayPattern = Pattern.compile("\\[anki:play:(q|a):(\\d+)]")
            m = ankiPlayPattern.matcher(s)
            while (m.find()) {
                val side = m.group(1) // 'q' or 'a'
                val index = m.group(2)!!.toInt()

                val avTag =
                    if (side == "q") {
                        if (index < renderOutput.questionAvTags.size) {
                            renderOutput.questionAvTags[index]
                        } else {
                            null
                        }
                    } else {
                        if (index < renderOutput.answerAvTags.size) {
                            renderOutput.answerAvTags[index]
                        } else {
                            null
                        }
                    }

                if (avTag != null) {
                    val fname = extractFilenameFromAvTag(avTag)
                    if (fname != null) {
                        val isLocal = !fRemotePattern.matcher(fname.lowercase(Locale.getDefault())).find()
                        if (isLocal || includeRemote) {
                            l.add(fname)
                        }
                    }
                }
            }
        }

        return l
    }

    private fun expandClozes(string: String): MutableList<String?> {
        val ords: MutableSet<String> = TreeSet()
        var m = Pattern.compile("\\{\\{c(\\d+)::.+?\\}\\}").matcher(string)

        while (m.find()) {
            ords.add(m.group(1)!!)
        }

        val strings = ArrayList<String?>(ords.size + 1)
        val clozeReg = "(?si)\\{\\{(c)%s::(.*?)(::(.*?))?\\}\\}"

        for (ord in ords) {
            val buf = StringBuffer()
            m = Pattern.compile(String.format(Locale.US, clozeReg, ord)).matcher(string)

            while (m.find()) {
                if (!m.group(4).isNullOrEmpty()) {
                    m.appendReplacement(buf, "[${m.group(4)}]")
                } else {
                    m.appendReplacement(buf, TemplateFilters.CLOZE_DELETION_REPLACEMENT)
                }
            }

            m.appendTail(buf)
            val s =
                buf.toString().replace(
                    String.format(Locale.US, clozeReg, ".+?").toRegex(),
                    "$2",
                )
            strings.add(s)
        }

        strings.add(
            string.replace(
                String.format(Locale.US, clozeReg, ".+?").toRegex(),
                "$2",
            ),
        )

        return strings
    }

    private fun extractFilenameFromAvTag(avTag: AvTag): String? {
        val tagString = avTag.toString()
        val fname = tagString.substringAfter("filename=").substringBefore(')')
        if (fname.isNotEmpty()) {
            return fname
        }

        return null // Could not extract filename
    }

    fun findUnusedMediaFiles(): List<File> = check().unusedFileNames.map { File(dir, it) }

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

    // FIXME: this also provides trash count, but UI can not handle it yet
    fun check(): MediaCheckResult {
        val out = col.backend.checkMedia()
        return MediaCheckResult(
            missingFileNames = out.missingList,
            unusedFileNames = out.unusedList,
            missingMediaNotes = out.missingMediaNotesList,
        )
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

    companion object {
        // Upstream illegal chars defined on disallowed_char()
        // in https://github.com/ankitects/anki/blob/main/rslib/src/media/files.rs
        private val fIllegalCharReg = Pattern.compile("[\\[\\]><:\"/?*^\\\\|\\x00\\r\\n]")
        private val fRemotePattern = Pattern.compile("(https?|ftp)://")
        /*
         * A note about the regular expressions below: the python code uses named groups for the image and sound patterns.
         * Our version of Java doesn't support named groups, so we must use indexes instead. In the expressions below, the
         * group names (e.g., ?P<fname>) have been stripped and a comment placed above indicating the index of the group
         * name in the original. Refer to these indexes whenever the python code makes use of a named group.
         */
        /**
         * Group 1 = Contents of [sound:] tag
         * Group 2 = "fname"
         */
        // Regexes defined on https://github.com/ankitects/anki/blob/b403f20cae8fcdd7c3ff4c8d21766998e8efaba0/pylib/anki/media.py#L34-L45
        private val fSoundRegexps = Pattern.compile("(?i)(\\[sound:([^]]+)])")
        // src element quoted case
        /**
         * Group 1 = Contents of `<img>|<audio>` tag
         * Group 2 = "str"
         * Group 3 = "fname"
         * Group 4 = Backreference to "str" (i.e., same type of quote character)  */
        private val fImgAudioRegExpQ =
            Pattern.compile("(?i)(<(?:img|audio)\\b[^>]* src=([\"'])([^>]+?)(\\2)[^>]*>)")
        private val fObjectRegExpQ =
            Pattern.compile("(?i)(<object\\b[^>]* data=([\"'])([^>]+?)(\\2)[^>]*>)")
        // unquoted case
        /**
         * Group 1 = Contents of `<img>|<audio>` tag
         * Group 2 = "fname"
         */
        private val fImgAudioRegExpU =
            Pattern.compile("(?i)(<(?:img|audio)\\b[^>]* src=(?!['\"])([^ >]+)[^>]*?>)")
        private val fObjectRegExpU =
            Pattern.compile("(?i)(<object\\b[^>]* data=(?!['\"])([^ >]+)[^>]*?>)")
        val REGEXPS =
            listOf(
                fSoundRegexps,
                fImgAudioRegExpQ,
                fImgAudioRegExpU,
                fObjectRegExpQ,
                fObjectRegExpU,
            )
    }
}

fun getCollectionMediaPath(collectionPath: String): String = collectionPath.replaceFirst("\\.anki2$".toRegex(), ".media")

data class MediaCheckResult(
    val missingFileNames: List<String>,
    val unusedFileNames: List<String>,
    val missingMediaNotes: List<Long>,
)
