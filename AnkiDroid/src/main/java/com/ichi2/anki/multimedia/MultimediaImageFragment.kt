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
import android.text.format.Formatter
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.viewModels
import com.canhub.cropper.CropException
import com.google.android.material.button.MaterialButton
import com.ichi2.anki.CrashReportService
import com.ichi2.anki.R
import com.ichi2.anki.multimedia.MultimediaActivity.Companion.MULTIMEDIA_RESULT
import com.ichi2.anki.multimedia.MultimediaActivity.Companion.MULTIMEDIA_RESULT_FIELD_INDEX
import com.ichi2.anki.multimedia.MultimediaUtils.createCachedFile
import com.ichi2.anki.multimedia.MultimediaUtils.createImageFile
import com.ichi2.anki.multimediacard.activity.MultimediaEditFieldActivity
import com.ichi2.anki.snackbar.showSnackbar
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

class MultimediaImageFragment : MultimediaFragment(R.layout.fragment_multimedia_image) {
    override val title: String
        get() = resources.getString(R.string.multimedia_editor_popup_image)

    private lateinit var imagePreview: ImageView

    private val viewModel: MultimediaViewModel by viewModels()

    private lateinit var pickImageLauncher: ActivityResultLauncher<Intent>

    private lateinit var cameraLauncher: ActivityResultLauncher<Uri>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ankiCacheDirectory = FileUtil.getAnkiCacheDirectory(requireContext(), "temp-photos")
        if (ankiCacheDirectory == null) {
            showSomethingWentWrong()
            Timber.e("createUI() failed to get cache directory")
            return
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMenu()
        imagePreview = view.findViewById(R.id.image_preview)
        (activity as? ToolbarTitle)?.setToolbarTitle(title)

        registerLauncher()

        when (selectedImageOptions) {
            ImageOptions.GALLERY -> {
                Timber.d("MultimediaImageFragment:: Opening gallery")
                openGallery()
            }

            ImageOptions.CAMERA -> {
                dispatchCamera()
                Timber.d("MultimediaImageFragment:: Launching camera")
            }

            ImageOptions.UNKNOWN -> {
                Timber.d("MultimediaImageFragment:: Error occurred, showing error dialog")
            }
        }

        view.findViewById<MaterialButton>(R.id.action_done).setOnClickListener {
            Timber.d("MultimediaImageFragment:: Done button pressed")
            if (viewModel.getImageLength() == 0L) {
                Timber.d("Image length is not valid")
                return@setOnClickListener
            }
            if (viewModel.getImageLength() > MultimediaUtils.IMAGE_LIMIT) {
                showLargeFileCropDialog((1.0 * viewModel.getImageLength() / MultimediaEditFieldActivity.IMAGE_LIMIT).toFloat())
                return@setOnClickListener
            }

            field.imagePath = viewModel.getCurrentImagePath()
            field.hasTemporaryMedia = true

            val resultData = Intent().apply {
                putExtra(MULTIMEDIA_RESULT, field)
                putExtra(MULTIMEDIA_RESULT_FIELD_INDEX, intValue)
            }
            requireActivity().setResult(AppCompatActivity.RESULT_OK, resultData)
            requireActivity().finish()
        }
    }

    private fun registerLauncher() {
        when (selectedImageOptions) {
            ImageOptions.GALLERY -> {
                Timber.d("Registering image result launcher")
                pickImageLauncher =
                    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                        if (result.resultCode == Activity.RESULT_OK) {
                            view?.findViewById<TextView>(R.id.no_image_textview)?.visibility =
                                View.GONE
                            handleSelectImageIntent(result.data)
                        }
                    }
            }

            ImageOptions.CAMERA -> {
                Timber.d("Registering camera result launcher")
                cameraLauncher =
                    registerForActivityResult(ActivityResultContracts.TakePicture()) { isPictureTaken ->
                        if (isPictureTaken) {
                            Timber.d("Image successfully captured")
                            view?.findViewById<TextView>(R.id.no_image_textview)?.visibility =
                                View.GONE
                            handleTakePictureResult(viewModel.getCurrentImagePath())
                        } else {
                            Timber.d("Camera aborted or some interruption")
                        }
                    }
            }

            ImageOptions.UNKNOWN -> {
                Timber.w("Unknown image option, no launcher registered")
                showSomethingWentWrong()
            }
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

        photoFile?.absolutePath?.let { viewModel.saveCurrentImagePath(it) }

        photoFile?.let {
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

        displayImageSize(imagePath)
        val photoFile = File(imagePath)
        val imageUri: Uri = FileProvider.getUriForFile(
            requireContext(),
            requireActivity().applicationContext.packageName + ".apkgfileprovider",
            photoFile
        )
        viewModel.currentImageUri(imageUri)
        imagePreview.setImageURI(imageUri)

        showCropDialog(getString(R.string.crop_image))
    }

    private fun displayImageSize(imagePath: String) {
        val file = File(imagePath)
        viewModel.setImageLength(file.length())
        val size = Formatter.formatFileSize(requireContext(), file.length())
        view?.findViewById<TextView>(R.id.image_size_textview)?.text = size
    }

    private fun showLargeFileCropDialog(length: Float) {
        val decimalFormat = DecimalFormat(".00")
        val size = decimalFormat.format(length.toDouble())
        val message = getString(R.string.save_dialog_content, size)
        showCropDialog(message)
    }

    private fun showCropDialog(message: String) {
        if (viewModel.getCurrentImageUri() == null) {
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
                text = resources.getString(R.string.no_image_preview)
                visibility = View.GONE
            }
        }

        imagePreview.setImageURI(selectedImage)
        viewModel.currentImageUri(selectedImage)

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

        viewModel.saveCurrentImagePath(imagePath)
        displayImageSize(imagePath)
    }

    private fun requestCrop() {
        viewModel.getCurrentImageUri()?.let {
            ImageUtils.cropImage(requireActivity().activityResultRegistry, it) { cropResult ->
                if (cropResult != null) {
                    if (cropResult.isSuccessful) {
                        cropResult.getUriFilePath(requireActivity(), true)
                            ?.let { path ->
                                displayImageSize(path)
                                viewModel.saveCurrentImagePath(path)
                            }
                        viewModel.currentImageUri(cropResult.uriContent)
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

    private fun showSomethingWentWrong() {
        showSnackbar(resources.getString(R.string.multimedia_editor_something_wrong))
    }

    private fun setupMenu() {
        (requireActivity() as MenuHost).addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menu.clear()
                    menuInflater.inflate(R.menu.multimedia_menu, menu)
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    return when (menuItem.itemId) {
                        R.id.action_crop -> {
                            viewModel.saveImageForRevert(
                                imagePath = viewModel.getCurrentImagePath(),
                                imageUri = viewModel.getCurrentImageUri()
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

                                ImageOptions.UNKNOWN -> {
                                    // Do nothing
                                }
                            }
                            true
                        }

                        else -> false
                    }
                }
            }
        )
    }

    companion object {

        private var selectedImageOptions: ImageOptions = ImageOptions.UNKNOWN

        fun getIntent(
            context: Context,
            multimediaExtra: MultimediaActivityExtra,
            imageOptions: ImageOptions
        ): Intent {
            selectedImageOptions = imageOptions
            return MultimediaActivity.getIntent(
                context,
                MultimediaImageFragment::class,
                multimediaExtra
            )
        }
    }

    /** Enum class that represents image options that a user choose from the bottom sheet  **/
    enum class ImageOptions {
        GALLERY,
        CAMERA,
        UNKNOWN
    }
}
