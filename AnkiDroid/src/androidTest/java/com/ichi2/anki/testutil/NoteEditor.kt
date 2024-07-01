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

package com.ichi2.anki.testutil

import androidx.test.core.app.ActivityScenario
import com.ichi2.anki.NoteEditor
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import java.util.concurrent.atomic.AtomicReference

@Throws(Throwable::class)
fun ActivityScenario<SingleFragmentActivity>.onNoteEditor(
    block: (NoteEditor) -> Unit
) {
    val wrapped = AtomicReference<Throwable?>(null)
    this.onActivity { activity: SingleFragmentActivity ->
        try {
            val editor = activity.supportFragmentManager.findFragmentById(R.id.fragment_container) as NoteEditor
            block(editor)
        } catch (t: Throwable) {
            wrapped.set(t)
        }
    }
    wrapped.get()?.let { throw it }
}
