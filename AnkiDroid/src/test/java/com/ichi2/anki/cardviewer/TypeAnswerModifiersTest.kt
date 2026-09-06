// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.cardviewer

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class TypeAnswerModifiersTest {
    @Test
    fun `parses bare field`() {
        val parsed = TypeAnswerModifiers.parse("Back")
        assertThat(parsed, equalTo(TypeAnswerModifiers("Back", combining = true, cloze = false)))
    }

    @Test
    fun `parses nc only`() {
        val parsed = TypeAnswerModifiers.parse("nc:Back")
        assertThat(parsed, equalTo(TypeAnswerModifiers("Back", combining = false, cloze = false)))
    }

    @Test
    fun `parses cloze only`() {
        val parsed = TypeAnswerModifiers.parse("cloze:Text")
        assertThat(parsed, equalTo(TypeAnswerModifiers("Text", combining = true, cloze = true)))
    }

    @Test
    fun `parses chained modifiers`() {
        val parsed = TypeAnswerModifiers.parse("cloze:nc:Text")
        assertThat(parsed, equalTo(TypeAnswerModifiers("Text", combining = false, cloze = true)))
    }

    @Test
    fun `parses chained modifiers in either order`() {
        val parsed = TypeAnswerModifiers.parse("nc:cloze:Text")
        assertThat(parsed, equalTo(TypeAnswerModifiers("Text", combining = false, cloze = true)))
    }
}
