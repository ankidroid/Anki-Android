/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.anki.multimediacard.activity

import android.Manifest
import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.multimediacard.IMultimediaEditableNote
import com.ichi2.anki.multimediacard.fields.IField
import com.ichi2.anki.multimediacard.fields.IFieldController
import com.ichi2.anki.multimediacard.fields.TextField
import com.ichi2.anki.multimediacard.impl.MultimediaEditableNote
import com.ichi2.annotations.KotlinCleanup
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.android.controller.ActivityController

abstract class MultimediaEditFieldActivityTestBase : RobolectricTest() {
    protected fun grantRecordAudioPermission() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val app = Shadows.shadowOf(application)
        app.grantPermissions(Manifest.permission.RECORD_AUDIO)
    }

    protected fun getControllerForField(field: IField, note: IMultimediaEditableNote, fieldIndex: Int): IFieldController {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra(MultimediaEditFieldActivity.EXTRA_MULTIMEDIA_EDIT_FIELD_ACTIVITY, MultimediaEditFieldActivityExtra(fieldIndex, field, note))
        return getControllerForIntent(intent)
    }

    protected val emptyNote: IMultimediaEditableNote
        get() {
            val note = MultimediaEditableNote()
            note.setNumFields(1)
            note.setField(0, TextField())
            note.freezeInitialFieldValues()
            return note
        }

    private fun getControllerForIntent(intent: Intent?): IFieldController {
        val multimediaController: ActivityController<*> = Robolectric.buildActivity(MultimediaEditFieldActivity::class.java, intent)
            .create().start().resume().visible()
        saveControllerForCleanup(multimediaController)
        val testCardTemplatePreviewer = multimediaController.get() as MultimediaEditFieldActivity
        return testCardTemplatePreviewer.fieldController!!
    }

    @KotlinCleanup("need a disabled lint check for this as it's a common issue/operation")
    protected fun setupActivityMock(controller: IFieldController, editFieldActivity: MultimediaEditFieldActivity): MultimediaEditFieldActivity {
        val activity = mock(MultimediaEditFieldActivity::class.java)

        whenever(activity.resources).thenReturn(editFieldActivity.resources)
        controller.setEditingActivity(activity)
        return activity
    }
}
