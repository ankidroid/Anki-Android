/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.utils;


import androidx.annotation.NonNull;

/** TODO: Move this to standard library in API 24 */
public final class FunctionalInterfaces {

    @FunctionalInterface
    public interface FunctionThrowable<TIn, TOut, TEx extends Throwable> {
        TOut apply(TIn item) throws TEx;
    }

    @FunctionalInterface
    public interface Filter<TIn> {
        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        boolean shouldInclude(TIn item);
    }

    public static class Filters {
        @NonNull
        public static <T> Filter<T> allowAll() {
            return (a) -> true;
        }
    }
}
