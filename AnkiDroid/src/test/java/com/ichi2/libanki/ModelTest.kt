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
import com.ichi2.anki.exception.ConfirmModSchemaException
import com.ichi2.libanki.Consts.MODEL_CLOZE
import com.ichi2.libanki.Models.Companion.REQ_ALL
import com.ichi2.libanki.Models.Companion.REQ_ANY
import com.ichi2.libanki.Utils.stripHTML
import com.ichi2.utils.KotlinCleanup
import com.ichi2.utils.ListUtil.Companion.assertListEquals
import net.ankiweb.rsdroid.BackendFactory
import net.ankiweb.rsdroid.RustCleanup
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.*
import kotlin.test.assertFailsWith

fun clozeClass(): String {
    return if (BackendFactory.defaultLegacySchema) {
        "class=cloze"
    } else {
        "class=\"cloze\""
    }
}

fun clozeData(data: String): String {
    return if (BackendFactory.defaultLegacySchema) {
        ""
    } else {
        " data-cloze=\"${data}\""
    }
}

@RunWith(AndroidJUnit4::class)
@KotlinCleanup("improve kotlin code where possible")
class ModelTest : RobolectricTest() {
    @Test
    fun test_frontSide_field() {
        // #8951 - Anki Special-cases {{FrontSide}} on the front to return empty string
        val col = col
        val m = col.models.current()
        m!!.getJSONArray("tmpls").getJSONObject(0).put("qfmt", "{{Front}}{{FrontSide}}")
        col.models.save(m)
        val note = col.newNote()
        note.setItem("Front", "helloworld")
        col.addNote(note)
        val card = note.firstCard()
        val q = card.q()
        assertThat(
            "field should be at the end of the template - empty string for front",
            q,
            endsWith("helloworld")
        )
        assertThat(
            "field should not have a problem",
            q,
            not(containsString("has a problem"))
        )
    }

    @Test
    fun test_field_named_frontSide() {
        // #8951 - A field named "FrontSide" is ignored - this matches Anki 2.1.34 (8af8f565)
        val col = col
        val m = col.models.current()

        // Add a field called FrontSide and FrontSide2 (to ensure that fields are added correctly)
        col.models.addFieldModChanged(m!!, col.models.newField("FrontSide"))
        col.models.addFieldModChanged(m, col.models.newField("FrontSide2"))
        m.getJSONArray("tmpls").getJSONObject(0).put("qfmt", "{{Front}}{{FrontSide}}{{FrontSide2}}")
        col.models.save(m)

        val note = col.newNote()
        note.setItem("Front", "helloworld")
        note.setItem("FrontSide", "1")
        note.setItem("FrontSide2", "2")
        col.addNote(note)
        val card = note.firstCard()
        val q = card.q()
        assertThat(
            "FrontSide should be an empty string, even though it was set",
            q,
            endsWith("helloworld2")
        )
    }

    /*****************
     * Models       *
     */
    @Test
    @Throws(ConfirmModSchemaException::class)
    fun test_modelDelete() {
        val col = col
        val note = col.newNote()
        note.setItem("Front", "1")
        note.setItem("Back", "2")
        col.addNote(note)
        assertEquals(1, col.cardCount())
        col.models.rem(col.models.current()!!)
        assertEquals(0, col.cardCount())
    }

    @Test
    fun test_modelCopy() {
        val col = col
        val m = col.models.current()
        val m2 = col.models.copy(m!!)
        assertEquals("Basic copy", m2.getString("name"))
        assertNotEquals(m2.getLong("id"), m.getLong("id"))
        assertEquals(2, m2.getJSONArray("flds").length())
        assertEquals(2, m.getJSONArray("flds").length())
        assertEquals(
            m.getJSONArray("flds").length(),
            m2.getJSONArray("flds").length()
        )
        assertEquals(1, m.getJSONArray("tmpls").length())
        assertEquals(1, m2.getJSONArray("tmpls").length())
        assertEquals(col.models.scmhash(m), col.models.scmhash(m2))
    }

    @Test
    @Throws(ConfirmModSchemaException::class)
    fun test_fields() {
        val col = col
        var note = col.newNote()
        note.setItem("Front", "1")
        note.setItem("Back", "2")
        col.addNote(note)
        val m = col.models.current()
        // make sure renaming a field updates the templates
        col.models.renameField(m!!, m.getJSONArray("flds").getJSONObject(0), "NewFront")
        assertThat(
            m.getJSONArray("tmpls").getJSONObject(0).getString("qfmt"),
            containsString("{{NewFront}}")
        )
        val h = col.models.scmhash(m)
        // add a field
        var field: JSONObject? = col.models.newField("foo")
        col.models.addField(m, field!!)
        assertArrayEquals(
            arrayOf("1", "2", ""),
            col.getNote(
                col.models.nids(
                    m
                )[0]
            ).fields
        )
        assertNotEquals(h, col.models.scmhash(m))
        // rename it
        field = m.getJSONArray("flds").getJSONObject(2)
        col.models.renameField(m, field, "bar")
        assertEquals("", col.getNote(col.models.nids(m)[0]).getItem("bar"))
        // delete back
        col.models.remField(m, m.getJSONArray("flds").getJSONObject(1))
        assertArrayEquals(
            arrayOf("1", ""),
            col.getNote(
                col.models.nids(
                    m
                )[0]
            ).fields
        )
        // move 0 -> 1
        col.models.moveField(m, m.getJSONArray("flds").getJSONObject(0), 1)
        assertArrayEquals(
            arrayOf("", "1"),
            col.getNote(
                col.models.nids(
                    m
                )[0]
            ).fields
        )
        // move 1 -> 0
        col.models.moveField(m, m.getJSONArray("flds").getJSONObject(1), 0)
        assertArrayEquals(
            arrayOf("1", ""),
            col.getNote(
                col.models.nids(
                    m
                )[0]
            ).fields
        )
        // add another and put in middle
        field = col.models.newField("baz")
        col.models.addField(m, field)
        note = col.getNote(col.models.nids(m)[0])
        note.setItem("baz", "2")
        note.flush()
        assertArrayEquals(
            arrayOf("1", "", "2"),
            col.getNote(
                col.models.nids(
                    m
                )[0]
            ).fields
        )
        // move 2 -> 1
        col.models.moveField(m, m.getJSONArray("flds").getJSONObject(2), 1)
        assertArrayEquals(
            arrayOf("1", "2", ""),
            col.getNote(
                col.models.nids(
                    m
                )[0]
            ).fields
        )
        // move 0 -> 2
        col.models.moveField(m, m.getJSONArray("flds").getJSONObject(0), 2)
        assertArrayEquals(
            arrayOf("2", "", "1"),
            col.getNote(
                col.models.nids(
                    m
                )[0]
            ).fields
        )
        // move 0 -> 1
        col.models.moveField(m, m.getJSONArray("flds").getJSONObject(0), 1)
        assertArrayEquals(
            arrayOf("", "2", "1"),
            col.getNote(
                col.models.nids(
                    m
                )[0]
            ).fields
        )
    }

    @Test
    @Throws(ConfirmModSchemaException::class)
    fun test_templates() {
        val col = col
        val m = col.models.current()
        val mm = col.models
        var t = Models.newTemplate("Reverse")
        t.put("qfmt", "{{Back}}")
        t.put("afmt", "{{Front}}")
        mm.addTemplateModChanged(m!!, t)
        mm.save(m)
        val note = col.newNote()
        note.setItem("Front", "1")
        note.setItem("Back", "2")
        col.addNote(note)
        assertEquals(2, col.cardCount())
        val cards: List<Card> = note.cards()
        assertEquals(2, cards.size)
        var c = cards[0]
        val c2 = cards[1]
        // first card should have first ord
        assertEquals(0, c.ord)
        assertEquals(1, c2.ord)
        // switch templates
        col.models.moveTemplate(m, c.template(), 1)
        c.load(col)
        c2.load(col)
        assertEquals(1, c.ord)
        assertEquals(0, c2.ord)
        // removing a template should delete its cards
        col.models.remTemplate(m, m.getJSONArray("tmpls").getJSONObject(0))
        assertEquals(1, col.cardCount())
        // and should have updated the other cards' ordinals
        c = note.cards()[0]
        assertEquals(0, c.ord)
        assertEquals("1", stripHTML(c.q()))
        // it shouldn't be possible to orphan notes by removing templates
        t = Models.newTemplate("template name")
        t.put("qfmt", "{{Front}}1")
        mm.addTemplateModChanged(m, t)
        col.models.remTemplate(m, m.getJSONArray("tmpls").getJSONObject(0))
        assertEquals(
            0,
            col.db.queryLongScalar(
                "select count() from cards where nid not in (select id from notes)"
            )
        )
    }

    @Test
    @Throws(ConfirmModSchemaException::class)
    fun test_cloze_ordinals() {
        val col = col
        col.models.setCurrent(col.models.byName("Cloze")!!)
        val m = col.models.current()
        val mm = col.models

        // We replace the default Cloze template
        val t = Models.newTemplate("ChainedCloze")
        t.put("qfmt", "{{text:cloze:Text}}")
        t.put("afmt", "{{text:cloze:Text}}")
        mm.addTemplateModChanged(m!!, t)
        mm.save(m)
        col.models.remTemplate(m, m.getJSONArray("tmpls").getJSONObject(0))

        val note = col.newNote()
        note.setItem("Text", "{{c1::firstQ::firstA}}{{c2::secondQ::secondA}}")
        col.addNote(note)
        assertEquals(2, col.cardCount())
        val cards: List<Card> = note.cards()
        assertEquals(2, cards.size)
        val c = cards[0]
        val c2 = cards[1]
        // first card should have first ord
        assertEquals(0, c.ord)
        assertEquals(1, c2.ord)
    }

    @Test
    fun test_cloze_empty() {
        val col = col
        val mm = col.models
        val clozeModel = mm.byName("Cloze")
        mm.setCurrent(clozeModel!!)
        assertListEquals(
            listOf(0, 1),
            Models.availOrds(clozeModel, arrayOf("{{c1::Empty}} and {{c2::}}", ""))
        )
    }

    @Test
    fun test_text() {
        val col = col
        val m = col.models.current()
        m!!.getJSONArray("tmpls").getJSONObject(0).put("qfmt", "{{text:Front}}")
        col.models.save(m)
        val note = col.newNote()
        note.setItem("Front", "hello<b>world")
        col.addNote(note)
        assertThat(note.cards()[0].q(), containsString("helloworld"))
    }

    @Test
    fun test_cloze() {
        fun clearId(note: Note) {
            if (!BackendFactory.defaultLegacySchema) {
                // backend protects against adding the same note twice
                note.id = 0
            }
        }
        val col = col
        col.models.setCurrent(col.models.byName("Cloze")!!)
        var note = col.newNote()
        assertEquals("Cloze", note.model().getString("name"))
        // a cloze model with no clozes is not empty
        note.setItem("Text", "nothing")
        assertEquals(1, col.addNote(note))
        clearId(note)
        assertEquals(1, col.addNote(note, Models.AllowEmpty.TRUE))
        clearId(note)
        assertEquals(1, col.addNote(note, Models.AllowEmpty.ONLY_CLOZE))
        if (BackendFactory.defaultLegacySchema) {
            assertEquals(0, col.addNote(note, Models.AllowEmpty.FALSE))
        }
        // try with one cloze
        note = col.newNote()
        note.setItem("Text", "hello {{c1::world}}")
        assertEquals(1, col.addNote(note))
        clearId(note)
        assertEquals(1, col.addNote(note, Models.AllowEmpty.TRUE))
        clearId(note)
        assertEquals(1, col.addNote(note, Models.AllowEmpty.ONLY_CLOZE))
        if (BackendFactory.defaultLegacySchema) {
            assertEquals(1, col.addNote(note, Models.AllowEmpty.FALSE))
        }
        if (!BackendFactory.defaultLegacySchema) {
            // below needs updating to support latest backend output
            return
        }

        assertThat(
            note.cards()[0].q(),
            containsString("hello <span ${clozeClass()}${clozeData("world")}>[...]</span>")
        )
        assertThat(
            note.cards()[0].a(),
            containsString("hello <span ${clozeClass()}>world</span>")
        )
        // and with a comment
        note = col.newNote()
        note.setItem("Text", "hello {{c1::world::typical}}")
        assertEquals(1, col.addNote(note))
        clearId(note)
        assertEquals(1, col.addNote(note, Models.AllowEmpty.TRUE))
        clearId(note)
        assertEquals(1, col.addNote(note, Models.AllowEmpty.ONLY_CLOZE))
        clearId(note)
        assertEquals(1, col.addNote(note, Models.AllowEmpty.FALSE))
        assertThat(
            note.cards()[0].q(),
            containsString("<span ${clozeClass()}${clozeData("world")}>[typical]</span>")
        )
        assertThat(
            note.cards()[0].a(),
            containsString("<span ${clozeClass()}>world</span>")
        )
        // and with 2 clozes
        note = col.newNote()
        note.setItem("Text", "hello {{c1::world}} {{c2::bar}}")
        assertEquals(2, col.addNote(note))
        val cards: List<Card> = note.cards()
        assertEquals(2, cards.size)
        val c1 = cards[0]
        val c2 = cards[1]
        assertThat(
            c1.q(),
            containsString("<span ${clozeClass()}${clozeData("world")}>[...]</span> bar")
        )
        assertThat(
            c1.a(),
            containsString("<span ${clozeClass()}>world</span> bar")
        )
        assertThat(
            c2.q(),
            containsString("world <span ${clozeClass()}${clozeData("bar")}>[...]</span>")
        )
        assertThat(
            c2.a(),
            containsString("world <span ${clozeClass()}>bar</span>")
        )
        // if there are multiple answers for a single cloze, they are given in a
        // list
        note.setItem("Text", "a {{c1::b}} {{c1::c}}")
        clearId(note)
        assertEquals(1, col.addNote(note))
        clearId(note)
        assertEquals(1, col.addNote(note, Models.AllowEmpty.TRUE))
        clearId(note)
        assertEquals(1, col.addNote(note, Models.AllowEmpty.ONLY_CLOZE))
        clearId(note)
        assertEquals(1, col.addNote(note, Models.AllowEmpty.FALSE))
        assertThat(
            note.cards()[0].a(),
            containsString("<span ${clozeClass()}>b</span> <span ${clozeClass()}>c</span>")
        )
        // if we add another cloze, a card should be generated
        note.setItem("Text", "{{c2::hello}} {{c1::foo}}")
        clearId(note)
        assertEquals(2, col.addNote(note))
        clearId(note)
        assertEquals(2, col.addNote(note, Models.AllowEmpty.TRUE))
        clearId(note)
        assertEquals(2, col.addNote(note, Models.AllowEmpty.ONLY_CLOZE))
        clearId(note)
        assertEquals(2, col.addNote(note, Models.AllowEmpty.FALSE))
        // 0 or negative indices are not supported
        note.setItem("Text", "{{c0::zero}} {{c-1:foo}}")
        clearId(note)
        assertEquals(1, col.addNote(note))
        clearId(note)
        assertEquals(1, col.addNote(note, Models.AllowEmpty.TRUE))
        clearId(note)
        assertEquals(1, col.addNote(note, Models.AllowEmpty.ONLY_CLOZE))
        if (BackendFactory.defaultLegacySchema) {
            assertEquals(0, col.addNote(note, Models.AllowEmpty.FALSE))
        }

        note = col.newNote()
        note.setItem("Text", "hello {{c1::world}}")
        col.addNote(note)
        assertEquals(1, note.numberOfCards())
        note.setItem("Text", "hello {{c2::world}}")
        note.flush()
        assertEquals(2, note.numberOfCards())
        note.setItem("Text", "{{c1::hello}} {{c2::world}}")
        note.flush()
        assertEquals(2, note.numberOfCards())
        note.setItem("Text", "{{c1::hello}} {{c3::world}}")
        note.flush()
        assertEquals(3, note.numberOfCards())
        note.setItem("Text", "{{c0::hello}} {{c-1::world}}")
        note.flush()
        assertEquals(3, note.numberOfCards())
    }

    @Test
    fun test_cloze_mathjax() {
        val col = col
        col.models.setCurrent(col.models.byName("Cloze")!!)
        var note = col.newNote()
        note.setItem(
            "Text",
            "{{c1::ok}} \\(2^2\\) {{c2::not ok}} \\(2^{{c3::2}}\\) \\(x^3\\) {{c4::blah}} {{c5::text with \\(x^2\\) jax}}"
        )
        assertNotEquals(0, col.addNote(note))
        assertEquals(5, note.numberOfCards())
        assertThat(note.cards()[0].q(), containsString(clozeClass()))
        assertThat(note.cards()[1].q(), containsString(clozeClass()))
        assertThat(
            note.cards()[2].q(),
            not(containsString(clozeClass()))
        )
        assertThat(note.cards()[3].q(), containsString(clozeClass()))
        assertThat(note.cards()[4].q(), containsString(clozeClass()))

        note = col.newNote()
        note.setItem("Text", "\\(a\\) {{c1::b}} \\[ {{c1::c}} \\]")
        assertNotEquals(0, col.addNote(note))
        assertEquals(1, note.numberOfCards())
        val question = note.cards()[0].q()
        if (!BackendFactory.defaultLegacySchema) {
            // below needs updating to support latest backend output
            return
        }
        assertTrue(
            "Question «$question» does not end correctly",
            question.endsWith("\\(a\\) <span ${clozeClass()}${clozeData("b")}>[...]</span> \\[ [...] \\]")
        )
    }

    @Test
    fun test_type_and_cloze() {
        val col = col
        val m = col.models.byName("Cloze")
        col.models.setCurrent(m!!)
        m.getJSONArray("tmpls").getJSONObject(0).put("qfmt", "{{cloze:Text}}{{type:cloze:Text}}")
        col.models.save(m)
        val note = col.newNote()
        note.setItem("Text", "hello {{c1::world}}")
        col.addNote(note)
        assertThat(
            note.cards()[0].q(),
            containsString("[[type:cloze:Text]]")
        )
    }

    @Test
    @Throws(ConfirmModSchemaException::class)
    @Suppress("SpellCheckingInspection") // chaine
    fun test_chained_mods() {
        val col = col
        col.models.setCurrent(col.models.byName("Cloze")!!)
        val m = col.models.current()
        val mm = col.models

        // We replace the default Cloze template
        val t = Models.newTemplate("ChainedCloze")
        t.put("qfmt", "{{cloze:text:Text}}")
        t.put("afmt", "{{cloze:text:Text}}")
        mm.addTemplateModChanged(m!!, t)
        mm.save(m)
        col.models.remTemplate(m, m.getJSONArray("tmpls").getJSONObject(0))
        val note = col.newNote()
        val q1 = "<span style=\"color:red\">phrase</span>"
        val a1 = "<b>sentence</b>"
        val q2 = "<span style=\"color:red\">en chaine</span>"
        val a2 = "<i>chained</i>"
        note.setItem("Text", "This {{c1::$q1::$a1}} demonstrates {{c1::$q2::$a2}} clozes.")
        assertEquals(1, col.addNote(note))
        note.cards()[0].q()
        /* TODO: chained modifier
        assertThat("Question «"+question+"» does not contain the expected string", question, containsString("This <span class=cloze>[sentence]</span> demonstrates <span class=cloze>[chained]</span> clozes.")
                   );
        assertThat(note.cards().get(0).a(), containsString("This <span class=cloze>phrase</span> demonstrates <span class=cloze>en chaine</span> clozes."
                                                    ));

         */
    }

    @Test
    @Throws(ConfirmModSchemaException::class)
    fun test_modelChange() {
        val col = col
        val cloze = col.models.byName("Cloze")
        // enable second template and add a note
        val basic = col.models.current()
        val mm = col.models
        val t = Models.newTemplate("Reverse")
        t.put("qfmt", "{{Back}}")
        t.put("afmt", "{{Front}}")
        mm.addTemplateModChanged(basic!!, t)
        mm.save(basic)
        var note = col.newNote()
        note.setItem("Front", "note")
        note.setItem("Back", "b123")
        col.addNote(note)
        // switch fields
        var map: MutableMap<Int, Int?> = HashMap()
        val noOp = mapOf<Int, Int?>(0 to 0, 1 to 1)
        map[0] = 1
        map[1] = 0
        col.models.change(basic, note.id, basic, map, noOp)
        note.load()
        assertEquals("b123", note.getItem("Front"))
        assertEquals("note", note.getItem("Back"))
        // switch cards
        val c0 = note.cards()[0]
        val c1 = note.cards()[1]
        assertThat(c0.q(), containsString("b123"))
        assertThat(c1.q(), containsString("note"))
        assertEquals(0, c0.ord)
        assertEquals(1, c1.ord)
        col.models.change(basic, note.id, basic, noOp, map)
        note.load()
        c0.load(col)
        c1.load(col)
        assertThat(c0.q(), containsString("note"))
        assertThat(c1.q(), containsString("b123"))
        assertEquals(1, c0.ord)
        assertEquals(0, c1.ord)
        // .cards() returns cards in order
        assertEquals(c1.id, note.cards()[0].id)
        // delete first card
        map = HashMap()
        map[0] = null
        map[1] = 1
        // if (isWin) {
        //     // The low precision timer on Windows reveals a race condition
        //     time.sleep(0.05);
        // }
        col.models.change(basic, note.id, basic, noOp, map)
        note.load()
        c0.load(col)
        // the card was deleted
        // but we have two cards, as a new one was generated
        assertEquals(2, note.numberOfCards())
        // an unmapped field becomes blank
        assertEquals("b123", note.getItem("Front"))
        assertEquals("note", note.getItem("Back"))
        col.models.change(basic, note.id, basic, map, noOp)
        note.load()
        assertEquals("", note.getItem("Front"))
        assertEquals("note", note.getItem("Back"))
        // another note to try model conversion
        note = col.newNote()
        note.setItem("Front", "f2")
        note.setItem("Back", "b2")
        col.addNote(note)
        // counts = col.getModels().all_use_counts();
        // Using older version of the test
        assertEquals(2, col.models.useCount(basic))
        assertEquals(0, col.models.useCount(cloze!!))
        // Identity map
        map = HashMap()
        map[0] = 0
        map[1] = 1
        col.models.change(basic, note.id, cloze, map, map)
        note.load()
        assertEquals("f2", note.getItem("Text"))
        assertEquals(2, note.numberOfCards())
        // back the other way, with deletion of second ord
        col.models.remTemplate(basic, basic.getJSONArray("tmpls").getJSONObject(1))
        assertEquals(
            2,
            col.db.queryScalar("select count() from cards where nid = ?", note.id)
        )
        map = HashMap()
        map[0] = 0
        col.models.change(cloze, note.id, basic, map, map)
        assertEquals(
            1,
            col.db.queryScalar("select count() from cards where nid = ?", note.id)
        )
    }

    private fun reqSize(model: Model?) {
        if (model!!.getInt("type") == MODEL_CLOZE) {
            return
        }
        assertEquals(
            model.getJSONArray("req").length(),
            model.getJSONArray("tmpls").length()
        )
    }

    @Test
    fun test_req() {
        val col = col
        val mm = col.models
        val basic = mm.byName("Basic")
        assertTrue(basic!!.has("req"))
        reqSize(basic)
        var r = basic.getJSONArray("req").getJSONArray(0)
        assertEquals(0, r.getInt(0))
        assertTrue(
            listOf(REQ_ANY, REQ_ALL).contains(r.getString(1))
        )
        assertEquals(1, r.getJSONArray(2).length())
        assertEquals(0, r.getJSONArray(2).getInt(0))

        var opt = mm.byName("Basic (optional reversed card)")
        reqSize(opt)

        r = opt!!.getJSONArray("req").getJSONArray(0)
        assertTrue(
            listOf(REQ_ANY, REQ_ALL).contains(r.getString(1))
        )
        assertEquals(1, r.getJSONArray(2).length())
        assertEquals(0, r.getJSONArray(2).getInt(0))

        assertEquals(JSONArray("[1,\"all\",[1,2]]"), opt.getJSONArray("req").getJSONArray(1))

        // testing any
        opt.getJSONArray("tmpls").getJSONObject(1).put("qfmt", "{{Back}}{{Add Reverse}}")
        mm.save(opt, true)
        assertEquals(
            JSONArray("[1, \"any\", [1, 2]]"),
            opt.getJSONArray("req").getJSONArray(1)
        )
        // testing null
        if (BackendFactory.defaultLegacySchema) {
            // can't add front without field in v16
            opt.getJSONArray("tmpls").getJSONObject(1)
                .put("qfmt", "{{^Add Reverse}}{{/Add Reverse}}")
            mm.save(opt, true)
            assertEquals(
                JSONArray("[1, \"none\", []]"),
                opt.getJSONArray("req").getJSONArray(1)
            )
        }

        opt = mm.byName("Basic (type in the answer)")
        reqSize(opt)
        r = opt!!.getJSONArray("req").getJSONArray(0)
        assertTrue(
            listOf(REQ_ANY, REQ_ALL).contains(r.getString(1))
        )
        if (col.models is ModelsV16) {
            assertEquals(JSONArray("[0, 1]"), r.getJSONArray(2))
        } else {
            // TODO: Port anki@4e33775ed4346ef136ece6ef5efec5ba46057c6b
            assertEquals(JSONArray("[0]"), r.getJSONArray(2))
        }
    }

    @Test
    @Config(qualifiers = "en")
    @RustCleanup("remove")
    fun regression_test_pipe() {
        if (!BackendFactory.defaultLegacySchema) {
            return
        }
        val col = col
        val mm = col.models
        val basic = mm.byName("Basic")
        val template = basic!!.getJSONArray("tmpls").getJSONObject(0)
        template.put("qfmt", "{{|Front}}{{Front}}{{/Front}}{{Front}}")
        assertFailsWith<Exception> {
            // in V16, the "save" throws, in V11, the "add" throws
            mm.save(basic, true)
            addNoteUsingBasicModel("foo", "bar")
        }
    }

    @Test
    fun test_getNamesOfFieldContainingCloze() {
        assertListEquals(ArrayList(), Models.getNamesOfFieldsContainingCloze(""))
        val example = "{{cloze::foo}} <%cloze:bar%>"
        assertListEquals(
            listOf("foo", "bar"),
            Models.getNamesOfFieldsContainingCloze(example)
        )
        assertListEquals(
            listOf("foo", "bar"),
            Models.getNamesOfFieldsContainingCloze(example)
        )
    }

    @Test
    fun nonEmptyFieldTest() {
        val col = col
        val mm = col.models
        val basic = mm.byName("Basic")
        val s: MutableSet<String> = HashSet<String>()
        assertEquals(s, basic!!.nonEmptyFields(arrayOf("", "")))
        s.add("Front")
        assertEquals(
            s,
            basic.nonEmptyFields(arrayOf("<br/>", "   \t "))
        ) // Html is not stripped to check for card generation
        assertEquals(s, basic.nonEmptyFields(arrayOf("P", "")))
        s.add("Back")
        assertEquals(s, basic.nonEmptyFields(arrayOf("P", "A")))
    }

    @Test
    fun avail_standard_order_test() {
        val col = col
        val mm = col.models
        val basic = mm.byName("Basic")!!
        val reverse = mm.byName("Basic (and reversed card)")!!

        assertListEquals(ArrayList(), Models._availStandardOrds(basic, arrayOf("", "")))
        assertListEquals(ArrayList(), Models._availStandardOrds(basic, arrayOf("", "Back")))
        assertListEquals(listOf(0), Models._availStandardOrds(basic, arrayOf("Foo", "")))
        assertListEquals(listOf(), Models._availStandardOrds(basic, arrayOf("  \t ", "")))
        assertListEquals(ArrayList(), Models._availStandardOrds(reverse, arrayOf("", "")))
        assertListEquals(listOf(0), Models._availStandardOrds(reverse, arrayOf("Foo", "")))
        assertListEquals(
            listOf(0, 1),
            Models._availStandardOrds(reverse, arrayOf("Foo", "Bar"))
        )
        assertListEquals(
            listOf(1),
            Models._availStandardOrds(reverse, arrayOf("  \t ", "Bar"))
        )

        assertListEquals(ArrayList(), Models._availStandardOrds(basic, arrayOf("", ""), false))
        assertListEquals(ArrayList(), Models._availStandardOrds(basic, arrayOf("", "Back"), false))
        assertListEquals(
            listOf(0),
            Models._availStandardOrds(basic, arrayOf("Foo", ""), false)
        )
        assertListEquals(
            listOf(),
            Models._availStandardOrds(basic, arrayOf("  \t ", ""), false)
        )
        assertListEquals(ArrayList(), Models._availStandardOrds(reverse, arrayOf("", ""), false))
        assertListEquals(
            listOf(0),
            Models._availStandardOrds(reverse, arrayOf("Foo", ""), false)
        )
        assertListEquals(
            listOf(0, 1),
            Models._availStandardOrds(reverse, arrayOf("Foo", "Bar"), false)
        )
        assertListEquals(
            listOf(1),
            Models._availStandardOrds(reverse, arrayOf("  \t ", "Bar"), false)
        )

        assertListEquals(listOf(0), Models._availStandardOrds(basic, arrayOf("", ""), true))
        assertListEquals(
            listOf(0),
            Models._availStandardOrds(basic, arrayOf("", "Back"), true)
        )
        assertListEquals(
            listOf(0),
            Models._availStandardOrds(basic, arrayOf("Foo", ""), true)
        )
        assertListEquals(
            listOf(0),
            Models._availStandardOrds(basic, arrayOf("  \t ", ""), true)
        )
        assertListEquals(
            listOf(0),
            Models._availStandardOrds(reverse, arrayOf("", ""), true)
        )
        assertListEquals(
            listOf(0),
            Models._availStandardOrds(reverse, arrayOf("Foo", ""), true)
        )
        assertListEquals(
            listOf(0, 1),
            Models._availStandardOrds(reverse, arrayOf("Foo", "Bar"), true)
        )
        assertListEquals(
            listOf(1),
            Models._availStandardOrds(reverse, arrayOf("  \t ", "Bar"), true)
        )
    }

    @Test
    fun avail_ords_test() {
        val col = col
        val mm = col.models
        val basic = mm.byName("Basic")!!
        val reverse = mm.byName("Basic (and reversed card)")!!

        assertListEquals(ArrayList(), Models.availOrds(basic, arrayOf("", "")))
        assertListEquals(ArrayList(), Models.availOrds(basic, arrayOf("", "Back")))
        assertListEquals(listOf(0), Models.availOrds(basic, arrayOf("Foo", "")))
        assertListEquals(listOf(), Models.availOrds(basic, arrayOf("  \t ", "")))
        assertListEquals(ArrayList(), Models.availOrds(reverse, arrayOf("", "")))
        assertListEquals(listOf(0), Models.availOrds(reverse, arrayOf("Foo", "")))
        assertListEquals(listOf(0, 1), Models.availOrds(reverse, arrayOf("Foo", "Bar")))
        assertListEquals(listOf(1), Models.availOrds(reverse, arrayOf("  \t ", "Bar")))

        for (allow in arrayOf(Models.AllowEmpty.ONLY_CLOZE, Models.AllowEmpty.FALSE)) {
            assertListEquals(ArrayList(), Models.availOrds(basic, arrayOf("", ""), allow))
            assertListEquals(ArrayList(), Models.availOrds(basic, arrayOf("", "Back"), allow))
            assertListEquals(listOf(0), Models.availOrds(basic, arrayOf("Foo", ""), allow))
            assertListEquals(listOf(), Models.availOrds(basic, arrayOf("  \t ", ""), allow))
            assertListEquals(ArrayList(), Models.availOrds(reverse, arrayOf("", ""), allow))
            assertListEquals(listOf(0), Models.availOrds(reverse, arrayOf("Foo", ""), allow))
            assertListEquals(
                listOf(0, 1),
                Models.availOrds(reverse, arrayOf("Foo", "Bar"), allow)
            )
            assertListEquals(
                listOf(1),
                Models.availOrds(reverse, arrayOf("  \t ", "Bar"), allow)
            )
        }

        assertListEquals(
            listOf(0),
            Models.availOrds(basic, arrayOf("", ""), Models.AllowEmpty.TRUE)
        )
        assertListEquals(
            listOf(0),
            Models.availOrds(basic, arrayOf("", "Back"), Models.AllowEmpty.TRUE)
        )
        assertListEquals(
            listOf(0),
            Models.availOrds(basic, arrayOf("Foo", ""), Models.AllowEmpty.TRUE)
        )
        assertListEquals(
            listOf(0),
            Models.availOrds(basic, arrayOf("  \t ", ""), Models.AllowEmpty.TRUE)
        )
        assertListEquals(
            listOf(0),
            Models.availOrds(reverse, arrayOf("", ""), Models.AllowEmpty.TRUE)
        )
        assertListEquals(
            listOf(0),
            Models.availOrds(reverse, arrayOf("Foo", ""), Models.AllowEmpty.TRUE)
        )
        assertListEquals(
            listOf(0, 1),
            Models.availOrds(reverse, arrayOf("Foo", "Bar"), Models.AllowEmpty.TRUE)
        )
        assertListEquals(
            listOf(1),
            Models.availOrds(reverse, arrayOf("  \t ", "Bar"), Models.AllowEmpty.TRUE)
        )
    }

    /**
     * tests if Model.getDid() returns model did
     * or default deck id (1) if null
     */

    @Test
    fun getDid_test() {
        val col = col
        val mm = col.models
        val basic = mm.byName("Basic")
        basic!!.put("did", 999L)

        val expected = 999L
        assertEquals("getDid() should return the model did", expected, basic.did)

        // Check if returns default deck id (1) when did is null
        basic.put("did", null)
        val expected2 = 1L
        assertEquals(
            "getDid() should return 1 (default deck id) if model did is null",
            expected2,
            basic.did
        )
    }
}
