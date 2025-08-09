/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.ui.windows.reviewer

import android.content.SharedPreferences
import com.ichi2.anki.preferences.reviewer.MenuDisplayType
import com.ichi2.anki.preferences.reviewer.ReviewerMenuRepository
import com.ichi2.anki.preferences.reviewer.ViewerAction
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.enums.ToolbarPosition
import timber.log.Timber
import java.net.BindException
import java.net.ServerSocket

class StudyScreenRepository(
    preferences: SharedPreferences,
) {
    val isMarkShownInToolbar: Boolean
    val isFlagShownInToolbar: Boolean

    init {
        val actions =
            ReviewerMenuRepository(preferences)
                .getActionsByMenuDisplayTypes(
                    MenuDisplayType.ALWAYS,
                ).getValue(MenuDisplayType.ALWAYS)
        val isToolbarShown = Prefs.toolbarPosition != ToolbarPosition.NONE
        isMarkShownInToolbar = isToolbarShown && ViewerAction.MARK in actions
        isFlagShownInToolbar = isToolbarShown && ViewerAction.FLAG_MENU in actions
    }

    companion object {
        fun getServerPort(): Int {
            if (!Prefs.useFixedPortInReviewer) return 0
            return try {
                ServerSocket(Prefs.reviewerPort)
                    .use {
                        it.reuseAddress = true
                        it.localPort
                    }.also {
                        if (Prefs.reviewerPort == 0) {
                            Prefs.reviewerPort = it
                        }
                    }
            } catch (_: BindException) {
                Timber.w("Fixed port %d under use. Using dynamic port", Prefs.reviewerPort)
                0
            }
        }
    }
}
