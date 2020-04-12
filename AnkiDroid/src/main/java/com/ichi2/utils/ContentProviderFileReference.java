package com.ichi2.utils;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import timber.log.Timber;

public class ContentProviderFileReference {
    @CheckResult
    public @Nullable String extractFilePath(@NonNull ContentResolver resolver, @NonNull Uri uri) {
        String[] filePathColumn = { MediaStore.MediaColumns.DATA };

        Cursor cursor = null;
        try {
            cursor = resolver.query(uri, filePathColumn, null, null, null);

            if (cursor == null) {
                Timber.w("cursor was null");
                return null;
            }

            if (!cursor.moveToFirst()) {
                //TODO: #5909, it would be best to instrument this to see if we can fix the failure
                Timber.w("cursor had no data");
                return null;
            }

            int columnIndex = cursor.getColumnIndex(filePathColumn[0]);

            String filePath = cursor.getString(columnIndex);

            if (filePath == null) {
                Timber.w("Failed to decode filepath");
                return null;
            }

            return filePath;
        } catch (Exception e) {
          Timber.e(e, "Exception decoding filepath");
          return null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
}
