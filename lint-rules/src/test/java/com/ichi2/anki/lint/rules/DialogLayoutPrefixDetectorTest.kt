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
class DialogLayoutPrefixDetectorTest {
    private val stubDialogFragment =
        kotlin(
            """
            package androidx.fragment.app
            open class DialogFragment
            """.trimIndent(),
        )

    private val stubBinding =
        kotlin(
            """
            package com.ichi2.anki.databinding
            class ConfirmBinding
            """.trimIndent(),
        )

    private val stubDialogBinding =
        kotlin(
            """
            package com.ichi2.anki.databinding
            class DialogConfirmBinding
            """.trimIndent(),
        )

    private val dialogWithBadLayout =
        kotlin(
            """
            package com.ichi2.anki
            import androidx.fragment.app.DialogFragment
            import com.ichi2.anki.databinding.ConfirmBinding
            class BadDialog : DialogFragment() {
                private lateinit var binding: ConfirmBinding
            }
            """.trimIndent(),
        )

    private val dialogWithGoodLayout =
        kotlin(
            """
            package com.ichi2.anki
            import androidx.fragment.app.DialogFragment
            import com.ichi2.anki.databinding.DialogConfirmBinding
            class GoodDialog : DialogFragment() {
                private lateinit var binding: DialogConfirmBinding
            }
            """.trimIndent(),
        )

    @Test
    fun showsErrorForDialogWithBadLayoutPrefix() {
        lint()
            .allowMissingSdk()
            .files(
                stubDialogFragment,
                stubBinding,
                dialogWithBadLayout,
            ).issues(DialogLayoutPrefixDetector.ISSUE)
            .run()
            .expectErrorCount(1)
            .expectContains("Dialog layout should be prefixed with")
    }

    @Test
    fun noErrorForDialogWithGoodLayoutPrefix() {
        lint()
            .allowMissingSdk()
            .files(
                stubDialogFragment,
                stubDialogBinding,
                dialogWithGoodLayout,
            ).issues(DialogLayoutPrefixDetector.ISSUE)
            .run()
            .expectClean()
    }
}
