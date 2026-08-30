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

package com.ichi2.widget

import com.ichi2.widget.cardanalysis.CardAnalysisWidget
import com.ichi2.widget.deckpicker.DeckPickerWidget

// These lists reference concrete provider classes, so they stay in :AnkiDroid
// until the providers move to :widgets

val RECURRING_WIDGETS: List<Class<out AnalyticsWidgetProvider>> =
    listOf(
        DeckPickerWidget::class.java,
        CardAnalysisWidget::class.java,
    )

val NON_RECURRING_WIDGETS: List<Class<out AnalyticsWidgetProvider>> =
    listOf(
        AddNoteWidget::class.java,
        AnkiDroidWidgetSmall::class.java,
    )
