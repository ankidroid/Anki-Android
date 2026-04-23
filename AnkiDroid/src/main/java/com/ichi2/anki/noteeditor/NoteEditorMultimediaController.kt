/*
 * Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>
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

package com.ichi2.anki.noteeditor

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.NoteEditorFragment
import com.ichi2.anki.exception.MediaSizeLimitExceededException
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.multimedia.MultimediaActionHandler
import com.ichi2.anki.multimedia.MultimediaActivityExtra
import com.ichi2.anki.multimedia.MultimediaResult
import com.ichi2.anki.multimediacard.fields.EFieldType
import com.ichi2.anki.multimediacard.fields.IField
import com.ichi2.anki.servicelayer.NoteService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Owns the multimedia capture lifecycle for a [NoteEditorFragment].
 *
 * The [launcher] must already be registered on the fragment (an
 * `ActivityResultLauncher` can only be created during fragment initialisation);
 * its callback forwards to [handleActions] / [handleResult].
 */
internal class NoteEditorMultimediaController(
    private val fragment: NoteEditorFragment,
    private val launcher: ActivityResultLauncher<Intent>,
) {
    private var actionJob: Job? = null

    /**
     * Subscribes to the next [MultimediaActionHandler] emitted by the bottom sheet
     * and launches its capture screen against [fieldIndex]. Cancels any previously
     * pending subscription so rapid field switches don't stack.
     */
    fun handleActions(fieldIndex: Int) {
        actionJob?.cancel()
        actionJob =
            fragment.lifecycleScope.launch {
                val note = fragment.getCurrentMultimediaEditableNote()
                if (note.isEmpty) return@launch

                fragment.multimediaViewModel.multimediaAction.first { action ->
                    Timber.i("Selected multimedia action: %s", action)
                    val handler = MultimediaActionHandler.forAction(action)
                    val field = handler.createField().also { note.setField(fieldIndex, it) }
                    val intent =
                        handler.buildIntent(
                            fragment.requireContext(),
                            MultimediaActivityExtra(fieldIndex, field, note),
                        )
                    launcher.launch(intent)
                    true
                }
            }
    }

    /** Imports the captured media into the collection if the result carries any. */
    fun handleResult(result: MultimediaResult.Success) {
        val field = result.field
        if (field.type != EFieldType.TEXT || field.mediaFile != null) {
            performAddMedia(result.fieldIndex, field, skipSizeCheck = false)
        } else {
            Timber.i("field imagePath and audioPath are both null")
        }
    }

    private fun performAddMedia(
        index: Int,
        field: IField,
        skipSizeCheck: Boolean,
    ) {
        fragment.launchCatchingTask {
            try {
                // Import field media before setting formattedValue so media paths
                // reflect the checksum when names collide.
                withCol {
                    NoteService.importMediaToDirectory(this, field, skipSizeCheck = skipSizeCheck)
                }

                val fieldEditText = fragment.editFieldAt(index) ?: return@launchCatchingTask
                val formattedValue = field.formattedValue
                if (field.type === EFieldType.TEXT) {
                    fieldEditText.setText(formattedValue)
                } else if (fieldEditText.text != null) {
                    fragment.insertStringInField(fieldEditText, formattedValue)
                }
                fragment.markMultimediaChanged()
            } catch (e: MediaSizeLimitExceededException) {
                fragment.showLargeMediaFileWarning(
                    e.fileName,
                    e.fileSize,
                    onForceAdd = { performAddMedia(index, field, skipSizeCheck = true) },
                )
            } catch (oomError: OutOfMemoryError) {
                // TODO: a 'retry' flow would be possible here
                throw Exception(oomError)
            }
        }
    }
}
