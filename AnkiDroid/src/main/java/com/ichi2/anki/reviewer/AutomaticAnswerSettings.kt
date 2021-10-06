/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.reviewer

import android.content.SharedPreferences
import com.ichi2.libanki.Collection

class AutomaticAnswerSettings(
    @get:JvmName("useTimer") val useTimer: Boolean = false,
    private val questionDelaySeconds: Int = 60,
    private val answerDelaySeconds: Int = 20
) {

    val questionDelayMilliseconds: Long; get() = questionDelaySeconds * 1000L
    val answerDelayMilliseconds: Long; get() = answerDelaySeconds * 1000L

    // a wait of zero means auto-advance is disabled
    @get:JvmName("autoAdvanceAnswer")
    val autoAdvanceAnswer; get() = answerDelaySeconds > 0

    @get:JvmName("autoAdvanceQuestion")
    val autoAdvanceQuestion; get() = questionDelaySeconds > 0

    companion object {
        @JvmStatic
        fun queryDeckSpecificOptions(col: Collection, selectedDid: Long): AutomaticAnswerSettings? {
            // Dynamic don't have review options; attempt to get deck-specific auto-advance options
            // but be prepared to go with all default if it's a dynamic deck
            if (col.decks.isDyn(selectedDid)) {
                return null
            }

            val revOptions = col.decks.confForDid(selectedDid).getJSONObject("rev")

            if (revOptions.optBoolean("useGeneralTimeoutSettings", true)) {
                // we want to use the general settings, no need for per-deck settings
                return null
            }

            val useTimer = revOptions.optBoolean("timeoutAnswer", false)
            val waitQuestionSecond = revOptions.optInt("timeoutQuestionSeconds", 60)
            val waitAnswerSecond = revOptions.optInt("timeoutAnswerSeconds", 20)
            return AutomaticAnswerSettings(useTimer, waitQuestionSecond, waitAnswerSecond)
        }

        @JvmStatic
        fun queryFromPreferences(preferences: SharedPreferences): AutomaticAnswerSettings {
            val prefUseTimer: Boolean = preferences.getBoolean("timeoutAnswer", false)
            val prefWaitQuestionSecond: Int = preferences.getInt("timeoutQuestionSeconds", 60)
            val prefWaitAnswerSecond: Int = preferences.getInt("timeoutAnswerSeconds", 20)
            return AutomaticAnswerSettings(prefUseTimer, prefWaitQuestionSecond, prefWaitAnswerSecond)
        }
    }
}
