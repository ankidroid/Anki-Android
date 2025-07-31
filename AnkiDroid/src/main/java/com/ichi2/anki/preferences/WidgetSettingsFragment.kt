/*
 *  Copyright (c) 2025 Snowiee <xenonnn4w@gmail.com>
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

package com.ichi2.anki.preferences

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import androidx.preference.SwitchPreferenceCompat
import com.ichi2.anki.R
import com.ichi2.widget.cardanalysis.CardAnalysisWidget

class WidgetSettingsFragment : SettingsFragment() {
    override val preferenceResource: Int
        get() = R.xml.preferences_widget
    override val analyticsScreenNameConstant: String
        get() = "prefs.widget"

    override fun initSubscreen() {
        val dynamicThemingPref = requirePreference<SwitchPreferenceCompat>(R.string.widget_dynamic_theming_key)
        dynamicThemingPref.setOnPreferenceChangeListener { _ ->
            // Trigger widget updates when the global setting changes
            updateAllWidgets()
        }
    }

    private fun updateAllWidgets() {
        val context = requireContext()
        val appWidgetManager = AppWidgetManager.getInstance(context)

        // Update CardAnalysisWidget instances
        val cardAnalysisProvider = ComponentName(context, CardAnalysisWidget::class.java)
        val cardAnalysisWidgetIds = appWidgetManager.getAppWidgetIds(cardAnalysisProvider)
        if (cardAnalysisWidgetIds.isNotEmpty()) {
            val updateIntent =
                Intent(context, CardAnalysisWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, cardAnalysisWidgetIds)
                }
            context.sendBroadcast(updateIntent)
        }
    }

    companion object {
        fun isGlobalDynamicThemingEnabled(context: android.content.Context): Boolean {
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
            return prefs.getBoolean(context.getString(R.string.widget_dynamic_theming_key), true)
        }
    }
}
