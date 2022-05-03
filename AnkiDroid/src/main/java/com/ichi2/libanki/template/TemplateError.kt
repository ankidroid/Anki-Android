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

package com.ichi2.libanki.template;

import android.content.Context;

import com.ichi2.anki.R;

import java.util.List;
import java.util.NoSuchElementException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public abstract class TemplateError extends NoSuchElementException {
    public abstract String message(Context context);

    public static class NoClosingBrackets extends TemplateError {
        public final @NonNull String mRemaining;

        public NoClosingBrackets(String remaining) {
            mRemaining = remaining;
        }


        public String message(Context context) {
            return context.getString(R.string.missing_closing_bracket, mRemaining);
        }
    }

    public static class ConditionalNotClosed extends TemplateError {
        public final @NonNull String mFieldName;

        public ConditionalNotClosed(String fieldName) {
            mFieldName = fieldName;
        }

        public String message(Context context) {
            return context.getString(R.string.open_tag_not_closed, mFieldName);
        }
    }

    public static class WrongConditionalClosed extends TemplateError {
        public final @NonNull String mExpected;
        public final @NonNull String mFound;


        public WrongConditionalClosed(@NonNull String expected, @NonNull String found) {
            this.mExpected = expected;
            this.mFound = found;
        }


        public String message(Context context) {
            return context.getString(R.string.wrong_tag_closed, mFound, mExpected);
        }
    }

    public static class ConditionalNotOpen extends TemplateError {
        public final @NonNull String mClosed;


        public ConditionalNotOpen(@NonNull String closed) {
            mClosed = closed;
        }

        public String message(Context context) {
            return context.getString(R.string.closed_tag_not_open, mClosed);
        }
    }

    public static class FieldNotFound extends TemplateError {
        public final @NonNull List<String> mFilters;
        public final @NonNull String mField;


        public FieldNotFound(@NonNull List<String> filters, @NonNull String field) {
            this.mFilters = filters;
            this.mField = field;
        }

        public String message(Context context) {
            return context.getString(R.string.no_field, mField);
        }
    }
}
