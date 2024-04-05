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

package com.ichi2.anki.ui.internationalization

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.IntroductionActivity
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.testutils.EmptyApplication
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
class SentenceCaseTest : RobolectricTest() {
    @Test
    fun `English is converted to sentence case`() {
        with(super.startRegularActivity<IntroductionActivity>()) {
            assertThat(TR.browsingToggleSuspend().toSentenceCase(R.string.sentence_toggle_suspend), equalTo("Toggle suspend"))
            assertThat("Toggle Suspend".toSentenceCase(R.string.sentence_toggle_suspend), equalTo("Toggle suspend"))
            assertThat("Ook? Ook?".toSentenceCase(R.string.sentence_toggle_suspend), equalTo("Ook? Ook?"))
        }
    }
}
