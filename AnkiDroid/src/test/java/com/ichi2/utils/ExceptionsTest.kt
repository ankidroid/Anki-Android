/*
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

package com.ichi2.utils

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.R
import com.ichi2.anki.ui.windows.managespace.CanNotWriteToOrCreateFileException
import com.ichi2.anki.utils.AggregateException
import com.ichi2.anki.utils.TranslatableAggregateException
import com.ichi2.anki.utils.getUserFriendlyErrorText
import com.ichi2.testutils.testExceptionWithStackTrace
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

class AggregateExceptionTest {
    @Test
    fun `AggregateException prints all causes in the stack trace`() {
        val first = testExceptionWithStackTrace("[aa]")
        val second = testExceptionWithStackTrace("[bb]")
        val aggregateException = AggregateException("[message]", listOf(first, second))

        val stackTrace = aggregateException.stackTraceToString()

        assertThat(stackTrace, containsString("[aa]"))
        assertThat(stackTrace, containsString("[bb]"))
        assertThat(stackTrace, containsString("[message]"))
        assertThat(stackTrace, containsString("testExceptionWithStackTrace"))
    }
}

// TODO Create and use a test exception using test string resources
@RunWith(AndroidJUnit4::class)
class TranslatableExceptionsTest {
    val context: Context get() = InstrumentationRegistry.getInstrumentation().context

    @Test
    fun `TranslatableException produces a proper error message`() {
        val exception = CanNotWriteToOrCreateFileException(File("my-file"))

        assertThat(
            context.getUserFriendlyErrorText(exception),
            equalTo("Cannot write to or create file my-file")
        )
    }

    @Test
    fun `TranslatableAggregateException produces a proper error message`() {
        val firstException = testExceptionWithStackTrace("[aa]")
        val secondException = CanNotWriteToOrCreateFileException(File("my-file"))

        val translatableAggregateException = TranslatableAggregateException(
            causes = listOf(firstException, secondException)
        )

        assertThat(
            context.getUserFriendlyErrorText(translatableAggregateException),
            equalTo("Multiple errors, most recent: Cannot write to or create file my-file")
        )

        val translatableAggregateExceptionWithACustomMessage = TranslatableAggregateException(
            translatableMessage = {
                getString(
                    R.string.error__etc__multiple_consecutive_errors_without_progress_most_recent,
                    getUserFriendlyErrorText(secondException)
                )
            },
            causes = listOf(firstException, secondException)
        )

        assertThat(
            context.getUserFriendlyErrorText(translatableAggregateExceptionWithACustomMessage),
            equalTo("Multiple consecutive errors without progress, most recent: Cannot write to or create file my-file")
        )
    }
}
