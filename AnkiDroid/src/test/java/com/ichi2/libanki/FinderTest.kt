/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.libanki

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.exception.ConfirmModSchemaException
import com.ichi2.libanki.Consts.CARD_TYPE_REV
import com.ichi2.libanki.Consts.QUEUE_TYPE_REV
import com.ichi2.libanki.Consts.QUEUE_TYPE_SUSPENDED
import com.ichi2.libanki.sched.SchedV2
import com.ichi2.libanki.utils.TimeManager
import com.ichi2.testutils.AnkiAssert
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.hasSize
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import timber.log.Timber
import java.util.*

@RunWith(AndroidJUnit4::class)
class FinderTest : RobolectricTest() {
    @Test
    @Config(qualifiers = "en")
    @Throws(
        ConfirmModSchemaException::class
    )
    fun searchForBuriedReturnsManuallyAndSiblingBuried() {
        val searchQuery = "is:buried"
        val sched = upgradeToSchedV2() // needs to be first
        enableBurySiblings()
        super.addNoteUsingModelName("Basic (and reversed card)", "Front", "Back")
        val toAnswer: Card = sched.card!!

        // act
        val siblingBuried = burySiblings(sched, toAnswer)
        val manuallyBuriedCard = buryManually(sched, toAnswer.id)

        // perform the search
        val buriedCards = col.findCards(searchQuery, SortOrder.NoOrdering())

        // assert
        assertThat(
            "A manually buried card should be returned",
            buriedCards,
            hasItem(manuallyBuriedCard.id)
        )
        assertThat(
            "A sibling buried card should be returned",
            buriedCards,
            hasItem(siblingBuried.id)
        )
        assertThat(
            "sibling and manually buried should be the only cards returned",
            buriedCards,
            hasSize(2)
        )
    }

    private fun enableBurySiblings() {
        val config = col.decks.allConf()[0]
        config.getJSONObject("new").put("bury", true)
        col.decks.save(config)
    }

    private fun burySiblings(sched: SchedV2, toManuallyBury: Card): Card {
        sched.answerCard(toManuallyBury, Consts.BUTTON_ONE)
        val siblingBuried = Note(col, toManuallyBury.nid).cards()[1]
        assertThat(siblingBuried.queue, equalTo(Consts.QUEUE_TYPE_SIBLING_BURIED))
        return siblingBuried
    }

    private fun buryManually(sched: SchedV2, id: Long): Card {
        sched.buryCards(listOf(id), true)
        val manuallyBuriedCard = Card(col, id)
        assertThat(
            manuallyBuriedCard.queue,
            equalTo(Consts.QUEUE_TYPE_MANUALLY_BURIED)
        )
        return manuallyBuriedCard
    }

    /*****************
     * autogenerated from https://github.com/ankitects/anki/blob/2c73dcb2e547c44d9e02c20a00f3c52419dc277b/pylib/tests/test_cards.py*
     */
    private fun isNearCutoff(): Boolean {
        val hour = TimeManager.time.calendar()[Calendar.HOUR_OF_DAY]
        return (hour >= 2) && (hour < 4)
    }

    @Test
    fun test_findCards() {
        TimeManager.reset()
        val col = col
        var note = col.newNote()
        note.setItem("Front", "dog")
        note.setItem("Back", "cat")
        note.addTag("monkey animal_1 * %")
        col.addNote(note)
        val n1id = note.id
        val firstCardId = note.cards()[0].id
        note = col.newNote()
        note.setItem("Front", "goats are fun")
        note.setItem("Back", "sheep")
        note.addTag("sheep goat horse animal11")
        col.addNote(note)
        val n2id = note.id
        note = col.newNote()
        note.setItem("Front", "cat")
        note.setItem("Back", "sheep")
        col.addNote(note)
        val catCard = note.cards()[0]
        var m = col.notetypes.current()
        m = col.notetypes.copy(m)
        val mm = col.notetypes
        val t = Notetypes.newTemplate("Reverse")
        t.put("qfmt", "{{Back}}")
        t.put("afmt", "{{Front}}")
        mm.addTemplateModChanged(m, t)
        mm.save(m)
        note = col.newNote()
        note.setItem("Front", "test")
        note.setItem("Back", "foo bar")
        col.addNote(note)

        val latestCardIds = note.cids()
        // tag searches
        assertEquals(5, col.findCards("tag:*").size)
        assertEquals(1, col.findCards("tag:\\*").size)
        assertEquals(
            1,
            col.findCards("tag:%").size
        )
        assertEquals(2, col.findCards("tag:animal_1").size)
        assertEquals(1, col.findCards("tag:animal\\_1").size)
        assertEquals(0, col.findCards("tag:donkey").size)
        assertEquals(1, col.findCards("tag:sheep").size)
        assertEquals(1, col.findCards("tag:sheep tag:goat").size)
        assertEquals(0, col.findCards("tag:sheep tag:monkey").size)
        assertEquals(1, col.findCards("tag:monkey").size)
        assertEquals(1, col.findCards("tag:sheep -tag:monkey").size)
        assertEquals(4, col.findCards("-tag:sheep").size)
        col.tags.bulkAdd(col.db.queryLongList("select id from notes"), "foo bar")
        assertEquals(5, col.findCards("tag:foo").size)
        assertEquals(5, col.findCards("tag:bar").size)
        col.tags.bulkAdd(col.db.queryLongList("select id from notes"), "foo", add = false)
        assertEquals(0, col.findCards("tag:foo").size)
        assertEquals(5, col.findCards("tag:bar").size)
        // text searches
        assertEquals(2, col.findCards("cat").size)
        assertEquals(1, col.findCards("cat -dog").size)
        assertEquals(1, col.findCards("cat -dog").size)
        assertEquals(1, col.findCards("are goats").size)
        assertEquals(0, col.findCards("\"are goats\"").size)
        assertEquals(1, col.findCards("\"goats are\"").size)
        // card states
        var c = note.cards()[0]
        c.queue = QUEUE_TYPE_REV
        c.type = CARD_TYPE_REV
        assertEquals(0, col.findCards("is:review").size)
        c.flush()
        AnkiAssert.assertEqualsArrayList(arrayOf(c.id), col.findCards("is:review"))
        assertEquals(0, col.findCards("is:due").size)
        c.due = 0
        c.queue = QUEUE_TYPE_REV
        c.flush()
        AnkiAssert.assertEqualsArrayList(arrayOf(c.id), col.findCards("is:due"))
        assertEquals(4, col.findCards("-is:due").size)
        c.queue = QUEUE_TYPE_SUSPENDED
        // ensure this card gets a later mod time
        c.flush()
        col.db.execute("update cards set mod = mod + 1 where id = ?", c.id)
        AnkiAssert.assertEqualsArrayList(arrayOf(c.id), col.findCards("is:suspended"))
        // nids
        assertEquals(0, col.findCards("nid:54321").size)
        assertEquals(2, col.findCards("nid:" + note.id).size)
        assertEquals(2, col.findCards("nid:$n1id,$n2id").size)
        // templates
        assertEquals(0, col.findCards("card:foo").size)
        assertEquals(4, col.findCards("\"card:card 1\"").size)
        assertEquals(1, col.findCards("card:reverse").size)
        assertEquals(4, col.findCards("card:1").size)
        assertEquals(1, col.findCards("card:2").size)
        // fields
        assertEquals(1, col.findCards("front:dog").size)
        assertEquals(4, col.findCards("-front:dog").size)
        assertEquals(0, col.findCards("front:sheep").size)
        assertEquals(2, col.findCards("back:sheep").size)
        assertEquals(3, col.findCards("-back:sheep").size)
        assertEquals(0, col.findCards("front:do").size)
        assertEquals(5, col.findCards("front:*").size)
        // ordering
        col.config.set("sortType", "noteCrt")

        assertTrue(
            latestCardIds.contains(
                col.findCards(
                    "front:*",
                    SortOrder.UseCollectionOrdering()
                ).last()
            )
        )
        assertTrue(
            latestCardIds.contains(
                col.findCards(
                    "",
                    SortOrder.UseCollectionOrdering()
                ).last()
            )
        )
        col.config.set("sortType", "noteFld")

        assertEquals(catCard.id, col.findCards("", SortOrder.UseCollectionOrdering())[0])
        assertTrue(
            latestCardIds.contains(
                col.findCards(
                    "",
                    SortOrder.UseCollectionOrdering()
                ).last()
            )
        )
        col.config.set("sortType", "cardMod")

        assertTrue(
            latestCardIds.contains(
                col.findCards(
                    "",
                    SortOrder.UseCollectionOrdering()
                ).last()
            )
        )
        assertEquals(firstCardId, col.findCards("", SortOrder.UseCollectionOrdering())[0])
        col.config.set("sortBackwards", true)

        assertTrue(latestCardIds.contains(col.findCards("", SortOrder.UseCollectionOrdering())[0]))
        /* TODO: Port BuiltinSortKind
           assertEquals(firstCardId,
           col.findCards("", BuiltinSortKind.CARD_DUE, reverse=false).get(0)
           );
           assertNotEquals(firstCardId,
           col.findCards("", BuiltinSortKind.CARD_DUE, reverse=true).get(0));
        */
        // model
        assertEquals(3, col.findCards("note:basic").size)
        assertEquals(2, col.findCards("-note:basic").size)
        assertEquals(5, col.findCards("-note:foo").size)
        // col
        assertEquals(5, col.findCards("deck:default").size)
        assertEquals(0, col.findCards("-deck:default").size)
        assertEquals(5, col.findCards("-deck:foo").size)
        assertEquals(5, col.findCards("deck:def*").size)
        assertEquals(5, col.findCards("deck:*EFAULT").size)
        assertEquals(0, col.findCards("deck:*cefault").size)
        // full search
        note = col.newNote()
        note.setItem("Front", "hello<b>world</b>")
        note.setItem("Back", "abc")
        col.addNote(note)
        // as it's the sort field, it matches
        assertEquals(2, col.findCards("helloworld").size)
        // assertEquals(, col.findCards("helloworld", full=true).size())2 This is commented upstream
        // if we put it on the back, it won't
        val noteFront = note.getItem("Front")
        val noteBack = note.getItem("Back")
        note.setItem("Front", noteBack)
        note.setItem("Back", noteFront)
        note.flush()
        assertEquals(0, col.findCards("helloworld").size)
        //  Those lines are commented above
        // assertEquals(, col.findCards("helloworld", full=true).size())2
        // assertEquals(, col.findCards("back:helloworld", full=true).size()G)2
        // searching for an invalid special tag should not error
        // TODO: ensure the search fail
        //  assertThrows(Exception.class, () -> col.findCards("is:invalid").size());
        // should be able to limit to parent col, no children
        var id = col.db.queryLongScalar("select id from cards limit 1")
        col.db.execute(
            "update cards set did = ? where id = ?",
            addDeck("Default::Child"),
            id
        )

        assertEquals(7, col.findCards("deck:default").size)
        assertEquals(1, col.findCards("deck:default::child").size)
        assertEquals(6, col.findCards("deck:default -deck:default::*").size)
        // properties
        id = col.db.queryLongScalar("select id from cards limit 1")
        col.db.execute(
            "update cards set queue=2, ivl=10, reps=20, due=30, factor=2200 where id = ?",
            id
        )
        assertEquals(1, col.findCards("prop:ivl>5").size)
        assertThat(col.findCards("prop:ivl<5").size, greaterThan(1))
        assertEquals(1, col.findCards("prop:ivl>=5").size)
        assertEquals(0, col.findCards("prop:ivl=9").size)
        assertEquals(1, col.findCards("prop:ivl=10").size)
        assertThat(col.findCards("prop:ivl!=10").size, greaterThan(1))
        assertEquals(1, col.findCards("prop:due>0").size)
        // due dates should work
        assertEquals(0, col.findCards("prop:due=29").size)
        assertEquals(1, col.findCards("prop:due=30").size)
        // ease factors
        assertEquals(0, col.findCards("prop:ease=2.3").size)
        assertEquals(1, col.findCards("prop:ease=2.2").size)
        assertEquals(1, col.findCards("prop:ease>2").size)
        assertThat(col.findCards("-prop:ease>2").size, greaterThan(1))
        // recently failed
        if (!isNearCutoff()) {
            assertEquals(0, col.findCards("rated:1:1").size)
            assertEquals(0, col.findCards("rated:1:2").size)
            c = card!!
            col.sched.answerCard(c, Consts.BUTTON_TWO)
            assertEquals(0, col.findCards("rated:1:1").size)
            assertEquals(1, col.findCards("rated:1:2").size)
            c = card!!
            col.sched.answerCard(c, Consts.BUTTON_ONE)
            assertEquals(1, col.findCards("rated:1:1").size)
            assertEquals(1, col.findCards("rated:1:2").size)
            assertEquals(2, col.findCards("rated:1").size)
            assertEquals(1, col.findCards("rated:2:2").size)
        } else {
            Timber.w("some find tests disabled near cutoff")
        }
        // empty field
        assertEquals(0, col.findCards("front:").size)
        note = col.newNote()
        note.setItem("Front", "")
        note.setItem("Back", "abc2")
        assertEquals(1, col.addNote(note))
        assertEquals(1, col.findCards("front:").size)
        // OR searches and nesting
        assertEquals(2, col.findCards("tag:monkey or tag:sheep").size)
        assertEquals(2, col.findCards("(tag:monkey OR tag:sheep)").size)
        assertEquals(6, col.findCards("-(tag:monkey OR tag:sheep)").size)
        assertEquals(2, col.findCards("tag:monkey or (tag:sheep sheep)").size)
        assertEquals(1, col.findCards("tag:monkey or (tag:sheep octopus)").size)
        // flag
        // Todo: ensure it fails
        // assertThrows(Exception.class, () -> col.findCards("flag:12"));
    }

    @Test
    fun test_findCardsHierarchyTag() {
        val col = col
        var note = col.newNote()
        note.setItem("Front", "foo")
        note.setItem("Back", "bar")
        note.addTag("cat1::some")
        col.addNote(note)
        note = col.newNote()
        note.setItem("Front", "foo")
        note.setItem("Back", "bar")
        note.addTag("cat1::something")
        col.addNote(note)
        note = col.newNote()
        note.setItem("Front", "foo")
        note.setItem("Front", "bar")
        note.addTag("cat2::some")
        col.addNote(note)
        note = col.newNote()
        note.setItem("Front", "foo")
        note.setItem("Back", "bar")
        note.addTag("cat2::some::something")
        col.addNote(note)

        assertEquals(0, col.findCards("tag:cat").size)
        assertEquals(4, col.findCards("tag:cat*").size)
        assertEquals(2, col.findCards("tag:cat1").size)
        assertEquals(2, col.findCards("tag:cat2").size)
        assertEquals(1, col.findCards("tag:cat1::some").size)
        assertEquals(2, col.findCards("tag:cat1::some*").size)
        assertEquals(1, col.findCards("tag:cat1::something").size)
        assertEquals(2, col.findCards("tag:cat2::some").size)
        assertEquals(
            0,
            col.findCards("tag:cat2::some::").size
        )
    }

    @Test
    fun test_deckNameContainingWildcardCanBeSearched() {
        val deck = "*Yr1::Med2::CAS4::F4: Renal::BRS (zanki)::HY"
        val col = col
        val currentDid = addDeck(deck)
        col.decks.select(currentDid)
        val note = col.newNote()
        note.setItem("Front", "foo")
        note.setItem("Back", "bar")
        note.model().put("did", currentDid)
        col.addNote(note)
        val did = note.firstCard().did
        assertEquals(currentDid, did)
        val cb = super.startActivityNormallyOpenCollectionWithIntent(
            CardBrowser::class.java,
            Intent()
        )
        cb.deckSpinnerSelection!!.updateDeckPosition(currentDid)
        advanceRobolectricLooperWithSleep()
        assertEquals(1L, cb.cardCount.toLong())
    }

    @Test
    fun test_findReplace() {
        val col = col
        val note = col.newNote()
        note.setItem("Front", "foo")
        note.setItem("Back", "bar")
        col.addNote(note)
        val note2 = col.newNote()
        note2.setItem("Front", "baz")
        note2.setItem("Back", "foo")
        col.addNote(note2)
        val nids = listOf(note.id, note2.id)
        // should do nothing
        assertEquals(0, col.findReplace(nids, "abc", "123"))
        // global replace
        assertEquals(2, col.findReplace(nids, "foo", "qux"))
        note.load()
        assertEquals("qux", note.getItem("Front"))
        note2.load()
        assertEquals("qux", note2.getItem("Back"))
        // single field replace
        assertEquals(1, col.findReplace(nids, "qux", "foo", field = "Front"))
        note.load()
        assertEquals("foo", note.getItem("Front"))
        note2.load()
        assertEquals("qux", note2.getItem("Back"))
        // regex replace
        assertEquals(0, col.findReplace(nids, "B.r", "reg"))
        note.load()
        assertNotEquals("reg", note.getItem("Back"))
        assertEquals(1, col.findReplace(nids, "B.r", "reg", true))
        note.load()
        assertEquals(note.getItem("Back"), "reg")
    }
}
