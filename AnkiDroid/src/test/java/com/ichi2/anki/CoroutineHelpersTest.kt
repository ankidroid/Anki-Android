/*
 Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki

import com.ichi2.anki.libanki.exception.InvalidSearchException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test

class CoroutineHelpersTest {
    @Test
    fun `launchCatching does not include class names for InvalidSearchException`() =
        runTest {
            val underlying = IllegalStateException("Invalid search: an and")
            val captured = captureErrorMessage { throw InvalidSearchException(underlying) }

            assertThat(captured, equalTo("Invalid search: an and"))
        }

    private suspend fun CoroutineScope.captureErrorMessage(block: suspend CoroutineScope.() -> Unit): String? {
        var captured: String? = null
        launchCatching(
            errorMessageHandler = { captured = it },
            block = block,
        ).join()
        return captured
    }
}
