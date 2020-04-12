package com.ichi2.utils;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import com.ichi2.compat.CompatHelper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import timber.log.Timber;

public class ContentProviderFileReference {
    @CheckResult
    public @Nullable String extractFilePath(@NonNull ContentResolver resolver, @NonNull Uri uri, String downloadDirectory) {

        //DEFECT: We could get a small amount of performance boost by checking providers based on the URI.
        //But, it's a lot of code for minimal performance gain.
        String localPath = getLocalFilePath(resolver, uri);

        if (localPath != null) {
            return localPath;
        }

        if (downloadDirectory == null) {
            return null;
        }

        return downloadFile(resolver, uri, downloadDirectory);
    }


    private String downloadFile(ContentResolver resolver, Uri uri, String downloadDir) {
        String fileName = getFileName(resolver, uri);

        if (!new File(downloadDir).isDirectory()) {
            Timber.w("Path does not exist: %s", downloadDir);
            return null;
        }

        if (fileName == null) {
            //DEFECT: We could do better and generate a name,
            Timber.w("Could not obtain file name");
            return null;
        }

        try (InputStream is = resolver.openInputStream(uri)) {
            if (is == null) {
                Timber.i("Failed to decode: no inputStream");
                return null;
            }

            File file = new File(downloadDir, fileName);

            //If we already have a file there, don't overwrite.
            if (file.exists()) {
                return file.getPath();
            }

            if (!file.createNewFile()) {
                Timber.w("Couldn't create %s", file.toString());
                return null;
            }
            CompatHelper.getCompat().copyFile(is, file.getAbsolutePath());
            return file.getPath();
        } catch (IOException e) {
            Timber.e(e, "Exception copying stream");
            return null;
        }
    }



    private String getFileName(@NonNull ContentResolver resolver, @NonNull Uri uri) {
        String[] projection = {MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.TITLE};
        Cursor metaCursor = resolver.query(uri, projection, null, null, null);
        if (metaCursor == null) {
            return null;
        }
        try {
            if (!metaCursor.moveToFirst()) {
                return null;
            }

            String fileName = metaCursor.getString(metaCursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME));
            if (fileName != null) {
                return fileName;
            }
            return metaCursor.getString(metaCursor.getColumnIndex(MediaStore.MediaColumns.TITLE));
        } catch (Exception e) {
            Timber.e(e, "Failed to get filename");
            return null;
        }
        finally {
            metaCursor.close();
        }
    }

    private String getLocalFilePath(@NonNull ContentResolver resolver, @NonNull Uri uri) {
        String[] filePathColumn = { MediaStore.MediaColumns.DATA };

        try (Cursor cursor = resolver.query(uri, filePathColumn, null, null, null)) {

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
        }
    }
}
