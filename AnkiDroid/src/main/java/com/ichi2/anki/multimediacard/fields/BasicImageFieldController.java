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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;

import com.canhub.cropper.CropImage;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.DrawingActivity;
import com.ichi2.anki.R;
import com.ichi2.anki.UIUtils;
import com.ichi2.compat.CompatHelper;
import com.ichi2.ui.FixedEditText;
import com.ichi2.utils.BitmapUtil;
import com.ichi2.utils.ExifUtil;
import com.ichi2.utils.FileUtil;
import com.ichi2.utils.Permissions;

import com.afollestad.materialdialogs.MaterialDialog;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


import timber.log.Timber;

public class BasicImageFieldController extends FieldControllerBase implements IFieldController {

    @VisibleForTesting
    static final int ACTIVITY_SELECT_IMAGE = 1;
    private static final int ACTIVITY_TAKE_PICTURE = 2;
    private static final int ACTIVITY_CROP_PICTURE = 3;
    private static final int ACTIVITY_DRAWING = 4;
    private static final int IMAGE_SAVE_MAX_WIDTH = 1920;

    private ImageView mImagePreview;
    private TextView mImageFileSize;
    private TextView mImageFileSizeWarning;

    private ImageViewModel mViewModel = new ImageViewModel(null, null);
    private @Nullable String mPreviousImagePath; // save the latest path to prevent from cropping or taking photo action canceled
    private @Nullable Uri mPreviousImageUri;
    private @Nullable String mAnkiCacheDirectory; // system provided 'External Cache Dir' with "temp-photos" on it
                                                  // e.g.  '/self/primary/Android/data/com.ichi2.anki.AnkiDroid/cache/temp-photos'
    private DisplayMetrics mMetrics = null;

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
        mViewModel = ImageViewModel.fromBundle(savedInstanceState);
        mPreviousImagePath = savedInstanceState.getString("mPreviousImagePath");
        mPreviousImageUri = savedInstanceState.getParcelable("mPreviousImageUri");
    }

    @Override
    public Bundle saveInstanceState() {
        Timber.d("saveInstanceState");
        Bundle savedInstanceState = new Bundle();
        mViewModel.enrich(savedInstanceState);
        savedInstanceState.putString("mPreviousImagePath", mPreviousImagePath);
        savedInstanceState.putParcelable("mPreviousImageUri", mPreviousImageUri);
        return savedInstanceState;
    }

    @Override
    public void createUI(Context context, LinearLayout layout) {
        Timber.d("createUI()");
        mViewModel = mViewModel.replaceNullValues(mField, mActivity);

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
        mCropButton.setOnClickListener(v -> mViewModel = requestCrop(mViewModel));
        mCropButton.setVisibility(View.INVISIBLE);

        Button btnGallery = new Button(mActivity);
        btnGallery.setText(gtxt(R.string.multimedia_editor_image_field_editing_galery));
        btnGallery.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_PICK);
            i.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
            mActivity.startActivityForResultWithoutAnimation(i, ACTIVITY_SELECT_IMAGE);
        });


        Button btnDraw = new Button(mActivity);
        btnDraw.setText(gtxt(R.string.drawing));
        btnDraw.setOnClickListener(v -> {
            mActivity.startActivityForResultWithoutAnimation(new Intent(mActivity, DrawingActivity.class), ACTIVITY_DRAWING);
        });

        Button btnCamera = new Button(mActivity);
        btnCamera.setText(gtxt(R.string.multimedia_editor_image_field_editing_photo));
        btnCamera.setOnClickListener(v -> mViewModel = captureImage(context));

        if (!canUseCamera(context)) {
            btnCamera.setVisibility(View.INVISIBLE);
        }

        setPreviewImage(mViewModel.mImagePath, getMaxImageSize());

        layout.addView(mImagePreview, ViewGroup.LayoutParams.MATCH_PARENT, p);
        layout.addView(mImageFileSize, ViewGroup.LayoutParams.MATCH_PARENT);
        layout.addView(mImageFileSizeWarning, ViewGroup.LayoutParams.MATCH_PARENT);
        layout.addView(btnGallery, ViewGroup.LayoutParams.MATCH_PARENT);
        // drew image appear far larger in preview in devices API < 24 #9439
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            layout.addView(btnDraw, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        if (com.ichi2.utils.CheckCameraPermission.manifestContainsPermission(context)) {
            layout.addView(btnCamera, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        layout.addView(mCropButton, ViewGroup.LayoutParams.MATCH_PARENT);
    }


    @SuppressLint("UnsupportedChromeOsCameraSystemFeature")
    private boolean canUseCamera(Context context) {
        if (!Permissions.canUseCamera(context)) {
            return false;
        }

        PackageManager pm = context.getPackageManager();

        if ((!pm.hasSystemFeature(PackageManager.FEATURE_CAMERA) && !pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT))) {
            return false;
        }

        // Some hardware has no camera or reports yes but has zero (e.g., cheap devices, and Chromebook emulator)
        CameraManager cameraManager = (CameraManager)AnkiDroidApp.getInstance().getApplicationContext()
                .getSystemService(Context.CAMERA_SERVICE);
        try {
            if (cameraManager != null) {
                return cameraManager.getCameraIdList().length > 0;
            }
        } catch (CameraAccessException e) {
            Timber.e(e, "Unable to enumerate cameras");
        }
        return false;
    }


    private ImageViewModel captureImage(Context context) {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File image;
        ImageViewModel toReturn = mViewModel;
        try {
            saveImageForRevert();

            // Create a new image for the camera result to land in, clear the URI
            image = createNewCacheImageFile();
            Uri imageUri = getUriForFile(image);
            toReturn = new ImageViewModel(image.getPath(), imageUri);
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);

            // Until Android API22 you must manually handle permissions for image capture w/FileProvider
            // This can be removed once minSDK is >= 22
            // https://medium.com/@quiro91/sharing-files-through-intents-part-2-fixing-the-permissions-before-lollipop-ceb9bb0eec3a
            if (CompatHelper.getSdkVersion() < Build.VERSION_CODES.LOLLIPOP_MR1) {
                cameraIntent.setClipData(ClipData.newRawUri("", imageUri));
                cameraIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }

            if (cameraIntent.resolveActivity(context.getPackageManager()) == null) {
                Timber.w("Device has a camera, but no app to handle ACTION_IMAGE_CAPTURE Intent");
                showSomethingWentWrong();
                onActivityResult(ACTIVITY_TAKE_PICTURE, Activity.RESULT_CANCELED, null);
                return toReturn;
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
        return toReturn;

    }


    private void saveImageForRevert() {
        if (!mViewModel.isPreExistingImage) {
            deletePreviousImage();
        }
        mPreviousImagePath = mViewModel.mImagePath;
        mPreviousImageUri = mViewModel.mImageUri;
    }


    private void deletePreviousImage() {
        // Store the old image path for deletion / error handling if the user cancels
        if (mPreviousImagePath != null && !(new File(mPreviousImagePath).delete())) {
            Timber.i("deletePreviousImage() unable to delete previous image file");
        }
    }


    private File createNewCacheImageFile() throws IOException {
        return createNewCacheImageFile("jpg");
    }

    private File createNewCacheImageFile(@NonNull String extension) throws IOException {
        File storageDir = new File(mAnkiCacheDirectory);
        return File.createTempFile("img", "." + extension, storageDir);
    }


    private void drawUIComponents(Context context) {
        DisplayMetrics metrics = getDisplayMetrics();

        int height = metrics.heightPixels;
        int width = metrics.widthPixels;


        mImagePreview = new ImageView(mActivity);
        mImagePreview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        mImagePreview.setAdjustViewBounds(true);

        mImagePreview.setMaxHeight((int) Math.round(height * 0.4));
        mImagePreview.setMaxWidth((int) Math.round(width * 0.6));

        mImageFileSize = new FixedEditText(context);
        mImageFileSize.setMaxWidth((int) Math.round(width * 0.6));
        mImageFileSize.setEnabled(false);
        mImageFileSize.setGravity(Gravity.CENTER_HORIZONTAL);
        mImageFileSize.setBackground(null);
        mImageFileSize.setVisibility(View.GONE);

        //#5513 - Image compression failed, but we'll confuse most users if we tell them that. Instead, just imply that
        //there's an action that they can take.
        mImageFileSizeWarning = new FixedEditText(context);
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

    @SuppressWarnings("deprecation") // #9333: getDefaultDisplay & getMetrics
    private DisplayMetrics getDisplayMetrics() {
        if (mMetrics == null) {
            mMetrics = new DisplayMetrics();
            mActivity.getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
        }
        return mMetrics;
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        // All image modification methods come through here - this ensures that the state is consistent

        Timber.d("onActivityResult()");
        if (resultCode != Activity.RESULT_OK) {
            Timber.d("Activity was not successful");
            // Restore the old version of the image if the user cancelled
            switch (requestCode) {
                case ACTIVITY_TAKE_PICTURE:
                case ACTIVITY_CROP_PICTURE:
                case CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE:
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

            // cropImage can give us more information. Not sure it is actionable so for now just log it.
            if ((requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) && (resultCode == CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE)) {
                CropImage.ActivityResult result = CropImage.getActivityResult(data);
                if (result != null) {
                    String error = String.valueOf(result.getError());
                    Timber.w(error, "cropImage threw an error");
                    AnkiDroidApp.sendExceptionReport(error, "cropImage threw an error");;
                }
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
        } else if (requestCode == ACTIVITY_DRAWING) {
            // receive image from drawing activity
            Uri savedImagePath = (Uri) data.getExtras().get(DrawingActivity.EXTRA_RESULT_WHITEBOARD);;
            handleDrawingResult(savedImagePath);
        }

        else if ((requestCode == ACTIVITY_CROP_PICTURE) || (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE)) {
            CropImage.ActivityResult result = CropImage.getActivityResult(data);
            if (result != null) {
                handleCropResult(result);
            }
        } else {
            Timber.w("Unhandled request code: %d", requestCode);
            return;
        }
        setPreviewImage(mViewModel.mImagePath, getMaxImageSize());
    }


    private void revertToPreviousImage() {
        mViewModel.deleteImagePath();
        mViewModel = new ImageViewModel(mPreviousImagePath, mPreviousImageUri);
        mField.setImagePath(mPreviousImagePath);
        mPreviousImagePath = null;
        mPreviousImageUri = null;
    }


    private void showSomethingWentWrong() {
        UIUtils.showThemedToast(mActivity, mActivity.getResources().getString(R.string.multimedia_editor_something_wrong), false);
    }

    private void showSVGPreviewToast() {
        UIUtils.showThemedToast(mActivity, mActivity.getResources().getString(R.string.multimedia_editor_svg_preview), false);
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

        String imagePath = internalizedPick.getAbsolutePath();
        mViewModel = new ImageViewModel(imagePath, getUriForFile(internalizedPick));
        setTemporaryMedia(imagePath);

        Timber.i("handleSelectImageIntent() Decoded image: '%s'", imagePath);
    }


    private void handleDrawingResult(Uri imageUri) {
        if (imageUri == null) {
            Timber.w("handleDrawingResult() no image Uri provided");
            showSomethingWentWrong();
            return;
        }

        File internalizedPick = internalizeUri(imageUri);
        if (internalizedPick == null) {
            Timber.w("handleDrawingResult() unable to internalize image from Uri %s", imageUri);
            showSomethingWentWrong();
            return;
        }

        String drewImagePath = internalizedPick.getAbsolutePath();
        mViewModel = new ImageViewModel(drewImagePath, imageUri);
        setTemporaryMedia(drewImagePath);

        Timber.i("handleDrawingResult() Decoded image: '%s'", drewImagePath);
    }

    private @Nullable File internalizeUri(Uri uri) {
        File internalFile;
        String uriFileName = getImageNameFromUri(mActivity, uri);

        // Use the display name from the image info to create a new file with correct extension
        if (uriFileName == null) {
            Timber.w("internalizeUri() unable to get file name");
            showSomethingWentWrong();
            return null;
        }
        String uriFileExtension = uriFileName.substring(uriFileName.lastIndexOf('.') + 1);
        try {
            internalFile = createNewCacheImageFile(uriFileExtension);
        } catch (IOException e) {
            Timber.w(e, "internalizeUri() failed to create new file with extension %s", uriFileExtension);
            showSomethingWentWrong();
            return null;
        }
        try {
            File returnFile = FileUtil.internalizeUri(uri, internalFile, mActivity.getContentResolver());
            Timber.d("internalizeUri successful. Returning internalFile.");
            return returnFile;
        } catch (Exception e) {
            Timber.w(e);
            showSomethingWentWrong();
            return null;
        }
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
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean rotateAndCompress(String imagePath, ImageViewModel imageViewModel) {
        Timber.d("rotateAndCompress() on %s", imagePath);
        // Set the rotation of the camera image and save as png
        File f = new File(imagePath);
        Timber.d("rotateAndCompress in path %s has size %d", f.getAbsolutePath(), f.length());
        // use same filename but with png extension for output file
        // Load into a bitmap with max size of 1920 pixels and rotate if necessary
        Bitmap b = BitmapUtil.decodeFile(f, IMAGE_SAVE_MAX_WIDTH);
        if (b == null) {
            //#5513 - if we can't decode a bitmap, leave the image alone
            //And display a warning to push users to compress manually.
            Timber.w("rotateAndCompress() unable to decode file %s", imagePath);
            return false;
        }

        FileOutputStream out = null;
        try {
            File outFile = createNewCacheImageFile();
            out = new FileOutputStream(outFile);
            b = ExifUtil.rotateFromCamera(f, b);
            b.compress(Bitmap.CompressFormat.JPEG, 90, out);
            if (!f.delete()) {
                Timber.w("rotateAndCompress() delete of pre-compressed image failed %s", imagePath);
            }
            imagePath = outFile.getAbsolutePath();
            mViewModel = imageViewModel.rotateAndCompressTo(imagePath, getUriForFile(outFile));
            mField.setImagePath(imagePath);
            Timber.d("rotateAndCompress out path %s has size %d", imagePath, outFile.length());
        } catch (FileNotFoundException e) {
            Timber.w(e, "rotateAndCompress() File not found for image compression %s", imagePath);
        } catch (IOException e) {
            Timber.w(e, "rotateAndCompress() create file failed for file %s", imagePath);
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


    private void setPreviewImage(@Nullable String imagePath, int maxsize) {
        if (imagePath != null && !"".equals(imagePath)) {
            File f = new File(imagePath);
            setImagePreview(f, maxsize);
        }
    }

    @VisibleForTesting
    void setImagePreview(File f, int maxsize) {
        String imgPath = f.toString();
        if (imgPath.endsWith(".svg")) {
            showSVGPreviewToast();
            hideImagePreview();
        } else {
            Bitmap b = BitmapUtil.decodeFile(f, maxsize);
            if (b == null) {
                Timber.i("setImagePreview() could not process image %s", f.getPath());
                return;
            }
            Timber.d("setPreviewImage path %s has size %d", f.getAbsolutePath(), f.length());
            b = ExifUtil.rotateFromCamera(f, b);
            onValidImage(b, Formatter.formatFileSize(mActivity, f.length()));
        }
    }


    private void onValidImage(Bitmap b, String fileSize) {
        mImagePreview.setImageBitmap(b);
        mImageFileSize.setVisibility(View.VISIBLE);
        mImageFileSize.setText(fileSize);
        mCropButton.setVisibility(View.VISIBLE);
    }

    // ensure the previous preview is not visible
    public void hideImagePreview() {
        ImageView imageView = mImagePreview;
        BitmapUtil.freeImageView(imageView);
        mCropButton.setVisibility(View.INVISIBLE);
        mImageFileSize.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onDestroy() {
        hideImagePreview();
    }


    private void handleTakePictureResult() {
        Timber.d("handleTakePictureResult");
        if (!rotateAndCompress()) {
            Timber.i("handleTakePictureResult appears to have an invalid picture");
            return;
        }
        showCropDialog(mActivity.getString(R.string.crop_image), null);
    }


    /**
     * Invoke system crop function
     */
    private ImageViewModel requestCrop(ImageViewModel viewModel) {
        ImageViewModel ret = viewModel;
        if (!ret.isValid()) {
            Timber.w("requestCrop() but mImagePath or mImageUri is null");
            return ret;
        }
        Timber.d("photoCrop() with path/uri %s/%s", ret.mImagePath, ret.mImageUri);

        // Pre-create a file in our cache for the cropping application to put results in
        File image;
        try {
            image = createNewCacheImageFile();
        } catch (IOException e) {
            Timber.w(e, "requestCrop() unable to create new file to drop crop results into");
            showSomethingWentWrong();
            return ret;
        }

        saveImageForRevert();
        // This must be the file URL it will not work with a content URI
        String imagePath = image.getPath();
        Uri imageUri = Uri.fromFile(image);
        ret = viewModel.beforeCrop(imagePath, imageUri);
        setTemporaryMedia(imagePath);
        Timber.d("requestCrop()  destination image has path/uri %s/%s", ret.mImagePath, ret.mImageUri);

        CropImage.activity(viewModel.mImageUri).start(mActivity);
        return ret;
    }


    private void setTemporaryMedia(String imagePath) {
        mField.setImagePath(imagePath);
        mField.setHasTemporaryMedia(true);
    }


    public void showCropDialog(String content, @Nullable MaterialDialog.SingleButtonCallback negativeCallBack) {
        if (!mViewModel.isValid()) {
            Timber.w("showCropDialog called with null URI or Path");
            return;
        }

        MaterialDialog.Builder builder = new MaterialDialog.Builder(mActivity)
                .content(content)
                .positiveText(R.string.dialog_ok)
                .negativeText(R.string.dialog_no)
                .onPositive((dialog, which) -> mViewModel = requestCrop(mViewModel));

        if (negativeCallBack != null) {
            builder.onNegative(negativeCallBack);
        }

        builder.build().show();
    }


    private void handleCropResult(CropImage.ActivityResult result) {
        Timber.d("handleCropResult");
        mViewModel.deleteImagePath();
        mViewModel = new ImageViewModel(result.getUriFilePath(mActivity, true), result.getUriContent());
        if (!rotateAndCompress()) {
            Timber.i("handleCropResult() appears to have an invalid file, reverting");
            return;
        }
        Timber.d("handleCropResult() = image path now %s", mField.getImagePath());
    }


    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean rotateAndCompress() {
        if (!rotateAndCompress(mViewModel.mImagePath, mViewModel)) {
            mImageFileSizeWarning.setVisibility(View.VISIBLE);
            revertToPreviousImage();
            showSomethingWentWrong();
            return false;
        }
        mField.setHasTemporaryMedia(true);
        return true;
    }


    private Uri getUriForFile(File file) {
        return getUriForFile(file, mActivity);
    }

    /**
     * Get Uri based on current image path
     *
     * @param file the file to get URI for
     * @return current image path's uri
     */
    private static Uri getUriForFile(File file, Context activity) {
        Timber.d("getUriForFile() %s", file);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                return FileProvider.getUriForFile(activity, activity.getApplicationContext().getPackageName() + ".apkgfileprovider", file);
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
        }
        return uri;
    }


    /**
     * Get image name based on uri and selection args
     *
     * @return Display name of file identified by uri (null if does not exist)
     */
    private String getImageNameFromUri(Context context, Uri uri) {
        Timber.d("getImageNameFromUri() URI: %s", uri);
        String imageName = null;
        if (DocumentsContract.isDocumentUri(context, uri)) {
            String docId = DocumentsContract.getDocumentId(uri);
            if ("com.android.providers.media.documents".equals(uri.getAuthority())) {
                String id = docId.split(":")[1];
                String selection = MediaStore.Images.Media._ID + "=" + id;
                imageName = getImageNameFromContentResolver(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, selection);
            } else if ("com.android.providers.downloads.documents".equals(uri.getAuthority())) {
                Uri contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.parseLong(docId));
                imageName = getImageNameFromContentResolver(context, contentUri, null);
            }
        } else if ("content".equalsIgnoreCase(uri.getScheme())) {
            imageName = getImageNameFromContentResolver(context, uri, null);
        } else if ("file".equalsIgnoreCase(uri.getScheme())) {
            if (uri.getPath() != null) {
                String[] pathParts = uri.getPath().split("/");
                imageName = pathParts[pathParts.length - 1];
            }
        }
        Timber.d("getImageNameFromUri() returning name %s", imageName);
        return imageName;
    }

    /**
     * Get image name based on uri and selection args
     *
     * @return Display name of file identified by uri (null if does not exist)
     */
    private String getImageNameFromContentResolver(Context context, Uri uri, String selection) {
        Timber.d("getImageNameFromContentResolver() %s", uri);
        String[] filePathColumns = { MediaStore.MediaColumns.DISPLAY_NAME };
        Cursor cursor = ContentResolverCompat.query(context.getContentResolver(), uri, filePathColumns, selection, null, null, null);

        if (cursor == null) {
            Timber.w("getImageNameFromContentResolver() cursor was null");
            showSomethingWentWrong();
            return null;
        }

        if (!cursor.moveToFirst()) {
            //TODO: #5909, it would be best to instrument this to see if we can fix the failure
            Timber.w("getImageNameFromContentResolver() cursor had no data");
            showSomethingWentWrong();
            return null;
        }

        String imageName = cursor.getString(cursor.getColumnIndex(filePathColumns[0]));
        cursor.close();
        Timber.d("getImageNameFromContentResolver() decoded image name %s", imageName);
        return imageName;
    }


    public boolean isShowingPreview() {
        return mImageFileSize.getVisibility() == View.VISIBLE;
    }

    private static class ImageViewModel {
        public final @Nullable String mImagePath;
        public final @Nullable Uri mImageUri;
        public boolean isPreExistingImage = false;

        private ImageViewModel(@Nullable String imagePath, @Nullable Uri imageUri) {
            this.mImagePath = imagePath;
            this.mImageUri = imageUri;
        }


        public static ImageViewModel fromBundle(Bundle savedInstanceState) {
            String imagePath = savedInstanceState.getString("mImagePath");
            Uri imageUri = savedInstanceState.getParcelable("mImageUri");
            return new ImageViewModel(imagePath, imageUri);
        }

        public void enrich(Bundle savedInstanceState) {
            savedInstanceState.putString("mImagePath", mImagePath);
            savedInstanceState.putParcelable("mImageUri", mImageUri);
        }

        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        public boolean isValid() {
            return mImagePath != null && mImageUri != null;
        }


        public ImageViewModel replaceNullValues(IField field, Context context) {
            String newImagePath = mImagePath;
            Uri newImageUri = mImageUri;
            if (newImagePath == null) {
                newImagePath = field.getImagePath();
            }
            if (newImageUri == null && newImagePath != null) {
                newImageUri = getUriForFile(new File(newImagePath), context);
            }
            ImageViewModel ivm = new ImageViewModel(newImagePath, newImageUri);
            ivm.isPreExistingImage = true;
            return ivm;
        }


        public void deleteImagePath() {
            if (mImagePath != null && new File(mImagePath).exists()) {
                if (!new File(mImagePath).delete()) {
                    Timber.i("deleteImagePath() had existing image, but delete failed");
                } else {
                    Timber.d("deleteImagePath() deleted %s", mImagePath);
                }
            }
        }


        public ImageViewModel beforeCrop(String imagePath, Uri imageUri) {
            return new ImageViewModel(imagePath, imageUri);
        }


        public ImageViewModel rotateAndCompressTo(String imagePath, Uri uri) {
            return new ImageViewModel(imagePath, uri);
        }
    }
}
