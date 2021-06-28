/*
 * Copyright (c) 2021 Akshay Jadhav <jadhavakshay0701@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */


package com.ichi2.anki;


import android.content.Context;
import android.net.Uri;

import com.ichi2.anki.multimediacard.fields.ImageField;
import com.ichi2.compat.CompatHelper;
import com.ichi2.libanki.exception.EmptyMediaException;
import com.ichi2.utils.ContentResolverUtil;
import com.ichi2.utils.FileUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import androidx.annotation.CheckResult;
import androidx.annotation.Nullable;
import timber.log.Timber;

/**
 * RegisterMediaForWebView is used for registering media in temp path,
 * this class is required in summer note class for paste image event and in visual editor activity for importing media,
 * (extracted code to avoid duplication of code).
 */
public class MediaRegistration {

    private static final int MEDIA_MAX_SIZE = 5 * 1000 * 1000;

    // Use the same HTML if the same image is pasted multiple times.
    private final HashMap<String, String> mPastedImageCache = new HashMap<>();
    private final Context mContext;


    public MediaRegistration(Context context) {
        mContext = context;
    }

    /**
     * Loads an image into the collection.media folder and returns a HTML reference
     * @param uri The uri of the image to load
     * @return HTML referring to the loaded image
     */
    @Nullable
    public String loadImageIntoCollection(Uri uri) throws IOException {
        String fileName;

        String filename = ContentResolverUtil.getFileName(mContext.getContentResolver(), uri);
        InputStream fd = mContext.getContentResolver().openInputStream(uri);

        Map.Entry<String, String> fileNameAndExtension = FileUtil.getFileNameAndExtension(filename);

        if (checkFilename(Objects.requireNonNull(fileNameAndExtension))) {
            fileName = String.format("%s-name", fileNameAndExtension.getKey());
        } else {
            fileName = fileNameAndExtension.getKey();
        }

        File clipCopy = File.createTempFile(fileName, fileNameAndExtension.getValue());
        String tempFilePath = clipCopy.getAbsolutePath();
        long bytesWritten = CompatHelper.getCompat().copyFile(fd, tempFilePath);

        // register media for webView
        if (!registerMediaForWebView(tempFilePath)) {
            return null;
        }

        Timber.d("File was %d bytes", bytesWritten);
        if (bytesWritten > MEDIA_MAX_SIZE) {
            Timber.w("File was too large: %d bytes", bytesWritten);
            UIUtils.showThemedToast(mContext, mContext.getString(R.string.note_editor_paste_too_large), false);
            //noinspection ResultOfMethodCallIgnored
            new File(tempFilePath).delete();
            return null;
        }

        ImageField field = new ImageField();
        field.setHasTemporaryMedia(true);
        field.setImagePath(tempFilePath);
        return field.getFormattedValue();
    }

    public boolean checkFilename(Map.Entry<String, String> fileNameAndExtension) {
        return fileNameAndExtension.getKey().length() <= 3;
    }

    public String onImagePaste(Uri uri) {
        try {
            // check if cache already holds registered file or not
            if (!mPastedImageCache.containsKey(uri.toString())) {
                mPastedImageCache.put(uri.toString(), loadImageIntoCollection(uri));
            }
            String imageTag = mPastedImageCache.get(uri.toString());
            return imageTag;
        } catch (NullPointerException | SecurityException ex) {
            // Tested under FB Messenger and GMail, both apps do nothing if this occurs.
            // This typically works if the user copies again - don't know the exact cause

            //  java.lang.SecurityException: Permission Denial: opening provider
            //  org.chromium.chrome.browser.util.ChromeFileProvider from ProcessRecord{80125c 11262:com.ichi2.anki/u0a455}
            //  (pid=11262, uid=10455) that is not exported from UID 10057
            Timber.w(ex, "Failed to paste image");
            return null;
        } catch (Exception e) {
            // NOTE: This is happy path coding which works on Android 9.
            AnkiDroidApp.sendExceptionReport("File is invalid issue:8880", "RegisterMediaForWebView:onImagePaste URI of file:" + uri);
            Timber.w(e, "Failed to paste image");
            UIUtils.showThemedToast(mContext, mContext.getString(R.string.multimedia_editor_something_wrong), false);
            return null;
        }
    }

    @SuppressWarnings( {"BooleanMethodIsAlwaysInverted", "RedundantSuppression"})
    @CheckResult
    public boolean registerMediaForWebView(String imagePath) {
        if (imagePath == null) {
            //Nothing to register - continue with execution.
            return true;
        }
        Timber.i("Adding media to collection: %s", imagePath);
        File f = new File(imagePath);
        try {
            CollectionHelper.getInstance().getCol(mContext).getMedia().addFile(f);
            return true;
        } catch (IOException | EmptyMediaException e) {
            Timber.w(e, "Failed to add file");
            return false;
        }
    }

}
