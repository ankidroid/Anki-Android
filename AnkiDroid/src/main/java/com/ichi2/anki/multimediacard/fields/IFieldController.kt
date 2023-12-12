/****************************************************************************************
 * Copyright (c) 2013 Bibek Shrestha <bibekshrestha@gmail.com>                          *
 * Copyright (c) 2013 Zaur Molotnikov <qutorial@gmail.com>                              *
 * Copyright (c) 2013 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>                                   *
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

import android.content.Context
import android.os.Bundle
import android.widget.LinearLayout
import com.ichi2.anki.multimediacard.IMultimediaEditableNote
import com.ichi2.anki.multimediacard.activity.MultimediaEditFieldActivity

/**
 * A note in anki has fields. Each of the fields can be edited.
 * <p>
 * A controller is about to decide, which UI elements have to be on the activity and what has to be done there to edit a
 * field.
 * <p>
 * MultimediaEditFieldActivity calls controller's set methods by protocol before it works on UI creation.
 */
interface IFieldController {

    // This is guaranteed to be called before create UI, so that the controller
    // is aware of the field, including type an content.
    fun setField(field: IField)

    // This is guaranteed to be called before create UI, so that the controller
    // is aware of the note.
    fun setNote(note: IMultimediaEditableNote)

    // This is guaranteed to be called before create UI, so that the controller
    // is aware of the field index in the note.
    fun setFieldIndex(index: Int)

    // Called before other
    fun setEditingActivity(activity: MultimediaEditFieldActivity)

    // Called after setting field/note/index/activity, allows state persistence across Activity restarts
    fun loadInstanceState(savedInstanceState: Bundle?)

    // Called during editing Activity pause, allows state persistence across Activity restarts
    fun saveInstanceState(): Bundle?

    // Layout is vertical inside a scroll view already
    fun createUI(context: Context, layout: LinearLayout)

    // Called when the controller has stopped showing the field in favor of another one
    fun onFocusLost()

    // Is called to apply in the field new data from UI.
    fun onDone()

    // Called to free memory
    fun onDestroy()
}
