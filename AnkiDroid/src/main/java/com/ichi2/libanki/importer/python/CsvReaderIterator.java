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

package com.ichi2.libanki.importer.python;

import android.annotation.SuppressLint;

import com.ichi2.libanki.importer.CsvException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import timber.log.Timber;

import static com.ichi2.libanki.importer.python.CsvDialect.Quoting.*;
import static com.ichi2.libanki.importer.python.CsvReaderIterator.State.*;

@SuppressLint("NonPublicNonStaticFieldName")
public class CsvReaderIterator implements Iterator<List<String>> {
    private final CsvReader reader;

    private int field_len;
    private State state;
    private int line_num;
    private List<String> fields;
    private int numeric_field;

    private final static int field_size = 5000;
    // These were modified from a bare array and size to a StringBuilder
    private final char[] field = new char[field_size];


    public CsvReaderIterator(@NonNull CsvReader reader) {
        this.reader = reader;
    }


    @Override
    public boolean hasNext() {
        return this.reader.input_iter.hasNext();
    }

    enum State {
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

    private int parse_save_field() {

        String field = new String(this.field, 0, this.field_len); // ignored field.length

        this.field_len = 0;
        if (this.numeric_field != 0) {
            Timber.w("skipping numeric field");
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
        this.fields.add(field);
        return 0;
    }

    private int parse_add_char(char c) {
//        if (this. field_len >= _csvstate_global->field_limit) {
//            PyErr_Format(_csvstate_global->error_obj, "field larger than field limit (%ld)",
//                    _csvstate_global->field_limit);
//            return -1;
//        }
        if (this.field_len == field_size)
            return -1;
        this.field[this.field_len++] = c;
        return 0;

    }

     void parse_reset() {
        this.fields = new ArrayList<>();
        this.field_len = 0;
        this.state = START_RECORD;
        this.numeric_field = 0;
    }

    //noinspection ControlFlowStatementWithoutBraces
    @SuppressWarnings( {"fallthrough", "RedundantSuppression"}) // Copied from C code
    private int parse_process_char(char c) {
        CsvDialect dialect = this.reader.dialect;

        switch (this.state) {
            case START_RECORD:
                /* start of record */
                if (c == '\0')
                    /* empty line - return [] */
                    break;
                else if (c == '\n' || c == '\r') {
                    this.state = EAT_CRNL;
                    break;
                }
                /* normal character - handle as START_FIELD */
                this.state = START_FIELD;
                /* fallthru */
            case START_FIELD:
                /* expecting field */
                if (c == '\n' || c == '\r' || c == '\0') {
                    /* save empty field - return [fields] */
                    if (parse_save_field() < 0)
                        return -1;
                    this.state = (c == '\0' ? START_RECORD : EAT_CRNL);
                }
                else if (c == dialect.mQuotechar &&
                        dialect.mQuoting != QUOTE_NONE) {
                    /* start quoted field */
                    this.state = IN_QUOTED_FIELD;
                }
                else if (c == dialect.mEscapechar) {
                    /* possible escaped character */
                    this.state = ESCAPED_CHAR;
                }
                else
                    if (c == ' ' && dialect.mSkipInitialSpace)
                    /* ignore space at start of field */
                    ;
                else if (c == dialect.mDelimiter) {
                    /* save empty field */
                    if (parse_save_field() < 0)
                        return -1;
                }
                else {
                    /* begin new unquoted field */
                    if (dialect.mQuoting == QUOTE_NONNUMERIC)
                        this.numeric_field = 1;
                    if (parse_add_char(c) < 0)
                        return -1;
                    this.state = IN_FIELD;
                }
                break;

            case ESCAPED_CHAR:
                if (c == '\n' || c=='\r') {
                    if (parse_add_char(c) < 0)
                        return -1;
                    this.state = AFTER_ESCAPED_CRNL;
                    break;
                }
                if (c == '\0')
                    c = '\n';
                if (parse_add_char(c) < 0)
                    return -1;
                this.state = IN_FIELD;
                break;

            case AFTER_ESCAPED_CRNL:
                if (c == '\0')
                    break;
                /*fallthru*/

            case IN_FIELD:
                /* in unquoted field */
                if (c == '\n' || c == '\r' || c == '\0') {
                    /* end of line - return [fields] */
                    if (parse_save_field() < 0)
                        return -1;
                    this.state = (c == '\0' ? START_RECORD : EAT_CRNL);
                }
                else if (c == dialect.mEscapechar) {
                    /* possible escaped character */
                    this.state = ESCAPED_CHAR;
                }
                else if (c == dialect.mDelimiter) {
                    /* save field - wait for new field */
                    if (parse_save_field() < 0)
                        return -1;
                    this.state = START_FIELD;
                }
                else {
                    /* normal character - save in field */
                    if (parse_add_char(c) < 0)
                        return -1;
                }
                break;

            case IN_QUOTED_FIELD:
                /* in quoted field */
                if (c == '\0')
                    ;
                else if (c == dialect.mEscapechar) {
                    /* Possible escape character */
                    this.state = ESCAPE_IN_QUOTED_FIELD;
                }
                else if (c == dialect.mQuotechar &&
                        dialect.mQuoting != QUOTE_NONE) {
                    if (dialect.mDoublequote) {
                        /* doublequote; " represented by "" */
                        this.state = QUOTE_IN_QUOTED_FIELD;
                    }
                    else {
                        /* end of quote part of field */
                        this.state = IN_FIELD;
                    }
                }
                else {
                    /* normal character - save in field */
                    if (parse_add_char(c) < 0)
                        return -1;
                }
                break;

            case ESCAPE_IN_QUOTED_FIELD:
                if (c == '\0')
                    c = '\n';
                if (parse_add_char(c) < 0)
                    return -1;
                this.state = IN_QUOTED_FIELD;
                break;

            case QUOTE_IN_QUOTED_FIELD:
                /* doublequote - seen a quote in a quoted field */
                if (dialect.mQuoting != QUOTE_NONE &&
                        c == dialect.mQuotechar) {
                    /* save "" as " */
                    if (parse_add_char(c) < 0)
                        return -1;
                    this.state = IN_QUOTED_FIELD;
                }
                else if (c == dialect.mDelimiter) {
                    /* save field - wait for new field */
                    if (parse_save_field() < 0)
                        return -1;
                    this.state = START_FIELD;
                }
                else if (c == '\n' || c == '\r' || c == '\0') {
                    /* end of line - return [fields] */
                    if (parse_save_field() < 0)
                        return -1;
                    this.state = (c == '\0' ? START_RECORD : EAT_CRNL);
                }
                else if (!dialect.mStrict) {
                    if (parse_add_char(c) < 0)
                        return -1;
                    this.state = IN_FIELD;
                }
                else {
                    /* illegal */
                    Timber.w("'%c' expected after '%c'", dialect.mDelimiter, dialect.mQuotechar);
                    return -1;
                }
                break;

            case EAT_CRNL:
                if (c == '\n' || c == '\r')
                    ;
                else if (c == '\0')
                    this.state = START_RECORD;
                else {
                    Timber.w("new-line character seen in unquoted field - do you need to open the file in universal-newline mode?");
                    return -1;
                }
                break;

        }
        return 0;
    }

    @Override
    @Nullable
    public List<String> next() {
        parse_reset();
        do {
            if (!reader.input_iter.hasNext()) {
                if (this.field_len != 0 || this.state == IN_QUOTED_FIELD) {
                    if (this.reader.dialect.mStrict) {
                        throw new CsvException("unexpected end of data");
                    } else if (parse_save_field() >= 0) {
                        break;
                    }
                }
            }
            String lineobj = this.reader.input_iter.next();

            line_num++;

            int pos = 0;
            int linelen = lineobj.length();
            while (linelen-- > 0) {
                char c = lineobj.charAt(pos);
                if (c == '\0') {
                    throw new CsvException("line contains NUL");
                }
                if (parse_process_char(c) < 0) {
                    // error
                    return null;
                }
                pos++;
            }
            if (parse_process_char('\0') < 0) {
                return null;
            }
        } while (state != START_RECORD);

        List<String> fields = this.fields;
        this.fields = null;

        return fields;
    }
}
