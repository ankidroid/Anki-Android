// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.cardviewer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.cardviewer.TypeAnswer.Companion.contentForCloze
import org.intellij.lang.annotations.Language
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class TypeAnswerTest : RobolectricTest() {
    override fun setUp() {
        super.setUp()
        col
    }

    @Test
    fun `HTML answer marks nosuggest fields and requests a Done key`() =
        runTest {
            val field = htmlAnswerField("nosuggest:type")

            assertEquals("true", field.attr("data-ankidroid-nosuggest"))
            assertEquals("done", field.attr("enterkeyhint"))
        }

    @Test
    fun `ordinary HTML answers keep their keyboard defaults`() =
        runTest {
            val field = htmlAnswerField("type")

            assertFalse(field.hasAttr("data-ankidroid-nosuggest"))
            assertFalse(field.hasAttr("enterkeyhint"))
        }

    private fun htmlAnswerField(filter: String): Element {
        val card = addBasicWithTypingNote("front", "back").firstCard()
        val noteType = card.noteType(col)
        noteType.templates[0].qfmt = "{{Front}}{{$filter:Back}}"
        col.notetypes.save(noteType)

        val typeAnswer = TypeAnswer(useInputTag = true, autoFocus = true)
        typeAnswer.updateInfo(col, card, targetContext.resources)
        val html = typeAnswer.filterQuestion(card.question(col))
        return assertNotNull(Jsoup.parseBodyFragment(html).selectFirst("input#typeans"))
    }

    @Test
    fun testTypeAnsAnswerFilterNormalCorrect() {
        @Language("HTML")
        val buf = """<style>.card {
 font-family: arial;
 font-size: 20px;
 text-align: center;
 color: black;
 background-color: white;
}
</style>Type in hello
[[type:Back]]

<hr id=answer>

$!"""

        @Language("HTML")
        val expectedOutput = """<style>.card {
 font-family: arial;
 font-size: 20px;
 text-align: center;
 color: black;
 background-color: white;
}
</style>Type in hello
<code id=typeans><span class=typeGood>hello</span></code>

<hr id=answer>

$!"""
        assertEquals(expectedOutput, typeAnsAnswerFilter(buf, "hello", "hello"))
    }

    @Test
    fun testTypeAnsAnswerFilterNormalIncorrect() {
        @Language("HTML")
        val buf = """<style>.card {
 font-family: arial;
 font-size: 20px;
 text-align: center;
 color: black;
 background-color: white;
}
</style>Type in hello
[[type:Back]]

<hr id=answer>

hello"""

        @Language("HTML")
        val expectedOutput = """<style>.card {
 font-family: arial;
 font-size: 20px;
 text-align: center;
 color: black;
 background-color: white;
}
</style>Type in hello
<code id=typeans><span class=typeBad>hello</span><br><span id=typearrow>&darr;</span><br><span class=typeMissed>xyzzy$$$22</span></code>

<hr id=answer>

hello"""
        // Make sure $! as typed shows up as $!
        assertEquals(expectedOutput, typeAnsAnswerFilter(buf, "hello", "xyzzy$$$22"))
    }

    @Test
    fun testTypeAnsAnswerFilterNormalEmpty() {
        @Language("HTML")
        val buf = """<style>.card {
 font-family: arial;
 font-size: 20px;
 text-align: center;
 color: black;
 background-color: white;
}
</style>Type in hello
[[type:Back]]

<hr id=answer>

hello"""

        @Language("HTML")
        val expectedOutput = """<style>.card {
 font-family: arial;
 font-size: 20px;
 text-align: center;
 color: black;
 background-color: white;
}
</style>Type in hello
<code id=typeans>hello</code>

<hr id=answer>

hello"""
        // Make sure $! as typed shows up as $!
        assertEquals(expectedOutput, typeAnsAnswerFilter(buf, "", "hello"))
    }

    @Test
    fun testTypeAnsAnswerFilterDollarSignsCorrect() {
        @Language("HTML")
        val buf = """<style>.card {
 font-family: arial;
 font-size: 20px;
 text-align: center;
 color: black;
 background-color: white;
}
</style>Type in $!
[[type:Back]]

<hr id=answer>

$!"""

        @Language("HTML")
        val expectedOutput = """<style>.card {
 font-family: arial;
 font-size: 20px;
 text-align: center;
 color: black;
 background-color: white;
}
</style>Type in $!
<code id=typeans><span class=typeGood>$!</span></code>

<hr id=answer>

$!"""
        // Make sure $! as typed shows up as $!
        assertEquals(expectedOutput, typeAnsAnswerFilter(buf, "$!", "$!"))
    }

    @Test
    fun testTypeAnsAnswerFilterDollarSignsIncorrect() {
        @Language("HTML")
        val buf = """<style>.card {
 font-family: arial;
 font-size: 20px;
 text-align: center;
 color: black;
 background-color: white;
}
</style>Type in $!
[[type:Back]]

<hr id=answer>

$!"""

        @Language("HTML")
        val expectedOutput = """<style>.card {
 font-family: arial;
 font-size: 20px;
 text-align: center;
 color: black;
 background-color: white;
}
</style>Type in $!
<code id=typeans><span class=typeBad>$!</span><br><span id=typearrow>&darr;</span><br><span class=typeMissed>hello</span></code>

<hr id=answer>

$!"""
        // Make sure $! as typed shows up as $!
        assertEquals(expectedOutput, typeAnsAnswerFilter(buf, "$!", "hello"))
    }

    @Test
    fun testTypeAnsAnswerFilterDollarSignsEmpty() {
        @Language("HTML")
        val buf = """<style>.card {
 font-family: arial;
 font-size: 20px;
 text-align: center;
 color: black;
 background-color: white;
}
</style>Type in $!
[[type:Back]]

<hr id=answer>

$!"""

        @Language("HTML")
        val expectedOutput = """<style>.card {
 font-family: arial;
 font-size: 20px;
 text-align: center;
 color: black;
 background-color: white;
}
</style>Type in $!
<code id=typeans>$!</code>

<hr id=answer>

$!"""
        // Make sure $! as typed shows up as $!
        assertEquals(expectedOutput, typeAnsAnswerFilter(buf, "", "$!"))
    }

    @Test
    fun testClozeWithRepeatedWords() {
        // 8229
        val cloze1 = "This is {{c1::test}} which is containing {{c1::test}} word twice"
        assertEquals("test", contentForCloze(cloze1, 1))
        val cloze2 = "This is {{c1::test}} which is containing {{c1::test}} word twice {{c1::test2}}"
        assertEquals("test, test, test2", contentForCloze(cloze2, 1))
    }

    private fun typeAnsAnswerFilter(
        answer: String,
        correctAnswer: String,
        userAnswer: String,
    ): String =
        TypeAnswer(
            useInputTag = false,
            autoFocus = false,
        ).filterAnswer(answer, correctAnswer, userAnswer)
}
