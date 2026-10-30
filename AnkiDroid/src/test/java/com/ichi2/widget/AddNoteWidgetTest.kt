// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.NoteEditorActivity
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.widgets.databinding.WidgetAddNoteBinding
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class AddNoteWidgetTest : RobolectricTest() {
    @Test
    fun `add note button opens the note editor before storage is decided`() {
        targetContext.sharedPrefs().edit { remove(CollectionHelper.PREF_COLLECTION_PATH) }
        val appWidgetManager = AppWidgetManager.getInstance(targetContext)
        shadowOf(appWidgetManager).bindAppWidgetId(WIDGET_ID, ComponentName(targetContext, AddNoteWidget::class.java))

        AddNoteWidget().onReceive(targetContext, widgetUpdateIntent(WIDGET_ID))

        val widget = assertNotNull(shadowOf(appWidgetManager).getViewFor(WIDGET_ID), "widget was not drawn")
        WidgetAddNoteBinding.bind(widget).widgetAddNoteButton.performClick()

        val opened = assertNotNull(shadowOf(targetContext as Application).nextStartedActivity, "button opened nothing")
        assertEquals(NoteEditorActivity::class.java.name, opened.component?.className)
    }

    companion object {
        private const val WIDGET_ID = 1
    }
}
