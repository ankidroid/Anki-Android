/***************************************************************************************
 * Copyright (c) 2023 Ankitects Pty Ltd <http://apps.ankiweb.net>                       *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.testutils

import android.annotation.SuppressLint
import androidx.annotation.CallSuper
import com.ichi2.anki.CollectionManager
import com.ichi2.libanki.ChangeManager
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Consts
import com.ichi2.libanki.Note
import com.ichi2.libanki.Storage
import com.ichi2.libanki.backend.exception.DeckRenameException
import com.ichi2.libanki.utils.TimeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import net.ankiweb.rsdroid.BackendException
import net.ankiweb.rsdroid.testing.RustBackendLoader
import org.junit.After
import org.junit.Before
import timber.log.Timber
import timber.log.Timber.Forest.plant

open class JvmTest {
    private fun maybeSetupBackend() {
        RustBackendLoader.ensureSetup()
    }

    val col: Collection
        get() = col_!!

    private var col_: Collection? = null

    @Before
    @CallSuper
    open fun setUp() {
        TimeManager.resetWith(MockTime(2020, 7, 7, 7, 0, 0, 0, 10))

        ChangeManager.clearSubscribers()

        maybeSetupBackend()

        plant(object : Timber.DebugTree() {
            @SuppressLint("PrintStackTraceUsage")
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                // This is noisy in test environments
                if (tag == "Backend\$checkMainThreadOp") {
                    return
                }
                System.out.println(tag + ": " + message)
                if (t != null) {
                    t.printStackTrace()
                }
            }
        })

        Storage.setUseInMemory(true)
        col_ = CollectionManager.getColUnsafe()
    }

    @After
    @CallSuper
    open fun tearDown() {
        try {
            // If you don't tear down the database you'll get unexpected IllegalStateExceptions related to connections
            col.close()
        } catch (ex: BackendException) {
            if ("CollectionNotOpen" == ex.message) {
                Timber.w(ex, "Collection was already disposed - may have been a problem")
            } else {
                throw ex
            }
        } finally {
            TimeManager.reset()
        }
        col_ = null
        Dispatchers.resetMain()
        runBlocking { CollectionManager.discardBackend() }
        Timber.uprootAll()
    }

    protected fun addNoteUsingBasicModel(front: String, back: String): Note {
        return addNoteUsingModelName("Basic", front, back)
    }

    protected fun addRevNoteUsingBasicModelDueToday(@Suppress("SameParameterValue") front: String, @Suppress("SameParameterValue") back: String): Note {
        val note = addNoteUsingBasicModel(front, back)
        val card = note.firstCard()
        card.queue = Consts.QUEUE_TYPE_REV
        card.type = Consts.CARD_TYPE_REV
        card.due = col.sched.today.toLong()
        return note
    }

    protected fun addNoteUsingBasicAndReversedModel(front: String, back: String): Note {
        return addNoteUsingModelName("Basic (and reversed card)", front, back)
    }

    protected fun addNoteUsingBasicTypedModel(@Suppress("SameParameterValue") front: String, @Suppress("SameParameterValue") back: String): Note {
        return addNoteUsingModelName("Basic (type in the answer)", front, back)
    }

    protected fun addNoteUsingModelName(name: String?, vararg fields: String): Note {
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

    protected fun addDeck(deckName: String?): Long {
        return try {
            col.decks.id(deckName!!)
        } catch (filteredAncestor: DeckRenameException) {
            throw RuntimeException(filteredAncestor)
        }
    }

    protected fun addDynamicDeck(name: String?): Long {
        return try {
            col.decks.newDyn(name!!)
        } catch (filteredAncestor: DeckRenameException) {
            throw RuntimeException(filteredAncestor)
        }
    }
}
