/*
 *  Copyright (c) 2021 Mike Hardy <github@mikehardy.net>
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
package com.ichi2.anki

import android.view.View
import com.ichi2.utils.KotlinCleanup

@KotlinCleanup("IDE lint")
class TestCardTemplatePreviewer : CardTemplatePreviewer() {
    var showingAnswer = false
        protected set

    fun disableDoubleClickPrevention() {
        mLastClickTime = (AnkiDroidApp.getSharedPrefs(baseContext).getInt(DOUBLE_TAP_TIME_INTERVAL, DEFAULT_DOUBLE_TAP_TIME_INTERVAL) * -2).toLong()
    }

    override fun displayCardAnswer() {
        super.displayCardAnswer()
        showingAnswer = true
    }

    override fun displayCardQuestion() {
        super.displayCardQuestion()
        showingAnswer = false
    }

    fun nextButtonVisible(): Boolean {
        return mPreviewLayout!!.nextCard.visibility != View.GONE
    }

    fun previousButtonVisible(): Boolean {
        return mPreviewLayout!!.prevCard.visibility != View.GONE
    }

    fun previousButtonEnabled(): Boolean {
        return mPreviewLayout!!.prevCard.isEnabled
    }

    fun nextButtonEnabled(): Boolean {
        return mPreviewLayout!!.nextCard.isEnabled
    }
}
