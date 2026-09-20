// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.utils

import android.content.Context
import androidx.annotation.CheckResult
import com.ichi2.anki.R
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.utils.android.showThemedToast
import java.io.PrintWriter
import java.io.StringWriter

object ExceptionUtil {
    @CheckResult
    fun getExceptionMessage(e: Throwable?): String = getExceptionMessage(e, "\n")

    @CheckResult
    fun getExceptionMessage(
        e: Throwable?,
        separator: String?,
    ): String {
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

    fun getFullStackTrace(ex: Throwable): String {
        val sw = StringWriter()
        ex.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }

    /** Executes a function, and logs the exception to ACRA and shows a toast if an issue occurs */
    fun executeSafe(
        context: Context,
        origin: String,
        runnable: (() -> Unit),
    ) {
        try {
            runnable.invoke()
        } catch (e: Exception) {
            CrashReportService.sendExceptionReport(e, origin)
            showThemedToast(
                context,
                context.getString(R.string.multimedia_editor_something_wrong),
                true,
            )
        }
    }
}
