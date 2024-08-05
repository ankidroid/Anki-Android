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
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.os.BundleCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import com.canhub.cropper.CropImageView
import com.google.android.material.color.MaterialColors
import com.ichi2.anki.AnkiFragment
import com.ichi2.anki.snackbar.showSnackbar
import kotlinx.parcelize.Parcelize
import timber.log.Timber

/**
 * Fragment for cropping images within the AnkiDroid, uses [CropImageView] to crop the image and
 * sends the image uri as result.
 *
 * Portions of this code were adapted from the Canub project.
 * Original source: https://github.com/CanHub/Android-Image-Cropper
 *
 * Attribution to the original authors of the CanHub/Android-Image-Cropper for their contributions.
 */
class ImageCropper :
    AnkiFragment(com.ichi2.anki.R.layout.fragment_image_cropper),
    CropImageView.OnSetImageUriCompleteListener,
    CropImageView.OnCropImageCompleteListener {

    private lateinit var cropImageView: CropImageView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cropImageView = findViewById(com.ichi2.anki.R.id.cropImageView)
        ankiActivity.setSupportActionBar(findViewById(com.ichi2.anki.R.id.toolbar))
        setOptions()
        setUpMenu()
        cropImageView.setOnSetImageUriCompleteListener(this)
        cropImageView.setOnCropImageCompleteListener(this)

        val originalImageUri =
            BundleCompat.getParcelable(requireArguments(), CROP_IMAGE_URI, Uri::class.java)
        cropImageView.setImageUriAsync(originalImageUri)
    }

    private fun setUpMenu() {
        val menuHost: MenuHost = ankiActivity
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menu.clear()
                menuInflater.inflate(com.canhub.cropper.R.menu.crop_image_menu, menu)

                // Canhub has white color icons we need to change that
                for (i in 0 until menu.size()) {
                    val menuItem = menu.getItem(i)
                    menuItem.icon?.setTint(
                        MaterialColors.getColor(
                            requireContext(),
                            com.ichi2.anki.R.attr.toolbarIconColor,
                            0
                        )
                    )
                }
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    com.canhub.cropper.R.id.crop_image_menu_crop -> {
                        Timber.d("Crop image clicked")
                        cropImageView.croppedImageAsync()
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
            }
        })
    }

    override fun onSetImageUriComplete(view: CropImageView, uri: Uri, error: Exception?) {
        if (error != null) {
            Timber.e(error, "Failed to load image by URI")
            showSnackbar(com.ichi2.anki.R.string.something_wrong)
        }
    }

    override fun onCropImageComplete(view: CropImageView, result: CropImageView.CropResult) {
        if (result.error == null) {
            sendCropResult(result)
        } else {
            Timber.e(result.error, "Failed to crop image")
            showSnackbar(com.ichi2.anki.R.string.something_wrong)
        }
    }

    private fun setOptions() {
        cropImageView.cropRect = Rect(100, 300, 500, 1200)
    }

    private fun sendCropResult(result: CropImageView.CropResult) {
        val resultIntent = Intent().apply {
            putExtra(CROP_IMAGE_RESULT, CropResultData(error = result.error, uriContent = result.uriContent, uriPath = result.getUriFilePath(ankiActivity)))
        }
        activity?.setResult(Activity.RESULT_OK, resultIntent)
        activity?.finish()
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
        val error: Exception? = null,
        val uriContent: Uri? = null,
        val uriPath: String? = null
    ) : Parcelable
}
