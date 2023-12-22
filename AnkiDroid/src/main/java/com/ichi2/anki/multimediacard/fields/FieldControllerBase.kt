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

import android.os.Bundle
import com.ichi2.anki.multimediacard.IMultimediaEditableNote
import com.ichi2.anki.multimediacard.activity.MultimediaEditFieldActivity
import com.ichi2.utils.KotlinCleanup

@KotlinCleanup("remove hungarian notation")
@Suppress("VariableNamingDetector")
abstract class FieldControllerBase : IFieldController {
    @KotlinCleanup("transform mActivity into a property")
    protected lateinit var mActivity: MultimediaEditFieldActivity
    protected lateinit var mField: IField
    protected lateinit var mNote: IMultimediaEditableNote
    protected var mIndex = 0

    override fun setField(field: IField) {
        mField = field
    }

    override fun setNote(note: IMultimediaEditableNote) {
        mNote = note
    }

    override fun setFieldIndex(index: Int) {
        mIndex = index
    }

    override fun setEditingActivity(activity: MultimediaEditFieldActivity) {
        mActivity = activity
    }

    fun getActivity(): MultimediaEditFieldActivity {
        return mActivity
    }

    override fun loadInstanceState(savedInstanceState: Bundle?) { // Default implementation does nothing
    }

    override fun saveInstanceState(): Bundle? {
        return null
    }
}
