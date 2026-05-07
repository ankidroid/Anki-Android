/*
 Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.noteeditor

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test

class MathJaxFormatTest {
    @Test
    fun `block wraps text in display-math delimiters`() {
        assertThat(
            MathJaxFormat.BLOCK
                .toTextWrapper()
                .format("E=mc^2")
                .result,
            equalTo("""\[E=mc^2\]"""),
        )
    }

    @Test
    fun `chemistry wraps text in mhchem ce delimiters`() {
        assertThat(
            MathJaxFormat.CHEMISTRY
                .toTextWrapper()
                .format("H2O")
                .result,
            equalTo("""\( \ce{H2O} \)"""),
        )
    }
}
