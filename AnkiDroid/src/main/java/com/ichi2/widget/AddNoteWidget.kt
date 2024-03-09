/****************************************************************************************
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

package com.ichi2.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.app.PendingIntentCompat
import com.ichi2.anki.IntentHandler
import com.ichi2.anki.NoteEditor
import com.ichi2.anki.R
import com.ichi2.anki.analytics.UsageAnalytics
import timber.log.Timber

class AddNoteWidget : AppWidgetProvider() {
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        UsageAnalytics.sendAnalyticsEvent(this.javaClass.simpleName, "enabled")
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        UsageAnalytics.sendAnalyticsEvent(this.javaClass.simpleName, "disabled")
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        if (!IntentHandler.grantedStoragePermissions(context, showToast = false)) {
            Timber.w("Opening AddNote widget without storage access")
            return
        }
        Timber.d("onUpdate")
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    companion object {

        /**
         * Updates the widgets displayed in the provided context using the given AppWidgetManager
         * and widget IDs, setting up an intent to open the NoteEditor with the caller as the deck picker.
         *
         * @param context The context in which the widgets are updated.
         * @param appWidgetManager The AppWidgetManager instance used to update widgets.
         * @param appWidgetIds The array of widget IDs to be updated.
         */
        fun updateWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            val remoteViews = RemoteViews(context.packageName, R.layout.widget_add_note)
            val intent = Intent(context, NoteEditor::class.java)
            intent.putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.CALLER_DECKPICKER)
            val pendingIntent = PendingIntentCompat.getActivity(context, 0, intent, 0, false)
            remoteViews.setOnClickPendingIntent(R.id.widget_add_note_button, pendingIntent)
            appWidgetManager.updateAppWidget(appWidgetIds, remoteViews)
        }
    }
}
