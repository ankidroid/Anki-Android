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

package com.ichi2.anki.utils

import android.content.Context
import com.ichi2.anki.R
import java.io.PrintStream
import java.io.PrintWriter
import java.lang.Exception
import java.lang.RuntimeException

/**
 * An exception that embeds multiple other exceptions.
 * It prints a stack trace like this:
 *
 *     AggregateException: Foo
 *         at ...
 *
 *     This exception was indirectly caused by 2 exceptions
 *
 *     Details of embedded exception 0:
 *
 *         java.lang.Exception: Bar
 *             at ...
 *
 *     Details of embedded exception 1:
 *
 *         java.lang.Exception
 *             at ...
 */
open class AggregateException(message: String?, val causes: List<Exception>) : RuntimeException(message) {
    override fun printStackTrace(s: PrintStream) {
        super.printStackTrace(s)

        s.print("\nThis exception was indirectly caused by ${causes.size} exceptions\n")

        causes.forEachIndexed { index, exception ->
            s.print("\nDetails of embedded exception $index:\n\n")
            exception.printStackTrace(IndentedPrintStream(s))
        }
    }

    override fun printStackTrace(s: PrintWriter) {
        super.printStackTrace(s)

        s.print("\nThis exception was indirectly caused by ${causes.size} exceptions\n")

        causes.forEachIndexed { index, exception ->
            s.print("\nDetails of embedded exception $index:\n\n")
            exception.printStackTrace(IndentedPrintWriter(s))
        }
    }

    private class IndentedPrintStream(s: PrintStream) : PrintStream(s) {
        override fun println(x: Any?) {
            super.print("\t")
            super.println(x)
        }
    }

    private class IndentedPrintWriter(s: PrintWriter) : PrintWriter(s) {
        override fun println(x: Any?) {
            super.print("\t")
            super.println(x)
        }
    }
}

fun interface TranslatableException {
    fun getTranslatedMessage(context: Context): String
}

class TranslatableAggregateException(
    message: String? = null,
    private val translatableMessage: TranslatableString? = null,
    causes: List<Exception>,
) : AggregateException(message, causes), TranslatableException {
    @Suppress("IfThenToElvis")
    override fun getTranslatedMessage(context: Context): String {
        return if (translatableMessage != null) {
            translatableMessage.toTranslatedString(context)
        } else {
            context.getString(
                R.string.error__etc__multiple_errors_most_recent,
                context.getUserFriendlyErrorText(causes.last()),
            )
        }
    }
}

/**
 * Get an user-friendly error message out of an exception.
 * If the exception is a [TranslatableException], a localized error message is returned.
 *
 * TODO Special-case some of the most common exceptions thrown by the system or the library.
 */
fun Context.getUserFriendlyErrorText(e: Exception): String =
    if (e is TranslatableException) {
        e.getTranslatedMessage(this)
    } else {
        e.localizedMessage?.ifBlank { null }
            ?: e.message?.ifBlank { null }
            ?: e::class.simpleName?.ifBlank { null }
            ?: getString(R.string.error__etc__unknown_error)
    }
