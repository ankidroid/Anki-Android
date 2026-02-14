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
class FragmentLayoutPrefixDetectorTest {
    private val stubFragment =
        kotlin(
            """
            package androidx.fragment.app
            open class Fragment
            """.trimIndent(),
        )

    private val stubBinding =
        kotlin(
            """
            package com.ichi2.anki.databinding
            class StudyOptionsBinding
            """.trimIndent(),
        )

    private val stubFragmentBinding =
        kotlin(
            """
            package com.ichi2.anki.databinding
            class FragmentStudyOptionsBinding
            """.trimIndent(),
        )

    private val fragmentWithBadLayout =
        kotlin(
            """
            package com.ichi2.anki
            import androidx.fragment.app.Fragment
            import com.ichi2.anki.databinding.StudyOptionsBinding
            class BadFragment : Fragment() {
                private lateinit var binding: StudyOptionsBinding
            }
            """.trimIndent(),
        )

    private val fragmentWithGoodLayout =
        kotlin(
            """
            package com.ichi2.anki
            import androidx.fragment.app.Fragment
            import com.ichi2.anki.databinding.FragmentStudyOptionsBinding
            class GoodFragment : Fragment() {
                private lateinit var binding: FragmentStudyOptionsBinding
            }
            """.trimIndent(),
        )

    @Test
    fun showsErrorForFragmentWithBadLayoutPrefix() {
        lint()
            .allowMissingSdk()
            .files(
                stubFragment,
                stubBinding,
                fragmentWithBadLayout,
            ).issues(FragmentLayoutPrefixDetector.ISSUE)
            .run()
            .expectErrorCount(1)
            .expectContains("Fragment layout should be prefixed with")
    }

    @Test
    fun noErrorForFragmentWithGoodLayoutPrefix() {
        lint()
            .allowMissingSdk()
            .files(
                stubFragment,
                stubFragmentBinding,
                fragmentWithGoodLayout,
            ).issues(FragmentLayoutPrefixDetector.ISSUE)
            .run()
            .expectClean()
    }
}
