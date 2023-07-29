//noinspection MissingCopyrightHeader #8659

package com.ichi2.libanki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.libanki.template.MathJax
import com.ichi2.libanki.template.TemplateFilters.removeFormattingFromMathjax
import com.ichi2.utils.KotlinCleanup
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@KotlinCleanup("removeFormattingFromMathjax was imported to stop bug in Kotlin: java.lang.NoSuchFieldError: INSTANCE")
@KotlinCleanup("add testing function returning c.models.byName(\"Cloze\")")
class MathJaxClozeTest : RobolectricTest() {

    @Test
    fun removeFormattingFromMathjax() {
        val original_s = "{{c1::ok}} \\(2^2\\) {{c2::not ok}} \\(2^{{c3::2}}\\) \\(x^3\\) {{c4::blah}} {{c5::text with \\(x^2\\) jax}}"

        assertEquals(original_s, removeFormattingFromMathjax(original_s, "1"))
        assertEquals(original_s, removeFormattingFromMathjax(original_s, "2"))
        assertEquals(original_s, removeFormattingFromMathjax(original_s, "4"))
        assertEquals(original_s, removeFormattingFromMathjax(original_s, "5"))

        val escaped_s = "{{c1::ok}} \\(2^2\\) {{c2::not ok}} \\(2^{{C3::2}}\\) \\(x^3\\) {{c4::blah}} {{c5::text with \\(x^2\\) jax}}"
        assertEquals(escaped_s, removeFormattingFromMathjax(original_s, "3"))

        val original_s2 = "\\(a\\) {{c1::b}} \\[ {{c1::c}} \\]"
        val escaped_s2 = "\\(a\\) {{c1::b}} \\[ {{C1::c}} \\]"
        assertEquals(escaped_s2, removeFormattingFromMathjax(original_s2, "1"))
    }

    @Test
    fun verifyMathJaxClozeCards() {
        val c = col

        val note = c.newNote(c.models.byName("Cloze")!!)
        note.setItem("Text", "{{c1::ok}} \\(2^2\\) {{c2::not ok}} \\(2^{{c3::2}}\\) \\(x^3\\) {{c4::blah}} {{c5::text with \\(x^2\\) jax}}")
        c.addNote(note)
        assertEquals(5, note.numberOfCards())

        val cards = note.cards()

        assertThat(cards[0].q(), containsString(clozeClass()))
        assertThat(cards[1].q(), containsString(clozeClass()))
        assertThat(cards[2].q(), not(containsString(clozeClass())))
        assertThat(cards[3].q(), containsString(clozeClass()))
        assertThat(cards[4].q(), containsString(clozeClass()))
    }

    @Test
    fun textContainsMathjax() {
        assertFalse(MathJax.textContainsMathjax("Hello world."))
        assertFalse(MathJax.textContainsMathjax(""))
        assertTrue(MathJax.textContainsMathjax("This is an inline! \\(1 \\div 2 =\\){{c1::\\(\\frac{1}{2}\\)}}"))
        assertTrue(MathJax.textContainsMathjax("This is two inlines! \\(1 \\div 2 =\\)\\(1 \\div 2 \\)"))
        assertTrue(MathJax.textContainsMathjax("This is an block equation! \\[1 \\div 2 = 1 \\div 2 \\]"))
        assertFalse(MathJax.textContainsMathjax("This has mismatched brackets! \\[1 \\div 2 = 1 \\div 2 \\)"))
        assertFalse(MathJax.textContainsMathjax("This has mismatched brackets too! \\(1 \\div 2 = 1 \\div 2 \\]"))
    }
}
