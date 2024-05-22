/*
   Copyright (c) 2024 Anoop <xenonnn4w@gmail.com>

This program is free software; you can redistribute it and/or modify it under
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

package com.ichi2.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import timber.log.Timber

data class DeckPickerWidgetData(
    val deckId: Long,
    val name: String,
    val reviewCount: Int,
    val learnCount: Int,
    val newCount: Int
)

suspend fun getDeckNameAndStats(deckIds: List<Long>): List<DeckPickerWidgetData> {
    val result = mutableListOf<DeckPickerWidgetData>()

    val deckTree = withCol { sched.deckDueTree() }

    deckTree.forEach { node ->
        if (node.did !in deckIds) return@forEach
        result.add(
            DeckPickerWidgetData(
                deckId = node.did,
                name = node.lastDeckNameComponent,
                reviewCount = node.revCount,
                learnCount = node.lrnCount,
                newCount = node.newCount
            )
        )
    }

    val deckIdToData = result.associateBy { it.deckId }
    return deckIds.mapNotNull { deckIdToData[it] }
}

class DeckPickerWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        Timber.d("onUpdate")
        updateWidget(context, appWidgetManager, appWidgetIds)
    }

    companion object {

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: IntArray
        ) {
            val remoteViews = RemoteViews(context.packageName, R.layout.widget_deck_picker_large)
            appWidgetManager.updateAppWidget(widgetId, remoteViews)
        }
    }
}
