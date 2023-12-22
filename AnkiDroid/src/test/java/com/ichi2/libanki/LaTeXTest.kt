/*
 *  Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>
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
package com.ichi2.libanki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.testutils.JvmTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LaTeXTest : JvmTest() {
    class MockMedia(col: Collection) : Media(col) {
        /**
         * @param fname A field name
         * @return Always true, given that we want to assume the field exists in test
         */
        override fun have(fname: String): Boolean {
            return true
        }
    }

    @Test
    fun imgLinkTest() {
        val col = col
        val m: Media = MockMedia(col)
        // The hashing function should never change, as it would broke link. So hard coding the expected hash value is valid
        // Test with media access
        assertThat(
            LaTeX.imgLink("$\\sqrt[3]{2} + \\text{\"var\"}$", false, m),
            @Suppress("ktlint:standard:max-line-length")
            equalTo(
                "<img class=latex alt=\"\\$\\\\sqrt[3]{2} + \\\\text{&quot;var&quot;}\\$\" src=\"latex-dd84e5d506179a137f7924d0960609a8c89d491e.png\">",
            ),
        )

        // Test without access to media
        assertThat(
            LaTeX.imgLink("$\\sqrt[3]{2} + \\text{\"var\"}$", false, col.media),
            equalTo("\\$\\\\sqrt[3]{2} + \\\\text{\"var\"}\\$"),
        )
    }

    @Test
    fun htmlMatchTest() {
        val col = col
        val media: Media = MockMedia(col)
        // The hashing function should never change, as it would broke link. So hard coding the expected hash value is valid
        // Test with media access
        assertThat(
            LaTeX.convertHTML("""[latex]\sqrt[3]{2} + \text{"var"}[/latex]""", media, false),
            equalTo(
                """<img class=latex alt="\sqrt[3]{2} + \text{&quot;var&quot;}" src="latex-def68dc5a5ada07529f673b6493464e94f88c3df.png">""",
            ),
        )

        // Test without access to media
        assertThat(
            LaTeX.convertHTML("""[latex]\sqrt[3]{2} + \text{"var"}[/latex]""", col.media, false),
            equalTo("""\sqrt[3]{2} + \text{"var"}"""),
        )
    }

    @Test
    fun mathMatchTest() {
        val col = col
        val media: Media = MockMedia(col)
        // The hashing function should never change, as it would broke link. So hard coding the expected hash value is valid
        // Test with media access
        assertThat(
            LaTeX.convertMath("""[$$]\sqrt[3]{2} + \text{"var"}[/$$]""", media, false),
            @Suppress("ktlint:standard:max-line-length")
            equalTo(
                """<img class=latex alt="\begin{displaymath}\sqrt[3]{2} + \text{&quot;var&quot;}\end{displaymath}" src="latex-ac92a31b0e2dc842ac2b3542a68f81d89438793a.png">""",
            ),
        )

        // Test without access to media
        assertThat(
            LaTeX.convertMath("""[$$]\sqrt[3]{2} + \text{"var"}[/$$]""", col.media, false),
            equalTo("""\begin{displaymath}\sqrt[3]{2} + \text{"var"}\end{displaymath}"""),
        )
    }

    @Test
    fun mungeQATest() {
        val col = col
        val m: Media = MockMedia(col)

        // Test with media access
        assertThat(
            LaTeX.mungeQA("[$]\\sqrt[3]{2} + \\text{\"var\"}[/$]", m, false),
            @Suppress("ktlint:standard:max-line-length")
            equalTo(
                "<img class=latex alt=\"$\\sqrt[3]{2} + \\text{&quot;var&quot;}$\" src=\"latex-dd84e5d506179a137f7924d0960609a8c89d491e.png\">",
            ),
        )

        // Test without access to media
        assertThat(
            LaTeX.mungeQA("[$]\\sqrt[3]{2} + \\text{\"var\"}[/$]", col, false),
            equalTo("$\\sqrt[3]{2} + \\text{\"var\"}$"),
        )
    }
}
