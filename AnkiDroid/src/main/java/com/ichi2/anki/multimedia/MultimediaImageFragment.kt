/*
 * Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.multimedia

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.core.os.BundleCompat
import androidx.lifecycle.lifecycleScope
import com.canhub.cropper.CropException
import com.google.android.material.button.MaterialButton
import com.ichi2.anki.CrashReportService
import com.ichi2.anki.DrawingActivity
import com.ichi2.anki.R
import com.ichi2.anki.multimedia.MultimediaActivity.Companion.EXTRA_MEDIA_OPTIONS
import com.ichi2.anki.multimedia.MultimediaActivity.Companion.MULTIMEDIA_RESULT
import com.ichi2.anki.multimedia.MultimediaActivity.Companion.MULTIMEDIA_RESULT_FIELD_INDEX
import com.ichi2.anki.multimedia.MultimediaUtils.IMAGE_LIMIT
import com.ichi2.anki.multimedia.MultimediaUtils.IMAGE_SAVE_MAX_WIDTH
import com.ichi2.anki.multimedia.MultimediaUtils.createCachedFile
import com.ichi2.anki.multimedia.MultimediaUtils.createImageFile
import com.ichi2.anki.multimedia.MultimediaUtils.createNewCacheImageFile
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.annotations.NeedsTest
import com.ichi2.compat.CompatHelper.Companion.getSerializableCompat
import com.ichi2.imagecropper.ImageCropper
import com.ichi2.imagecropper.ImageCropper.Companion.CROP_IMAGE_RESULT
import com.ichi2.utils.BitmapUtil
import com.ichi2.utils.ExifUtil
import com.ichi2.utils.FileUtil
import com.ichi2.utils.message
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.text.NumberFormat

@NeedsTest("Ensure correct option is executed i.e. gallery or camera")
class MultimediaImageFragment : MultimediaFragment(R.layout.fragment_multimedia_image) {
    override val title: String
        get() = resources.getString(R.string.multimedia_editor_popup_image)

    private lateinit var imagePreview: ImageView
    private lateinit var imageFileSize: TextView

    private lateinit var selectedImageOptions: ImageOptions

    /**
     * Launches an activity to pick an image from the device's gallery.
     * This launcher is registered using `ActivityResultContracts.StartActivityForResult()`.
     */
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            when (result.resultCode) {
                Activity.RESULT_CANCELED -> {
                    if (viewModel.currentMultimediaUri.value == null) {
                        val resultData = Intent().apply {
                            putExtra(MULTIMEDIA_RESULT_FIELD_INDEX, indexValue)
                        }
                        requireActivity().setResult(AppCompatActivity.RESULT_CANCELED, resultData)
                        requireActivity().finish()
                    }
                }

                Activity.RESULT_OK -> {
                    view?.findViewById<TextView>(R.id.no_image_textview)?.visibility = View.GONE
                    val data = result.data
                    if (data == null) {
                        Timber.w("handleSelectImageIntent() no intent provided")
                        showSomethingWentWrong()
                        return@registerForActivityResult
                    }

                    val selectedImage = getImageUri(data)
                    handleSelectImageIntent(selectedImage)
                }
            }
        }

    /**
     * Launches the [DrawingActivity] and handles the result by adding the drawing as image.
     */
    private val drawingActivityLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            when (result.resultCode) {
                Activity.RESULT_CANCELED -> {
                    // If user didn't draw, return the indexValue as a result and finish the activity
                    if (viewModel.currentMultimediaUri.value == null) {
                        val resultData = Intent().apply {
                            putExtra(MULTIMEDIA_RESULT_FIELD_INDEX, indexValue)
                        }
                        requireActivity().setResult(AppCompatActivity.RESULT_CANCELED, resultData)
                        requireActivity().finish()
                    }
                }

                Activity.RESULT_OK -> {
                    view?.findViewById<TextView>(R.id.no_image_textview)?.visibility = View.GONE
                    val intent = result.data ?: return@registerForActivityResult
                    Timber.d("Intent not null, handling the result")
                    handleDrawingResult(intent)
                }
            }
        }

    /**
     * Launches the device's camera to take a picture.
     * This launcher is registered using `ActivityResultContracts.TakePicture()`.
     */
    @NeedsTest("Works fine without permission as we use Camera as feature")
    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { isPictureTaken ->
            when {
                !isPictureTaken && viewModel.currentMultimediaUri.value == null -> {
                    val resultData = Intent().apply {
                        putExtra(MULTIMEDIA_RESULT_FIELD_INDEX, indexValue)
                    }
                    requireActivity().setResult(AppCompatActivity.RESULT_CANCELED, resultData)
                    requireActivity().finish()
                }

                isPictureTaken -> {
                    Timber.d("Image successfully captured")
                    view?.findViewById<TextView>(R.id.no_image_textview)?.visibility = View.GONE
                    handleTakePictureResult(viewModel.currentMultimediaPath.value)
                }

                else -> {
                    Timber.d("Camera aborted or some interruption, restoring multimedia data")
                    viewModel.restoreMultimedia()
                }
            }
        }

    /** Launches an activity to crop the image, using the [ImageCropper] */
    private val imageCropperLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            when (result.resultCode) {
                Activity.RESULT_OK -> {
                    result.data?.let {
                        val cropResultData = IntentCompat.getParcelableExtra(
                            it,
                            CROP_IMAGE_RESULT,
                            ImageCropper.CropResultData::class.java
                        )
                        Timber.d("Cropped image data: $cropResultData")

                        if (cropResultData?.error != null) {
                            handleCropResultError(error = cropResultData.error)
                        }

                        if (cropResultData?.uriPath == null || cropResultData.uriContent == null) return@registerForActivityResult
                        updateAndDisplayImageSize(cropResultData.uriPath)
                        viewModel.updateCurrentMultimediaPath(cropResultData.uriPath)
                        viewModel.updateCurrentMultimediaUri(cropResultData.uriContent)
                        imagePreview.setImageURI(cropResultData.uriContent)
                    }
                }
                else -> {
                    Timber.v("Unable to crop the image")
                }
            }
        }

    /**
     * Lazily initialized instance of MultimediaMenu.
     * The instance is created only when first accessed.
     */
    private val multimediaMenu by lazy {
        MultimediaMenuProvider(
            menuResId = R.menu.multimedia_menu,
            onCreateMenuCondition = { menu ->

                setMenuItemIcon(menu.findItem(R.id.action_restart), R.drawable.ic_replace_image)
                lifecycleScope.launch {
                    viewModel.currentMultimediaUri.collectLatest { uri ->
                        menu.findItem(R.id.action_crop).isVisible = uri != null
                    }
                }
            }
        ) { menuItem ->
            when (menuItem.itemId) {
                R.id.action_crop -> {
                    viewModel.saveMultimediaForRevert(
                        imagePath = viewModel.currentMultimediaPath.value,
                        imageUri = viewModel.currentMultimediaUri.value
                    )
                    requestCrop()
                    true
                }

                R.id.action_restart -> {
                    when (selectedImageOptions) {
                        ImageOptions.GALLERY -> {
                            openGallery()
                        }

                        ImageOptions.CAMERA -> {
                            viewModel.saveMultimediaForRevert(
                                imagePath = viewModel.currentMultimediaPath.value,
                                imageUri = viewModel.currentMultimediaUri.value
                            )
                            dispatchCamera()
                        }

                        ImageOptions.DRAWING -> {
                            openDrawingCanvas()
                        }
                    }
                    true
                }

                else -> false
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ankiCacheDirectory = FileUtil.getAnkiCacheDirectory(requireContext(), "temp-photos")
        if (ankiCacheDirectory == null) {
            Timber.e("createUI() failed to get cache directory")
            showErrorDialog(errorMessage = resources.getString(R.string.multimedia_editor_failed))
            return
        }

        arguments?.let {
            selectedImageOptions = it.getSerializableCompat<ImageOptions>(EXTRA_MEDIA_OPTIONS) as ImageOptions
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMenu(multimediaMenu)
        imagePreview = view.findViewById(R.id.image_preview)
        imageFileSize = view.findViewById(R.id.image_size_textview)

        handleImageUri()
        setupDoneButton()
    }

    private fun handleImageUri() {
        fun processExternalImage(uri: Uri): Uri? = internalizeUri(uri)?.let { Uri.fromFile(it) }

        if (imageUri != null) {
            view?.findViewById<TextView>(R.id.no_image_textview)?.visibility = View.GONE

            val internalUri = imageUri?.let { processExternalImage(it) }
            handleSelectImageIntent(internalUri)
        } else {
            handleSelectedImageOptions()
        }
    }

    private fun handleSelectedImageOptions() {
        when (selectedImageOptions) {
            ImageOptions.GALLERY -> {
                Timber.d("MultimediaImageFragment:: Opening gallery")
                openGallery()
            }
            ImageOptions.CAMERA -> {
                dispatchCamera()
                Timber.d("MultimediaImageFragment:: Launching camera")
            }

            ImageOptions.DRAWING -> {
                Timber.d("MultimediaImageFragment:: Opening drawing canvas")
                openDrawingCanvas()
            }
        }
    }

    private fun setupDoneButton() {
        view?.findViewById<MaterialButton>(R.id.action_done)?.setOnClickListener {
            Timber.d("MultimediaImageFragment:: Done button pressed")
            if (viewModel.selectedMediaFileSize == 0L) {
                Timber.d("Image length is not valid")
                return@setOnClickListener
            }
            if (viewModel.selectedMediaFileSize > IMAGE_LIMIT) {
                showLargeFileCropDialog(viewModel.selectedMediaFileSize)
                return@setOnClickListener
            }
            finishAddingImage()
        }
    }

    private fun finishAddingImage() {
        field.mediaPath = viewModel.currentMultimediaPath.value
        field.hasTemporaryMedia = true

        val resultData = Intent().apply {
            putExtra(MULTIMEDIA_RESULT, field)
            putExtra(MULTIMEDIA_RESULT_FIELD_INDEX, indexValue)
        }
        requireActivity().setResult(AppCompatActivity.RESULT_OK, resultData)
        requireActivity().finish()
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        try {
            pickImageLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "MultimediaImageFragment:: No app found to select image")
            showSnackbar(R.string.activity_start_failed)
        }
    }

    private fun openDrawingCanvas() {
        drawingActivityLauncher.launch(Intent(requireContext(), DrawingActivity::class.java))
    }

    private fun dispatchCamera() {
        val photoFile: File? = try {
            requireContext().createImageFile()
        } catch (e: Exception) {
            Timber.w(e, "Error creating the file")
            return
        }

        photoFile?.let {
            viewModel.updateCurrentMultimediaPath(it.absolutePath)
            val photoURI: Uri = FileProvider.getUriForFile(
                requireContext(),
                requireActivity().applicationContext.packageName + ".apkgfileprovider",
                it
            )

            try {
                cameraLauncher.launch(photoURI)
            } catch (e: ActivityNotFoundException) {
                Timber.w(e, "MultimediaImageFragment:: No camera found")
                showSnackbar(R.string.activity_start_failed)
            }
        }
    }

    private fun handleDrawingResult(intent: Intent) {
        val imageUri = BundleCompat.getParcelable(
            intent.extras!!,
            DrawingActivity.EXTRA_RESULT_WHITEBOARD,
            Uri::class.java
        )

        if (imageUri == null) {
            Timber.w("handleDrawingResult() no image Uri provided")
            showSomethingWentWrong()
            return
        }

        val internalizedPick = internalizeUri(imageUri)

        if (internalizedPick == null) {
            Timber.w(
                "handleSelectImageIntent() unable to internalize image from Uri %s",
                imageUri
            )
            showSomethingWentWrong()
            return
        }

        val drawImagePath = internalizedPick.absolutePath
        Timber.i("handleDrawingResult() Decoded image: '%s'", drawImagePath)

        imagePreview.setImageURI(imageUri)
        viewModel.updateCurrentMultimediaPath(drawImagePath)
        viewModel.updateCurrentMultimediaUri(imageUri)
        updateAndDisplayImageSize(drawImagePath)
    }

    private fun handleTakePictureResult(imagePath: String?) {
        Timber.d("handleTakePictureResult, imagePath: %s", imagePath)
        if (imagePath == null) {
            Timber.i("handleTakePictureResult appears to have an invalid picture")
            return
        }

        val imageFile = File(imagePath)
        viewModel.updateCurrentMultimediaPath(imagePath)
        viewModel.updateCurrentMultimediaUri(getUriForFile(imageFile))
        imagePreview.setImageURI(getUriForFile(imageFile))
        updateAndDisplayImageSize(imagePath)

        showCropDialog(getString(R.string.crop_image))
    }

    private fun updateAndDisplayImageSize(imagePath: String) {
        val file = File(imagePath)
        viewModel.selectedMediaFileSize = file.length()
        imageFileSize.text = file.toHumanReadableSize()
    }

    private fun showLargeFileCropDialog(length: Long) {
        val numberFormat = NumberFormat.getInstance()
        // length is in bits, other elements have MB, convert to MB
        val size = numberFormat.format(length / 1000000.0)
        val message = getString(R.string.save_dialog_content, size)
        showCompressImageDialog(message)
    }

    private fun showCompressImageDialog(message: String) {
        AlertDialog.Builder(requireActivity()).show {
            message(text = message)
            positiveButton(R.string.compress) {
                viewModel.currentMultimediaPath.value.let {
                    if (it == null) return@positiveButton
                    if (!rotateAndCompress(it)) {
                        Timber.d("Unable to compress the clicked image")
                        showErrorDialog(errorMessage = resources.getString(R.string.multimedia_editor_image_compression_failed))
                        return@positiveButton
                    }
                }
            }
            negativeButton(R.string.dialog_no) {
                finishAddingImage()
            }
        }
    }

    private fun showCropDialog(message: String) {
        if (viewModel.currentMultimediaUri.value == null) {
            Timber.w("showCropDialog called with null URI or Path")
            return
        }

        AlertDialog.Builder(requireActivity()).show {
            message(text = message)
            positiveButton(R.string.dialog_yes) {
                requestCrop()
            }
            negativeButton(R.string.dialog_no)
        }
    }

    private fun handleSelectImageIntent(imageUri: Uri?) {
        val mimeType = imageUri?.let { context?.contentResolver?.getType(it) }
        if (mimeType == "image/svg+xml") {
            Timber.i("Selected image is an SVG.")
            view?.findViewById<TextView>(R.id.no_image_textview)?.apply {
                text = resources.getString(R.string.multimedia_editor_svg_preview)
                visibility = View.VISIBLE
            }
        } else {
            // reset the no preview text
            view?.findViewById<TextView>(R.id.no_image_textview)?.apply {
                text = null
                visibility = View.GONE
            }
        }

        if (imageUri == null) {
            Timber.w("handleSelectImageIntent() selectedImage was null")
            showSomethingWentWrong()
            return
        }

        val internalizedPick = internalizeUri(imageUri)
        if (internalizedPick == null) {
            Timber.w(
                "handleSelectImageIntent() unable to internalize image from Uri %s",
                imageUri
            )
            showSomethingWentWrong()
            return
        }

        val imagePath = internalizedPick.absolutePath

        try {
            // set this first, if it blows up we don't want to set the others...
            imagePreview.setImageURI(imageUri)

            // if that worked, the image was not too large / good format, update viewModel
            viewModel.updateCurrentMultimediaUri(imageUri)
            viewModel.updateCurrentMultimediaPath(imagePath)
        } catch (e: Exception) {
            Timber.w(e, "handleSelectImageIntent() unable to set image for preview")
            // clear the image out of the preview so we may recover
            imagePreview.setImageURI(null)
            showSomethingWentWrong()
            return
        }
        viewModel.selectedMediaFileSize = internalizedPick.length()
        updateAndDisplayImageSize(imagePath)
    }

    private fun requestCrop() {
        val imageUri = viewModel.currentMultimediaUri.value ?: return
        val intent = com.ichi2.imagecropper.ImageCropperLauncher.ImageUri(imageUri).getIntent(requireContext())
        imageCropperLauncher.launch(intent)
    }

    private fun handleCropResultError(error: Exception) {
        // cropImage can give us more information. Not sure it is actionable so for now just log it.
        Timber.w(error, "cropImage threw an error")
        // condition can be removed if #12768 get fixed by Canhub
        if (error is CropException.Cancellation) {
            Timber.i(error, "CropException caught, seemingly nothing to do ")
        } else {
            showErrorDialog(resources.getString(R.string.activity_result_unexpected))
            CrashReportService.sendExceptionReport(
                error,
                "cropImage threw an error"
            )
        }
    }

    /**
     * Rotate and compress the image, with the side effect of the current image being backed by a new file
     *
     * @return true if successful, false indicates the current image is likely not usable, revert if possible
     */
    private fun rotateAndCompress(imagePath: String): Boolean {
        Timber.d("rotateAndCompress() on %s", imagePath)

        // Set the rotation of the camera image and save as JPEG
        val imageFile = File(imagePath)
        Timber.d("rotateAndCompress in path %s has size %d", imageFile.absolutePath, imageFile.length())

        // Load into a bitmap with max size of 1920 pixels and rotate if necessary
        var bitmap = BitmapUtil.decodeFile(imageFile, IMAGE_SAVE_MAX_WIDTH)
        if (bitmap == null) {
            Timber.w("rotateAndCompress() unable to decode file %s", imagePath)
            return false
        }

        var out: FileOutputStream? = null
        try {
            val outFile = createNewCacheImageFile(directory = ankiCacheDirectory)
            out = FileOutputStream(outFile)

            // Rotate the bitmap if needed
            bitmap = ExifUtil.rotateFromCamera(imageFile, bitmap)

            // Compress the bitmap to JPEG format with 90% quality
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)

            // Delete the original image file
            if (!imageFile.delete()) {
                Timber.w("rotateAndCompress() delete of pre-compressed image failed %s", imagePath)
            }

            val imageUri = getUriForFile(outFile)

            // TODO: see if we can use one value to the viewModel
            viewModel.updateCurrentMultimediaUri(imageUri)
            viewModel.updateCurrentMultimediaPath(outFile.path)
            imagePreview.setImageURI(imageUri)
            viewModel.selectedMediaFileSize = outFile.length()
            updateAndDisplayImageSize(outFile.path)

            Timber.d("rotateAndCompress out path %s has size %d", outFile.absolutePath, outFile.length())
        } catch (e: FileNotFoundException) {
            Timber.w(e, "rotateAndCompress() File not found for image compression %s", imagePath)
            return false
        } catch (e: IOException) {
            Timber.w(e, "rotateAndCompress() create file failed for file %s", imagePath)
            return false
        } finally {
            try {
                out?.close()
            } catch (e: IOException) {
                Timber.w(e, "rotateAndCompress() Unable to clean up image compression output stream")
            }
        }

        return true
    }

    private fun internalizeUri(uri: Uri): File? {
        val internalFile: File
        val uriFileName = MultimediaUtils.getImageNameFromUri(requireContext(), uri)

        // Use the display name from the image info to create a new file with correct extension
        if (uriFileName == null) {
            Timber.w("internalizeUri() unable to get file name")
            showSomethingWentWrong()
            return null
        }
        internalFile = try {
            createCachedFile(uriFileName, ankiCacheDirectory)
        } catch (e: IOException) {
            Timber.w(e, "internalizeUri() failed to create new file with name %s", uriFileName)
            showSomethingWentWrong()
            return null
        }
        return try {
            val returnFile =
                FileUtil.internalizeUri(uri, internalFile, requireActivity().contentResolver)
            Timber.d("internalizeUri successful. Returning internalFile.")
            returnFile
        } catch (e: Exception) {
            Timber.w(e)
            showSomethingWentWrong()
            null
        }
    }

    private fun getImageUri(data: Intent): Uri? {
        Timber.d("getImageUri for data %s", data)
        val uri = data.data
        if (uri == null) {
            showSnackbar(getString(R.string.select_image_failed))
        }
        return uri
    }

    companion object {

        fun getIntent(
            context: Context,
            multimediaExtra: MultimediaActivityExtra,
            imageOptions: ImageOptions
        ): Intent {
            return MultimediaActivity.getIntent(
                context,
                MultimediaImageFragment::class,
                multimediaExtra,
                imageOptions
            )
        }
    }

    /** Image options that a user choose from the bottom sheet which [MultimediaImageFragment] uses **/
    enum class ImageOptions {
        GALLERY,
        CAMERA,
        DRAWING
    }
}
