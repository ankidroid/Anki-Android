//noinspection MissingCopyrightHeader #8659

package com.ichi2.libanki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import net.ankiweb.rsdroid.BackendFactory
import net.ankiweb.rsdroid.RustCleanup
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RustCleanup("remove")
@RunWith(AndroidJUnit4::class)
class ClozeTest : RobolectricTest() {
    @Test
    fun testCloze() {
        if (!BackendFactory.defaultLegacySchema) {
            // cloze generation is exercised by the backend tests already, and also in
            // ModelTest.kt
            return
        }
        val d = col
        var f = d.newNote(d.models.byName(col, "Cloze")!!)
        val name = f.model().getString("name")
        assertEquals("Cloze", name)
        // a cloze model with no clozes is not empty
        f.setItem("Text", "nothing")
        assertThat(d.addNote(f), greaterThan(0))
        val card = f.cards(col)[0]
        assertTrue(card.isEmpty(col))
        // try with one cloze
        f = d.newNote(d.models.byName(col, "Cloze")!!)
        f.setItem("Text", "hello {{c1::world}}")
        assertEquals(1, d.addNote(f))
        assertThat(f.firstCard(col).q(col), containsString("hello <span class=cloze>[...]</span>"))
        assertThat(f.firstCard(col).a(col), containsString("hello <span class=cloze>world</span>"))
        // and with a comment
        f = d.newNote(d.models.byName(col, "Cloze")!!)
        f.setItem("Text", "hello {{c1::world::typical}}")
        assertEquals(1, d.addNote(f))
        assertThat(f.firstCard(col).q(col), containsString("<span class=cloze>[typical]</span>"))
        assertThat(f.firstCard(col).a(col), containsString("<span class=cloze>world</span>"))
        // and with two clozes
        f = d.newNote(d.models.byName(col, "Cloze")!!)
        f.setItem("Text", "hello {{c1::world}} {{c2::bar}}")
        assertEquals(2, d.addNote(f))
        val c1 = f.firstCard(col)
        val c2 = f.cards(col)[1]
        assertThat(c1.q(col), containsString("<span class=cloze>[...]</span> bar"))
        assertThat(c1.a(col), containsString("<span class=cloze>world</span> bar"))
        assertThat(c2.q(col), containsString("world <span class=cloze>[...]</span>"))
        assertThat(c2.a(col), containsString("world <span class=cloze>bar</span>"))
        // if there are multiple answers for a single cloze, they are given in a
        // list
        f = d.newNote(d.models.byName(col, "Cloze")!!)
        f.setItem("Text", "a {{c1::b}} {{c1::c}}")
        assertEquals(1, d.addNote(f))
        assertThat(f.firstCard(col).a(col), containsString("<span class=cloze>b</span> <span class=cloze>c</span>"))
        // if we add another cloze, a card should be generated
        val cnt = d.cardCount()
        f.setItem("Text", "{{c2::hello}} {{c1::foo}}")
        f.flush(col)
        assertEquals((cnt + 1), d.cardCount())
        // 0 or negative indices are not supported
        f.setItem("Text", "{{c0::zero}} {{c-1:foo}}")
        f.flush(col)
        assertEquals(2, f.cards(col).size)
        // Try a multiline cloze
        f.setItem(
            "Text",
            """
     Cloze with {{c1::multi-line
     string}}
            """.trimIndent()
        )
        f.flush(col)
        if (!BackendFactory.defaultLegacySchema) { f.id = 0 }
        assertEquals(1, d.addNote(f))
        assertThat(f.firstCard(col).q(col), containsString("Cloze with <span class=cloze>[...]</span>"))
        assertThat(f.firstCard(col).a(col), containsString("Cloze with <span class=cloze>multi-line\nstring</span>"))
        // try a multiline cloze in p tag
        f.setItem(
            "Text",
            """
     <p>Cloze in html tag with {{c1::multi-line
     string}}</p>
            """.trimIndent()
        )
        f.flush(col)
        if (!BackendFactory.defaultLegacySchema) { f.id = 0 }
        assertEquals(1, d.addNote(f))
        assertThat(f.firstCard(col).q(col), containsString("<p>Cloze in html tag with <span class=cloze>[...]</span>"))
        assertThat(f.firstCard(col).a(col), containsString("<p>Cloze in html tag with <span class=cloze>multi-line\nstring</span>"))

        // make sure multiline cloze things aren't too greedy
        f.setItem(
            "Text",
            """
     <p>Cloze in html tag with {{c1::multi-line
     string}} and then {{c2:another
     one}}</p>
            """.trimIndent()
        )
        f.flush(col)
        if (!BackendFactory.defaultLegacySchema) { f.id = 0 }
        assertEquals(1, d.addNote(f))
        assertThat(
            f.firstCard(col).q(col),
            containsString(
                """
    <p>Cloze in html tag with <span class=cloze>[...]</span> and then {{c2:another
    one}}</p>
                """.trimIndent()
            )
        )
        assertThat(
            f.firstCard(col).a(col),
            containsString(
                """
    <p>Cloze in html tag with <span class=cloze>multi-line
    string</span> and then {{c2:another
    one}}</p>
                """.trimIndent()
            )
        )
    }
}
