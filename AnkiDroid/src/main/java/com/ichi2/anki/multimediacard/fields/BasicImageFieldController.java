/****************************************************************************************
 * Copyright (c) 2013 Bibek Shrestha <bibekshrestha@gmail.com>                          *
 * Copyright (c) 2013 Zaur Molotnikov <qutorial@gmail.com>                              *
 * Copyright (c) 2013 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>                                   *
 * Copyright (c) 2020 Mike Hardy <github@mikehardy.net>                                 *
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

package com.ichi2.anki.multimediacard.fields;

import android.app.Activity;
import android.content.ClipData;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContentResolverCompat;
import androidx.core.content.FileProvider;

import android.text.TextUtils;
import android.text.format.Formatter;
import android.provider.DocumentsContract;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;
import com.ichi2.anki.UIUtils;
import com.ichi2.compat.CompatHelper;
import com.ichi2.libanki.utils.SystemTime;
import com.ichi2.libanki.utils.TimeUtils;
import com.ichi2.utils.BitmapUtil;
import com.ichi2.utils.ExifUtil;
import com.ichi2.utils.Permissions;

import com.afollestad.materialdialogs.MaterialDialog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import androidx.core.util.Pair;
import timber.log.Timber;

public class BasicImageFieldController extends FieldControllerBase implements IFieldController {

    @VisibleForTesting
    static final int ACTIVITY_SELECT_IMAGE = 1;
    private static final int ACTIVITY_TAKE_PICTURE = 2;
    private static final int ACTIVITY_CROP_PICTURE = 3;
    private static final int IMAGE_SAVE_MAX_WIDTH = 1920;

    private ImageView mImagePreview;
    private TextView mImageFileSize;
    private TextView mImageFileSizeWarning;

    private @Nullable String mImagePath;
    private @Nullable Uri mImageUri;
    private @Nullable String mPreviousImagePath; // save the latest path to prevent from cropping or taking photo action canceled
    private @Nullable Uri mPreviousImageUri;
    private @Nullable String mAnkiCacheDirectory; // system provided 'External Cache Dir' with "temp-photos" on it
                                                  // e.g.  '/self/primary/Android/data/com.ichi2.anki.AnkiDroid/cache/temp-photos'
    private DisplayMetrics mMetrics = null;
    private SystemTime mTime = new SystemTime();

    private Button mCropButton = null;

    private int getMaxImageSize() {
        DisplayMetrics metrics = getDisplayMetrics();

        int height = metrics.heightPixels;
        int width = metrics.widthPixels;

        return (int) Math.min(height * 0.4, width * 0.6);
    }

    public void loadInstanceState(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            Timber.i("loadInstanceState but null so nothing to load");
            return;
        }

        Timber.i("loadInstanceState loading saved state...");
        mImagePath = savedInstanceState.getString("mImagePath");
        mImageUri = savedInstanceState.getParcelable("mImageUri");
        mPreviousImagePath = savedInstanceState.getString("mPreviousImagePath");
        mPreviousImageUri = savedInstanceState.getParcelable("mPreviousImageUri");
    }

    public Bundle saveInstanceState() {
        Timber.d("saveInstanceState");
        Bundle savedInstanceState = new Bundle();
        savedInstanceState.putString("mImagePath", mImagePath);
        savedInstanceState.putParcelable("mImageUri", mImageUri);
        savedInstanceState.putString("mPreviousImagePath", mPreviousImagePath);
        savedInstanceState.putParcelable("mPreviousImageUri", mPreviousImageUri);
        return savedInstanceState;
    }

    @Override
    public void createUI(Context context, LinearLayout layout) {
        Timber.d("createUI()");
        if (mImagePath == null) {
            mImagePath = mField.getImagePath();
        }
        if (mImageUri == null && mImagePath != null) {
            mImageUri = getUriForFile(new File(mImagePath));
        }

        mImagePreview = new ImageView(mActivity);
        File externalCacheDirRoot = context.getExternalCacheDir();
        if (externalCacheDirRoot == null) {
            Timber.e("createUI() unable to get external cache directory");
            showSomethingWentWrong();
            return;
        }
        File externalCacheDir = new File(externalCacheDirRoot.getAbsolutePath() + "/temp-photos");
        if (!externalCacheDir.exists() && !externalCacheDir.mkdir()) {
            Timber.e("createUI() externalCacheDir did not exist and could not be created");
            showSomethingWentWrong();
            return;
        }
        mAnkiCacheDirectory = externalCacheDir.getAbsolutePath();

        LinearLayout.LayoutParams p = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);

        drawUIComponents(context);

        mCropButton = new Button(mActivity);
        mCropButton.setText(gtxt(R.string.crop_button));
        mCropButton.setOnClickListener(v -> requestCrop());
        mCropButton.setVisibility(View.INVISIBLE);

        Button mBtnGallery = new Button(mActivity);
        mBtnGallery.setText(gtxt(R.string.multimedia_editor_image_field_editing_galery));
        mBtnGallery.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_PICK);
            i.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
            mActivity.startActivityForResultWithoutAnimation(i, ACTIVITY_SELECT_IMAGE);
        });

        Button mBtnCamera = new Button(mActivity);
        mBtnCamera.setText(gtxt(R.string.multimedia_editor_image_field_editing_photo));
        mBtnCamera.setOnClickListener(v -> {
            Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            File image;
            try {
                saveImageForRevert();

                // Create a new image for the camera result to land in, clear the URI
                image = createNewCacheFile();
                mImagePath = image.getPath();
                mImageUri = getUriForFile(image);
                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, mImageUri);

                // Until Android API21 (maybe 22) you must manually handle permissions for image capture w/FileProvider
                // This can be removed once minSDK is >= 22
                // https://medium.com/@quiro91/sharing-files-through-intents-part-2-fixing-the-permissions-before-lollipop-ceb9bb0eec3a
                if (CompatHelper.getSdkVersion() <= Build.VERSION_CODES.LOLLIPOP) {
                    cameraIntent.setClipData(ClipData.newRawUri("", mImageUri));
                    cameraIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }

                if (cameraIntent.resolveActivity(context.getPackageManager()) == null) {
                    Timber.w("Device has a camera, but no app to handle ACTION_IMAGE_CAPTURE Intent");
                    showSomethingWentWrong();
                    onActivityResult(ACTIVITY_TAKE_PICTURE, Activity.RESULT_CANCELED, null);
                    return;
                }
                try {
                    mActivity.startActivityForResultWithoutAnimation(cameraIntent, ACTIVITY_TAKE_PICTURE);
                } catch (Exception e) {
                    Timber.w(e, "Unable to take picture");
                    showSomethingWentWrong();
                    onActivityResult(ACTIVITY_TAKE_PICTURE, Activity.RESULT_CANCELED, null);
                }
            } catch (IOException e) {
                Timber.w(e, "mBtnCamera::onClickListener() unable to prepare file and launch camera");
            }
        });

        if (!Permissions.canUseCamera(context)) {
            mBtnCamera.setVisibility(View.INVISIBLE);
        }

        // Some hardware has no camera or reports yes but has zero (e.g., cheap devices, and Chromebook emulator)
        if ((!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA) &&
                !context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)) ||
                (CompatHelper.getCompat().getCameraCount() < 1)) {
            mBtnCamera.setVisibility(View.INVISIBLE);
        }

        setPreviewImage(mImagePath, getMaxImageSize());

        layout.addView(mImagePreview, ViewGroup.LayoutParams.MATCH_PARENT, p);
        layout.addView(mImageFileSize, ViewGroup.LayoutParams.MATCH_PARENT);
        layout.addView(mImageFileSizeWarning, ViewGroup.LayoutParams.MATCH_PARENT);
        layout.addView(mBtnGallery, ViewGroup.LayoutParams.MATCH_PARENT);
        layout.addView(mBtnCamera, ViewGroup.LayoutParams.MATCH_PARENT);
        layout.addView(mCropButton, ViewGroup.LayoutParams.MATCH_PARENT);
    }


    private void saveImageForRevert() {
        deletePreviousImage();
        mPreviousImagePath = mImagePath;
        mPreviousImageUri = mImageUri;
    }


    private void deletePreviousImage() {
        // Store the old image path for deletion / error handling if the user cancels
        if (mPreviousImagePath != null && !(new File(mPreviousImagePath).delete())) {
            Timber.i("deletePreviousImage() unable to delete previous image file");
        }
    }


    private File createNewCacheFile() throws IOException {
        return createNewCacheFile("jpg");
    }

    private File createNewCacheFile(@NonNull String extension) throws IOException {
        String timeStamp = TimeUtils.getTimestamp(mTime);
        File storageDir = new File(mAnkiCacheDirectory);
        return File.createTempFile("img_" + timeStamp, "." + extension, storageDir);
    }


    private void drawUIComponents(Context context) {
        mImagePreview = new ImageView(mActivity);

        DisplayMetrics metrics = getDisplayMetrics();

        int height = metrics.heightPixels;
        int width = metrics.widthPixels;


        mImagePreview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        mImagePreview.setAdjustViewBounds(true);

        mImagePreview.setMaxHeight((int) Math.round(height * 0.4));
        mImagePreview.setMaxWidth((int) Math.round(width * 0.6));

        mImageFileSize = new EditText(context);
        mImageFileSize.setMaxWidth((int) Math.round(width * 0.6));
        mImageFileSize.setEnabled(false);
        mImageFileSize.setGravity(Gravity.CENTER_HORIZONTAL);
        mImageFileSize.setBackground(null);
        mImageFileSize.setVisibility(View.GONE);

        //#5513 - Image compression failed, but we'll confuse most users if we tell them that. Instead, just imply that
        //there's an action that they can take.
        mImageFileSizeWarning = new EditText(context);
        mImageFileSizeWarning.setMaxWidth((int) Math.round(width * 0.6));
        mImageFileSizeWarning.setEnabled(false);
        mImageFileSizeWarning.setTextColor(Color.parseColor("#FF4500")); //Orange-Red
        mImageFileSizeWarning.setGravity(Gravity.CENTER_HORIZONTAL);
        mImageFileSizeWarning.setVisibility(View.GONE);
        mImageFileSize.setBackground(null);
        mImageFileSizeWarning.setText(R.string.multimedia_editor_image_compression_failed);
    }


    private String gtxt(int id) {
        return mActivity.getText(id).toString();
    }


    private DisplayMetrics getDisplayMetrics() {
        if (mMetrics == null) {
            mMetrics = new DisplayMetrics();
            mActivity.getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
        }
        return mMetrics;
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Timber.d("onActivityResult()");
        if (resultCode != Activity.RESULT_OK) {
            Timber.d("Activity was not successful");
            // Restore the old version of the image if the user cancelled
            switch (requestCode) {
                case ACTIVITY_TAKE_PICTURE:
                case ACTIVITY_CROP_PICTURE:
                    if (!TextUtils.isEmpty(mPreviousImagePath)) {
                        revertToPreviousImage();
                    }
                    break;
                default:
                    break;
            }

            // Some apps send this back with app-specific data, direct the user to another app
            if (resultCode >= Activity.RESULT_FIRST_USER) {
                UIUtils.showThemedToast(mActivity, mActivity.getString(R.string.activity_result_unexpected), true);
            }
            return;
        }

        mImageFileSizeWarning.setVisibility(View.GONE);
        if (requestCode == ACTIVITY_SELECT_IMAGE) {
            try {
                handleSelectImageIntent(data);
                mImageFileSizeWarning.setVisibility(View.GONE);
            } catch (Exception e) {
                AnkiDroidApp.sendExceptionReport(e, "BasicImageFieldController - handleSelectImageIntent");
                Timber.e(e, "Failed to select image");
                showSomethingWentWrong();
                return;
            }
        } else if (requestCode == ACTIVITY_TAKE_PICTURE) {
            handleTakePictureResult();
        } else if (requestCode == ACTIVITY_CROP_PICTURE) {
            handleCropResult();
        } else {
            Timber.w("Unhandled request code: %d", requestCode);
            return;
        }
        setPreviewImage(mImagePath, getMaxImageSize());
    }


    private void revertToPreviousImage() {
        if (mImagePath != null && new File(mImagePath).exists()) {
            if (!new File(mImagePath).delete()) {
                Timber.i("revertToPreviousImage() had existing image, but delete failed");
            }
        }
        mImagePath = mPreviousImagePath;
        mField.setImagePath(mImagePath);
        mImageUri = mPreviousImageUri;
        mPreviousImagePath = null;
        mPreviousImageUri = null;
    }


    private void showSomethingWentWrong() {
        UIUtils.showThemedToast(mActivity, mActivity.getResources().getString(R.string.multimedia_editor_something_wrong), false);
    }

    private void handleSelectImageIntent(Intent data) {
        if (data == null) {
            Timber.e("handleSelectImageIntent() no intent provided");
            showSomethingWentWrong();
            return;
        }

        Timber.i("handleSelectImageIntent() Intent: %s. extras: %s", data, data.getExtras() == null ? "null" : TextUtils.join(", ", data.getExtras().keySet()));
        Uri selectedImage = getImageUri(mActivity, data);

        if (selectedImage == null) {
            Timber.w("handleSelectImageIntent() selectedImage was null");
            showSomethingWentWrong();
            return;
        }

        File internalizedPick = internalizeUri(selectedImage);
        if (internalizedPick == null) {
            Timber.w("handleSelectImageIntent() unable to internalize image from Uri %s", selectedImage);
            showSomethingWentWrong();
            return;
        }

        mImagePath = internalizedPick.getAbsolutePath();
        mImageUri = getUriForFile(internalizedPick);
        mField.setHasTemporaryMedia(true);

        Timber.i("handleSelectImageIntent() Decoded image: '%s'", mImagePath);
        mField.setImagePath(mImagePath);
    }

    private @Nullable File internalizeUri(Uri uri) {
        File internalFile;
        Pair<String, String> uriFileInfo = getImageInfoFromUri(mActivity, uri);

        // Use the display name from the image info to create a new file with correct extension
        if (uriFileInfo.second == null) {
            Timber.w("internalizeUri() unable to get file name");
            showSomethingWentWrong();
            return null;
        }
        String uriFileExtension = uriFileInfo.second.substring(uriFileInfo.second.lastIndexOf('.') + 1);
        try {
            internalFile = createNewCacheFile(uriFileExtension);
        } catch (IOException e) {
            Timber.w(e, "internalizeUri() failed to create new file with extension %s", uriFileExtension);
            showSomethingWentWrong();
            return null;
        }

        // If we got a real file name, do a copy from it
        InputStream inputStream;
        if (uriFileInfo.first != null) {
            Timber.d("internalizeUri() got file path for direct copy from Uri %s", uri);
            try {
                inputStream = new FileInputStream(new File(uriFileInfo.first));
            } catch (FileNotFoundException e) {
                Timber.w(e, "internalizeUri() unable to open input stream on file %s", uriFileInfo.first);
                showSomethingWentWrong();
                return null;
            }
        } else {
            try {
                inputStream = mActivity.getContentResolver().openInputStream(uri);
            } catch (Exception e) {
                Timber.w(e, "internalizeUri() unable to open input stream from content resolver for Uri %s", uri);
                showSomethingWentWrong();
                return null;
            }
        }

        try {
            CompatHelper.getCompat().copyFile(inputStream, internalFile.getAbsolutePath());
        } catch (Exception e) {
            Timber.w(e, "internalizeUri() unable to internalize file from Uri %s to File %s", uri, internalFile.getAbsolutePath());
            showSomethingWentWrong();
            return null;
        }
        return internalFile;
    }


    @Override
    public void onFocusLost() {
        // do nothing

    }


    @Override
    public void onDone() {
        deletePreviousImage();
    }


    /**
     * Rotate and compress the image, with the side effect of current image being backed by a new file
     *
     * @return true if successful, false indicates the current image is likely not usable, revert if possible
     */
    private boolean rotateAndCompress() {
        Timber.d("rotateAndCompress() on %s", mImagePath);
        // Set the rotation of the camera image and save as png
        File f = new File(mImagePath);
        Timber.d("rotateAndCompress in path %s has size %d", f.getAbsolutePath(), f.length());
        // use same filename but with png extension for output file
        // Load into a bitmap with max size of 1920 pixels and rotate if necessary
        Bitmap b = BitmapUtil.decodeFile(f, IMAGE_SAVE_MAX_WIDTH);
        if (b == null) {
            //#5513 - if we can't decode a bitmap, leave the image alone
            //And display a warning to push users to compress manually.
            Timber.w("rotateAndCompress() unable to decode file %s", mImagePath);
            mImageFileSizeWarning.setVisibility(View.VISIBLE);
            return false;
        }

        FileOutputStream out = null;
        try {
            File outFile = createNewCacheFile();
            out = new FileOutputStream(outFile);
            b = ExifUtil.rotateFromCamera(f, b);
            b.compress(Bitmap.CompressFormat.JPEG, 90, out);
            if (!f.delete()) {
                Timber.w("rotateAndCompress() delete of pre-compressed image failed %s", mImagePath);
            }
            mImagePath = outFile.getAbsolutePath();
            mImageUri = getUriForFile(outFile);
            mField.setImagePath(mImagePath);
            Timber.d("rotateAndCompress out path %s has size %d", mImagePath, outFile.length());
        } catch (FileNotFoundException e) {
            Timber.w(e, "rotateAndCompress() File not found for image compression %s", mImagePath);
        } catch (IOException e) {
            Timber.w(e, "rotateAndCompress() create file failed for file %s", mImagePath);
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {
                Timber.w(e, "rotateAndCompress() Unable to clean up image compression output stream");
            }
        }
        return true;
    }


    private void setPreviewImage(String imagePath, int maxsize) {
        if (imagePath != null && !"".equals(imagePath)) {
            File f = new File(imagePath);
            setImagePreview(f, maxsize);
        }
    }

    @VisibleForTesting
    void setImagePreview(File f, int maxsize) {
        Bitmap b = BitmapUtil.decodeFile(f, maxsize);
        if (b == null) {
            Timber.i("setImagePreview() could not process image %s", f.getPath());
            return;
        }
        Timber.d("setPreviewImage path %s has size %d", f.getAbsolutePath(), f.length());
        b = ExifUtil.rotateFromCamera(f, b);
        mImagePreview.setImageBitmap(b);
        mImageFileSize.setVisibility(View.VISIBLE);
        mImageFileSize.setText(Formatter.formatFileSize(mActivity, f.length()));
        mCropButton.setVisibility(View.VISIBLE);
    }


    @Override
    public void onDestroy() {
        ImageView imageView = mImagePreview;
        BitmapUtil.freeImageView(imageView);
        mCropButton.setVisibility(View.INVISIBLE);
    }


    private void handleTakePictureResult() {
        Timber.d("handleTakePictureResult");
        if (!rotateAndCompress()) {
            Timber.i("handleTakePictureResult appears to have an invalid picture");
            revertToPreviousImage();
            showSomethingWentWrong();
            return;
        };
        mField.setHasTemporaryMedia(true);
        showCropDialog(mActivity.getString(R.string.crop_image), null);
    }


    /**
     * Invoke system crop function
     */
    private void requestCrop() {

        if (mImagePath == null || mImageUri == null) {
            Timber.w("requestCrop() but mImagePath or mImageUri is null");
            return;
        }
        Timber.d("photoCrop() with path/uri %s/%s", mImagePath, mImageUri);

        // Pre-create a file in our cache for the cropping application to put results in
        File image;
        try {
            image = createNewCacheFile();
        } catch (IOException e) {
            Timber.w(e, "requestCrop() unable to create new file to drop crop results into");
            showSomethingWentWrong();
            return;
        }

        saveImageForRevert();
        mImagePath = image.getPath();
        mImageUri = Uri.fromFile(image); // This must be the file URL it will not work with a content URI
        mField.setImagePath(mImagePath);
        mField.setHasTemporaryMedia(true);
        Timber.d("requestCrop()  destination image has path/uri %s/%s", mImagePath, mImageUri);

        // This is basically a "magic" recipe to get the system to crop, gleaned from StackOverflow etc
        // Intent intent = new Intent(Intent.ACTION_EDIT);  // edit (vs crop) would be even better, but it fails differently and needs lots of testing
        Intent intent = new Intent("com.android.camera.action.CROP");
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.setDataAndType(mPreviousImageUri, "image/*");
        intent.putExtra("return-data", false);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, mImageUri);
        intent.putExtra("outputFormat", Bitmap.CompressFormat.JPEG.toString()); // worked w/crop but not edit
        intent.putExtra("noFaceDetection", true); // no face detection
        try {
            mActivity.startActivityForResultWithoutAnimation(Intent.createChooser(intent, null), ACTIVITY_CROP_PICTURE);
        } catch (Exception e) {
            Timber.w(e, "requestCrop unable to start cropping activity for Uri %s", mPreviousImageUri);
            showSomethingWentWrong();
            onActivityResult(ACTIVITY_CROP_PICTURE, Activity.RESULT_CANCELED, null);
        }
    }


    public void showCropDialog(String content, @Nullable MaterialDialog.SingleButtonCallback negativeCallBack) {
        if (mImagePath == null || mImageUri == null) {
            Timber.w("showCropDialog called with null URI or Path");
            return;
        }

        MaterialDialog.Builder builder = new MaterialDialog.Builder(mActivity)
                .content(content)
                .positiveText(R.string.dialog_ok)
                .negativeText(R.string.dialog_no)
                .onPositive((dialog, which) -> requestCrop());

        if (negativeCallBack != null) {
            builder.onNegative(negativeCallBack);
        }

        builder.build().show();
    }


    private void handleCropResult() {
        Timber.d("handleCropResult");
        if (!rotateAndCompress()) {
            Timber.i("handleCropResult() appears to have an invalid file, reverting");
            revertToPreviousImage();
            showSomethingWentWrong();
            return;
        }
        Timber.d("handleCropResult() = image path now %s", mField.getImagePath());
        mField.setHasTemporaryMedia(true);
    }


    /**
     * Get Uri based on current image path
     *
     * @param file the file to get URI for
     * @return current image path's uri
     */
    private Uri getUriForFile(File file) {
        Timber.d("getUriForFile() %s", file);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                return FileProvider.getUriForFile(mActivity, mActivity.getApplicationContext().getPackageName() + ".apkgfileprovider", file);
            }
        } catch (Exception e) {
            // #6628 - What would cause this? Is the fallback is effective? Telemetry to diagnose more:
            Timber.w(e, "getUriForFile failed on %s - attempting fallback", file);
            AnkiDroidApp.sendExceptionReport(e, "BasicImageFieldController", "Unexpected getUriForFile failure on " + file, true);
        }

        return Uri.fromFile(file);
    }


    /**
     * Get image uri that adapts various model
     *
     * @return image uri
     */
    private @Nullable Uri getImageUri(Context context, Intent data) {
        Timber.d("getImageUri for data %s", data);
        Uri uri = data.getData();
        if (uri == null) {
            UIUtils.showThemedToast(context, context.getString(R.string.select_image_failed), false);
            return null;
        }
        mImageUri = uri;
        mImagePath = getImageInfoFromUri(context, uri).first;
        mField.setImagePath(mImagePath);
        mField.setHasTemporaryMedia(false);
        return mImageUri;
    }


    /**
     * Get image information based on uri and selection args
     *
     * @return Pair<String, String>: first file path (null if does not exist), second display name (null if does not exist)
     */
    private @NonNull Pair<String, String> getImageInfoFromUri(Context context, Uri uri) {
        Timber.d("getImagePathFromUri() URI: %s", uri);
        Pair<String, String> imageInfo = new Pair<>(null, null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && DocumentsContract.isDocumentUri(context, uri)) {
            String docId = DocumentsContract.getDocumentId(uri);
            if ("com.android.providers.media.documents".equals(uri.getAuthority())) {
                String id = docId.split(":")[1];
                String selection = MediaStore.Images.Media._ID + "=" + id;
                imageInfo = getImageInfoFromContentResolver(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, selection);
            } else if ("com.android.providers.downloads.documents".equals(uri.getAuthority())) {
                Uri contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.parseLong(docId));
                imageInfo = getImageInfoFromContentResolver(context, contentUri, null);
            }
        } else if ("content".equalsIgnoreCase(uri.getScheme())) {
            imageInfo = getImageInfoFromContentResolver(context, uri, null);
        } else if ("file".equalsIgnoreCase(uri.getScheme())) {
            if (uri.getPath() != null) {
                String[] pathParts = uri.getPath().split("/");
                imageInfo = new Pair<>(
                        uri.getPath(),
                        pathParts[pathParts.length - 1]
                );
            }
        }
        Timber.d("getImagePathFromUri() returning path/name %s/%s", imageInfo.first, imageInfo.second);
        return imageInfo;
    }

    /**
     * Get image information based on uri and selection args
     *
     * @return string[] 0: file path (null if does not exist), 1: display name (null if does not exist)
     */
    private @NonNull Pair<String, String> getImageInfoFromContentResolver(Context context, Uri uri, String selection) {
        Timber.d("getImagePathFromContentResolver() %s", uri);
        String[] filePathColumns = {
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.DISPLAY_NAME
        };
        Cursor cursor = ContentResolverCompat.query(context.getContentResolver(), uri, filePathColumns, selection, null, null, null);

        if (cursor == null) {
            Timber.w("getImageInfoFromContentResolver() cursor was null");
            showSomethingWentWrong();
            return new Pair<>(null, null);
        }

        if (!cursor.moveToFirst()) {
            //TODO: #5909, it would be best to instrument this to see if we can fix the failure
            Timber.w("getImageInfoFromContentResolver() cursor had no data");
            showSomethingWentWrong();
            return new Pair<>(null, null);
        }

        Pair<String, String> imageInfo = new Pair<>(
                cursor.getString(cursor.getColumnIndex(filePathColumns[0])),
                cursor.getString(cursor.getColumnIndex(filePathColumns[1]))
        );
        cursor.close();
        Timber.d("getImageInfoFromContentResolver() decoded image info path/name %s/%s", imageInfo.first, imageInfo.second);
        return imageInfo;
    }


    public boolean isShowingPreview() {
        return mImageFileSize.getVisibility() == View.VISIBLE;
    }
}
