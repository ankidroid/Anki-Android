/*
 *  Copyright (c) 2024 Abd-Elrahman Esam <abdelrahmanesam20000@gmail.com>
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

package com.ichi2.anki.tasks

import org.gradle.api.GradleScriptException
import org.gradle.api.Project


// Issue 11078 - some emulators run, but run zero tests, and still report success
fun Project.assertNonZeroAndroidTests() {
    tasks.register("assertNonzeroAndroidTests")
    {
        doLast {
            // androidTest currently creates one .xml file per emulator with aggregate results in this dir
            val folder = file("./build/outputs/androidTest-results/connected/flavors/play")
            val listOfFiles =
                folder.listFiles { _, f -> f.matches(".*\\.xml".toRegex()) } ?: emptyArray()
            for (file in listOfFiles) {
                // The aggregate results file currently contains a line with this pattern holding test count
                val matches = file.readLines().filter { it.contains("<testsuite") }
                if (matches.size != 1) {
                    throw GradleScriptException(
                        "Unable to determine count of tests executed for ${file.name}. Regex pattern out of date?",
                        Throwable()
                    )
                }
                if (!matches[0].matches(".* tests=\"\\d+\".*".toRegex()) || matches[0].contains("tests=\"0\"")) {
                    throw GradleScriptException(
                        "androidTest executed 0 tests for ${file.name} - Probably a bug with the emulator. Try another image.",
                        Throwable()
                    )
                }
            }
        }
    }
}