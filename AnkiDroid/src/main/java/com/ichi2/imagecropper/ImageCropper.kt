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

package com.ichi2.imagecropper

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.BundleCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import com.canhub.cropper.CropImageView
import com.ichi2.anki.R
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.utils.ContentResolverUtil
import kotlinx.parcelize.Parcelize
import timber.log.Timber

/**
 * Fragment for cropping images within the AnkiDroid, uses [CropImageView] to crop the image and
 * sends the image uri as result.
 *
 * Portions of this code were adapted from the CanHub project.
 * Original source: https://github.com/CanHub/Android-Image-Cropper
 *
 * Attribution to the original authors of the CanHub/Android-Image-Cropper for their contributions.
 */
class ImageCropper :
    Fragment(R.layout.fragment_image_cropper),
    MenuProvider {
    private lateinit var cropImageView: CropImageView

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? AppCompatActivity)?.apply {
            setSupportActionBar(view.findViewById(R.id.toolbar))
            // there's no need for a title anyway and if we don't set it we end up with "AnkiDroid"
            // as the title which is useless
            supportActionBar?.title = ""
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
        }
        cropImageView =
            view.findViewById<CropImageView>(R.id.cropImageView).apply {
                setOnSetImageUriCompleteListener(::onSetImageUriComplete)
                setOnCropImageCompleteListener(::onCropImageComplete)
                cropRect = Rect(100, 300, 500, 1200)
                val originalImageUri =
                    BundleCompat.getParcelable(requireArguments(), CROP_IMAGE_URI, Uri::class.java)
                setImageUriAsync(originalImageUri)
            }
        requireActivity().addMenuProvider(this, viewLifecycleOwner)
    }

    override fun onCreateMenu(
        menu: Menu,
        menuInflater: MenuInflater,
    ) {
        menu.clear()
        // TODO make our own menu, we shouldn't rely on third party menu files
        menuInflater.inflate(com.canhub.cropper.R.menu.crop_image_menu, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
        when (menuItem.itemId) {
            com.canhub.cropper.R.id.crop_image_menu_crop -> {
                Timber.d("Crop image clicked")
                val imageFormat = cropImageView.imageUri?.let { getImageCompressFormat(it) }
                Timber.d("Compress format: $imageFormat")
                if (imageFormat != null) {
                    cropImageView.croppedImageAsync(
                        saveCompressFormat = imageFormat,
                    )
                }
                true
            }

            com.canhub.cropper.R.id.ic_rotate_right_24 -> {
                Timber.d("Rotate right clicked")
                cropImageView.rotateImage(90)
                true
            }

            com.canhub.cropper.R.id.ic_flip_24_horizontally -> {
                Timber.d("Flip horizontally clicked")

                cropImageView.flipImageHorizontally()
                true
            }

            com.canhub.cropper.R.id.ic_flip_24_vertically -> {
                Timber.d("Flip vertically clicked")
                cropImageView.flipImageVertically()
                true
            }

            else -> false
        }

    private fun getImageCompressFormat(uri: Uri): Bitmap.CompressFormat {
        Timber.d("Original image URI: $uri")

        val fileExtension =
            try {
                ContentResolverUtil.getFileName(requireContext().contentResolver, uri).substringAfterLast('.')
            } catch (e: Exception) {
                Timber.w(e, "Failed to retrieve file extension from URI")
                null
            }

        return when (fileExtension?.lowercase()) {
            "png" -> Bitmap.CompressFormat.PNG
            "jpeg", "jpg" -> Bitmap.CompressFormat.JPEG
            "webp" -> {
                if (Build.VERSION.SDK_INT >= 30) {
                    Bitmap.CompressFormat.WEBP_LOSSLESS
                } else {
                    Bitmap.CompressFormat.WEBP
                }
            }
            else -> {
                Timber.w("Unknown image format: $fileExtension. Defaulting to JPEG.")
                Bitmap.CompressFormat.JPEG
            }
        }
    }

    private fun onSetImageUriComplete(
        @Suppress("UNUSED_PARAMETER") view: CropImageView,
        @Suppress("UNUSED_PARAMETER") uri: Uri,
        error: Exception?,
    ) {
        if (error != null) {
            Timber.e(error, "Failed to load image by URI")
            showSnackbar(R.string.something_wrong)
        }
    }

    private fun onCropImageComplete(
        @Suppress("UNUSED_PARAMETER") view: CropImageView,
        result: CropImageView.CropResult,
    ) {
        if (result.error == null) {
            val resultIntent = Intent()
            resultIntent.putExtra(
                CROP_IMAGE_RESULT,
                CropResultData(
                    uriContent = result.uriContent,
                    uriPath = context?.let { result.getUriFilePath(it) },
                ),
            )
            activity?.setResult(Activity.RESULT_OK, resultIntent)
            activity?.finish()
        } else {
            Timber.e(result.error, "Failed to crop image")
            showSnackbar(R.string.something_wrong)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cropImageView.setOnSetImageUriCompleteListener(null)
        cropImageView.setOnCropImageCompleteListener(null)
    }

    companion object {
        /**
         * The key for the original image URI passed as an argument.
         */
        const val CROP_IMAGE_URI = "image_uri"

        /**
         * The key for the cropped image path sent back in the result Intent.
         */
        const val CROP_IMAGE_RESULT = "crop_image_result"
    }

    @Parcelize
    data class CropResultData(
        val uriContent: Uri? = null,
        val uriPath: String? = null,
    ) : Parcelable
}
