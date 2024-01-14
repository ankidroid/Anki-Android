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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class AutomaticAnswerAndroidTest : RobolectricTest() {

    @Test
    fun default_is_bury() {
        assertThat("no value", createInstance().settings.answerAction, equalTo(AutomaticAnswerAction.BURY_CARD))
        setPreference(-1) // invalid
        assertThat("bad pref", createInstance().settings.answerAction, equalTo(AutomaticAnswerAction.BURY_CARD))
        assertThat("default", AutomaticAnswer.defaultInstance(mock()).settings.answerAction, equalTo(AutomaticAnswerAction.BURY_CARD))

        // ensure "bad pref" isn't picked up as a good value
        setPreference(1)
        assertThat("good pref", createInstance().settings.answerAction, not(equalTo(AutomaticAnswerAction.BURY_CARD)))

        // reset the value
        resetPrefs()
        assertThat("xml pref", createInstance().settings.answerAction, equalTo(AutomaticAnswerAction.BURY_CARD))
    }

    @Test
    fun preference_sets_action() {
        setPreference(1)
        assertThat(createInstance().settings.answerAction, equalTo(AutomaticAnswerAction.ANSWER_AGAIN))
    }

    private fun resetPrefs() {
        col.config.remove("automaticAnswerAction")
    }

    private fun setPreference(value: Int) {
        val conf = col.decks.confForDid(col.decks.selected())
        conf.put(AutomaticAnswerAction.CONFIG_KEY, value)
    }

    private fun createInstance() =
        AutomaticAnswer.createInstance(mock(), super.getPreferences(), super.col)
}
