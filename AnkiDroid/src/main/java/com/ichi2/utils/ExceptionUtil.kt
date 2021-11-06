/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.utils

import androidx.annotation.CheckResult
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.StringBuilder

object ExceptionUtil {
    @JvmStatic
    fun containsMessage(e: Throwable?, needle: String?): Boolean {
        if (e == null) {
            return false
        }
        if (containsMessage(e.cause, needle)) {
            return true
        }
        val message = e.message
        return message != null && message.contains(needle!!)
    }

    @CheckResult
    @JvmStatic
    fun getExceptionMessage(e: Throwable?): String {
        return getExceptionMessage(e, "\n")
    }

    @CheckResult
    fun getExceptionMessage(e: Throwable?, separator: String?): String {
        val ret = StringBuilder()
        var cause: Throwable? = e
        while (cause != null) {
            if (cause.localizedMessage != null || cause === e) {
                if (cause !== e) {
                    ret.append(separator)
                }
                ret.append(cause.localizedMessage)
            }
            cause = cause.cause
        }
        return ret.toString()
    }

    /** Whether the exception is, or contains a cause of a given type  */
    @JvmStatic
    fun <T> containsCause(ex: Throwable, clazz: Class<T>): Boolean {
        if (clazz.isInstance(ex)) {
            return true
        }
        val cause = ex.cause ?: return false
        return containsCause(cause, clazz)
    }

    @JvmStatic
    fun getFullStackTrace(ex: Throwable): String {
        val sw = StringWriter()
        ex.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }
}
