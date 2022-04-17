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
import com.ichi2.anki.RobolectricTest
import com.ichi2.utils.KotlinCleanup
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@KotlinCleanup("IDE lint")
@KotlinCleanup("`is` -> equalTo")
class LaTeXTest : RobolectricTest() {
    class MockMedia(col: Collection?) : Media(col, false) {
        /**
         * @param fname A field name
         * @return Always true, given that we want to assume the field exists in test
         */
        override fun have(fname: String): Boolean {
            return true
        }
    }

    @Test
    fun _imgLinkTest() {
        val col = col
        val m: Media = MockMedia(col)
        val model = col.models.byName("Basic")
        // The hashing function should never change, as it would broke link. So hard coding the expected hash value is valid
        //  Test with media access
        assertThat(
            LaTeX._imgLink("$\\sqrt[3]{2} + \\text{\"var\"}$", model, m),
            `is`("<img class=latex alt=\"\\$\\\\sqrt[3]{2} + \\\\text{&quot;var&quot;}\\$\" src=\"latex-dd84e5d506179a137f7924d0960609a8c89d491e.png\">")
        )

        // Test without access to media
        assertThat(
            LaTeX._imgLink("$\\sqrt[3]{2} + \\text{\"var\"}$", model, col.media),
            `is`("\\$\\\\sqrt[3]{2} + \\\\text{\"var\"}\\$")
        )
    }

    @Test
    fun mungeQATest() {
        val col = col
        val m: Media = MockMedia(col)
        val model = col.models.byName("Basic")

        //  Test with media access
        assertThat(
            LaTeX.mungeQA("[$]\\sqrt[3]{2} + \\text{\"var\"}[/$]", m, model),
            `is`("<img class=latex alt=\"$\\sqrt[3]{2} + \\text{&quot;var&quot;}$\" src=\"latex-dd84e5d506179a137f7924d0960609a8c89d491e.png\">")
        )

        // Test without access to media
        assertThat(
            LaTeX.mungeQA("[$]\\sqrt[3]{2} + \\text{\"var\"}[/$]", col, model),
            `is`("$\\sqrt[3]{2} + \\text{\"var\"}$")
        )
    }
}
