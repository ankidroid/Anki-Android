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
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.R
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.compat.CompatHelper
import com.ichi2.ui.FixedTextView
import com.ichi2.utils.ExceptionUtil.executeSafe
import com.ichi2.utils.KotlinCleanup
import timber.log.Timber
import java.io.File

class BasicMediaClipFieldController : FieldControllerBase(), IFieldController {
    private var storingDirectory: File? = null

    @KotlinCleanup("Convert to FixedTextView and make lateinit")
    private var tvAudioClip: TextView? = null

    @KotlinCleanup("make context non-null")
    override fun createUI(context: Context?, layout: LinearLayout?) {
        val col = CollectionHelper.getInstance().getCol(context)
        storingDirectory = File(col.media.dir())
        // #9639: .opus is application/octet-stream in API 26,
        // requires a workaround as we don't want to enable application/octet-stream by default
        val btnLibrary = Button(mActivity)
        btnLibrary.text = mActivity.getText(R.string.multimedia_editor_import_audio)
        btnLibrary.setOnClickListener {
            openChooserPrompt(
                "audio/*",
                arrayOf("audio/*", "application/ogg"), // #9226: allows ogg on Android 8
                R.string.multimedia_editor_popup_audio_clip,
                ACTIVITY_SELECT_AUDIO_CLIP
            )
        }
        layout!!.addView(btnLibrary, ViewGroup.LayoutParams.MATCH_PARENT)
        val btnVideo = Button(mActivity).apply {
            text = mActivity.getText(R.string.multimedia_editor_import_video)
            setOnClickListener {
                openChooserPrompt(
                    "video/*",
                    emptyArray(),
                    R.string.multimedia_editor_popup_video_clip,
                    ACTIVITY_SELECT_VIDEO_CLIP
                )
            }
        }
        layout.addView(btnVideo, ViewGroup.LayoutParams.MATCH_PARENT)
        tvAudioClip = FixedTextView(mActivity)
        if (mField.audioPath == null) {
            (tvAudioClip as FixedTextView).setVisibility(View.GONE)
        } else {
            (tvAudioClip as FixedTextView).setText(mField.audioPath)
            (tvAudioClip as FixedTextView).setVisibility(View.VISIBLE)
        }
        layout.addView(tvAudioClip, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    private fun openChooserPrompt(initialMimeType: String, extraMimeTypes: Array<String>, @StringRes prompt: Int, resultCode: Int) {
        val allowAllFiles = AnkiDroidApp.getSharedPrefs(this.mActivity).getBoolean("mediaImportAllowAllFiles", false)
        val i = Intent()
        i.type = if (allowAllFiles) "*/*" else initialMimeType
        if (!allowAllFiles && extraMimeTypes.any()) {
            // application/ogg takes precedence over "*/*" for application/octet-stream
            // so don't add it if we're want */*
            i.putExtra(Intent.EXTRA_MIME_TYPES, extraMimeTypes)
        }
        i.action = Intent.ACTION_GET_CONTENT
        // Only get openable files, to avoid virtual files issues with Android 7+
        i.addCategory(Intent.CATEGORY_OPENABLE)
        val chooserPrompt = mActivity.resources.getString(prompt)
        mActivity.startActivityForResultWithoutAnimation(Intent.createChooser(i, chooserPrompt), resultCode)
    }

    @KotlinCleanup("make data non-null")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_CANCELED && requestCode == ACTIVITY_SELECT_AUDIO_CLIP) {
            executeSafe(mActivity, "handleMediaSelection:unhandled") {
                handleMediaSelection(data!!, "ankidroid_audioclip_")
            }
        }
        if (resultCode != Activity.RESULT_CANCELED && requestCode == ACTIVITY_SELECT_VIDEO_CLIP) {
            executeSafe(mActivity, "handleMediaSelection:unhandled") {
                handleMediaSelection(data!!, "ankidroid_videoclip_")
            }
        }
    }

    private fun handleMediaSelection(data: Intent, clipNamePrefix: String) {
        val selectedClip = data.data

        // Get information about the selected document
        val queryColumns = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.MIME_TYPE)
        var mediaClipFullNameParts: Array<String>
        mActivity.contentResolver.query(selectedClip!!, queryColumns, null, null, null).use { cursor ->
            if (cursor == null) {
                showThemedToast(
                    AnkiDroidApp.getInstance().applicationContext,
                    AnkiDroidApp.getInstance().getString(R.string.multimedia_editor_something_wrong), true
                )
                return
            }
            cursor.moveToFirst()
            var mediaClipFullName = cursor.getString(0)
            mediaClipFullName = checkFileName(mediaClipFullName)
            mediaClipFullNameParts = mediaClipFullName.split("\\.").toTypedArray()
            if (mediaClipFullNameParts.size < 2) {
                mediaClipFullNameParts = try {
                    Timber.i("Media clip name does not have extension, using second half of mime type")
                    arrayOf(mediaClipFullName, cursor.getString(2).split("/").toTypedArray()[1])
                } catch (e: Exception) {
                    Timber.w(e)
                    // This code is difficult to stabilize - it is not clear how to handle files with no extension
                    // and apparently we may fail to get MIME_TYPE information - in that case we will gather information
                    // about what people are experiencing in the real world and decide later, but without crashing at least
                    AnkiDroidApp.sendExceptionReport(e, "Media Clip addition failed. Name " + mediaClipFullName + " / cursor mime type column type " + cursor.getType(2))
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
                clipNamePrefix + mediaClipFullNameParts[0],
                "." + mediaClipFullNameParts[1],
                storingDirectory
            )
            Timber.d("media clip picker file path is: %s", clipCopy.absolutePath)
        } catch (e: Exception) {
            Timber.e(e, "Could not create temporary media file. ")
            AnkiDroidApp.sendExceptionReport(e, "handleMediaSelection:tempFile")
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
            Timber.e(e, "Unable to copy media file from ContentProvider")
            AnkiDroidApp.sendExceptionReport(e, "handleMediaSelection:copyFromProvider")
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
        private const val ACTIVITY_SELECT_VIDEO_CLIP = 2

        /**
         * This method replaces any character that isn't a number, letter or underscore with underscore in file name.
         * This method doesn't check that file name is valid or not it simply operates on all file name.
         * @param mediaClipFullName name of the file.
         * @return file name which is valid.
         */
        @JvmStatic
        @VisibleForTesting
        fun checkFileName(mediaClipFullName: String): String {
            return mediaClipFullName.replace("[^\\w.]+".toRegex(), "_")
        }
    }
}
