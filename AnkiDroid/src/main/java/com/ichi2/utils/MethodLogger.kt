/****************************************************************************************
 * Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>                                   *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.utils

import android.text.TextUtils
import timber.log.Timber

/**
 * Helper class to log method invocation.
 * <p>
 * Use with moderation as it spans the logcat and reduces performances.
 * <p>
 * Consider guarding calls to this method with an if statement on a static final constant, as in:
 *
 * <pre>
 *   public static final boolean DEBUG = false;  // Enable for debugging this class.
 *
 *   public void methodName(int value, String name) {
 *     if (DEBUG) {
 *       MethodLogger.log(value, name);
 *     }
 *     ...
 *   }
 * </pre>
 */

object MethodLogger {
    /**
     * Logs the method being called.
     *
     * @param message to add to the logged statement
     */
    fun log(message: String) {
        logInternal(message)
    }

    /**
     * Logs the method being called.
     */
    fun log() {
        logInternal("")
    }

    /**
     * Logs the method that made the call.
     * <p>
     * A helper method is needed to make sure the number of stack frames is the same on every path.
     *
     * @param message to be added to the logged message
     */
    private fun logInternal(message: String) {
        // Get the name of the class and method.
        val stack = Thread.currentThread().stackTrace
        // Look for the index of this method call in the stack trace.
        //
        // The task should be something like:
        // 0: dalvik.system.VMStack.getThreadStackTrace()
        // 1: java.lang.Thread.getStackTrace()
        // 2: com.ichi2.utils.MethodLogger.logInternal()
        // 3: com.ichi2.utils.MethodLogger.log()
        // 4: THE METHOD WE ARE LOOKING FOR
        //
        // But we cannot guarantee what the stack trace below this method will be, and it might be different on
        // different versions of Android. Instead, we look for the call to our own method and we assume there is a
        // single public method on this class above it before the call to logInternal.
        val size = stack.size
        var logInternalIndex = 0
        while (logInternalIndex < size) {
            if (TextUtils.equals(stack[logInternalIndex].className, MethodLogger::class.java.name) &&
                TextUtils.equals(stack[logInternalIndex].methodName, "logInternal")
            ) {
                break
            }
            ++logInternalIndex
        }
        check(logInternalIndex + 2 < size) { "there should always be a caller for this method" }
        val caller = stack[logInternalIndex + 2]
        val callerClass = caller.className
        val callerMethod = caller.methodName
        if (message.isEmpty()) {
            Timber.d("called: %s.%s()", callerClass, callerMethod)
        } else {
            Timber.d("called: %s.%s(): %s", callerClass, callerMethod, message)
        }
    }
}
