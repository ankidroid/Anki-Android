/*
 * Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.noteeditor

import android.os.Bundle
import com.ichi2.anki.NoteEditorActivity
import com.ichi2.anki.NoteEditorFragment
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.annotations.DuplicatedCode

/**
 * Hosts a [NoteEditorActivity] with the given launcher [arguments] and returns the
 * embedded [NoteEditorFragment]. Shared between tests that need to drive note-editor
 * flows without re-implementing the activity setup.
 */
fun RobolectricTest.openNoteEditorWithArgs(
    arguments: Bundle,
    action: String? = null,
): NoteEditorFragment {
    val activity =
        startActivityNormallyOpenCollectionWithIntent(
            NoteEditorActivity::class.java,
            NoteEditorLauncher.PassArguments(arguments).toIntent(targetContext, action),
        )
    return activity.getNoteEditorFragment()
}

@DuplicatedCode("NoteEditor in androidTest")
fun NoteEditorActivity.getNoteEditorFragment(): NoteEditorFragment =
    supportFragmentManager.findFragmentById(R.id.note_editor_fragment_frame) as NoteEditorFragment
