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

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.Ease
import com.ichi2.libanki.exception.ConfirmModSchemaException
import com.ichi2.testutils.JvmTest
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasItemInArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class CardTest : JvmTest() {
    /******************
     ** autogenerated from https://github.com/ankitects/anki/blob/2c73dcb2e547c44d9e02c20a00f3c52419dc277b/pylib/tests/test_cards.py*
     ******************/
    @Test
    fun test_delete() {
        val note = col.newNote()
        note.setItem("Front", "1")
        note.setItem("Back", "2")
        col.addNote(note)
        val cid = note.cards()[0].id
        col.sched.answerCard(col.sched.card!!, Ease.HARD)
        col.removeCardsAndOrphanedNotes(listOf(cid))
        assertEquals(0, col.cardCount())
        assertEquals(0, col.noteCount())
        assertEquals(0, col.db.queryScalar("select count() from notes"))
        assertEquals(0, col.db.queryScalar("select count() from cards"))
        assertEquals(2, col.db.queryScalar("select count() from graves"))
    }

    @Test
    @SuppressLint("CheckResult") // col.models.current()!!.getLong("id")
    fun test_misc_cards() {
        val note = col.newNote()
        note.setItem("Front", "1")
        note.setItem("Back", "2")
        col.addNote(note)
        val c = note.cards()[0]
        col.notetypes.current().getLong("id")
        assertEquals(0, c.template().getInt("ord"))
    }

    @Test
    fun test_genrem() {
        val note = col.newNote()
        note.setItem("Front", "1")
        note.setItem("Back", "")
        col.addNote(note)
        assertEquals(1, note.numberOfCards())
        val m = col.notetypes.current()
        val mm = col.notetypes
        // adding a new template should automatically create cards
        var t = Notetypes.newTemplate("rev")
        t.put("qfmt", "{{Front}}1")
        t.put("afmt", "")
        mm.addTemplateModChanged(m, t)
        mm.save(m)
        assertEquals(2, note.numberOfCards())
        // if the template is changed to remove cards, they'll be removed
        t = m.getJSONArray("tmpls").getJSONObject(1)
        t.put("qfmt", "{{Back}}")
        mm.save(m)
        val rep = col.emptyCids()
        col.removeCardsAndOrphanedNotes(rep)
        assertEquals(1, note.numberOfCards())
        // if we add to the note, a card should be automatically generated
        note.load()
        note.setItem("Back", "1")
        note.flush()
        assertEquals(2, note.numberOfCards())
    }

    @Test
    fun test_gendeck() {
        val cloze = col.notetypes.byName("Cloze")
        col.notetypes.setCurrent(cloze!!)
        val note = col.newNote()
        note.setItem("Text", "{{c1::one}}")
        col.addNote(note)
        assertEquals(1, col.cardCount())
        assertEquals(1, note.cards()[0].did)
        // set the model to a new default col
        val newId = addDeck("new")
        cloze.put("did", newId)
        col.notetypes.save(cloze)
        // a newly generated card should share the first card's col
        note.setItem("Text", "{{c2::two}}")
        note.flush()
        assertEquals(1, note.cards()[1].did)
        // and same with multiple cards
        note.setItem("Text", "{{c3::three}}")
        note.flush()
        assertEquals(1, note.cards()[2].did)
    }

    @Test
    @Throws(ConfirmModSchemaException::class)
    fun test_gen_or() {
        val models = col.notetypes
        val model = models.byName("Basic")
        assertNotNull(model)
        models.renameFieldLegacy(model, model.getJSONArray("flds").getJSONObject(0), "A")
        models.renameFieldLegacy(model, model.getJSONArray("flds").getJSONObject(1), "B")
        val fld2 = models.newField("C")
        fld2.put("ord", JSONObject.NULL)
        models.addFieldLegacy(model, fld2)
        val tmpls = model.getJSONArray("tmpls")
        tmpls.getJSONObject(0).put("qfmt", "{{A}}{{B}}{{C}}")
        // ensure first card is always generated,
        // because at last one card is generated
        val tmpl = Notetypes.newTemplate("AND_OR")
        tmpl.put("qfmt", "        {{A}}    {{#B}}        {{#C}}            {{B}}        {{/C}}    {{/B}}")
        models.addTemplate(model, tmpl)
        models.save(model)
        models.setCurrent(model)
        var note = col.newNote()
        note.setItem("A", "foo")
        col.addNote(note)
        assertNoteOrdinalAre(note, arrayOf(0, 1))
        note = col.newNote()
        note.setItem("B", "foo")
        note.setItem("C", "foo")
        col.addNote(note)
        assertNoteOrdinalAre(note, arrayOf(0, 1))
        note = col.newNote()
        note.setItem("B", "foo")
        col.addNote(note)
        assertNoteOrdinalAre(note, arrayOf(0))
        note = col.newNote()
        note.setItem("C", "foo")
        col.addNote(note)
        assertNoteOrdinalAre(note, arrayOf(0))
        note = col.newNote()
        note.setItem("A", "foo")
        note.setItem("B", "foo")
        note.setItem("C", "foo")
        col.addNote(note)
        assertNoteOrdinalAre(note, arrayOf(0, 1))
        note = col.newNote()
        col.addNote(note)
        assertNoteOrdinalAre(note, arrayOf(0))
        // First card is generated if no other card
    }

    @Test
    @Throws(ConfirmModSchemaException::class)
    fun test_gen_not() {
        val models = col.notetypes
        val model = models.byName("Basic")
        assertNotNull(model)
        val tmpls = model.getJSONArray("tmpls")
        models.renameFieldLegacy(model, model.getJSONArray("flds").getJSONObject(0), "First")
        models.renameFieldLegacy(model, model.getJSONArray("flds").getJSONObject(1), "Front")
        val fld2 = models.newField("AddIfEmpty")
        fld2.put("name", "AddIfEmpty")
        models.addFieldLegacy(model, fld2)

        // ensure first card is always generated,
        // because at last one card is generated
        tmpls.getJSONObject(0).put("qfmt", "{{AddIfEmpty}}{{Front}}{{First}}")
        val tmpl = Notetypes.newTemplate("NOT")
        tmpl.put("qfmt", "    {{^AddIfEmpty}}        {{Front}}    {{/AddIfEmpty}}    ")
        models.addTemplate(model, tmpl)
        models.save(model)
        models.setCurrent(model)
        var note = col.newNote()
        note.setItem("First", "foo")
        note.setItem("AddIfEmpty", "foo")
        note.setItem("Front", "foo")
        col.addNote(note)
        assertNoteOrdinalAre(note, arrayOf(0))
        note = col.newNote()
        note.setItem("First", "foo")
        note.setItem("AddIfEmpty", "foo")
        col.addNote(note)
        assertNoteOrdinalAre(note, arrayOf(0))
        note = col.newNote()
        note.setItem("First", "foo") // ensure first note generated
        col.addNote(note)
        assertNoteOrdinalAre(note, arrayOf(0))
        note = col.newNote()
        note.setItem("First", "foo")
        note.setItem("Front", "foo")
        col.addNote(note)
        assertNoteOrdinalAre(note, arrayOf(0, 1))
    }

    private fun assertNoteOrdinalAre(
        note: Note,
        ords: Array<Int>
    ) {
        val cards = note.cards()
        assumeThat(cards.size, equalTo(ords.size))
        for (card in cards) {
            val ord = card.ord
            assumeThat(ords, hasItemInArray(ord))
        }
    }
}
