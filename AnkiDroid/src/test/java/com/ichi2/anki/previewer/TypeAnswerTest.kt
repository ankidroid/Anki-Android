// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.previewer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.libanki.Card
import com.ichi2.testutils.JvmTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.runner.RunWith
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class TypeAnswerTest : JvmTest() {
    /** [Issue #20575](https://github.com/ankidroid/Anki-Android/issues/20575) */
    @Test
    fun `answerFilter escapes Regex`() =
        runTest {
            val card = addBasicWithTypingNote("List directory contents.", "$ ls").firstCard()

            val typeAnswer = TypeAnswer.createInstance(card)

            val result = assertDoesNotThrow { typeAnswer.answerFilter("") }
            assertThat(result, containsString("$ ls"))
            assertThat(result, not(containsString("[[type:Back]]")))
        }

    @Test
    fun `chained modifiers are parsed`() =
        runTest {
            val card = addClozeNote("{{c1::hello}} world").firstCard()

            val typeAnswer = TypeAnswer.getInstance(card, "[[type:cloze:nc:Text]]")

            assertNotNull(typeAnswer, "chained 'cloze' and 'nc' modifiers")
            assertThat(typeAnswer.expectedAnswer, equalTo("hello"))
        }

    companion object {
        suspend fun TypeAnswer.Companion.createInstance(card: Card) = requireNotNull(TypeAnswer.getInstance(card, VALID_CARD_TEXT))

        const val VALID_CARD_TEXT = """<style>.card {
    font-family: arial;
    font-size: 20px;
    line-height: 1.5;
    text-align: center;
    color: black;
    background-color: white;
}
</style>List directory contents.

<hr id=answer>

[[type:Back]]"""
    }
}
