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
package com.ichi2.anki.multimediacard.fields

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.text.format.Formatter
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContentResolverCompat
import androidx.core.content.FileProvider
import androidx.core.content.getSystemService
import androidx.core.os.BundleCompat
import com.canhub.cropper.*
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CrashReportService
import com.ichi2.anki.DrawingActivity
import com.ichi2.anki.R
import com.ichi2.anki.UIUtils
import com.ichi2.anki.multimediacard.activity.MultimediaEditFieldActivity
import com.ichi2.annotations.NeedsTest
import com.ichi2.ui.FixedEditText
import com.ichi2.utils.*
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.min
import kotlin.math.roundToLong

class BasicImageFieldController : FieldControllerBase(), IFieldController {
    @KotlinCleanup("lateinit wherever possible")
    private lateinit var mImagePreview: ImageView
    private lateinit var mImageFileSize: TextView
    private lateinit var mImageFileSizeWarning: TextView
    private var mViewModel = ImageViewModel(null, null)
    private var mPreviousImagePath: String? = null // save the latest path to prevent from cropping or taking photo action canceled
    private var mPreviousImageUri: Uri? = null
    private var mAnkiCacheDirectory: String? = null // system provided 'External Cache Dir' with "temp-photos" on it
    // e.g.  '/self/primary/Android/data/com.ichi2.anki.AnkiDroid/cache/temp-photos'

    private lateinit var mCropButton: Button
    private val maxImageSize: Int
        get() {
            val metrics = displayMetrics

            val height = metrics.heightPixels
            val width = metrics.widthPixels

            return min(height * 0.4, width * 0.6).toInt()
        }
    private lateinit var cropImageRequest: ActivityResultLauncher<CropImageContractOptions>

    @VisibleForTesting
    lateinit var registryToUse: ActivityResultRegistry

    private lateinit var takePictureLauncher: ActivityResultLauncher<Intent?>

    private inner class BasicImageFieldControllerResultCallback(
        private val onSuccess: (result: ActivityResult) -> Unit,
        private val onFailure: (result: ActivityResult) -> Unit = {}
    ) : ActivityResultCallback<ActivityResult> {
        override fun onActivityResult(result: ActivityResult) {
            if (result.resultCode != Activity.RESULT_OK) {
                Timber.d("Activity was not successful")

                onFailure(result)

                // Some apps send this back with app-specific data, direct the user to another app
                if (result.resultCode >= Activity.RESULT_FIRST_USER) {
                    UIUtils.showThemedToast(mActivity, mActivity.getString(R.string.activity_result_unexpected), true)
                }
                return
            }

            mImageFileSizeWarning.visibility = View.GONE
            onSuccess(result)
            setPreviewImage(mViewModel.imagePath, maxImageSize)
        }
    }

    override fun loadInstanceState(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            Timber.i("loadInstanceState but null so nothing to load")
            return
        }

        Timber.i("loadInstanceState loading saved state...")
        mViewModel = ImageViewModel.fromBundle(savedInstanceState)
        mPreviousImagePath = savedInstanceState.getString("mPreviousImagePath")
        mPreviousImageUri = BundleCompat.getParcelable(savedInstanceState, "mPreviousImageUri", Uri::class.java)
    }

    override fun saveInstanceState(): Bundle {
        Timber.d("saveInstanceState")
        val savedInstanceState = Bundle()
        mViewModel.enrich(savedInstanceState)
        savedInstanceState.putString("mPreviousImagePath", mPreviousImagePath)
        savedInstanceState.putParcelable("mPreviousImageUri", mPreviousImageUri)
        return savedInstanceState
    }

    override fun createUI(context: Context, layout: LinearLayout) {
        Timber.d("createUI()")
        mViewModel = mViewModel.replaceNullValues(mField, mActivity)

        mImagePreview = ImageView(mActivity)
        val externalCacheDirRoot = context.externalCacheDir
        if (externalCacheDirRoot == null) {
            Timber.e("createUI() unable to get external cache directory")
            showSomethingWentWrong()
            return
        }
        val externalCacheDir = File(externalCacheDirRoot.absolutePath + "/temp-photos")
        if (!externalCacheDir.exists() && !externalCacheDir.mkdir()) {
            Timber.e("createUI() externalCacheDir did not exist and could not be created")
            showSomethingWentWrong()
            return
        }
        mAnkiCacheDirectory = externalCacheDir.absolutePath

        val p = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        drawUIComponents(context)

        mCropButton = Button(mActivity).apply {
            text = gtxt(R.string.crop_button)
            setOnClickListener { mViewModel = requestCrop(mViewModel) }
            visibility = View.INVISIBLE
        }

        val btnGallery = Button(mActivity).apply {
            text = gtxt(R.string.multimedia_editor_image_field_editing_galery)
            setOnClickListener {
                val i = Intent(Intent.ACTION_PICK)
                i.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
                mActivity.startActivityForResultWithoutAnimation(i, ACTIVITY_SELECT_IMAGE)
            }
        }

        val btnDraw = Button(mActivity).apply {
            text = gtxt(R.string.drawing)
            setOnClickListener {
                mActivity.startActivityForResultWithoutAnimation(Intent(mActivity, DrawingActivity::class.java), ACTIVITY_DRAWING)
            }
        }

        val btnCamera = Button(mActivity).apply {
            text = gtxt(R.string.multimedia_editor_image_field_editing_photo)
            setOnClickListener { mViewModel = captureImage(context) }
            if (!canUseCamera(context)) {
                visibility = View.INVISIBLE
            }
        }

        setPreviewImage(mViewModel.imagePath, maxImageSize)

        layout.addView(mImagePreview, ViewGroup.LayoutParams.MATCH_PARENT, p)
        layout.addView(mImageFileSize, ViewGroup.LayoutParams.MATCH_PARENT)
        layout.addView(mImageFileSizeWarning, ViewGroup.LayoutParams.MATCH_PARENT)
        layout.addView(btnGallery, ViewGroup.LayoutParams.MATCH_PARENT)
        // drew image appear far larger in preview in devices API < 24 #9439
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            layout.addView(btnDraw, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        if (canUseCamera(context)) {
            layout.addView(btnCamera, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        layout.addView(mCropButton, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun setEditingActivity(activity: MultimediaEditFieldActivity) {
        super.setEditingActivity(activity)
        val registryToUse = if (this::registryToUse.isInitialized) registryToUse else mActivity.activityResultRegistry
        @NeedsTest("check the happy/failure path for the crop action")
        cropImageRequest = registryToUse.register(CROP_IMAGE_LAUNCHER_KEY, CropImageContract()) { cropResult ->
            if (cropResult.isSuccessful) {
                mImageFileSizeWarning.visibility = View.GONE
                if (cropResult != null) {
                    handleCropResult(cropResult)
                }
                setPreviewImage(mViewModel.imagePath, maxImageSize)
            } else {
                if (!mPreviousImagePath.isNullOrEmpty()) {
                    revertToPreviousImage()
                }
                // cropImage can give us more information. Not sure it is actionable so for now just log it.
                val error: String = cropResult.error?.toString() ?: "Error info not available"
                Timber.w(error, "cropImage threw an error")
                // condition can be removed if #12768 get fixed by Canhub
                if (cropResult.error is CropException.Cancellation) {
                    Timber.i("CropException caught, seemingly nothing to do ", error)
                } else {
                    CrashReportService.sendExceptionReport(error, "cropImage threw an error")
                }
            }
        }

        takePictureLauncher = registryToUse.register(
            TAKE_PICTURE_LAUNCHER_KEY,
            ActivityResultContracts.StartActivityForResult(),
            BasicImageFieldControllerResultCallback(
                onSuccess = {
                    handleTakePictureResult()
                },
                onFailure = {
                    cancelImageCapture()
                }
            )
        )
    }

    @SuppressLint("UnsupportedChromeOsCameraSystemFeature")
    private fun canUseCamera(context: Context): Boolean {
        val pm = context.packageManager
        if (!pm.hasSystemFeature(PackageManager.FEATURE_CAMERA) && !pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)) {
            return false
        }

        // Some hardware has no camera or reports yes but has zero (e.g., cheap devices, and Chromebook emulator)
        val cameraManager = AnkiDroidApp.instance.applicationContext.getSystemService<CameraManager>()
        return try {
            cameraManager?.cameraIdList?.isNotEmpty() ?: false
        } catch (e: CameraAccessException) {
            Timber.e(e, "Unable to enumerate cameras")
            false
        }
    }

    private fun captureImage(context: Context): ImageViewModel {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val image: File
        var toReturn = mViewModel
        try {
            saveImageForRevert()

            // Create a new image for the camera result to land in, clear the URI
            image = createNewCacheImageFile()
            val imageUri = getUriForFile(image)
            toReturn = ImageViewModel(image.path, imageUri)
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)

            if (cameraIntent.resolveActivity(context.packageManager) == null) {
                Timber.w("Device has a camera, but no app to handle ACTION_IMAGE_CAPTURE Intent")
                showSomethingWentWrong()
                cancelImageCapture()
                return toReturn
            }
            mActivity.launchActivityForResultWithAnimation(cameraIntent, takePictureLauncher, ActivityTransitionAnimation.Direction.NONE)
        } catch (e: IOException) {
            Timber.w(e, "mBtnCamera::onClickListener() unable to prepare file and launch camera")
        }
        return toReturn
    }

    private fun saveImageForRevert() {
        if (!mViewModel.isPreExistingImage) {
            deletePreviousImage()
        }
        mPreviousImagePath = mViewModel.imagePath
        mPreviousImageUri = mViewModel.imageUri
    }

    private fun deletePreviousImage() {
        // Store the old image path for deletion / error handling if the user cancels
        if (mPreviousImagePath != null && !File(mPreviousImagePath!!).delete()) {
            Timber.i("deletePreviousImage() unable to delete previous image file")
        }
    }

    @Throws(IOException::class)
    private fun createNewCacheImageFile(extension: String = "jpg"): File {
        val storageDir = File(mAnkiCacheDirectory!!)
        return File.createTempFile("img", ".$extension", storageDir)
    }

    @Throws(IOException::class)
    private fun createCachedFile(filename: String) = File(mAnkiCacheDirectory, filename).apply {
        deleteOnExit()
    }

    private fun drawUIComponents(context: Context) {
        val metrics = displayMetrics

        val height = metrics.heightPixels
        val width = metrics.widthPixels

        mImagePreview = ImageView(mActivity).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            adjustViewBounds = true

            maxHeight = (height * 0.4).roundToLong().toInt()
            maxWidth = (width * 0.6).roundToLong().toInt()
        }

        mImageFileSize = FixedEditText(context).apply {
            maxWidth = (width * 0.6).roundToLong().toInt()
            isEnabled = false
            gravity = Gravity.CENTER_HORIZONTAL
            background = null
            visibility = View.GONE
        }

        // #5513 - Image compression failed, but we'll confuse most users if we tell them that. Instead, just imply that
        // there's an action that they can take.
        mImageFileSizeWarning = FixedEditText(context).apply {
            maxWidth = (width * 0.6).roundToLong().toInt()
            isEnabled = false
            setTextColor(Color.parseColor("#FF4500")) // Orange-Red
            gravity = Gravity.CENTER_HORIZONTAL
            visibility = View.GONE
            setText(R.string.multimedia_editor_image_compression_failed)
        }
    }

    private fun gtxt(id: Int): String {
        return mActivity.getText(id).toString()
    }

    // #9333: getDefaultDisplay & getMetrics
    @Suppress("deprecation") // defaultDisplay
    private val displayMetrics: DisplayMetrics by lazy {
        DisplayMetrics().apply {
            mActivity.windowManager.defaultDisplay.getMetrics(this)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        // All image modification methods come through here - this ensures that the state is consistent

        Timber.d("onActivityResult()")
        if (resultCode != Activity.RESULT_OK) {
            Timber.d("Activity was not successful")

            // Some apps send this back with app-specific data, direct the user to another app
            if (resultCode >= Activity.RESULT_FIRST_USER) {
                UIUtils.showThemedToast(mActivity, mActivity.getString(R.string.activity_result_unexpected), true)
            }
            return
        }

        mImageFileSizeWarning.visibility = View.GONE
        when (requestCode) {
            ACTIVITY_SELECT_IMAGE -> {
                try {
                    handleSelectImageIntent(data)
                    mImageFileSizeWarning.visibility = View.GONE
                } catch (e: Exception) {
                    CrashReportService.sendExceptionReport(e, "BasicImageFieldController - handleSelectImageIntent")
                    Timber.e(e, "Failed to select image")
                    showSomethingWentWrong()
                    return
                }
            }
            ACTIVITY_DRAWING -> {
                // receive image from drawing activity
                val savedImagePath = BundleCompat.getParcelable(
                    data!!.extras!!,
                    DrawingActivity.EXTRA_RESULT_WHITEBOARD,
                    Uri::class.java
                )
                handleDrawingResult(savedImagePath)
            }
            else -> {
                Timber.w("Unhandled request code: %d", requestCode)
                return
            }
        }
        setPreviewImage(mViewModel.imagePath, maxImageSize)
    }

    private fun cancelImageCapture() {
        if (!mPreviousImagePath.isNullOrEmpty()) {
            revertToPreviousImage()
        }
    }

    private fun revertToPreviousImage() {
        mViewModel.deleteImagePath()
        mViewModel = ImageViewModel(mPreviousImagePath, mPreviousImageUri)
        mField.imagePath = mPreviousImagePath
        mPreviousImagePath = null
        mPreviousImageUri = null
    }

    private fun showSomethingWentWrong() {
        try {
            UIUtils.showThemedToast(mActivity, mActivity.resources.getString(R.string.multimedia_editor_something_wrong), false)
        } catch (e: Exception) {
            // ignore. A NullPointerException may occur in Robolectric
            Timber.w(e, "Failed to display toast")
        }
    }

    private fun showSVGPreviewToast() {
        UIUtils.showThemedToast(mActivity, mActivity.resources.getString(R.string.multimedia_editor_svg_preview), false)
    }

    private fun handleSelectImageIntent(data: Intent?) {
        if (data == null) {
            Timber.e("handleSelectImageIntent() no intent provided")
            showSomethingWentWrong()
            return
        }
        @KotlinCleanup("Make this non-inline")
        Timber.i(
            "handleSelectImageIntent() Intent: %s. extras: %s",
            data,
            if (data.extras == null) "null" else data.extras!!.keySet().joinToString(", ")
        )

        val selectedImage = getImageUri(mActivity, data)
        if (selectedImage == null) {
            Timber.w("handleSelectImageIntent() selectedImage was null")
            showSomethingWentWrong()
            return
        }

        val internalizedPick = internalizeUri(selectedImage)
        if (internalizedPick == null) {
            Timber.w("handleSelectImageIntent() unable to internalize image from Uri %s", selectedImage)
            showSomethingWentWrong()
            return
        }

        val imagePath = internalizedPick.absolutePath
        mViewModel = ImageViewModel(imagePath, getUriForFile(internalizedPick))
        setTemporaryMedia(imagePath)

        Timber.i("handleSelectImageIntent() Decoded image: '%s'", imagePath)
    }

    private fun handleDrawingResult(imageUri: Uri?) {
        if (imageUri == null) {
            Timber.w("handleDrawingResult() no image Uri provided")
            showSomethingWentWrong()
            return
        }

        val internalizedPick = internalizeUri(imageUri)
        if (internalizedPick == null) {
            Timber.w("handleDrawingResult() unable to internalize image from Uri %s", imageUri)
            showSomethingWentWrong()
            return
        }

        val drewImagePath = internalizedPick.absolutePath
        mViewModel = ImageViewModel(drewImagePath, imageUri)
        setTemporaryMedia(drewImagePath)

        Timber.i("handleDrawingResult() Decoded image: '%s'", drewImagePath)
    }

    private fun internalizeUri(uri: Uri): File? {
        val internalFile: File
        val uriFileName = getImageNameFromUri(mActivity, uri)

        // Use the display name from the image info to create a new file with correct extension
        if (uriFileName == null) {
            Timber.w("internalizeUri() unable to get file name")
            showSomethingWentWrong()
            return null
        }
        internalFile = try {
            createCachedFile(uriFileName)
        } catch (e: IOException) {
            Timber.w(e, "internalizeUri() failed to create new file with name %s", uriFileName)
            showSomethingWentWrong()
            return null
        }
        return try {
            val returnFile = FileUtil.internalizeUri(uri, internalFile, mActivity.contentResolver)
            Timber.d("internalizeUri successful. Returning internalFile.")
            returnFile
        } catch (e: Exception) {
            Timber.w(e)
            showSomethingWentWrong()
            null
        }
    }

    override fun onFocusLost() {
        // do nothing
    }

    override fun onDone() {
        deletePreviousImage()
        if (::cropImageRequest.isInitialized) {
            cropImageRequest.unregister()
        }
        if (::takePictureLauncher.isInitialized) {
            takePictureLauncher.unregister()
        }
    }

    /**
     * Rotate and compress the image, with the side effect of current image being backed by a new file
     *
     * @return true if successful, false indicates the current image is likely not usable, revert if possible
     */
    private fun rotateAndCompress(imagePath: String, imageViewModel: ImageViewModel): Boolean {
        Timber.d("rotateAndCompress() on %s", imagePath)
        // Set the rotation of the camera image and save as png
        val f = File(imagePath)
        Timber.d("rotateAndCompress in path %s has size %d", f.absolutePath, f.length())
        // use same filename but with png extension for output file
        // Load into a bitmap with max size of 1920 pixels and rotate if necessary
        var b = BitmapUtil.decodeFile(f, IMAGE_SAVE_MAX_WIDTH)
        if (b == null) {
            // #5513 - if we can't decode a bitmap, leave the image alone
            // And display a warning to push users to compress manually.
            Timber.w("rotateAndCompress() unable to decode file %s", imagePath)
            return false
        }

        var out: FileOutputStream? = null
        try {
            val outFile = createNewCacheImageFile()
            out = FileOutputStream(outFile)
            b = ExifUtil.rotateFromCamera(f, b)
            b.compress(Bitmap.CompressFormat.JPEG, 90, out)
            if (!f.delete()) {
                Timber.w("rotateAndCompress() delete of pre-compressed image failed %s", imagePath)
            }
            mViewModel = imageViewModel.rotateAndCompressTo(outFile.absolutePath, getUriForFile(outFile))
            mField.imagePath = outFile.absolutePath
            Timber.d("rotateAndCompress out path %s has size %d", outFile.absolutePath, outFile.length())
        } catch (e: FileNotFoundException) {
            Timber.w(e, "rotateAndCompress() File not found for image compression %s", imagePath)
        } catch (e: IOException) {
            Timber.w(e, "rotateAndCompress() create file failed for file %s", imagePath)
        } finally {
            try {
                out?.close()
            } catch (e: IOException) {
                Timber.w(e, "rotateAndCompress() Unable to clean up image compression output stream")
            }
        }
        return true
    }

    private fun setPreviewImage(imagePath: String?, maxsize: Int) {
        if (imagePath.isNullOrEmpty()) return

        val f = File(imagePath)
        setImagePreview(f, maxsize)
    }

    @VisibleForTesting
    fun setImagePreview(f: File, maxsize: Int) {
        val imgPath = f.toString()
        if (imgPath.endsWith(".svg")) {
            showSVGPreviewToast()
            hideImagePreview()
        } else {
            var b = BitmapUtil.decodeFile(f, maxsize)
            if (b == null) {
                Timber.i("setImagePreview() could not process image %s", f.path)
                return
            }
            Timber.d("setPreviewImage path %s has size %d", f.absolutePath, f.length())
            b = ExifUtil.rotateFromCamera(f, b)
            onValidImage(b, Formatter.formatFileSize(mActivity, f.length()))
        }
    }

    private fun onValidImage(b: Bitmap, fileSize: String) {
        mImagePreview.setImageBitmap(b)
        mImageFileSize.visibility = View.VISIBLE
        mImageFileSize.text = fileSize
        mCropButton.visibility = View.VISIBLE
    }

    // ensure the previous preview is not visible
    private fun hideImagePreview() {
        BitmapUtil.freeImageView(mImagePreview)
        if (::mCropButton.isInitialized) mCropButton.visibility = View.INVISIBLE
        if (::mImageFileSize.isInitialized) mImageFileSize.visibility = View.INVISIBLE
    }

    override fun onDestroy() {
        hideImagePreview()
    }

    private fun handleTakePictureResult() {
        Timber.d("handleTakePictureResult")
        if (!rotateAndCompress()) {
            Timber.i("handleTakePictureResult appears to have an invalid picture")
            return
        }
        showCropDialog(mActivity.getString(R.string.crop_image), null)
    }

    /**
     * Invoke system crop function
     */
    private fun requestCrop(viewModel: ImageViewModel): ImageViewModel {
        var ret = viewModel
        if (!ret.isValid) {
            Timber.w("requestCrop() but mImagePath or mImageUri is null")
            return ret
        }
        Timber.d("photoCrop() with path/uri %s/%s", ret.imagePath, ret.imageUri)

        // Pre-create a file in our cache for the cropping application to put results in
        val image: File = try {
            createNewCacheImageFile()
        } catch (e: IOException) {
            Timber.w(e, "requestCrop() unable to create new file to drop crop results into")
            showSomethingWentWrong()
            return ret
        }

        saveImageForRevert()
        // This must be the file URL it will not work with a content URI
        val imagePath = image.path
        val imageUri = Uri.fromFile(image)
        ret = viewModel.beforeCrop(imagePath, imageUri)
        setTemporaryMedia(imagePath)
        Timber.d("requestCrop()  destination image has path/uri %s/%s", ret.imagePath, ret.imageUri)
        if (this::cropImageRequest.isInitialized) {
            cropImageRequest.launch(
                CropImageContractOptions(
                    viewModel.imageUri,
                    CropImageOptions()
                )
            )
        }
        return ret
    }

    private fun setTemporaryMedia(imagePath: String) {
        mField.apply {
            this.imagePath = imagePath
            hasTemporaryMedia = true
        }
    }

    fun showCropDialog(content: String, negativeCallback: (() -> Unit)?) {
        if (!mViewModel.isValid) {
            Timber.w("showCropDialog called with null URI or Path")
            return
        }

        AlertDialog.Builder(mActivity).show {
            message(text = content)
            positiveButton(R.string.dialog_yes) {
                mViewModel = requestCrop(mViewModel)
            }
            negativeButton(R.string.dialog_no) {
                negativeCallback?.invoke() // Using invoke since negativeCallback is nullable
            }
        }
    }

    private fun handleCropResult(result: CropImageView.CropResult) {
        Timber.d("handleCropResult")
        mViewModel.deleteImagePath()
        mViewModel = ImageViewModel(result.getUriFilePath(mActivity, true), result.uriContent)
        if (!rotateAndCompress()) {
            Timber.i("handleCropResult() appears to have an invalid file, reverting")
            return
        }
        Timber.d("handleCropResult() = image path now %s", mField.imagePath)
    }

    private fun rotateAndCompress(): Boolean {
        if (!rotateAndCompress(mViewModel.imagePath!!, mViewModel)) {
            mImageFileSizeWarning.visibility = View.VISIBLE
            revertToPreviousImage()
            showSomethingWentWrong()
            return false
        }
        mField.hasTemporaryMedia = true
        return true
    }

    private fun getUriForFile(file: File): Uri {
        return getUriForFile(file, mActivity)
    }

    /**
     * Get image uri that adapts various model
     *
     * @return image uri
     */
    private fun getImageUri(context: Context, data: Intent): Uri? {
        Timber.d("getImageUri for data %s", data)
        val uri = data.data
        if (uri == null) {
            UIUtils.showThemedToast(context, context.getString(R.string.select_image_failed), false)
        }
        return uri
    }

    /**
     * Get image name based on uri and selection args
     *
     * @return Display name of file identified by uri (null if does not exist)
     */
    private fun getImageNameFromUri(context: Context, uri: Uri): String? {
        Timber.d("getImageNameFromUri() URI: %s", uri)
        var imageName: String? = null
        if (DocumentsContract.isDocumentUri(context, uri)) {
            val docId = DocumentsContract.getDocumentId(uri)
            if ("com.android.providers.media.documents" == uri.authority) {
                val id = docId.split(":").toTypedArray()[1]
                val selection = MediaStore.Images.Media._ID + "=" + id
                imageName = getImageNameFromContentResolver(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, selection)
            } else if ("com.android.providers.downloads.documents" == uri.authority) {
                val contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), docId.toLong())
                imageName = getImageNameFromContentResolver(context, contentUri, null)
            }
        } else if ("content".equals(uri.scheme, ignoreCase = true)) {
            imageName = getImageNameFromContentResolver(context, uri, null)
        } else if ("file".equals(uri.scheme, ignoreCase = true)) {
            if (uri.path != null) {
                val pathParts = uri.path!!.split("/").toTypedArray()
                imageName = pathParts[pathParts.size - 1]
            }
        }
        Timber.d("getImageNameFromUri() returning name %s", imageName)
        return imageName
    }

    /**
     * Get image name based on uri and selection args
     *
     * @return Display name of file identified by uri (null if does not exist)
     */
    private fun getImageNameFromContentResolver(context: Context, uri: Uri, selection: String?): String? {
        Timber.d("getImageNameFromContentResolver() %s", uri)
        val filePathColumns = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
        ContentResolverCompat.query(context.contentResolver, uri, filePathColumns, selection, null, null, null).use { cursor ->

            if (cursor == null) {
                Timber.w("getImageNameFromContentResolver() cursor was null")
                showSomethingWentWrong()
                return null
            }

            if (!cursor.moveToFirst()) {
                // TODO: #5909, it would be best to instrument this to see if we can fix the failure
                Timber.w("getImageNameFromContentResolver() cursor had no data")
                showSomethingWentWrong()
                return null
            }

            val imageName = cursor.getString(cursor.getColumnIndex(filePathColumns[0]))
            Timber.d("getImageNameFromContentResolver() decoded image name %s", imageName)
            return imageName
        }
    }

    val isShowingPreview: Boolean
        get() = mImageFileSize.visibility == View.VISIBLE

    private class ImageViewModel(val imagePath: String?, val imageUri: Uri?) {
        var isPreExistingImage = false
        fun enrich(savedInstanceState: Bundle) {
            savedInstanceState.putString("imagePath", imagePath)
            savedInstanceState.putParcelable("imageUri", imageUri)
        }

        val isValid: Boolean
            get() = imagePath != null && imageUri != null

        fun replaceNullValues(field: IField, context: Context): ImageViewModel {
            var newImagePath = imagePath
            var newImageUri = imageUri
            if (newImagePath == null) {
                newImagePath = field.imagePath
            }
            if (newImageUri == null && newImagePath != null) {
                newImageUri = getUriForFile(File(newImagePath), context)
            }
            val ivm = ImageViewModel(newImagePath, newImageUri)
            ivm.isPreExistingImage = true
            return ivm
        }

        fun deleteImagePath() {
            if (imagePath != null && File(imagePath).exists()) {
                if (!File(imagePath).delete()) {
                    Timber.i("deleteImagePath() had existing image, but delete failed")
                } else {
                    Timber.d("deleteImagePath() deleted %s", imagePath)
                }
            }
        }

        fun beforeCrop(imagePath: String?, imageUri: Uri?): ImageViewModel {
            return ImageViewModel(imagePath, imageUri)
        }

        fun rotateAndCompressTo(imagePath: String?, uri: Uri?): ImageViewModel {
            return ImageViewModel(imagePath, uri)
        }

        companion object {
            fun fromBundle(savedInstanceState: Bundle): ImageViewModel {
                val imagePath = savedInstanceState.getString("mImagePath")
                val imageUri = BundleCompat.getParcelable(savedInstanceState, "mImageUri", Uri::class.java)
                return ImageViewModel(imagePath, imageUri)
            }
        }
    }

    companion object {
        @VisibleForTesting
        val ACTIVITY_SELECT_IMAGE = 1
        private const val ACTIVITY_DRAWING = 4
        private const val IMAGE_SAVE_MAX_WIDTH = 1920
        private const val CROP_IMAGE_LAUNCHER_KEY = "crop_image_launcher_key"
        private const val TAKE_PICTURE_LAUNCHER_KEY = "take_picture_launcher_key"

        /**
         * Get Uri based on current image path
         *
         * @param file the file to get URI for
         * @return current image path's uri
         */
        private fun getUriForFile(file: File, activity: Context): Uri {
            Timber.d("getUriForFile() %s", file)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    return FileProvider.getUriForFile(
                        activity,
                        activity.applicationContext.packageName + ".apkgfileprovider",
                        file
                    )
                }
            } catch (e: Exception) {
                // #6628 - What would cause this? Is the fallback is effective? Telemetry to diagnose more:
                Timber.w(e, "getUriForFile failed on %s - attempting fallback", file)
                CrashReportService.sendExceptionReport(e, "BasicImageFieldController", "Unexpected getUriForFile failure on $file", true)
            }
            return Uri.fromFile(file)
        }
    }
}
