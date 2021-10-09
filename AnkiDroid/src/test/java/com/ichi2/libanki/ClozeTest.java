//noinspection MissingCopyrightHeader #8659

package com.ichi2.libanki;

import android.content.Context;

import com.ichi2.anki.RobolectricTest;

import com.ichi2.utils.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class ClozeTest extends RobolectricTest {

    @Test
    public void testCloze() {
        final Context context = ApplicationProvider.getApplicationContext();

        Collection d = getCol();
        Note f = d.newNote(d.getModels().byName("Cloze"));

        try {
            String name = f.model().getString("name");
            assertEquals("Cloze", name);
        } catch (JSONException e) {
            fail();
        }
        // a cloze model with no clozes is not empty
        f.setItem("Text", "nothing");
        assertThat(d.addNote(f), is(greaterThan(0)));
        Card card = f.cards().get(0);
        assertTrue(card.isEmpty());
        // try with one cloze
        f = d.newNote(d.getModels().byName("Cloze"));
        f.setItem("Text", "hello {{c1::world}}");
        assertEquals(1, d.addNote(f));
        assertThat(f.firstCard().q(), containsString("hello <span class=cloze>[...]</span>"));
        assertThat(f.firstCard().a(), containsString("hello <span class=cloze>world</span>"));
        // and with a comment
        f = d.newNote(d.getModels().byName("Cloze"));
        f.setItem("Text", "hello {{c1::world::typical}}");
        assertEquals(1, d.addNote(f));
        assertThat(f.firstCard().q(), containsString("<span class=cloze>[typical]</span>"));
        assertThat(f.firstCard().a(), containsString("<span class=cloze>world</span>"));
        // and with two clozes
        f = d.newNote(d.getModels().byName("Cloze"));
        f.setItem("Text", "hello {{c1::world}} {{c2::bar}}");
        assertEquals(2, d.addNote(f));
        Card c1 = f.firstCard();
        Card c2 = f.cards().get(1);
        assertThat(c1.q(), containsString("<span class=cloze>[...]</span> bar"));
        assertThat(c1.a(), containsString("<span class=cloze>world</span> bar"));
        assertThat(c2.q(), containsString("world <span class=cloze>[...]</span>"));
        assertThat(c2.a(), containsString("world <span class=cloze>bar</span>"));
        // if there are multiple answers for a single cloze, they are given in a
        // list
        f = d.newNote(d.getModels().byName("Cloze"));
        f.setItem("Text", "a {{c1::b}} {{c1::c}}");
        assertEquals(1, d.addNote(f));
        assertThat(f.firstCard().a(), containsString("<span class=cloze>b</span> <span class=cloze>c</span>"));
        // if we add another cloze, a card should be generated
        int cnt = d.cardCount();
        f.setItem("Text", "{{c2::hello}} {{c1::foo}}");
        f.flush();
        assertEquals(cnt+1, d.cardCount());
        // 0 or negative indices are not supported
        f.setItem("Text", "{{c0::zero}} {{c-1:foo}}");
        f.flush();
        assertEquals(2, f.cards().size());
        //Try a multiline cloze
        f.setItem("Text", "Cloze with {{c1::multi-line\n" +
                "string}}");
        f.flush();
        assertEquals(1, d.addNote(f));
        String a = f.firstCard().q();
        String b = f.firstCard().a();
        assertThat(f.firstCard().q(), containsString("Cloze with <span class=cloze>[...]</span>"));
        assertThat(f.firstCard().a(), containsString("Cloze with <span class=cloze>multi-line\nstring</span>"));
        //try a multiline cloze in p tag
        f.setItem("Text", "<p>Cloze in html tag with {{c1::multi-line\n" +
                "string}}</p>");
        f.flush();
        assertEquals(1, d.addNote(f));
        assertThat(f.firstCard().q(), containsString("<p>Cloze in html tag with <span class=cloze>[...]</span>"));
        assertThat(f.firstCard().a(), containsString("<p>Cloze in html tag with <span class=cloze>multi-line\nstring</span>"));

        //make sure multiline cloze things aren't too greedy
        f.setItem("Text", "<p>Cloze in html tag with {{c1::multi-line\n" +
                "string}} and then {{c2:another\n" +
                "one}}</p>");
        f.flush();
        assertEquals(1, d.addNote(f));
        assertThat(f.firstCard().q(), containsString("<p>Cloze in html tag with <span class=cloze>[...]</span> and then {{c2:another\n" +
                "one}}</p>"));

        assertThat(f.firstCard().a(), containsString("<p>Cloze in html tag with <span class=cloze>multi-line\n" +
                "string</span> and then {{c2:another\n" +
                "one}}</p>"));
    }
}
