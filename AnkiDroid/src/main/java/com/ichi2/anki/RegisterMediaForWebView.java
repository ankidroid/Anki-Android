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
public class RegisterMediaForWebView {

    // Use the same HTML if the same image is pasted multiple times.
    private final HashMap<String, String> mPastedImageCache = new HashMap<>();
    private final Context mContext;


    public RegisterMediaForWebView(Context context) {
        mContext = context;
    }

    /**
     * Loads an image into the collection.media folder and returns a HTML reference
     * @param uri The uri of the image to load
     * @return HTML referring to the loaded image
     */
    @SuppressWarnings("PointlessArithmeticExpression")
    @Nullable
    public String loadImageIntoCollection(Uri uri) throws IOException {
        String fileName;
        final int oneMegabyte = 1 * 1000 * 1000;

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
        if(!registerMediaForWebView(tempFilePath)){
            return null;
        }

        Timber.d("File was %d bytes", bytesWritten);
        if (bytesWritten > oneMegabyte) {
            Timber.w("File was too large: %d bytes", bytesWritten);
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
            if (imageTag == null) {
                return null;
            }
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
            AnkiDroidApp.sendExceptionReport("File is invalid issue:8880", "NoteEditor:onImagePaste URI of file:" + uri);
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
            Timber.e(e, "Failed to add file");
            return false;
        }
    }

}