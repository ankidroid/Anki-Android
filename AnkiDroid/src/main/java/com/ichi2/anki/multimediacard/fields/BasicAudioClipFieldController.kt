/****************************************************************************************
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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.R
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.compat.CompatHelper
import com.ichi2.ui.FixedTextView
import com.ichi2.utils.KotlinCleanup
import timber.log.Timber
import java.io.File

class BasicAudioClipFieldController : FieldControllerBase(), IFieldController {
    private var storingDirectory: File? = null

    @KotlinCleanup("Convert to FixedTextView and make lateinit")
    private var tvAudioClip: TextView? = null
    override fun createUI(context: Context, layout: LinearLayout) {
        val col = CollectionHelper.getInstance().getCol(context)
        storingDirectory = File(col.media.dir())
        // #9639: .opus is application/octet-stream in API 26,
        // requires a workaround as we don't want to enable application/octet-stream by default
        val allowAllFiles = AnkiDroidApp.getSharedPrefs(context).getBoolean("mediaImportAllowAllFiles", false)
        val btnLibrary = Button(mActivity)
        btnLibrary.text = mActivity.getText(R.string.multimedia_editor_image_field_editing_library)
        btnLibrary.setOnClickListener {
            val i = Intent()
            i.type = if (allowAllFiles) "*/*" else "audio/*"
            if (!allowAllFiles) {
                // application/ogg takes precedence over "*/*" for application/octet-stream
                // so don't add it if we're want */*
                val extraMimeTypes = arrayOf("audio/*", "application/ogg") // #9226 allows ogg on Android 8
                i.putExtra(Intent.EXTRA_MIME_TYPES, extraMimeTypes)
            }
            i.action = Intent.ACTION_GET_CONTENT
            // Only get openable files, to avoid virtual files issues with Android 7+
            i.addCategory(Intent.CATEGORY_OPENABLE)
            val chooserPrompt = mActivity.resources.getString(R.string.multimedia_editor_popup_audio_clip)
            mActivity.startActivityForResultWithoutAnimation(Intent.createChooser(i, chooserPrompt), ACTIVITY_SELECT_AUDIO_CLIP)
        }
        layout.addView(btnLibrary, ViewGroup.LayoutParams.MATCH_PARENT)
        tvAudioClip = FixedTextView(mActivity)
        if (mField.audioPath == null) {
            (tvAudioClip as FixedTextView).setVisibility(View.GONE)
        } else {
            (tvAudioClip as FixedTextView).setText(mField.audioPath)
            (tvAudioClip as FixedTextView).setVisibility(View.VISIBLE)
        }
        layout.addView(tvAudioClip, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        if (resultCode != Activity.RESULT_CANCELED && requestCode == ACTIVITY_SELECT_AUDIO_CLIP) {
            try {
                handleAudioSelection(data)
            } catch (e: Exception) {
                AnkiDroidApp.sendExceptionReport(e, "handleAudioSelection:unhandled")
                showThemedToast(
                    AnkiDroidApp.getInstance().applicationContext,
                    AnkiDroidApp.getInstance().getString(R.string.multimedia_editor_something_wrong), true
                )
            }
        }
    }

    private fun handleAudioSelection(data: Intent) {
        val selectedClip = data.data

        // Get information about the selected document
        val queryColumns = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.MIME_TYPE)
        var audioClipFullNameParts: Array<String>
        mActivity.contentResolver.query(selectedClip!!, queryColumns, null, null, null).use { cursor ->
            if (cursor == null) {
                showThemedToast(
                    AnkiDroidApp.getInstance().applicationContext,
                    AnkiDroidApp.getInstance().getString(R.string.multimedia_editor_something_wrong), true
                )
                return
            }
            cursor.moveToFirst()
            var audioClipFullName = cursor.getString(0)
            audioClipFullName = checkFileName(audioClipFullName)
            audioClipFullNameParts = audioClipFullName.split("\\.").toTypedArray()
            if (audioClipFullNameParts.size < 2) {
                audioClipFullNameParts = try {
                    Timber.i("Audio clip name does not have extension, using second half of mime type")
                    arrayOf(audioClipFullName, cursor.getString(2).split("/").toTypedArray()[1])
                } catch (e: Exception) {
                    Timber.w(e)
                    // This code is difficult to stabilize - it is not clear how to handle files with no extension
                    // and apparently we may fail to get MIME_TYPE information - in that case we will gather information
                    // about what people are experiencing in the real world and decide later, but without crashing at least
                    AnkiDroidApp.sendExceptionReport(e, "Audio Clip addition failed. Name " + audioClipFullName + " / cursor mime type column type " + cursor.getType(2))
                    showThemedToast(
                        AnkiDroidApp.getInstance().applicationContext,
                        AnkiDroidApp.getInstance().getString(R.string.multimedia_editor_something_wrong), true
                    )
                    return
                }
            }
        }

        // We may receive documents we can't access directly, we have to copy to a temp file
        val clipCopy: File
        try {
            clipCopy = File.createTempFile(
                "ankidroid_audioclip_" + audioClipFullNameParts[0],
                "." + audioClipFullNameParts[1],
                storingDirectory
            )
            Timber.d("audio clip picker file path is: %s", clipCopy.absolutePath)
        } catch (e: Exception) {
            Timber.e(e, "Could not create temporary audio file. ")
            AnkiDroidApp.sendExceptionReport(e, "handleAudioSelection:tempFile")
            showThemedToast(
                AnkiDroidApp.getInstance().applicationContext,
                AnkiDroidApp.getInstance().getString(R.string.multimedia_editor_something_wrong), true
            )
            return
        }

        // Copy file contents into new temp file. Possibly check file size first and warn if large?
        try {
            mActivity.contentResolver.openInputStream(selectedClip).use { inputStream ->
                CompatHelper.getCompat().copyFile(inputStream, clipCopy.absolutePath)

                // If everything worked, hand off the information
                mField.setHasTemporaryMedia(true)
                mField.audioPath = clipCopy.absolutePath
                tvAudioClip!!.text = mField.formattedValue
                tvAudioClip!!.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            Timber.e(e, "Unable to copy audio file from ContentProvider")
            AnkiDroidApp.sendExceptionReport(e, "handleAudioSelection:copyFromProvider")
            showThemedToast(
                AnkiDroidApp.getInstance().applicationContext,
                AnkiDroidApp.getInstance().getString(R.string.multimedia_editor_something_wrong), true
            )
        }
    }

    override fun onDone() {
        /* nothing */
    }

    override fun onFocusLost() {
        /* nothing */
    }

    override fun onDestroy() {
        /* nothing */
    }

    companion object {
        private const val ACTIVITY_SELECT_AUDIO_CLIP = 1

        /**
         * This method replaces any character that isn't a number, letter or underscore with underscore in file name.
         * This method doesn't check that file name is valid or not it simply operates on all file name.
         * @param audioClipFullName name of the file.
         * @return file name which is valid.
         */
        @JvmStatic
        @VisibleForTesting
        fun checkFileName(audioClipFullName: String): String {
            return audioClipFullName.replace("[^\\w.]+".toRegex(), "_")
        }
    }
}
