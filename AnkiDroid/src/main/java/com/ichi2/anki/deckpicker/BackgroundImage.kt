/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.deckpicker

import android.net.Uri
import android.provider.MediaStore
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.R
import com.ichi2.anki.preferences.AppearanceSettingsFragment
import com.ichi2.anki.snackbar.showSnackbar
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object BackgroundImage {
    sealed interface FileSizeResult {
        data object OK : FileSizeResult
        data class FileTooLarge(val currentMB: Long, val maxMB: Long) : FileSizeResult
    }

    context (AppearanceSettingsFragment)
    fun validateBackgroundImageFileSize(selectedImage: Uri): FileSizeResult {
        val filePathColumn = arrayOf(MediaStore.MediaColumns.SIZE)
        requireContext().contentResolver.query(selectedImage, filePathColumn, null, null, null).use { cursor ->
            cursor!!.moveToFirst()
            val fileSizeInMB = cursor.getLong(0) / (1024 * 1024)
            if (fileSizeInMB >= 10) {
                return FileSizeResult.FileTooLarge(currentMB = fileSizeInMB, maxMB = 10)
            } else {
                return FileSizeResult.OK
            }
        }
    }

    context (AppearanceSettingsFragment)
    fun import(selectedImage: Uri) {
        val currentAnkiDroidDirectory = CollectionHelper.getCurrentAnkiDroidDirectory(requireContext())
        val imageName = "DeckPickerBackground.png"
        val destFile = File(currentAnkiDroidDirectory, imageName)
        (requireContext().contentResolver.openInputStream(selectedImage) as FileInputStream).channel.use { sourceChannel ->
            FileOutputStream(destFile).channel.use { destChannel ->
                destChannel.transferFrom(sourceChannel, 0, sourceChannel.size())
                showSnackbar(R.string.background_image_applied)
            }
        }
    }
}
