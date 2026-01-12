/*
 * Copyright (c) 2026 Sonal Yadav <sonal.y6390@gmail.com>
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

import com.android.tools.lint.checks.infrastructure.LintDetectorTest.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.Test

@Suppress("UnstableApiUsage")
class ViewLayoutPrefixDetectorTest {
    private val stubView =
        kotlin(
            """
            package android.view
            open class View
            """.trimIndent(),
        )

    private val stubBinding =
        kotlin(
            """
            package com.ichi2.anki.databinding
            class CustomButtonBinding
            """.trimIndent(),
        )

    private val stubViewBinding =
        kotlin(
            """
            package com.ichi2.anki.databinding
            class ViewCustomButtonBinding
            """.trimIndent(),
        )

    private val viewWithBadLayout =
        kotlin(
            """
            package com.ichi2.anki
            import android.view.View
            import com.ichi2.anki.databinding.CustomButtonBinding
            class BadView : View() {
                private lateinit var binding: CustomButtonBinding
            }
            """.trimIndent(),
        )

    private val viewWithGoodLayout =
        kotlin(
            """
            package com.ichi2.anki
            import android.view.View
            import com.ichi2.anki.databinding.ViewCustomButtonBinding
            class GoodView : View() {
                private lateinit var binding: ViewCustomButtonBinding
            }
            """.trimIndent(),
        )

    @Test
    fun showsErrorForViewWithBadLayoutPrefix() {
        lint()
            .allowMissingSdk()
            .files(
                stubView,
                stubBinding,
                viewWithBadLayout,
            ).issues(ViewLayoutPrefixDetector.ISSUE)
            .run()
            .expectErrorCount(1)
            .expectContains("View layout should be prefixed with")
    }

    @Test
    fun noErrorForViewWithGoodLayoutPrefix() {
        lint()
            .allowMissingSdk()
            .files(
                stubView,
                stubViewBinding,
                viewWithGoodLayout,
            ).issues(ViewLayoutPrefixDetector.ISSUE)
            .run()
            .expectClean()
    }
}
