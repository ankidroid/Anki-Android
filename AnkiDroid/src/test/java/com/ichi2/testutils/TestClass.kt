/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.testutils

import com.ichi2.anki.CollectionManager
import com.ichi2.libanki.Card
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Consts
import com.ichi2.libanki.DeckConfig
import com.ichi2.libanki.DeckId
import com.ichi2.libanki.Note
import com.ichi2.libanki.NotetypeJson
import com.ichi2.libanki.Notetypes
import com.ichi2.libanki.exception.ConfirmModSchemaException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import net.ankiweb.rsdroid.exceptions.BackendDeckIsFilteredException
import timber.log.Timber
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * marker interface for classes which contain tests and access the Anki collection
 * @see AndroidTest
 */
interface TestClass {
    val col: Collection

    fun addNoteUsingBasicModel(front: String = "Front", back: String = "Back"): Note {
        return addNoteUsingModelName("Basic", front, back)
    }

    fun addRevNoteUsingBasicModelDueToday(@Suppress("SameParameterValue") front: String, @Suppress("SameParameterValue") back: String): Note {
        val note = addNoteUsingBasicModel(front, back)
        val card = note.firstCard()
        card.queue = Consts.QUEUE_TYPE_REV
        card.type = Consts.CARD_TYPE_REV
        card.due = col.sched.today.toLong()
        return note
    }

    fun addNoteUsingBasicAndReversedModel(front: String = "Front", back: String = "Back"): Note {
        return addNoteUsingModelName("Basic (and reversed card)", front, back)
    }

    fun addNoteUsingBasicTypedModel(@Suppress("SameParameterValue") front: String, @Suppress("SameParameterValue") back: String): Note {
        return addNoteUsingModelName("Basic (type in the answer)", front, back)
    }

    fun addCloseNote(text: String, extra: String = "Extra"): Note =
        col.newNote(col.notetypes.byName("Cloze")!!).apply {
            setItem("Text", text)
            col.addNote(this)
        }

    fun addNoteUsingModelName(name: String?, vararg fields: String): Note {
        val model = col.notetypes.byName((name)!!)
            ?: throw IllegalArgumentException("Could not find model '$name'")
        // PERF: if we modify newNote(), we can return the card and return a Pair<Note, Card> here.
        // Saves a database trip afterwards.
        val n = col.newNote(model)
        for ((i, field) in fields.withIndex()) {
            n.setField(i, field)
        }
        check(col.addNote(n) != 0) { "Could not add note: {${fields.joinToString(separator = ", ")}}" }
        return n
    }

    fun addNonClozeModel(name: String, fields: Array<String>, qfmt: String?, afmt: String?): String {
        val model = col.notetypes.newModel(name)
        for (field in fields) {
            col.notetypes.addFieldInNewModel(model, col.notetypes.newField(field))
        }
        val t = Notetypes.newTemplate("Card 1")
        t.put("qfmt", qfmt)
        t.put("afmt", afmt)
        col.notetypes.addTemplateInNewModel(model, t)
        col.notetypes.add(model)
        return name
    }

    /** Adds a note with Text to Speech functionality */
    fun addNoteUsingTextToSpeechNoteType(front: String, back: String) {
        addNonClozeModel("TTS", arrayOf("Front", "Back"), "{{Front}}{{tts en_GB:Front}}", "{{tts en_GB:Front}}<br>{{Back}}")
        addNoteUsingModelName("TTS", front, back)
    }

    fun addField(notetype: NotetypeJson, name: String) {
        val models = col.notetypes
        try {
            models.addField(notetype, models.newField(name))
        } catch (e: ConfirmModSchemaException) {
            throw RuntimeException(e)
        }
    }

    fun ensureCollectionLoadIsSynchronous() {
        // HACK: We perform this to ensure that onCollectionLoaded is performed synchronously when startLoadingCollection
        // is called.
        col
    }

    fun addDeck(deckName: String?, setAsSelected: Boolean = false): DeckId {
        return try {
            col.decks.id(deckName!!).also { did ->
                if (setAsSelected) col.decks.select(did)
            }
        } catch (filteredAncestor: BackendDeckIsFilteredException) {
            throw RuntimeException(filteredAncestor)
        }
    }

    fun addDynamicDeck(name: String?): DeckId {
        return try {
            col.decks.newDyn(name!!)
        } catch (filteredAncestor: BackendDeckIsFilteredException) {
            throw RuntimeException(filteredAncestor)
        }
    }

    /** Adds [count] notes in the same deck with the same front & back */
    fun addNotes(count: Int): List<Note> = (0..count).map { addNoteUsingBasicModel() }

    fun Note.moveToDeck(deckName: String, createDeckIfMissing: Boolean = true) {
        val deckId: DeckId? = if (createDeckIfMissing) {
            col.decks.id(deckName)
        } else {
            col.decks.idForName(deckName)
        }
        check(deckId != null) { "$deckName not found" }

        updateCards { did = deckId }
    }

    /** helper method to update deck config */
    fun updateDeckConfig(deckId: DeckId, function: DeckConfig.() -> Unit) {
        val deckConfig = col.decks.confForDid(deckId)
        function(deckConfig)
        col.decks.save(deckConfig)
    }

    /** Helper method to all cards of a note */
    fun Note.updateCards(update: Card.() -> Unit): Note {
        cards().forEach { it.update(update) }
        return this
    }

    /** Helper method to update a card */
    fun Card.update(update: Card.() -> Unit): Card {
        update(this)
        this@TestClass.col.updateCard(this, skipUndoEntry = true)
        return this
    }

    fun Card.note() = this.note(col)
    fun Card.note(reload: Boolean) = this.note(col, reload)
    fun Card.model() = this.model(col)
    fun Card.template() = this.template(col)
    fun Card.question() = this.question(col)
    fun Card.question(reload: Boolean = false, browser: Boolean = false) = this.question(col, reload, browser)
    fun Card.answer() = this.answer(col)
    fun Card.load() = this.load(col)
    fun Card.nextDue() = this.nextDue(col)
    fun Card.dueString() = this.dueString(col)
    fun Card.pureAnswer() = this.pureAnswer(col)

    /** * A wrapper around the standard [kotlinx.coroutines.test.runTest] that
     * takes care of updating the dispatcher used by CollectionManager as well.
     * * An argument could be made for using [StandardTestDispatcher] and
     * explicitly advanced coroutines with advanceUntilIdle(), but there are
     * issues with using it at the moment:
     * * - Any usage of CollectionManager with runBlocking() will hang. tearDown()
     * calls runBlocking() twice, which prevents tests from finishing.
     * - The hang is not limited to the scope of runTest(). Even if the runBlocking
     * calls in tearDown() are selectively moved into this function,
     * when a coroutine test fails, the next regular test
     * that executes after it will call runBlocking(), and it then hangs.
     *
     * A fix for this might require either wrapping all tests in runTest(),
     * or finding some other way to isolate the coroutine and non-coroutine tests
     * on separate threads/processes.
     * */
    fun runTest(
        context: CoroutineContext = EmptyCoroutineContext,
        dispatchTimeoutMs: Long = 60_000L,
        times: Int = 1,
        testBody: suspend TestScope.() -> Unit
    ) {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repeat(times) {
            if (times != 1) Timber.d("------ Executing test $it/$times ------")
            kotlinx.coroutines.test.runTest(context, dispatchTimeoutMs.milliseconds) {
                CollectionManager.setTestDispatcher(UnconfinedTestDispatcher(testScheduler))
                testBody()
            }
        }
    }
}
