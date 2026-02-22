/*
 * Copyright (c) 2026 lukstbit <52494258+lukstbit@users.noreply.github.com>
 *
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
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.lint.rules

import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.intellij.lang.annotations.Language
import org.junit.Test

class LayoutPrefixDetectorTest {
    @Language("XML")
    private val layoutContent =
        """
        <?xml version="1.0" encoding="utf-8"?>
        <TextView xmlns:android="http://schemas.android.com/apk/res/android"
            android:id="@+id/text"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"                        
        />
        """.trimIndent()

    @Test
    fun `doesn't show errors for proper layout names`() {
        lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(
                TestFiles.xml("res/layout/activity_textview.xml", layoutContent),
                TestFiles.xml("res/layout/fragment_textview.xml", layoutContent),
                TestFiles.xml("res/layout/dialog_textview.xml", layoutContent),
                TestFiles.xml("res/layout/view_textview.xml", layoutContent),
            ).issues(LayoutPrefixDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun `shows expected error for name not following convention`() {
        val testFileName = "random_textview.xml"
        lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(TestFiles.xml("res/layout/$testFileName", layoutContent))
            .issues(LayoutPrefixDetector.ISSUE)
            .run()
            .expectErrorCount(1)
            .expectContains("Error: Layout doesn't follow naming convention: $testFileName")
    }
}
