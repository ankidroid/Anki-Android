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

package com.ichi2.anki.noteeditor

import android.content.Context
import android.content.Intent
import android.os.Parcelable
import androidx.annotation.CheckResult
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.NoteEditor
import com.ichi2.libanki.CardId

/**
 * Opens the [Note Editor][NoteEditor] to the note of a selected card
 *
 * As the card of the note is known, additional context is provided to the UI
 */
data class EditCardDestination(val cardId: CardId)

@CheckResult
fun EditCardDestination.toIntent(context: Context, animation: ActivityTransitionAnimation.Direction): Intent {
    return Intent(context, NoteEditor::class.java).apply {
        putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.CALLER_REVIEWER_EDIT)
        putExtra(NoteEditor.EXTRA_CARD_ID, cardId)
        putExtra(AnkiActivity.FINISH_ANIMATION_EXTRA, animation as Parcelable)
    }
}
