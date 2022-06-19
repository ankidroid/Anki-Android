/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.cardviewer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.cardviewer.TypeAnswer.Companion.cleanCorrectAnswer
import com.ichi2.anki.cardviewer.TypeAnswer.Companion.contentForCloze
import com.ichi2.testutils.EmptyApplication
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class) // dependency on TextUtils.htmlEncode
@Config(application = EmptyApplication::class)
class TypeAnswerTest {
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
<div><code id="typeans"><span class="typeGood">hello</span><span id="typecheckmark">✔</span></code></div>

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
<div><code id="typeans"><span class="typeBad">hello</span><br><span id="typearrow">&darr;</span><br><span class="typeMissed">xyzzy${"$"}${"$"}$22</span></code></div>

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
<div><code id="typeans"><span class="typeMissed">hello</span></code></div>

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
<div><code id="typeans"><span class="typeGood">$!</span><span id="typecheckmark">✔</span></code></div>

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
<div><code id="typeans"><span class="typeBad">$!</span><br><span id="typearrow">&darr;</span><br><span class="typeMissed">hello</span></code></div>

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
<div><code id="typeans"><span class="typeMissed">$!</span></code></div>

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

    @Test
    fun testMediaIsNotExpected() {
        // #0096 - Anki Desktop did not expect media.
        @Language("HTML")
        val input = "ya[sound:36_ya.mp3]<div><img src=\"paste-efbfdfbff329f818e3b5568e578234d0d0054067.png\" /><br /></div>"
        val expected = "ya"
        val actual: String = cleanCorrectAnswer(input)
        MatcherAssert.assertThat(actual, Matchers.equalTo(expected))
    }

    private fun typeAnsAnswerFilter(answer: String, correctAnswer: String, userAnswer: String): String =
        TypeAnswer(
            doNotUseCodeFormatting = false,
            useInputTag = false,
            autoFocus = false
        ).filterAnswer(answer, correctAnswer, userAnswer)
}
