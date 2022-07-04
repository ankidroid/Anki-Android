//noinspection MissingCopyrightHeader #8659

package com.ichi2.libanki.importer

import android.os.Build
import android.text.TextUtils
import androidx.annotation.RequiresApi
import com.ichi2.anki.R
import com.ichi2.libanki.Collection
import com.ichi2.libanki.importer.python.CsvDialect
import com.ichi2.libanki.importer.python.CsvReader
import com.ichi2.libanki.importer.python.CsvSniffer
import com.ichi2.utils.KotlinCleanup
import org.jetbrains.annotations.Contract
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Arrays
import java.util.Optional
import java.util.regex.Pattern
import java.util.stream.Collectors
import java.util.stream.Stream

@KotlinCleanup("ide-lint")
@KotlinCleanup("lateinit")
@RequiresApi(api = Build.VERSION_CODES.O)
class TextImporter(col: Collection, file: String) : NoteImporter(col, file) {
    override var needDelimiter = true
    val mPatterns = "\t|,;:"
    private var mFileObj: FileObj? = null
    private var mDelimiter = '\u0000'
    private var mTagsToAdd: Array<String> = emptyArray()
    private var mDialect: CsvDialect? = null
    private var mNumFields = 0
    private var mFirstLineWasTags = false
    override fun foreignNotes(): List<ForeignNote> {
        open()
        // process all lines
        val _log: MutableList<String> = ArrayList() // Number of element is reader's size
        val notes: MutableList<ForeignNote> = ArrayList() // Number of element is reader's size
        var ignored = 0
        // Note: This differs from libAnki as we don't have csv.reader
        val data = dataStream.iterator()
        val reader: CsvReader
        reader = if (mDelimiter != '\u0000') {
            CsvReader.fromDelimiter(data, mDelimiter)
        } else {
            CsvReader.fromDialect(data, mDialect!!)
        }
        try {
            for (row in reader) {
                if (row == null) {
                    continue
                }
                val rowAsString: List<String?> = ArrayList(row)
                if (rowAsString.size != mNumFields) {
                    if (!rowAsString.isEmpty()) {
                        val formatted = getString(
                            R.string.csv_importer_error_invalid_field_count,
                            TextUtils.join(" ", rowAsString),
                            rowAsString.size,
                            mNumFields
                        )
                        _log.add(formatted)
                        ignored += 1
                    }
                    continue
                }
                val note = noteFromFields(rowAsString)
                notes.add(note)
            }
        } catch (e: CsvException) {
            _log.add(getString(R.string.csv_importer_error_exception, e))
        }
        log = _log
        mFileObj!!.close()
        return notes
    }

    /**
     * Number of fields.
     * @throws UnknownDelimiterException Could not determine delimiter (example: empty file)
     * @throws EncodingException Non-UTF file (for example: an image)
     */
    override fun fields(): Int {
        open()
        return mNumFields
    }

    @KotlinCleanup("simply fun with scope function")
    private fun noteFromFields(fields: List<String?>): ForeignNote {
        val note = ForeignNote()
        note.mFields.addAll(fields)
        note.mTags.addAll(Arrays.asList(*mTagsToAdd))
        return note
    }

    /** Parse the top line and determine the pattern and number of fields.  */
    override fun open() {
        // load & look for the right pattern
        cacheFile()
    }

    /** Read file into self.lines if not already there.  */
    private fun cacheFile() {
        if (mFileObj == null) {
            openFile()
        }
    }

    @KotlinCleanup("simplify null comparison")
    private fun openFile() {
        mDialect = null
        mFileObj = FileObj.open(file)
        val firstLine = firstFileLine.orElse(null)
        if (firstLine != null) {
            if (firstLine.startsWith("tags:")) {
                val tags = firstLine.substring("tags:".length).trim { it <= ' ' }
                mTagsToAdd = tags.split(" ").toTypedArray()
                mFirstLineWasTags = true
            }
            updateDelimiter()
        }
        if (mDialect == null && mDelimiter == '\u0000') {
            throw UnknownDelimiterException()
        }
    }

    @Contract(" -> fail")
    private fun err() {
        throw UnknownDelimiterException()
    }

    private fun updateDelimiter() {
        mDialect = null
        val sniffer = CsvSniffer()
        if (mDelimiter == '\u0000') {
            try {
                val join = getLinesFromFile(10)
                mDialect = sniffer.sniff(join, mPatterns.toCharArray())
            } catch (e: Exception) {
                // expected: do not log the exception
                try {
                    mDialect = sniffer.sniff(firstFileLine.orElse("")!!, mPatterns.toCharArray())
                } catch (ex: Exception) {
                    // expected and ignored: do not log the exception
                }
            }
        }
        val data = dataStream.iterator()
        var reader: CsvReader? = null
        if (mDialect != null) {
            try {
                reader = CsvReader.fromDialect(data, mDialect!!)
            } catch (e: Exception) {
                // expected and ignored: do not log the exception
                err()
            }
        } else {
            // PERF: This starts the file read twice - whereas we only need the first line
            val firstLine = firstFileLine.orElse("")
            if (mDelimiter == '\u0000') {
                mDelimiter = if (firstLine!!.contains("\t")) {
                    '\t'
                } else if (firstLine.contains(";")) {
                    ';'
                } else if (firstLine.contains(",")) {
                    ','
                } else {
                    ' '
                }
            }
            reader = CsvReader.fromDelimiter(data, mDelimiter)
        }
        try {
            while (true) {
                val row = reader!!.next()
                if (!row!!.isEmpty()) {
                    mNumFields = row.size
                    break
                }
            }
        } catch (e: Exception) {
            Timber.e(e)
            err()
        }
        initMapping()
    }

    companion object {
        /*
    In python:
    >>> pp(re.sub("^\#.*$", "__comment", "#\r\n"))
    '__comment\n'
    In Java:
    COMMENT_PATTERN.matcher("#\r\n").replaceAll("__comment") -> "__comment\r\n"
    So we use .DOTALL to ensure we get the \r
    */
        private val COMMENT_PATTERN = Pattern.compile("^#.*$", Pattern.DOTALL)
    }

    private fun sub(s: String): String {
        return COMMENT_PATTERN.matcher(s).replaceAll("__comment")
    }

    private val dataStream: Stream<String>
        get() {
            val data: Stream<String>
            data = try {
                mFileObj!!.readAsUtf8WithoutBOM()
            } catch (e: IOException) {
                throw EncodingException(e)
            }
            var withoutComments = data.filter { x: String -> "__comment" != sub(x) }.map { s: String -> s + "\n" }
            if (mFirstLineWasTags) {
                withoutComments = withoutComments.skip(1)
            }
            return withoutComments
        }
    private val firstFileLine: Optional<String?>
        get() = try {
            dataStream.findFirst()
        } catch (e: UncheckedIOException) {
            throw EncodingException(e)
        }

    private fun getLinesFromFile(numberOfLines: Int): String {
        return try {
            TextUtils.join(
                "\n",
                dataStream.limit(numberOfLines.toLong()).collect(Collectors.toList())
            )
        } catch (e: UncheckedIOException) {
            throw EncodingException(e)
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private class FileObj(private val file: File) {
        fun close() {
            // No need for anything - closed in the read
        }

        @Throws(IOException::class)
        fun readAsUtf8WithoutBOM(): Stream<String> {
            return Files.lines(Paths.get(file.absolutePath), StandardCharsets.UTF_8)
        }

        companion object {
            fun open(file: String): FileObj {
                return FileObj(File(file))
            }
        }
    }

    class UnknownDelimiterException : RuntimeException("unknownFormat")

    /** Non-UTF file was provided (for example: an image)  */
    class EncodingException(e: Exception?) : RuntimeException(e)
}
