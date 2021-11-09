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

package com.ichi2.utils;

import android.text.TextUtils;


import timber.log.Timber;

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
public class MethodLogger {

    private MethodLogger() {
    }


    /**
     * Logs the method being called.
     * 
     * @param message to add to the logged statement
     */
    public static void log(String message) {
        logInternal(message);
    }


    /**
     * Logs the method being called.
     */
    public static void log() {
        logInternal("");
    }


    /**
     * Logs the method that made the call.
     * <p>
     * A helper method is needed to make sure the number of stack frames is the same on every path.
     * 
     * @param message to be added to the logged message
     */
    private static void logInternal(String message) {
        // Get the name of the class and method.
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        // Look for the index of this method call in the stack trace.
        //
        // The task should something like:
        // 0: dalvik.system.VMStack.getThreadStackTrace()
        // 1: java.lang.Thread.getStackTrace()
        // 2: com.ichi2.utils.MethodLogger.logInternal()
        // 3: com.ichi2.utils.MethodLogger.log()
        // 4: THE METHOD WE ARE LOOKING FOR
        //
        // But we cannot guarantee what the stack trace below this method will be, and it might be different on
        // different versions of Android. Instead, we look for the call to our own method and we assume there is a
        // single public method on this class above it before the call to logInternal.
        int size = stack.length;
        int logInternalIndex = 0;
        for (; logInternalIndex < size; ++logInternalIndex) {
            if (TextUtils.equals(stack[logInternalIndex].getClassName(), MethodLogger.class.getName())
                    && TextUtils.equals(stack[logInternalIndex].getMethodName(), "logInternal")) {
                break;
            }
        }
        if (logInternalIndex + 2 >= size) {
            throw new IllegalStateException("there should always be a caller for this method");
        }
        StackTraceElement caller = stack[logInternalIndex + 2];
        String callerClass = caller.getClassName();
        String callerMethod = caller.getMethodName();
        if (TextUtils.isEmpty(message)) {
            Timber.d("called: %s.%s()", callerClass, callerMethod);
        } else {
            Timber.d("called: %s.%s(): %s", callerClass, callerMethod, message);
        }
    }

}
