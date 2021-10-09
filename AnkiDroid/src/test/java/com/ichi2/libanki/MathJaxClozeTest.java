//noinspection MissingCopyrightHeader #8659

package com.ichi2.libanki;

import android.content.Context;

import com.ichi2.anki.RobolectricTest;
import com.ichi2.libanki.template.MathJax;
import com.ichi2.libanki.template.TemplateFilters;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

@RunWith(AndroidJUnit4.class)
public class MathJaxClozeTest extends RobolectricTest {

    @Test
    public void removeFormattingFromMathjax() {
        final String original_s = "{{c1::ok}} \\(2^2\\) {{c2::not ok}} \\(2^{{c3::2}}\\) \\(x^3\\) {{c4::blah}} {{c5::text with \\(x^2\\) jax}}";

        assertEquals(original_s, TemplateFilters.removeFormattingFromMathjax(original_s, "1"));
        assertEquals(original_s, TemplateFilters.removeFormattingFromMathjax(original_s, "2"));
        assertEquals(original_s, TemplateFilters.removeFormattingFromMathjax(original_s, "4"));
        assertEquals(original_s, TemplateFilters.removeFormattingFromMathjax(original_s, "5"));

        final String escaped_s = "{{c1::ok}} \\(2^2\\) {{c2::not ok}} \\(2^{{C3::2}}\\) \\(x^3\\) {{c4::blah}} {{c5::text with \\(x^2\\) jax}}";
        assertEquals(escaped_s, TemplateFilters.removeFormattingFromMathjax(original_s, "3"));

        final String original_s2 = "\\(a\\) {{c1::b}} \\[ {{c1::c}} \\]";
        final String escaped_s2 = "\\(a\\) {{c1::b}} \\[ {{C1::c}} \\]";
        assertEquals(escaped_s2, TemplateFilters.removeFormattingFromMathjax(original_s2, "1"));
    }

    @Test
    public void verifyMathJaxClozeCards() {
        final Context context = ApplicationProvider.getApplicationContext();

        Collection c = getCol();
        Note f = c.newNote(c.getModels().byName("Cloze"));
        f.setItem("Text", "{{c1::ok}} \\(2^2\\) {{c2::not ok}} \\(2^{{c3::2}}\\) \\(x^3\\) {{c4::blah}} {{c5::text with \\(x^2\\) jax}}");
        c.addNote(f);
        assertEquals(5, f.numberOfCards());

        ArrayList<Card> cards = f.cards();

        assertThat(cards.get(0).q(), containsString("class=cloze"));
        assertThat(cards.get(1).q(), containsString("class=cloze"));
        String s = cards.get(2).q();
        assertThat(cards.get(2).q(), not(containsString("class=cloze")));
        assertThat(cards.get(3).q(), containsString("class=cloze"));
        assertThat(cards.get(4).q(), containsString("class=cloze"));
    }

    @Test
    public void verifyMathJaxInCloze() {
        final Context context = ApplicationProvider.getApplicationContext();

        Collection c = getCol();
	{
            Note f = c.newNote(c.getModels().byName("Cloze"));
            f.setItem("Text", "\\(1 \\div 2 =\\){{c1::\\(\\frac{1}{2}\\)}}");
            c.addNote(f);

            ArrayList<Card> cards = f.cards();
            Card c2 = cards.get(0);
            String q = c2.q();
            String a = c2.a();
            assertThat(q, containsString("\\(1 \\div 2 =\\)"));
            assertThat(a, containsString("\\(1 \\div 2 =\\)"));
            assertThat(a, containsString("<span class=cloze>\\(\\frac{1}{2}\\)</span>"));
	}

	{
            Note f = c.newNote(c.getModels().byName("Cloze"));
            f.setItem("Text", "\\(a\\) {{c1::b}} \\[ {{c1::c}} \\]");
            c.addNote(f);
            ArrayList<Card> cards = f.cards();
            Card c2 = cards.get(0);
            String q = c2.q();
            assertThat(q, containsString("\\(a\\) <span class=cloze>[...]</span> \\[ [...] \\]"));
	}
    }

    @Test
    public void verifyComplicatedMathJaxCloze() {
        final Context context = ApplicationProvider.getApplicationContext();

        Collection c = getCol();
        Note f = c.newNote(c.getModels().byName("Cloze"));
        f.setItem("Text", "the \\((\\){{c1::\\(x\\)}}\\()\\) is {{c2::\\(y\\)}} but not {{c1::\\(z\\)}} or {{c2::\\(\\lambda\\)}}");

        c.addNote(f);

        ArrayList<Card> cards = f.cards();
        Card c2 = cards.get(0);
        String q = c2.q();
        String a = c2.a();
        assertThat(q, endsWith("</style>the \\((\\)<span class=cloze>[...]</span>\\()\\) is \\(y\\) but not <span class=cloze>[...]</span> or \\(\\lambda\\)"));
        assertThat(a, endsWith("</style>the \\((\\)<span class=cloze>\\(x\\)</span>\\()\\) is \\(y\\) but not <span class=cloze>\\(z\\)</span> or \\(\\lambda\\)<br>\n"));
    }

    @Test
    public void textContainsMathjax()
    {
        assertFalse(MathJax.textContainsMathjax("Hello world."));
        assertFalse(MathJax.textContainsMathjax(""));
        assertTrue(MathJax.textContainsMathjax("This is an inline! \\(1 \\div 2 =\\){{c1::\\(\\frac{1}{2}\\)}}"));
        assertTrue(MathJax.textContainsMathjax("This is two inlines! \\(1 \\div 2 =\\)\\(1 \\div 2 \\)"));
        assertTrue(MathJax.textContainsMathjax("This is an block equation! \\[1 \\div 2 = 1 \\div 2 \\]"));
        assertFalse(MathJax.textContainsMathjax("This has mismatched brackets! \\[1 \\div 2 = 1 \\div 2 \\)"));
        assertFalse(MathJax.textContainsMathjax("This has mismatched brackets too! \\(1 \\div 2 = 1 \\div 2 \\]"));
    }
}
