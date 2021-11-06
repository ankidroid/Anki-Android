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

import android.content.ContentResolver;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.util.Locale;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import timber.log.Timber;

public class ContentResolverUtil {

    /** Obtains the filename from the url. Throws if all methods return exception */
    @CheckResult
    public static String getFileName(ContentResolver contentResolver, Uri uri) {
        try {
             String filename = getFilenameViaDisplayName(contentResolver, uri);
             if (filename != null) {
                 return filename;
             }
        } catch (Exception e) {
            Timber.w(e, "getFilenameViaDisplayName");
        }

        // let this one throw
        String filename = getFilenameViaMimeType(contentResolver, uri);
        if (filename != null) {
            return filename;
        }
        throw new IllegalStateException(String.format("Unable to obtain valid filename from uri: %s", uri));
    }

    @CheckResult
    @Nullable
    private static String getFilenameViaMimeType(ContentResolver contentResolver, @NonNull Uri uri) {
        // value: "png" when testing
        String extension = null;

        //Check uri format to avoid null
        if (uri.getScheme() != null && uri.getScheme().equals(ContentResolver.SCHEME_CONTENT)) {
            //If scheme is a content
            final MimeTypeMap mime = MimeTypeMap.getSingleton();
            extension = mime.getExtensionFromMimeType(contentResolver.getType(uri));
        } else {
            // If scheme is a File
            // This will replace white spaces with %20 and also other special characters. This will avoid returning null values on file name with spaces and special characters.
            if (uri.getPath() != null) {
                extension = MimeTypeMap.getFileExtensionFromUrl(Uri.fromFile(new File(uri.getPath())).toString().toLowerCase(Locale.ROOT));
            }
        }
        if (extension == null) {
            return null;
        }

        return "image" + "." + extension;
    }


    @CheckResult
    @Nullable
    private static String getFilenameViaDisplayName(ContentResolver contentResolver, Uri uri) {
        // 7748: android.database.sqlite.SQLiteException: no such column: _display_name (code 1 SQLITE_ERROR[1]): ...
        try (Cursor c = contentResolver.query(uri, new String[] { MediaStore.MediaColumns.DISPLAY_NAME }, null, null, null)) {
            if (c != null) {
                c.moveToNext();
                return c.getString(0);
            }
        } catch (SQLiteException e) {
            Timber.w(e, "getFilenameViaDisplayName ContentResolver query failed.");
        }
        return null;
    }
}
