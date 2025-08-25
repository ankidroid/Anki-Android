/*
 *  Copyright (c) 2024 Anoop <xenonnn4w@gmail.com>
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
package com.ichi2.widget

import android.appwidget.AppWidgetManager
import android.content.Intent

/**
 * Each widget has a unique identifier. This id allows to associate to each widget the preferences associated to this widget.
 * This class encapsulate such an id.
 */
@JvmInline
value class AppWidgetId(
    val id: Int,
) {
    companion object {
        fun Intent.getAppWidgetId() = AppWidgetId(getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID))

        fun Intent.updateWidget(appWidgetId: AppWidgetId) = putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId.id)

        val INVALID_APPWIDGET_ID = AppWidgetId(AppWidgetManager.INVALID_APPWIDGET_ID)
    }
}

/**
 * Represents a sequence of AppWidgetId.
 */
@JvmInline
value class AppWidgetIds(
    val ids: IntArray,
) : Iterable<AppWidgetId> {
    override fun iterator() = ids.asSequence().map { AppWidgetId(it) }.iterator()

    companion object {
        fun of(ids: IntArray?) = ids?.let { AppWidgetIds(it) }
    }
}
