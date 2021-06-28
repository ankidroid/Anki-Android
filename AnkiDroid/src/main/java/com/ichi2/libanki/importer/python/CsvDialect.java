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

@SuppressLint("NonPublicNonStaticFieldName")
public class CsvDialect {
    /** Controls when quotes should be generated by the writer and recognised by the reader. It can take on any of the QUOTE_* constants (see section Module Contents) and defaults to QUOTE_MINIMAL. */
    public final Quoting mQuoting = Quoting.QUOTE_MINIMAL;
    /**  Controls how instances of quotechar appearing inside a field should themselves be quoted. When True, the character is doubled. When False, the escapechar is used as a prefix to the quotechar.
     *    On output, if doublequote is False and no escapechar is set, Error is raised if a quotechar is found in a field. */
    public boolean mDoublequote = true;
    /** A one-character string used to separate fields. It defaults to ','. */
    public char mDelimiter = ',';

    /** When True, whitespace immediately following the delimiter is ignored. The default is False. */
    public boolean mSkipInitialSpace = false;

    /** The string used to terminate lines produced by the writer. It defaults to '\r\n'.
     * Note The reader is hard-coded to recognise either '\r' or '\n' as end-of-line, and ignores lineterminator. This behavior may change in the future.
     */
    public final String mLineTerminator = "\r\n";
    /** A one-character string used to quote fields containing special characters, such as the delimiter or quotechar, or which contain new-line characters. It defaults to '"'. */
    public char mQuotechar = '"';

    /** When True, raise exception Error on bad CSV input. The default is False. */
    public final boolean mStrict = false;
    /** A one-character string used by the writer to escape the delimiter if quoting is set to QUOTE_NONE and the quotechar if doublequote is False. On reading, the escapechar removes any special meaning from the following character. It defaults to None, which disables escaping. */
    public final char mEscapechar = '\0';


    @SuppressWarnings( {"FieldCanBeLocal", "unused"})
    private final String mName;

    public CsvDialect(String _name) {
        this.mName = _name;
    }


    public enum Quoting {
        QUOTE_MINIMAL,
        QUOTE_NONE,
        QUOTE_NONNUMERIC,
    }
}
