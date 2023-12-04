/*
 * Copyright (c) 2021 Nicola Dardanis <nicdard@gmail.com>
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
package com.ichi2.anki.lint.rules

import com.android.tools.lint.checks.infrastructure.LintDetectorTest.java
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.ichi2.anki.lint.rules.VariableNamingDetector.Companion.ISSUE
import org.junit.Test

internal class VariableNamingDetectorTest {
    @Test
    @Suppress("ktlint:standard:max-line-length")
    fun reportsErrorTest() {
        lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(file)
            .issues(ISSUE)
            .run()
            .expect(
                "src/com/ichi2/anki/exception/FilteredAncestor.java:14: Error: Variable name should not use field prefixes. [VariableNamingDetector]\n" +
                    "    public void setFilteredAncestorName(String mFilteredAncestorName, String member) {\n" +
                    "                                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                    "src/com/ichi2/anki/exception/FilteredAncestor.java:22: Error: Variable name should not use field prefixes. [VariableNamingDetector]\n" +
                    "    public static setFilteredAncestorName(String sFilteredAncestorName, String staticMember) {\n" +
                    "                                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                    "2 errors, 0 warnings"
            )
    }

    companion object {
        private val file = java(
            """
            package com.ichi2.anki.exception;

            public class FilteredAncestor extends Exception {
                private String mFilteredAncestorName;
                private static String sFilteredAncestorName;
                public FilteredAncestor(String filteredAncestorName) {
                    this.mFilteredAncestorName = filteredAncestorName;
                }

                public String getFilteredAncestorName() {
                    return mFilteredAncestorName;
                }
                
                public void setFilteredAncestorName(String mFilteredAncestorName, String member) {
                    this.mFilteredAncestorName = mFilteredAncestorName;
                }
                
                public static String getStaticFilteredAncestorName() {
                    return sFilteredAncestorName;
                }
                
                public static setFilteredAncestorName(String sFilteredAncestorName, String staticMember) {
                    this.sFilteredAncestorName = sFilteredAncestorName;
                }
            }
            """.trimIndent()
        )
    }
}
