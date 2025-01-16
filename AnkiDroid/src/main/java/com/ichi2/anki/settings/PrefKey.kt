/*
 * Copyright (c) 2025 Brayan Oliveira <brayandso.dev@gmail.com>
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
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.settings

object PrefKey {
    // ****************************************** Sync ****************************************** //
    const val AUTO_SYNC = "automaticSyncMode"
    const val USERNAME = "username"
    const val HKEY = "hkey"

    // **************************************** Reviewer **************************************** //
    const val FRAME_STYLE = "reviewerFrameStyle"
    const val HIDE_SYSTEM_BARS = "hideSystemBars"
    const val IGNORE_DISPLAY_CUTOUT = "ignoreDisplayCutout"
    const val AUTO_FOCUS_TYPE_ANSWER = "autoFocusTypeInAnswer"

    // ************************************** Accessibility ************************************* //
    const val ANSWER_BUTTON_SIZE = "answerButtonSize"

    // ************************************* Developer options ********************************** //
    const val DEV_OPTIONS_ENABLED = "devOptionsEnabledByUser"
}
