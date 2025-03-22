/****************************************************************************************
 * Copyright (c) 2024 Spencer Poisseroux <me@spoisseroux.com>                           *
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
package com.ichi2.anki.lint.rules

import com.android.tools.lint.checks.infrastructure.TestFile.JavaTestFile.create
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.android.tools.lint.checks.infrastructure.TestMode
import org.intellij.lang.annotations.Language
import org.junit.Test

class LocaleRootDetectorTest {
    @Language("JAVA")
    private val invalidCode =
        """
        package com.ichi2.test;
        import java.util.Locale;
        import java.text.NumberFormat;
        import static java.util.Locale.ROOT;

        public class InvalidClass {
            public void invalidMethod() {
                String.format(Locale.ROOT, "Number: %d", 42); // Should be flagged
                NumberFormat.getInstance(ROOT); // Should be ignored, static import
            }
        }
        """.trimIndent()

    @Language("JAVA")
    private val suppressedCode =
        """
        package com.ichi2.test;
        import java.util.Locale;
        import androidx.annotation.SuppressLint;

        @SuppressLint("LocaleRootUsage")
        public class SuppressedClass {
            public void suppressedMethod() {
                String id = String.format(Locale.ROOT, "ID_%d", 123);
            }
        }
        """.trimIndent()

    @Test
    fun `allows suppressed Locale ROOT usage`() {
        lint()
            .testModes(TestMode.DEFAULT)
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(create(suppressedCode))
            .issues(LocaleRootDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun `detects explicit Locale ROOT usage`() {
        lint()
            .testModes(TestMode.DEFAULT)
            .allowMissingSdk()
            .files(create(invalidCode))
            .issues(LocaleRootDetector.ISSUE)
            .run()
            .expectErrorCount(1)
            .expectContains("String.format(Locale.ROOT")
    }
}
