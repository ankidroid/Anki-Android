/*
 *  Copyright (c) 2022 David Allison <davidallisongithub@gmail.com>
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
 *
 * ----
 * This file incorporates code under the following license
 * https://cs.android.com/android/platform/superproject/+/master:frameworks/base/tools/aapt2/util/Util.cpp;drc=fbb7fe0da32896a871dd395845f1f510b075f8d5
 *
 *     Copyright (C) 2015 The Android Open Source Project
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */

@file:Suppress("SpellCheckingInspection") // converted from AOSP - some typos

package com.ichi2.anki.lint.utils

/**
 * Converted from C++
 * https://cs.android.com/android/platform/superproject/+/master:frameworks/base/tools/aapt2/util/Util.cpp;drc=fbb7fe0da32896a871dd395845f1f510b075f8d5
 */
object Aapt2Util {

    fun verifyJavaStringFormat(str: String): Boolean {

        var argCount = 0
        var nonpositional = false

        var index = 0
        fun c() = str[index]

        while (index < str.length) {

            if (c() == '%' && index + 1 < str.length) {
                index++

                if (c() == '%' || c() == 'n') {
                    index++
                    continue
                }

                argCount++

                val numDigits = consumeDigits(str, index)
                if (numDigits > 0) {
                    index += numDigits
                    if (index < str.length && c() != '$') {
                        // The digits were a size, but not a positional argument.
                        nonpositional = true
                    }
                } else if (c() == '<') {
                    // Reusing last argument, bad idea since positions can be moved around
                    // during translation.
                    nonpositional = true

                    index++

                    // Optionally we can have a $ after
                    if (index <= str.length && c() == '$') {
                        index++
                    }
                } else {
                    nonpositional = true
                }

                // Ignore size, width, flags, etc.
                while (index < str.length && (
                    c() == '-' || c() == '#' || c() == '+' || c() == ' ' ||
                        c() == ',' || c() == '(' || (c() in '0'..'9')
                    )
                ) {
                    index++
                }

                /*
                 * This is a shortcut to detect strings that are going to Time.format()
                 * instead of String.format()
                 *
                 * Comparison of String.format() and Time.format() args:
                 *
                 * String: ABC E GH  ST X abcdefgh  nost x
                 *   Time:    DEFGHKMS W Za  d   hkm  s w yz
                 *
                 * Therefore we know it's definitely Time if we have:
                 *     DFKMWZkmwyz
                 */
                if (index < str.length) {
                    when (c()) {
                        'D' -> return true
                        'F' -> return true
                        'K' -> return true
                        'M' -> return true
                        'W' -> return true
                        'Z' -> return true
                        'k' -> return true
                        'm' -> return true
                        'w' -> return true
                        'y' -> return true
                        'z' -> return true
                    }
                }
            }

            if (index < str.length) {
                index++
            }
        }

        if (argCount > 1 && nonpositional) {
            // Multiple arguments were specified, but some or all were non positional.
            // Translated strings may rearrange the order of the arguments, which will break the
            // string.
            return false
        }
        return true
    }

    /**
     * Returns the number of consecutive digits in [s], starting at position [index]
     * @param s The string to search
     * @param index The index to start searching for digits at
     * @return Number of consecutive digits in [s], starting at position [index]
     */
    private fun consumeDigits(s: String, index: Int): Int {
        var digits = 0
        @Suppress("UseWithIndex")
        for (i in index until s.length) {
            if (!s[i].isDigit()) {
                return digits
            }
            digits++
        }
        return 0
    }
}
