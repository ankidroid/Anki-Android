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


        public WrongConditionalClosed(@NonNull String mExpected, @NonNull String mFound) {
            this.mExpected = mExpected;
            this.mFound = mFound;
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
