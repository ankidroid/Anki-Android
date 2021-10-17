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

import java.util.Iterator;
import java.util.List;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

@SuppressLint("NonPublicNonStaticFieldName")
public class CsvReader implements Iterable<List<String>> {

    public final CsvDialect dialect;
    public Iterator<String> input_iter;
    public CsvReaderIterator iter;


    public CsvReader(@NonNull Iterator<String> data, char delimiter, @Nullable CsvDialect dialect) {
        if (delimiter == '\0' && dialect == null) {
            throw new IllegalStateException("either the dialect or the delimiter must be set");
        }

        this.input_iter = null;

        if (dialect == null) {
            dialect = new CsvDialect("unused");
            dialect.mDelimiter = delimiter;
        }

        // PORTING: Python does this in the constructor
        dialect.mDoublequote = true;

        this.input_iter = data;
        this.dialect = dialect;
    }


    @NonNull
    @CheckResult
    public static CsvReader fromDelimiter(@NonNull Iterator<String> data, char delimiter) {
        return new CsvReader(data, delimiter, null);
    }


    @NonNull
    @CheckResult
    public static CsvReader fromDialect(@NonNull Iterator<String> data, @NonNull CsvDialect dialect) {
        return new CsvReader(data, '\0', dialect);
    }


    @NonNull
    @Override
    public Iterator<List<String>> iterator() {
        if (this.iter == null) {
            this.iter = new CsvReaderIterator(this);
        }
        return this.iter;
    }


    @Nullable
    public List<String> next() {
        return iterator().next();
    }
}
