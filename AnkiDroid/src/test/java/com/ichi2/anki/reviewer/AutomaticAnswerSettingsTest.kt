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
import com.ichi2.testutils.EmptyApplication
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
class AutomaticAnswerSettingsTest {

    @Test
    fun stopAllWorks() {
        val target: AutomaticAnswerSettings.AutomaticallyAnswered = mock()
        val answer = AutomaticAnswerSettings(
            useTimer = true,
            questionDelaySeconds = 10,
            answerDelaySeconds = 10,
            target = target
        )

        answer.delayedShowQuestion(0)
        answer.delayedShowAnswer(0)

        assertThat(answer.timeoutHandler.hasMessages(0), equalTo(true))

        answer.stopAll()

        assertThat(answer.timeoutHandler.hasMessages(0), equalTo(false))
    }
}
