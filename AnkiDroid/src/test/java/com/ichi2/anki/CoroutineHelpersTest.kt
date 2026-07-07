// SPDX-License-Identifier: GPL-3.0-or-later

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

    @Test
    fun `strips a dangling empty error details label`() {
        // the backend formats network errors as `<summary>\n\n<details>`; with no details to
        // report the "Error details:" label is left dangling with nothing after it
        val message = "A network error occurred.\n\nError details: "
        val cleaned = stripEmptyErrorDetails(message) { details -> "Error details: $details" }
        assertThat(cleaned, equalTo("A network error occurred."))
    }

    @Test
    fun `keeps the label when there are real error details`() {
        val message = "A network error occurred.\n\nError details: connection refused"
        val cleaned = stripEmptyErrorDetails(message) { details -> "Error details: $details" }
        assertThat(cleaned, equalTo(message))
    }

    @Test
    fun `strips the dangling label in other languages`() {
        // German `network-details` translation, to confirm the check isn't hardcoded to English
        val message = "Ein Netzwerkfehler ist aufgetreten.\n\nFehlerbeschreibung: "
        val cleaned = stripEmptyErrorDetails(message) { details -> "Fehlerbeschreibung: $details" }
        assertThat(cleaned, equalTo("Ein Netzwerkfehler ist aufgetreten."))
    }

    @Test
    fun `leaves the message unchanged when details are not substituted at the end of the label`() {
        // Lojban wraps the details value, so an empty value can't be told apart from real content
        val lojban = { details: String -> ".i tcila pa nabmi fa la'o zoi. $details .zoi" }
        val message = "summary\n\n${lojban("")}"
        assertThat(stripEmptyErrorDetails(message, lojban), equalTo(message))
    }

    @Test
    fun `leaves messages without an error details label unchanged`() {
        val message = "Your account has been disabled."
        val cleaned = stripEmptyErrorDetails(message) { details -> "Error details: $details" }
        assertThat(cleaned, equalTo(message))
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
