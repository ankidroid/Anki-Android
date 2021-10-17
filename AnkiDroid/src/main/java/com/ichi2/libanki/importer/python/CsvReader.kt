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
import androidx.annotation.CheckResult

@SuppressLint("NonPublicNonStaticFieldName")
class CsvReader(data: Iterator<String>, delimiter: Char, inputDialect: CsvDialect?) : Iterable<List<String>> {
    @JvmField
    val dialect: CsvDialect
    @JvmField
    var input_iter: Iterator<String>?
    var iter: CsvReaderIterator? = null
    override fun iterator(): MutableIterator<List<String>> {
        if (iter == null) {
            iter = CsvReaderIterator(this)
        }
        return iter!!
    }

    operator fun next(): List<String> {
        return iterator().next()
    }

    companion object {
        @JvmStatic
        @CheckResult
        fun fromDelimiter(data: Iterator<String>, delimiter: Char): CsvReader {
            return CsvReader(data, delimiter, null)
        }

        @JvmStatic
        @CheckResult
        fun fromDialect(data: Iterator<String>, dialect: CsvDialect): CsvReader {
            return CsvReader(data, '\u0000', dialect)
        }
    }

    init {
        var dialect = inputDialect
        check(!(delimiter == '\u0000' && dialect == null)) { "either the dialect or the delimiter must be set" }
        input_iter = null
        if (dialect == null) {
            dialect = CsvDialect("unused")
            dialect.mDelimiter = delimiter
        }

        dialect.mDoublequote = true
        input_iter = data
        this.dialect = dialect
    }
}
