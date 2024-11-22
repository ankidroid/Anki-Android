/****************************************************************************************
 * Copyright (c) 2024 Sanjay Sargam <sargamsanjaykumar@gmail.com>                          *
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

package com.ichi2.anki

import android.os.Bundle
import com.ichi2.anki.noteeditor.NoteEditorLauncher
import timber.log.Timber

/**
 * This activity serves as an intermediate handler to process various types of intents and forward them to the NoteEditor fragment hosted within the SingleFragmentActivity.
 *
 * The main reason for using this IntentHandler2 is to avoid conflicts in the manifest file. We can't have multiple
 * ACTION_SEND intents in same activity (IntentHandler) that only differ by their labels. By using this handler, we can manage these intents
 * and make sure they are sent to the NoteEditor correctly.
 */
class IntentHandler2 : AbstractIntentHandler() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.v(intent.toString())
        if (NoteEditor.intentLaunchedWithImage(intent)) {
            Timber.i("Intent contained an image")
            intent.putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.CALLER_ADD_IMAGE)
        }
        if (intent.extras == null) {
            Timber.w("Intent unexpectedly has no extras. Notifying user, defaulting to add note.")
            showThemedToast(this, getString(R.string.something_wrong), false)
            startActivity(NoteEditorLauncher.AddNote().getIntent(this))
            finish()
        } else {
            val noteEditorIntent =
                NoteEditorLauncher.PassArguments(intent.extras!!).getIntent(this, intent.action)
            noteEditorIntent.setDataAndType(intent.data, intent.type)
            startActivity(noteEditorIntent)
            finish()
        }
    }
}
