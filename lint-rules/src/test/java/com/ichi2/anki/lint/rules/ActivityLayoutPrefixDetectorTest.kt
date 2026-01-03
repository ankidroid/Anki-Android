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
class ActivityLayoutPrefixDetectorTest {
    private val stubActivity =
        kotlin(
            """
            package android.app
                open class Activity
            """.trimIndent(),
        )

    private val stubBinding =
        kotlin(
            """
            package com.ichi2.anki.databinding
                class CardBrowserAppearanceBinding
            """.trimIndent(),
        )

    private val stubActivityBinding =
        kotlin(
            """
            package com.ichi2.anki.databinding
                class ActivityDeckPickerBinding
            """.trimIndent(),
        )

    private val activityWithBadLayout =
        kotlin(
            """
            package com.ichi2.anki
            import android.app.Activity
            import com.ichi2.anki.databinding.CardBrowserAppearanceBinding
                class BadActivity : Activity() {
                private lateinit var binding: CardBrowserAppearanceBinding
            }
            """.trimIndent(),
        )

    private val activityWithGoodLayout =
        kotlin(
            """
            package com.ichi2.anki
            import android.app.Activity
            import com.ichi2.anki.databinding.ActivityDeckPickerBinding
                class GoodActivity : Activity() {
                private lateinit var binding: ActivityDeckPickerBinding
            }
            """.trimIndent(),
        )

    @Test
    fun showsErrorForActivityWithBadLayoutPrefix() {
        lint()
            .allowMissingSdk()
            .files(
                stubActivity,
                stubBinding,
                activityWithBadLayout,
            ).issues(ActivityLayoutPrefixDetector.ISSUE)
            .run()
            .expectErrorCount(1)
            .expectContains("Activity layout should be prefixed with")
    }

    @Test
    fun noErrorForActivityWithGoodLayoutPrefix() {
        lint()
            .allowMissingSdk()
            .files(
                stubActivity,
                stubActivityBinding,
                activityWithGoodLayout,
            ).issues(ActivityLayoutPrefixDetector.ISSUE)
            .run()
            .expectClean()
    }
}
