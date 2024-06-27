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
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
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
        // Do not call updateWidget here directly since we need the deckIds
    }

    companion object {

        @OptIn(DelicateCoroutinesApi::class)
        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: IntArray,
            deckIds: LongArray
        ) {
            val remoteViews = RemoteViews(context.packageName, R.layout.widget_deck_picker_large)

            // Launch a coroutine to fetch deck stats
            GlobalScope.launch(Dispatchers.Main) {
                val deckData = getDeckNameAndStats(deckIds.toList())

                // Clear previous deck views
                remoteViews.removeAllViews(R.id.deckCollection)

                // Inflate deck views dynamically based on deckData
                for (deck in deckData) {
                    val deckView = RemoteViews(context.packageName, R.layout.widget_item_deck_main)

                    deckView.setTextViewText(R.id.deckName, deck.name)
                    deckView.setTextViewText(R.id.deckNew, deck.newCount.toString())
                    deckView.setTextViewText(R.id.deckDue, deck.reviewCount.toString())
                    deckView.setTextViewText(R.id.deckLearn, deck.learnCount.toString())

                    remoteViews.addView(R.id.deckCollection, deckView)
                }
                appWidgetManager.updateAppWidget(widgetId, remoteViews)
            }
        }
    }
}
