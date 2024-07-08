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

package com.ichi2.anki

import android.content.Context
import anki.notes.NoteFieldsCheckResponse
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.NoteFieldsCheckResult.*
import com.ichi2.anki.utils.ext.isImageOcclusion
import com.ichi2.libanki.Note
import timber.log.Timber

/**
 * The result of [checking note fields][Note.fieldsCheck], either a [Success], or [Failure] with
 * user-readable message
 *
 * @see NoteFieldsCheckResponse.State
 * @see checkNoteFieldsResponse
 */
sealed interface NoteFieldsCheckResult {
    data object Success : NoteFieldsCheckResult

    /** @property localizedMessage user-readable error message */
    data class Failure(private val localizedMessage: String?) : NoteFieldsCheckResult {
        fun getLocalizedMessage(context: Context) = localizedMessage ?: context.getString(R.string.something_wrong)
    }
}

/**
 * Validates fields of a note; produces a human-readable error on failure
 *
 * @see NoteFieldsCheckResult
 * @see Note.fieldsCheck
 **/
suspend fun checkNoteFieldsResponse(note: Note): NoteFieldsCheckResult {
    Timber.d("validating note fields")
    val fieldsCheckState = withCol { note.fieldsCheck(this) }

    return when (fieldsCheckState) {
        NoteFieldsCheckResponse.State.NORMAL, NoteFieldsCheckResponse.State.DUPLICATE
        -> Success

        NoteFieldsCheckResponse.State.EMPTY -> if (note.notetype.isImageOcclusion) {
            Failure(TR.notetypesNoOcclusionCreated2())
        } else {
            Failure(TR.addingTheFirstFieldIsEmpty())
        }

        NoteFieldsCheckResponse.State.MISSING_CLOZE ->
            Failure(TR.addingYouHaveAClozeDeletionNote())

        NoteFieldsCheckResponse.State.NOTETYPE_NOT_CLOZE ->
            Failure(TR.addingClozeOutsideClozeNotetype())

        NoteFieldsCheckResponse.State.FIELD_NOT_CLOZE ->
            Failure(TR.addingClozeOutsideClozeField())

        NoteFieldsCheckResponse.State.UNRECOGNIZED ->
            Failure(localizedMessage = null)
    }
}
