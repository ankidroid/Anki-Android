/*
 *  Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.utils

import android.net.Uri
import androidx.activity.result.ActivityResultRegistry
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import timber.log.Timber

object ImageUtils {
    private const val CROP_IMAGE_KEY = "crop_image_launcher"

    /**
     * Initiates the image cropping process using the specified ActivityResultRegistry, URI of the image to crop,
     * and a callback function to handle the result.
     *
     * @param registry The ActivityResultRegistry instance used for registering the activity result.
     * @param uri The URI of the image to be cropped.
     * @param callback The callback function that receives the crop result (CropImageView.CropResult) or null if the cropping operation fails.
     */
    fun cropImage(
        registry: ActivityResultRegistry,
        uri: Uri,
        callback: (CropImageView.CropResult?) -> Unit
    ) {
        val cropImage = registry.register(CROP_IMAGE_KEY, CropImageContract()) { result ->
            if (result.isSuccessful) {
                callback(result)
            } else {
                Timber.v(result.error)
                callback(null)
            }
        }
        cropImage.launch(
            CropImageContractOptions(
                uri,
                CropImageOptions()
            )
        )
    }
}
