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

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.edit
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.R
import com.ichi2.anki.preferences.AppearanceSettingsFragment
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.snackbar.showSnackbar
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

const val BITMAP_BYTES_PER_PIXEL = 4

object BackgroundImage {
    /*
     * RecordingCanvas.MAX_BITMAP_SIZE is @hide
     * https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/graphics/java/android/graphics/RecordingCanvas.java;l=49;drc=ed769e0aede2a840ea8cdff87ce593eb6ea8a7c6;bpv=1;bpt=1?q=%22trying%20to%20draw%20too%20large%22
     *
     * WARN: This skips a test for "ro.hwui.max_texture_allocation_size"
     * The actual size may be larger, this is a minimum
     */
    const val MAX_BITMAP_SIZE: Long = 100 * 1024 * 1024

    /**
     * @see shouldBeShown
     */
    var enabled: Boolean
        get() = AnkiDroidApp.instance.sharedPrefs().getBoolean("deckPickerBackground", false)
        set(value) {
            AnkiDroidApp.instance.sharedPrefs().edit {
                putBoolean("deckPickerBackground", value)
            }
        }

    fun shouldBeShown(context: Context) = enabled && getImageFile(context) != null

    sealed interface FileSizeResult {
        data object OK : FileSizeResult

        /** Large files can cause OutOfMemoryError */
        data class FileTooLarge(val currentMB: Long, val maxMB: Long) : FileSizeResult

        /** Large bitmaps cause uncatchable: RuntimeException("Canvas: trying to draw too large(Xbytes) bitmap.") */
        data class UncompressedBitmapTooLarge(val width: Long, val height: Long) : FileSizeResult
    }

    context (AppearanceSettingsFragment)
    fun validateBackgroundImageFileSize(selectedImage: Uri): FileSizeResult {
        val filePathColumn = arrayOf(MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.WIDTH, MediaStore.MediaColumns.HEIGHT)
        requireContext().contentResolver.query(selectedImage, filePathColumn, null, null, null).use { cursor ->
            cursor!!.moveToFirst()
            val fileSizeInMB = cursor.getLong(0) / (1024 * 1024)
            if (fileSizeInMB >= 10) {
                return FileSizeResult.FileTooLarge(currentMB = fileSizeInMB, maxMB = 10)
            }

            val width = cursor.getLong(1)
            val height = cursor.getLong(2)

            // Default MAX_IMAGE_SIZE on Android
            if (width * height * BITMAP_BYTES_PER_PIXEL > MAX_BITMAP_SIZE) {
                return FileSizeResult.UncompressedBitmapTooLarge(width = width, height = height)
            }

            return FileSizeResult.OK
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
        this.enabled = true
    }

    data class Size(val width: Int, val height: Int)
    fun getBackgroundImageDimensions(context: Context): Size {
        val currentAnkiDroidDirectory = CollectionHelper.getCurrentAnkiDroidDirectory(context)
        val imageName = "DeckPickerBackground.png"
        val destFile = File(currentAnkiDroidDirectory, imageName)
        val bmp = BitmapFactory.decodeFile(destFile.absolutePath)
        val w = bmp.width
        val h = bmp.height
        bmp.recycle()
        return Size(width = w, height = h)
    }

    /**
     * @return `true` if the image no longer exists. `false` if an error occurred
     */
    fun remove(context: Context): Boolean {
        val imgFile = getImageFile(context)
        enabled = false
        if (imgFile == null) {
            return true
        }
        return imgFile.delete()
    }

    /** @return a [File] referencing the image, or `null` if the file does not exist */
    private fun getImageFile(context: Context): File? {
        val currentAnkiDroidDirectory = CollectionHelper.getCurrentAnkiDroidDirectory(context)
        val imgFile = File(currentAnkiDroidDirectory, "DeckPickerBackground.png")
        if (!imgFile.exists()) {
            return null
        }
        return imgFile
    }
}
