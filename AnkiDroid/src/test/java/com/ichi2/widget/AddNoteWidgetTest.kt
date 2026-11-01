// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.view.View
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.widgets.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class AddNoteWidgetTest : RobolectricTest() {
    @Test
    fun `add note button works before storage is decided`() {
        targetContext.sharedPrefs().edit { remove(CollectionHelper.PREF_COLLECTION_PATH) }
        val appWidgetManager = AppWidgetManager.getInstance(targetContext)
        shadowOf(appWidgetManager).bindAppWidgetId(WIDGET_ID, ComponentName(targetContext, AddNoteWidget::class.java))

        AddNoteWidget().onReceive(
            targetContext,
            Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(WIDGET_ID)),
        )

        val widget = assertNotNull(shadowOf(appWidgetManager).getViewFor(WIDGET_ID), "widget was not drawn")
        assertTrue(widget.findViewById<View>(R.id.widget_add_note_button).hasOnClickListeners(), "button has no click action")
    }

    companion object {
        private const val WIDGET_ID = 1
    }
}
