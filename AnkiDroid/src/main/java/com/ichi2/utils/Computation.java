/****************************************************************************************
 * Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>                              *
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

package com.ichi2.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Represents a computed value or a failure. Similar to c++ absl::StatusOr<U>, Rust Result<U>,
 * and almost similar to Future<U>, except that `get` don't throws.
 * @param <ComputedType> The value of a succesful computation
 */
// "Result" is used as a type parameter in AsyncTask, where this class is used a lot. Hence,
// `Result` would not be an acceptable type name.
public class Computation<ComputedType> {
    /**
     * The computed value in case of success. Null in case of failure
     */
    private final @Nullable ComputedType mValue;

    public boolean succeeded() {
        return mValue != null;
    }
    public static final Computation ERR = new Computation();
    public static final Computation OK = new Computation<>(new Object());

    public ComputedType getValue() {
        return mValue;
    }

    private Computation() {
        mValue = null;
    }
    public Computation(@NonNull ComputedType value) {
        mValue = value;
    }
}
