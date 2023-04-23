/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.exceptions

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.testutils.testExceptionWithStackTrace
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AggregateExceptionAndroidTest {

    @Test
    fun aggregateExceptionFriendlyErrorMessage() {
        val first = testExceptionWithStackTrace("[aa]")
        val second = testExceptionWithStackTrace("[bb]")
        val result = AggregateException.raise(
            "10 consecutive exceptions without progress",
            listOf(first, second)
        )

        val asAggregateException = result as AggregateException

        val systemErrorMessage = asAggregateException.message
        val friendlyErrorMessage = asAggregateException.getUserFriendlyErrorText().toString()

        MatcherAssert.assertThat(
            systemErrorMessage,
            Matchers.equalTo("2 errors, the last being: '[bb]' [10 consecutive exceptions without progress]")
        )
        MatcherAssert.assertThat(
            friendlyErrorMessage,
            Matchers.equalTo("2 errors, the last being: '[bb]' [10 consecutive exceptions without progress]")
        )
    }

    /**
     * Copied from pending PR (#13651)
     * TODO: use the actual code when it's live
     */
    private fun Exception.getUserFriendlyErrorText(): CharSequence =
        localizedMessage
            ?: message
            ?: this::class.simpleName
            ?: "Unknown error"
}
