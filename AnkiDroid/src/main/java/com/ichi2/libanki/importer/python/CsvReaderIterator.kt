/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.

 This file incorporates work covered by the following copyright and permission notice.
 Please see the file LICENSE in this directory for full details

 Ported from https://github.com/python/cpython/blob/c88239f864a27f673c0f0a9e62d2488563f9d081/Modules/_csv.c
 */
package com.ichi2.libanki.importer.python

import android.annotation.SuppressLint
import com.ichi2.libanki.importer.CsvException
import com.ichi2.libanki.importer.python.CsvDialect.Quoting
import com.ichi2.utils.KotlinCleanup
import timber.log.Timber

@SuppressLint("NonPublicNonStaticFieldName")
@KotlinCleanup("fix IDE lint issues")
class CsvReaderIterator(private val reader: CsvReader) : MutableIterator<List<String>?> {
    private var field_len = 0
    private var state: State? = null
    private var line_num = 0
    private var fields: MutableList<String>? = null
    private var numeric_field = 0

    // These were modified from a bare array and size to a StringBuilder
    private val field = CharArray(field_size)

    override fun hasNext(): Boolean {
        return reader.input_iter!!.hasNext()
    }

    override fun remove() {
        throw UnsupportedOperationException("remove")
    }

    internal enum class State {
        START_RECORD,
        START_FIELD,
        IN_QUOTED_FIELD,
        EAT_CRNL,
        AFTER_ESCAPED_CRNL,
        ESCAPED_CHAR,
        IN_FIELD,
        ESCAPE_IN_QUOTED_FIELD,
        QUOTE_IN_QUOTED_FIELD
    }

    private fun parse_save_field(): Int {
        val field = String(field, 0, field_len) // ignored field.length
        field_len = 0
        if (numeric_field != 0) {
            Timber.w("skipping numeric field")
            //            PyObject *tmp;
//
//            this.numeric_field = 0;
//            try {
//                tmp = convertToFloat(field);
//            } catch ()
//            tmp = PyNumber_Float(field);
//
//            if (tmp == NULL)
//                return -1;
//            field = tmp;
        }
        fields!!.add(field)
        return 0
    }

    private fun parse_add_char(c: Char): Int {
//        if (this. field_len >= _csvstate_global->field_limit) {
//            PyErr_Format(_csvstate_global->error_obj, "field larger than field limit (%ld)",
//                    _csvstate_global->field_limit);
//            return -1;
//        }
        if (field_len == field_size) return -1
        field[field_len++] = c
        return 0
    }

    fun parse_reset() {
        fields = ArrayList()
        field_len = 0
        state = State.START_RECORD
        numeric_field = 0
    }

    // Copied from C code
    private fun parse_process_char(ch: Char): Int {
        var c = ch
        val dialect = reader.dialect
        when (state) {
            State.START_RECORD -> {
                /* start of record */
                if (c == '\u0000') {
                    /* empty line - return [] */
                    return 0
                } else if (c == '\n' || c == '\r') {
                    state = State.EAT_CRNL
                    return 0
                }
                /* normal character - handle as START_FIELD */
                state = State.START_FIELD
                // in the java code this case didn't have a break(set to fallthrough to State.START_FIELD)
                // so the migration tool copied that code here
                /* expecting field */
                if (c == '\n' || c == '\r' || c == '\u0000') {
                    /* save empty field - return [fields] */
                    if (parse_save_field() < 0) {
                        return -1
                    }
                    state = if (c == '\u0000') State.START_RECORD else State.EAT_CRNL
                } else if (c == dialect.mQuotechar && dialect.mQuoting !== Quoting.QUOTE_NONE) {
                    /* start quoted field */
                    state = State.IN_QUOTED_FIELD
                } else if (c == dialect.mEscapechar) {
                    /* possible escaped character */
                    state = State.ESCAPED_CHAR
                } else if (c == ' ' && dialect.mSkipInitialSpace) {
                } else if (c == dialect.mDelimiter) {
                    /* save empty field */
                    if (parse_save_field() < 0) {
                        return -1
                    }
                } else {
                    /* begin new unquoted field */
                    if (dialect.mQuoting === Quoting.QUOTE_NONNUMERIC) {
                        numeric_field = 1
                    }
                    if (parse_add_char(c) < 0) {
                        return -1
                    }
                    state = State.IN_FIELD
                }
            }
            State.START_FIELD ->
                /* expecting field */
                if (c == '\n' || c == '\r' || c == '\u0000') {
                    if (parse_save_field() < 0) {
                        return -1
                    }
                    state = if (c == '\u0000') State.START_RECORD else State.EAT_CRNL
                } else if (c == dialect.mQuotechar && dialect.mQuoting !== Quoting.QUOTE_NONE) {
                    state = State.IN_QUOTED_FIELD
                } else if (c == dialect.mEscapechar) {
                    state = State.ESCAPED_CHAR
                } else if (c == ' ' && dialect.mSkipInitialSpace) {
                } else if (c == dialect.mDelimiter) {
                    if (parse_save_field() < 0) {
                        return -1
                    }
                } else {
                    if (dialect.mQuoting === Quoting.QUOTE_NONNUMERIC) {
                        numeric_field = 1
                    }
                    if (parse_add_char(c) < 0) {
                        return -1
                    }
                    state = State.IN_FIELD
                }
            State.ESCAPED_CHAR -> {
                if (c == '\n' || c == '\r') {
                    if (parse_add_char(c) < 0) {
                        return -1
                    }
                    state = State.AFTER_ESCAPED_CRNL
                    return 0
                }
                if (c == '\u0000') {
                    c = '\n'
                }
                if (parse_add_char(c) < 0) {
                    return -1
                }
                state = State.IN_FIELD
            }
            State.AFTER_ESCAPED_CRNL -> {
                if (c == '\u0000') {
                    return 0
                }
                // in the java code this case didn't have a break(set to fallthrough to State.IN_FIELD)
                // so the migration tool copied that code here
                /* in unquoted field */
                if (c == '\n' || c == '\r' || c == '\u0000') {
                    /* end of line - return [fields] */
                    if (parse_save_field() < 0) {
                        return -1
                    }
                    state = if (c == '\u0000') State.START_RECORD else State.EAT_CRNL
                } else if (c == dialect.mEscapechar) {
                    /* possible escaped character */
                    state = State.ESCAPED_CHAR
                } else if (c == dialect.mDelimiter) {
                    /* save field - wait for new field */
                    if (parse_save_field() < 0) {
                        return -1
                    }
                    state = State.START_FIELD
                } else {
                    /* normal character - save in field */
                    if (parse_add_char(c) < 0) {
                        return -1
                    }
                }
            }
            State.IN_FIELD ->
                /* in unquoted field */
                if (c == '\n' || c == '\r' || c == '\u0000') {
                    if (parse_save_field() < 0) {
                        return -1
                    }
                    state = if (c == '\u0000') State.START_RECORD else State.EAT_CRNL
                } else if (c == dialect.mEscapechar) {
                    state = State.ESCAPED_CHAR
                } else if (c == dialect.mDelimiter) {
                    if (parse_save_field() < 0) {
                        return -1
                    }
                    state = State.START_FIELD
                } else {
                    if (parse_add_char(c) < 0) {
                        return -1
                    }
                }
            State.IN_QUOTED_FIELD ->
                /* in quoted field */
                if (c == '\u0000') {
                } else if (c == dialect.mEscapechar) {
                    /* Possible escape character */
                    state = State.ESCAPE_IN_QUOTED_FIELD
                } else if (c == dialect.mQuotechar && dialect.mQuoting !== Quoting.QUOTE_NONE) {
                    if (dialect.mDoublequote) {
                        /* doublequote; " represented by "" */
                        state = State.QUOTE_IN_QUOTED_FIELD
                    } else {
                        /* end of quote part of field */
                        state = State.IN_FIELD
                    }
                } else {
                    /* normal character - save in field */
                    if (parse_add_char(c) < 0) {
                        return -1
                    }
                }
            State.ESCAPE_IN_QUOTED_FIELD -> {
                if (c == '\u0000') {
                    c = '\n'
                }
                if (parse_add_char(c) < 0) {
                    return -1
                }
                state = State.IN_QUOTED_FIELD
            }
            State.QUOTE_IN_QUOTED_FIELD ->
                /* doublequote - seen a quote in a quoted field */
                if (dialect.mQuoting !== Quoting.QUOTE_NONE && c == dialect.mQuotechar) {
                    /* save "" as " */
                    if (parse_add_char(c) < 0) {
                        return -1
                    }
                    state = State.IN_QUOTED_FIELD
                } else if (c == dialect.mDelimiter) {
                    /* save field - wait for new field */
                    if (parse_save_field() < 0) {
                        return -1
                    }
                    state = State.START_FIELD
                } else if (c == '\n' || c == '\r' || c == '\u0000') {
                    /* end of line - return [fields] */
                    if (parse_save_field() < 0) {
                        return -1
                    }
                    state = if (c == '\u0000') State.START_RECORD else State.EAT_CRNL
                } else if (!dialect.mStrict) {
                    if (parse_add_char(c) < 0) {
                        return -1
                    }
                    state = State.IN_FIELD
                } else {
                    /* illegal */
                    Timber.w("'%c' expected after '%c'", dialect.mDelimiter, dialect.mQuotechar)
                    return -1
                }
            State.EAT_CRNL ->
                if (c == '\n' || c == '\r') {
                } else if (c == '\u0000') {
                    state = State.START_RECORD
                } else {
                    Timber.w("new-line character seen in unquoted field - do you need to open the file in universal-newline mode?")
                    return -1
                }
            else -> {}
        }
        return 0
    }

    override fun next(): List<String>? {
        parse_reset()
        do {
            if (!reader.input_iter!!.hasNext()) {
                if (field_len != 0 || state == State.IN_QUOTED_FIELD) {
                    if (reader.dialect.mStrict) {
                        throw CsvException("unexpected end of data")
                    } else if (parse_save_field() >= 0) {
                        break
                    }
                }
            }
            val lineobj = reader.input_iter!!.next()
            line_num++
            var pos = 0
            var linelen = lineobj.length
            while (linelen-- > 0) {
                val c = lineobj[pos]
                if (c == '\u0000') {
                    throw CsvException("line contains NUL")
                }
                if (parse_process_char(c) < 0) {
                    // error
                    return null
                }
                pos++
            }
            if (parse_process_char('\u0000') < 0) {
                return null
            }
        } while (state != State.START_RECORD)
        val fields: List<String>? = fields
        this.fields = null
        return fields
    }

    companion object {
        private const val field_size = 5000
    }
}
