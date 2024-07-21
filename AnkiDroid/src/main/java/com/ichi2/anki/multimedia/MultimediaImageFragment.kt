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
import android.content.Context
import android.content.Intent
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
import androidx.fragment.app.viewModels
import com.canhub.cropper.CropException
import com.google.android.material.button.MaterialButton
import com.ichi2.anki.CrashReportService
import com.ichi2.anki.R
import com.ichi2.anki.multimedia.MultimediaActivity.Companion.EXTRA_MEDIA_OPTIONS
import com.ichi2.anki.multimedia.MultimediaActivity.Companion.MULTIMEDIA_RESULT
import com.ichi2.anki.multimedia.MultimediaActivity.Companion.MULTIMEDIA_RESULT_FIELD_INDEX
import com.ichi2.anki.multimedia.MultimediaUtils.createCachedFile
import com.ichi2.anki.multimedia.MultimediaUtils.createImageFile
import com.ichi2.anki.multimediacard.activity.MultimediaEditFieldActivity
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.annotations.NeedsTest
import com.ichi2.compat.CompatHelper.Companion.getSerializableCompat
import com.ichi2.utils.FileUtil
import com.ichi2.utils.ImageUtils
import com.ichi2.utils.message
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.text.DecimalFormat

@NeedsTest("Ensure correct option is executed i.e. gallery or camera")
class MultimediaImageFragment : MultimediaFragment(R.layout.fragment_multimedia_image) {
    override val title: String
        get() = resources.getString(R.string.multimedia_editor_popup_image)

    private lateinit var imagePreview: ImageView
    private lateinit var imageFileSize: TextView

    private val viewModel: MultimediaViewModel by viewModels()

    private lateinit var selectedImageOptions: ImageOptions

    /**
     * Launches an activity to pick an image from the device's gallery.
     * This launcher is registered using `ActivityResultContracts.StartActivityForResult()`.
     */
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            when (result.resultCode) {
                Activity.RESULT_CANCELED -> {
                    if (viewModel.currentMultimediaUri == null) {
                        val resultData = Intent().apply {
                            putExtra(MULTIMEDIA_RESULT_FIELD_INDEX, indexValue)
                        }
                        requireActivity().setResult(AppCompatActivity.RESULT_CANCELED, resultData)
                        requireActivity().finish()
                    }
                }

                Activity.RESULT_OK -> {
                    view?.findViewById<TextView>(R.id.no_image_textview)?.visibility = View.GONE
                    handleSelectImageIntent(result.data)
                }
            }
        }

    /**
     * Launches the device's camera to take a picture.
     * This launcher is registered using `ActivityResultContracts.TakePicture()`.
     */
    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { isPictureTaken ->
            when {
                !isPictureTaken && viewModel.currentMultimediaUri == null -> {
                    val resultData = Intent().apply {
                        putExtra(MULTIMEDIA_RESULT_FIELD_INDEX, indexValue)
                    }
                    requireActivity().setResult(AppCompatActivity.RESULT_CANCELED, resultData)
                    requireActivity().finish()
                }

                isPictureTaken -> {
                    Timber.d("Image successfully captured")
                    view?.findViewById<TextView>(R.id.no_image_textview)?.visibility = View.GONE
                    handleTakePictureResult(viewModel.currentMultimediaPath)
                }

                else -> {
                    Timber.d("Camera aborted or some interruption")
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
                menu.findItem(R.id.action_crop).isVisible = viewModel.currentMultimediaUri != null
            }
        ) { menuItem ->
            when (menuItem.itemId) {
                R.id.action_crop -> {
                    viewModel.saveMultimediaForRevert(
                        imagePath = viewModel.currentMultimediaPath,
                        imageUri = viewModel.currentMultimediaUri
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
                            dispatchCamera()
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
            showErrorDialog()
            Timber.e("createUI() failed to get cache directory")
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
        handleSelectedImageOptions()
        setupDoneButton()
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
        }
    }

    private fun setupDoneButton() {
        view?.findViewById<MaterialButton>(R.id.action_done)?.setOnClickListener {
            Timber.d("MultimediaImageFragment:: Done button pressed")
            if (viewModel.getMultimediaFileSize() == 0L) {
                Timber.d("Image length is not valid")
                return@setOnClickListener
            }
            if (viewModel.getMultimediaFileSize() > MultimediaUtils.IMAGE_LIMIT) {
                showLargeFileCropDialog((1.0 * viewModel.getMultimediaFileSize() / MultimediaEditFieldActivity.IMAGE_LIMIT).toFloat())
                return@setOnClickListener
            }

            field.mediaPath = viewModel.currentMultimediaPath
            field.hasTemporaryMedia = true

            val resultData = Intent().apply {
                putExtra(MULTIMEDIA_RESULT, field)
                putExtra(MULTIMEDIA_RESULT_FIELD_INDEX, indexValue)
            }
            requireActivity().setResult(AppCompatActivity.RESULT_OK, resultData)
            requireActivity().finish()
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    private fun dispatchCamera() {
        val photoFile: File? = try {
            requireContext().createImageFile()
        } catch (e: Exception) {
            Timber.w("Error creating the file", e)
            return
        }

        photoFile?.let {
            viewModel.currentMultimediaPath = it.absolutePath
            // viewModel.saveCurrentImagePath(it.absolutePath)
            val photoURI: Uri = FileProvider.getUriForFile(
                requireContext(),
                requireActivity().applicationContext.packageName + ".apkgfileprovider",
                it
            )
            cameraLauncher.launch(photoURI)
        }
    }

    private fun handleTakePictureResult(imagePath: String?) {
        Timber.d("handleTakePictureResult")
        if (imagePath == null) {
            Timber.i("handleTakePictureResult appears to have an invalid picture")
            return
        }

        updateAndDisplayImageSize(imagePath)
        val photoFile = File(imagePath)
        val imageUri: Uri = FileProvider.getUriForFile(
            requireContext(),
            requireActivity().applicationContext.packageName + ".apkgfileprovider",
            photoFile
        )
        viewModel.currentMultimediaUri = imageUri
        imagePreview.setImageURI(imageUri)

        showCropDialog(getString(R.string.crop_image))
    }

    private fun updateAndDisplayImageSize(imagePath: String) {
        val file = File(imagePath)
        viewModel.selectedMediaFileSize = file.length()
        imageFileSize.text = file.toHumanReadableSize()
    }

    private fun showLargeFileCropDialog(length: Float) {
        val decimalFormat = DecimalFormat(".00")
        val size = decimalFormat.format(length.toDouble())
        val message = getString(R.string.save_dialog_content, size)
        showCropDialog(message)
    }

    private fun showCropDialog(message: String) {
        if (viewModel.currentMultimediaUri == null) {
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

    private fun handleSelectImageIntent(data: Intent?) {
        if (data == null) {
            Timber.e("handleSelectImageIntent() no intent provided")
            showSomethingWentWrong()
            return
        }

        val selectedImage = getImageUri(data)

        val mimeType = selectedImage?.let { context?.contentResolver?.getType(it) }
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

        imagePreview.setImageURI(selectedImage)
        viewModel.currentMultimediaUri = selectedImage

        if (selectedImage == null) {
            Timber.w("handleSelectImageIntent() selectedImage was null")
            showSomethingWentWrong()
            return
        }

        val internalizedPick = internalizeUri(selectedImage)
        if (internalizedPick == null) {
            Timber.w(
                "handleSelectImageIntent() unable to internalize image from Uri %s",
                selectedImage
            )
            showSomethingWentWrong()
            return
        }

        val imagePath = internalizedPick.absolutePath

        viewModel.currentMultimediaPath = imagePath
        updateAndDisplayImageSize(imagePath)
    }

    private fun requestCrop() {
        val imageUri = viewModel.currentMultimediaUri ?: return
        ImageUtils.cropImage(requireActivity().activityResultRegistry, imageUri) { cropResult ->
            if (cropResult == null) {
                Timber.d("Image crop result was null")
                return@cropImage
            }

            if (cropResult.isSuccessful) {
                cropResult.getUriFilePath(requireActivity(), true)
                    ?.let { path ->
                        updateAndDisplayImageSize(path)
                        viewModel.currentMultimediaPath = path
                    }
                viewModel.currentMultimediaUri = cropResult.uriContent
                imagePreview.setImageURI(cropResult.uriContent)
            } else {
                // cropImage can give us more information. Not sure it is actionable so for now just log it.
                val error: String =
                    cropResult.error?.toString() ?: "Error info not available"
                Timber.w(error, "cropImage threw an error")
                // condition can be removed if #12768 get fixed by Canhub
                if (cropResult.error is CropException.Cancellation) {
                    Timber.i("CropException caught, seemingly nothing to do ", error)
                } else {
                    CrashReportService.sendExceptionReport(
                        error,
                        "cropImage threw an error"
                    )
                }
            }
        }
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

    /** Represents image options that a user choose from the bottom sheet which [MultimediaImageFragment] uses **/
    enum class ImageOptions {
        GALLERY,
        CAMERA
    }
}
