/****************************************************************************************
 * Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>                             *
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>                         *
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

package com.ichi2.async

import android.content.Context
import android.util.Pair
import androidx.annotation.VisibleForTesting
import com.fasterxml.jackson.core.JsonToken
import com.ichi2.anki.*
import com.ichi2.anki.AnkiSerialization.factory
import com.ichi2.anki.CardBrowser.CardCache
import com.ichi2.anki.CardBrowser.CardCollection
import com.ichi2.anki.StudyOptionsFragment.DeckStudyData
import com.ichi2.anki.exception.ConfirmModSchemaException
import com.ichi2.anki.exception.ImportExportException
import com.ichi2.anki.servicelayer.NoteService
import com.ichi2.anki.servicelayer.SearchService.SearchCardsResult
import com.ichi2.libanki.*
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Collection.CheckDatabaseResult
import com.ichi2.libanki.importer.AnkiPackageImporter
import com.ichi2.libanki.sched.DeckDueTreeNode
import com.ichi2.libanki.sched.DeckTreeNode
import com.ichi2.libanki.sched.TreeNode
import com.ichi2.utils.*
import com.ichi2.utils.SyncStatus.Companion.ignoreDatabaseModification
import org.apache.commons.compress.archivers.zip.ZipFile
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException

/**
 * This is essentially an AsyncTask with some more logging. It delegates to TaskDelegate the actual business logic.
 * It adds some extra check.
 * TODO: explain the goal of those extra checks. They seems redundant with AsyncTask specification.
 *
 * The CollectionTask should be created by the TaskManager. All creation of background tasks (except for Connection and Widget) should be done by sending a TaskDelegate to the ThreadManager.launchTask.
 *
 * @param <Progress> The type of progress that is sent by the TaskDelegate. E.g. a Card, a pairWithBoolean.
 * @param <Result>   The type of result that the TaskDelegate sends. E.g. a tree of decks, counts of a deck.
 */
@KotlinCleanup("IDE Lint")
@KotlinCleanup("Lots to do")
open class CollectionTask<Progress, Result>(val task: TaskDelegateBase<Progress, Result>, private val listener: TaskListener<in Progress, in Result?>?, private var previousTask: CollectionTask<*, *>?) : BaseAsyncTask<Void, Progress, Result>(), Cancellable {
    /**
     * A reference to the application context to use to fetch the current Collection object.
     */
    protected var context: Context? = null
        private set

    /** Cancel the current task.
     * @return whether cancelling did occur.
     */
    @Suppress("deprecation") // #7108: AsyncTask
    override fun safeCancel(): Boolean {
        try {
            if (status != Status.FINISHED) {
                return cancel(true)
            }
        } catch (e: Exception) {
            // Potentially catching SecurityException, from
            // Thread.interrupt from FutureTask.cancel from
            // AsyncTask.cancel
            Timber.w(e, "Exception cancelling task")
        } finally {
            TaskManager.removeTask(this)
        }
        return false
    }

    private val col: Collection
        get() = CollectionHelper.getInstance().getCol(context)

    protected override fun doInBackground(vararg arg0: Void): Result? {
        return try {
            actualDoInBackground()
        } finally {
            TaskManager.removeTask(this)
        }
    }

    // This method and those that are called here are executed in a new thread
    @Suppress("deprecation") // #7108: AsyncTask
    protected fun actualDoInBackground(): Result? {
        super.doInBackground()
        // Wait for previous thread (if any) to finish before continuing
        if (previousTask != null && previousTask!!.status != Status.FINISHED) {
            Timber.d("Waiting for %s to finish before starting %s", previousTask!!.task, task.javaClass)
            try {
                previousTask!!.get()
                Timber.d("Finished waiting for %s to finish. Status= %s", previousTask!!.task, previousTask!!.status)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                // We have been interrupted, return immediately.
                Timber.d(e, "interrupted while waiting for previous task: %s", previousTask!!.task.javaClass)
                return null
            } catch (e: ExecutionException) {
                // Ignore failures in the previous task.
                Timber.e(e, "previously running task failed with exception: %s", previousTask!!.task.javaClass)
            } catch (e: CancellationException) {
                // Ignore cancellation of previous task
                Timber.d(e, "previously running task was cancelled: %s", previousTask!!.task.javaClass)
            }
        }
        TaskManager.setLatestInstance(this)
        context = AnkiDroidApp.getInstance().applicationContext

        // Skip the task if the collection cannot be opened
        if (task.requiresOpenCollection() && CollectionHelper.getInstance().getColSafe(context) == null) {
            Timber.e("CollectionTask CollectionTask %s as Collection could not be opened", task.javaClass)
            return null
        }
        // Actually execute the task now that we are at the front of the queue.
        return task.execTask(col, this)
    }

    /** Delegates to the [TaskListener] for this task.  */
    override fun onPreExecute() {
        super.onPreExecute()
        listener?.onPreExecute()
    }

    /** Delegates to the [TaskListener] for this task.  */
    override fun onProgressUpdate(vararg values: Progress) {
        super.onProgressUpdate(*values)
        listener?.onProgressUpdate(values[0])
    }

    /** Delegates to the [TaskListener] for this task.  */
    override fun onPostExecute(result: Result?) {
        super.onPostExecute(result)
        listener?.onPostExecute(result)
        Timber.d("enabling garbage collection of mPreviousTask...")
        previousTask = null
    }

    override fun onCancelled() {
        TaskManager.removeTask(this)
        listener?.onCancelled()
    }

    @KotlinCleanup("non-null return")
    class AddNote(private val note: Note) : TaskDelegate<Int, Boolean?>() {
        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<Int>): Boolean {
            Timber.d("doInBackgroundAddNote")
            try {
                val db = col.db
                db.executeInTransaction {
                    val value = col.addNote(note, Models.AllowEmpty.ONLY_CLOZE)
                    collectionTask.doProgress(value)
                }
            } catch (e: RuntimeException) {
                Timber.e(e, "doInBackgroundAddNote - RuntimeException on adding note")
                CrashReportService.sendExceptionReport(e, "doInBackgroundAddNote")
                return false
            }
            return true
        }
    }

    class UpdateNote(private val editCard: Card, val isFromReviewer: Boolean, private val canAccessScheduler: Boolean) : TaskDelegate<Card, Computation<*>>() {
        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<Card>): Computation<*> {
            Timber.d("doInBackgroundUpdateNote")
            // Save the note
            val sched = col.sched
            val editNote = editCard.note()
            try {
                col.db.executeInTransaction {
                    // TODO: undo integration
                    editNote.flush()
                    // flush card too, in case, did has been changed
                    editCard.flush()
                    if (isFromReviewer) {
                        val newCard: Card?
                        if (col.decks.active().contains(editCard.did) || !canAccessScheduler) {
                            newCard = editCard
                            newCard.load()
                            // reload qa-cache
                            newCard.q(true)
                        } else {
                            newCard = sched.card
                        }
                        collectionTask.doProgress(newCard) // check: are there deleted too?
                    } else {
                        collectionTask.doProgress(editCard)
                    }
                }
            } catch (e: RuntimeException) {
                Timber.e(e, "doInBackgroundUpdateNote - RuntimeException on updating note")
                CrashReportService.sendExceptionReport(e, "doInBackgroundUpdateNote")
                return Computation.ERR
            }
            return Computation.OK
        }
    }

    class UpdateMultipleNotes @JvmOverloads constructor(private val notesToUpdate: List<Note>, private val shouldUpdateCards: Boolean = false) : TaskDelegate<List<Note>, Computation<*>>() {
        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<List<Note>>): Computation<*> {
            Timber.d("doInBackgroundUpdateMultipleNotes")
            try {
                col.db.executeInTransaction {
                    for (note in notesToUpdate) {
                        note.flush()
                        if (shouldUpdateCards) {
                            for (card in note.cards()) {
                                card.flush()
                            }
                        }
                    }
                    collectionTask.doProgress(notesToUpdate)
                }
            } catch (e: RuntimeException) {
                Timber.w(e, "doInBackgroundUpdateMultipleNotes - RuntimeException on updating multiple note")
                CrashReportService.sendExceptionReport(e, "doInBackgroundUpdateMultipleNotes")
                return Computation.ERR
            }
            return Computation.OK
        }
    }

    @KotlinCleanup("can quickDeckDueTree return null?")
    class LoadDeck : TaskDelegate<Void, List<TreeNode<DeckTreeNode>>?>() {
        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<Void>): List<TreeNode<DeckTreeNode>>? {
            Timber.d("doInBackgroundLoadDeckCounts")
            return try {
                // Get due tree
                col.sched.quickDeckDueTree()
            } catch (e: RuntimeException) {
                Timber.w(e, "doInBackgroundLoadDeckCounts - error")
                null
            }
        }
    }

    class LoadDeckCounts : TaskDelegate<Void, List<TreeNode<DeckDueTreeNode>>?>() {
        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<Void>): List<TreeNode<DeckDueTreeNode>>? {
            Timber.d("doInBackgroundLoadDeckCounts")
            return try {
                // Get due tree
                col.sched.deckDueTree(collectionTask)!!
            } catch (e: RuntimeException) {
                Timber.e(e, "doInBackgroundLoadDeckCounts - error")
                null
            }
        }
    }

    class SaveCollection(private val syncIgnoresDatabaseModification: Boolean) : TaskDelegate<Void?, Void?>() {
        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<Void?>): Void? {
            Timber.d("doInBackgroundSaveCollection")
            try {
                if (syncIgnoresDatabaseModification) {
                    ignoreDatabaseModification { col.save() }
                } else {
                    col.save()
                }
            } catch (e: RuntimeException) {
                Timber.e(e, "Error on saving deck in background")
            }
            return null
        }
    }

    /** @param hasUnsuspended  whether there were any unsuspended card (in which card the action was "Suspend",
     * otherwise the action was "Unsuspend")
     */
    protected class UndoSuspendCardMulti(
        private val cards: Array<Card>,
        private val originalSuspended: BooleanArray,
        hasUnsuspended: Boolean
    ) : UndoAction(if (hasUnsuspended) R.string.menu_suspend_card else R.string.card_browser_unsuspend_card) {
        override fun undo(col: Collection): Card? {
            Timber.i("Undo: Suspend multiple cards")
            val nbOfCards = cards.size
            val toSuspendIds: MutableList<Long> = ArrayList(nbOfCards)
            val toUnsuspendIds: MutableList<Long> = ArrayList(nbOfCards)
            for (i in 0 until nbOfCards) {
                val card = cards[i]
                if (originalSuspended[i]) {
                    toSuspendIds.add(card.id)
                } else {
                    toUnsuspendIds.add(card.id)
                }
            }

            // unboxing
            val toSuspendIdsArray = LongArray(toSuspendIds.size)
            val toUnsuspendIdsArray = LongArray(toUnsuspendIds.size)
            for (i in toSuspendIds.indices) {
                toSuspendIdsArray[i] = toSuspendIds[i]
            }
            for (i in toUnsuspendIds.indices) {
                toUnsuspendIdsArray[i] = toUnsuspendIds[i]
            }
            col.sched.suspendCards(toSuspendIdsArray)
            col.sched.unsuspendCards(toUnsuspendIdsArray)
            return null // don't fetch new card
        }
    }

    private class UndoDeleteNoteMulti(private val notesArr: Array<Note>, private val allCards: List<Card>) : UndoAction(R.string.card_browser_delete_card) {
        override fun undo(col: Collection): Card? {
            Timber.i("Undo: Delete notes")
            // undo all of these at once instead of one-by-one
            val ids = ArrayList<Long>(notesArr.size + allCards.size)
            for (n in notesArr) {
                n.flush(n.mod, false)
                ids.add(n.id)
            }
            for (c in allCards) {
                c.flush(false)
                ids.add(c.id)
            }
            col.db.execute("DELETE FROM graves WHERE oid IN " + Utils.ids2str(ids))
            return null // don't fetch new card
        }
    }

    private class UndoChangeDeckMulti(private val cards: Array<Card>, private val originalDids: LongArray) : UndoAction(R.string.undo_action_change_deck_multi) {
        override fun undo(col: Collection): Card? {
            Timber.i("Undo: Change Decks")
            // move cards to original deck
            for (i in cards.indices) {
                val card = cards[i]
                card.load()
                card.did = originalDids[i]
                val note = card.note()
                note.flush()
                card.flush()
            }
            return null // don't fetch new card
        }
    }

    /** @param hasUnmarked whether there were any unmarked card (in which card the action was "mark",
     * otherwise the action was "Unmark")
     */
    private class UndoMarkNoteMulti
    (private val originalMarked: List<Note>, private val originalUnmarked: List<Note>, hasUnmarked: Boolean) : UndoAction(if (hasUnmarked) R.string.card_browser_mark_card else R.string.card_browser_unmark_card) {
        override fun undo(col: Collection): Card? {
            Timber.i("Undo: Mark notes")
            CardUtils.markAll(originalMarked, true)
            CardUtils.markAll(originalUnmarked, false)
            return null // don't fetch new card
        }
    }

    abstract class DismissNotes<Progress>(protected val cardIds: List<Long>) : TaskDelegate<Progress, Computation<Array<Card>>>() {
        /**
         * @param col
         * @param collectionTask Represents the background tasks.
         * @return whether the task succeeded, and the array of cards affected.
         */
        @KotlinCleanup("fix requireNoNulls")
        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<Progress>): Computation<Array<Card>> {
            // query cards
            val cards = arrayOfNulls<Card>(cardIds.size)
            for (i in cardIds.indices) {
                cards[i] = col.getCard(cardIds[i])
            }
            try {
                col.db.database.beginTransaction()
                try {
                    val succeeded = actualTask(col, collectionTask, cards.requireNoNulls())
                    if (!succeeded) {
                        return Computation.err()
                    }
                    col.db.database.setTransactionSuccessful()
                } finally {
                    DB.safeEndInTransaction(col.db)
                }
            } catch (e: RuntimeException) {
                Timber.e(e, "doInBackgroundSuspendCard - RuntimeException on suspending card")
                CrashReportService.sendExceptionReport(e, "doInBackgroundSuspendCard")
                return Computation.err()
            }
            // pass cards back so more actions can be performed by the caller
            // (querying the cards again is unnecessarily expensive)
            return Computation.ok(cards.requireNoNulls())
        }

        /**
         * @param col The collection
         * @param collectionTask, where to send progress and listen for cancellation
         * @param cards Cards to which the task should be applied
         * @return Whether the tasks succeeded.
         */
        protected abstract fun actualTask(col: Collection, collectionTask: ProgressSenderAndCancelListener<Progress>, cards: Array<Card>): Boolean
    }

    class SuspendCardMulti(cardIds: List<Long>) : DismissNotes<Void?>(cardIds) {
        override fun actualTask(col: Collection, collectionTask: ProgressSenderAndCancelListener<Void?>, cards: Array<Card>): Boolean {
            val sched = col.sched
            // collect undo information
            val cids = LongArray(cards.size)
            val originalSuspended = BooleanArray(cards.size)
            var hasUnsuspended = false
            for (i in cards.indices) {
                val card = cards[i]
                cids[i] = card.id
                if (card.queue != Consts.QUEUE_TYPE_SUSPENDED) {
                    hasUnsuspended = true
                    originalSuspended[i] = false
                } else {
                    originalSuspended[i] = true
                }
            }

            // if at least one card is unsuspended -> suspend all
            // otherwise unsuspend all
            if (hasUnsuspended) {
                sched.suspendCards(cids)
            } else {
                sched.unsuspendCards(cids)
            }

            // mark undo for all at once
            col.markUndo(UndoSuspendCardMulti(cards, originalSuspended, hasUnsuspended))

            // reload cards because they'll be passed back to caller
            for (c in cards) {
                c.load()
            }
            sched.deferReset()
            return true
        }
    }

    class Flag(cardIds: List<Long>, private val flag: Int) : DismissNotes<Void?>(cardIds) {
        override fun actualTask(col: Collection, collectionTask: ProgressSenderAndCancelListener<Void?>, cards: Array<Card>): Boolean {
            col.setUserFlag(flag, cardIds)
            for (c in cards) {
                c.load()
            }
            return true
        }
    }

    class MarkNoteMulti(cardIds: List<Long>) : DismissNotes<Void>(cardIds) {
        override fun actualTask(col: Collection, collectionTask: ProgressSenderAndCancelListener<Void>, cards: Array<Card>): Boolean {
            val notes = CardUtils.getNotes(Arrays.asList(*cards))
            // collect undo information
            val originalMarked: MutableList<Note> = ArrayList()
            val originalUnmarked: MutableList<Note> = ArrayList()
            for (n in notes) {
                if (NoteService.isMarked(n)) originalMarked.add(n) else originalUnmarked.add(n)
            }
            val hasUnmarked = !originalUnmarked.isEmpty()
            CardUtils.markAll(ArrayList(notes), hasUnmarked)

            // mark undo for all at once
            col.markUndo(UndoMarkNoteMulti(originalMarked, originalUnmarked, hasUnmarked))

            // reload cards because they'll be passed back to caller
            for (c in cards) {
                c.load()
            }
            return true
        }
    }

    class DeleteNoteMulti(cardIds: List<Long>) : DismissNotes<Array<Card>>(cardIds) {
        override fun actualTask(col: Collection, collectionTask: ProgressSenderAndCancelListener<Array<Card>>, cards: Array<Card>): Boolean {
            val sched = col.sched
            // list of all ids to pass to remNotes method.
            // Need Set (-> unique) so we don't pass duplicates to col.remNotes()
            val notes = CardUtils.getNotes(Arrays.asList(*cards))
            val allCards = CardUtils.getAllCards(notes)
            // delete note
            val uniqueNoteIds = LongArray(notes.size)
            val notesArr = notes.toTypedArray()
            var count = 0
            for (note in notes) {
                uniqueNoteIds[count] = note.id
                count++
            }
            col.markUndo(UndoDeleteNoteMulti(notesArr, allCards))
            col.remNotes(uniqueNoteIds)
            sched.deferReset()
            // pass back all cards because they can't be retrieved anymore by the caller (since the note is deleted)
            collectionTask.doProgress(allCards.toTypedArray())
            return true
        }
    }

    class ChangeDeckMulti(cardIds: List<Long>, private val newDid: Long) : DismissNotes<Void?>(cardIds) {
        override fun actualTask(col: Collection, collectionTask: ProgressSenderAndCancelListener<Void?>, cards: Array<Card>): Boolean {
            Timber.i("Changing %d cards to deck: '%d'", cards.size, newDid)
            val deckData = col.decks.get(newDid)
            if (Decks.isDynamic(deckData)) {
                // #5932 - can't change to a dynamic deck. Use "Rebuild"
                Timber.w("Attempted to move to dynamic deck. Cancelling task.")
                return false
            }

            // Confirm that the deck exists (and is not the default)
            try {
                val actualId = deckData.getLong("id")
                if (actualId != newDid) {
                    Timber.w("Attempted to move to deck %d, but got %d", newDid, actualId)
                    return false
                }
            } catch (e: Exception) {
                Timber.e(e, "failed to check deck")
                return false
            }
            val changedCardIds = LongArray(cards.size)
            for (i in cards.indices) {
                changedCardIds[i] = cards[i].id
            }
            col.sched.remFromDyn(changedCardIds)
            val originalDids = LongArray(cards.size)
            for (i in cards.indices) {
                val card = cards[i]
                card.load()
                // save original did for undo
                originalDids[i] = card.did
                // then set the card ID to the new deck
                card.did = newDid
                val note = card.note()
                note.flush()
                // flush card too, in case, did has been changed
                card.flush()
            }
            val changeDeckMulti: UndoAction = UndoChangeDeckMulti(cards, originalDids)
            // mark undo for all at once
            col.markUndo(changeDeckMulti)
            return true
        }
    }

    /**
     * A class allowing to send partial search result to the browser to display while the search ends
     */
    @KotlinCleanup("move variables to constructor")
    class PartialSearch(cards: List<CardCache>, columnIndex1: Int, columnIndex2: Int, numCardsToRender: Int, collectionTask: ProgressSenderAndCancelListener<List<CardCache>>, col: Collection) : ProgressSenderAndCancelListener<List<Long>> {
        private val mCards: MutableList<CardCache>
        private val mColumn1Index: Int
        private val mColumn2Index: Int
        private val mNumCardsToRender: Int
        private val mCollectionTask: ProgressSenderAndCancelListener<List<CardCache>>
        private val mCol: Collection
        override fun isCancelled(): Boolean {
            return mCollectionTask.isCancelled()
        }

        /**
         * @param cards Card ids to display in the browser. It is assumed that it is as least as long as mCards, and that
         * mCards[i].cid = cards[i].  It add the cards in cards after `mPosition` to mCards
         */
        fun add(cards: List<Long?>) {
            while (mCards.size < cards.size) {
                mCards.add(CardCache(cards[mCards.size]!!, mCol, mCards.size))
            }
        }

        @KotlinCleanup("non-null argument to doProgress")
        override fun doProgress(value: List<Long>?) {
            if (value == null) {
                return
            }
            // PERF: This is currently called on the background thread and blocks further execution of the search
            // PERF: This performs an individual query to load each note
            add(value)
            for (card in mCards) {
                if (isCancelled()) {
                    Timber.d("doInBackgroundSearchCards was cancelled so return")
                    return
                }
                card.load(false, mColumn1Index, mColumn2Index)
            }
            mCollectionTask.doProgress(mCards)
        }

        val progressSender: ProgressSender<Long>
            get() = object : ProgressSender<Long> {
                private val mRes: MutableList<Long> = ArrayList()
                private var mSendProgress = true
                override fun doProgress(value: Long?) {
                    if (!mSendProgress || value == null) {
                        return
                    }
                    mRes.add(value)
                    if (mRes.size >= mNumCardsToRender) {
                        this@PartialSearch.doProgress(mRes)
                        mSendProgress = false
                    }
                }
            }

        init {
            mCards = ArrayList(cards)
            mColumn1Index = columnIndex1
            mColumn2Index = columnIndex2
            mNumCardsToRender = numCardsToRender
            mCollectionTask = collectionTask
            mCol = col
        }
    }

    class SearchCards(private val query: String, private val order: SortOrder, private val numCardsToRender: Int, private val column1Index: Int, private val column2Index: Int) : TaskDelegate<List<CardCache>, SearchCardsResult>() {
        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<List<CardCache>>): SearchCardsResult {
            Timber.d("doInBackgroundSearchCards")
            if (collectionTask.isCancelled()) {
                Timber.d("doInBackgroundSearchCards was cancelled so return null")
                return SearchCardsResult.invalidResult()
            }
            val searchResult: MutableList<CardCache> = ArrayList()
            val searchResult_: List<Long>
            searchResult_ = try {
                col.findCards(query, order, PartialSearch(searchResult, column1Index, column2Index, numCardsToRender, collectionTask, col))
            } catch (e: Exception) {
                // exception can occur via normal operation
                Timber.w(e)
                return SearchCardsResult.error(e)
            }
            Timber.d("The search found %d cards", searchResult_.size)
            var position = 0
            for (cid in searchResult_) {
                val card = CardCache(cid, col, position++)
                searchResult.add(card)
            }
            // Render the first few items
            for (i in 0 until Math.min(numCardsToRender, searchResult.size)) {
                if (collectionTask.isCancelled()) {
                    Timber.d("doInBackgroundSearchCards was cancelled so return null")
                    return SearchCardsResult.invalidResult()
                }
                searchResult[i].load(false, column1Index, column2Index)
            }
            // Finish off the task
            return if (collectionTask.isCancelled()) {
                Timber.d("doInBackgroundSearchCards was cancelled so return null")
                SearchCardsResult.invalidResult()
            } else {
                SearchCardsResult.success(searchResult)
            }
        }
    }

    class RenderBrowserQA(private val cards: CardCollection<CardCache>, private val startPos: Int, private val n: Int, private val column1Index: Int, private val column2Index: Int) : TaskDelegate<Int, Pair<CardCollection<CardCache>, List<Long>>?>() {
        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<Int>): Pair<CardCollection<CardCache>, List<Long>>? {
            Timber.d("doInBackgroundRenderBrowserQA")
            val invalidCardIds: MutableList<Long> = ArrayList()
            // for each specified card in the browser list
            for (i in startPos until startPos + n) {
                // Stop if cancelled
                if (collectionTask.isCancelled()) {
                    Timber.d("doInBackgroundRenderBrowserQA was aborted")
                    return null
                }
                if (i < 0 || i >= cards.size()) {
                    continue
                }
                var card: CardCache
                card = try {
                    cards[i]
                } catch (e: IndexOutOfBoundsException) {
                    // even though we test against card.size() above, there's still a race condition
                    // We might be able to optimise this to return here. Logically if we're past the end of the collection,
                    // we won't reach any more cards.
                    continue
                }
                if (card.isLoaded) {
                    // We've already rendered the answer, we don't need to do it again.
                    continue
                }
                // Extract card item
                try {
                    // Ensure that card still exists.
                    card.card
                } catch (e: WrongId) {
                    // #5891 - card can be inconsistent between the deck browser screen and the collection.
                    // Realistically, we can skip any exception as it's a rendering task which should not kill the
                    // process
                    val cardId = card.id
                    Timber.e(e, "Could not process card '%d' - skipping and removing from sight", cardId)
                    invalidCardIds.add(cardId)
                    continue
                }
                // Update item
                card.load(false, column1Index, column2Index)
                val progress = i.toFloat() / n * 100
                collectionTask.doProgress(progress.toInt())
            }
            return Pair(cards, invalidCardIds)
        }
    }

    class CheckDatabase : TaskDelegate<String, Pair<Boolean, CheckDatabaseResult?>>() {
        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<String>): Pair<Boolean, CheckDatabaseResult?> {
            Timber.d("doInBackgroundCheckDatabase")
            // Don't proceed if collection closed
            val result = col.fixIntegrity(TaskManager.ProgressCallback(collectionTask, AnkiDroidApp.getAppResources()))
            return if (result.failed) {
                // we can fail due to a locked database, which requires knowledge of the failure.
                Pair(false, result)
            } else {
                // Close the collection and we restart the app to reload
                CollectionHelper.getInstance().closeCollection(true, "Check Database Completed")
                Pair(true, result)
            }
        }
    }

    @KotlinCleanup("doesn't work on null collection - only on non-openable")
    class RepairCollection : UnsafeTaskDelegate<Void, Boolean>() {
        override fun task(col: Collection?, collectionTask: ProgressSenderAndCancelListener<Void>): Boolean {
            Timber.d("doInBackgroundRepairCollection")
            if (col != null) {
                Timber.i("RepairCollection: Closing collection")
                col.close(false)
            }
            return BackupManager.repairCollection(col!!)
        }
    }

    class UpdateValuesFromDeck(private val reset: Boolean) : TaskDelegate<Void, DeckStudyData?>() {
        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<Void>): DeckStudyData? {
            Timber.d("doInBackgroundUpdateValuesFromDeck")
            return try {
                val sched = col.sched
                if (reset) {
                    // reset actually required because of counts, which is used in getCollectionTaskListener
                    sched.resetCounts()
                }
                val counts = sched.counts()
                val totalNewCount = sched.totalNewForCurrentDeck()
                val totalCount = sched.cardCount()
                DeckStudyData(
                    counts.new, counts.lrn, counts.rev, totalNewCount,
                    totalCount, sched.eta(counts)
                )
            } catch (e: RuntimeException) {
                Timber.e(e, "doInBackgroundUpdateValuesFromDeck - an error occurred")
                null
            }
        }
    }

    class DeleteDeck(private val did: Long) : TaskDelegate<Void, IntArray?>() {
        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<Void>): IntArray? {
            Timber.d("doInBackgroundDeleteDeck")
            col.decks.rem(did, true)
            // TODO: if we had "undo delete note" like desktop client then we won't need this.
            col.clearUndo()
            return null
        }
    }

    class RebuildCram : TaskDelegate<Void, DeckStudyData?>() {
        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<Void>): DeckStudyData? {
            Timber.d("doInBackgroundRebuildCram")
            col.sched.rebuildDyn(col.decks.selected())
            return UpdateValuesFromDeck(true).execTask(col, collectionTask)
        }
    }

    class EmptyCram : TaskDelegate<Void, DeckStudyData?>() {
        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<Void>): DeckStudyData? {
            Timber.d("doInBackgroundEmptyCram")
            col.sched.emptyDyn(col.decks.selected())
            return UpdateValuesFromDeck(true).execTask(col, collectionTask)
        }
    }

    class ImportAdd(private val path: String) : TaskDelegate<String, Triple<AnkiPackageImporter?, Boolean, String?>>() {
        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<String>): Triple<AnkiPackageImporter?, Boolean, String?> {
            Timber.d("doInBackgroundImportAdd")
            val res = AnkiDroidApp.getInstance().baseContext.resources
            val imp = AnkiPackageImporter(col, path)
            imp.setProgressCallback(TaskManager.ProgressCallback(collectionTask, res))
            try {
                imp.run()
            } catch (e: ImportExportException) {
                Timber.w(e)
                return Triple(null, true, e.message)
            }
            return Triple(imp, false, null)
        }
    }

    @KotlinCleanup("needs to handle null collection")
    class ImportReplace(private val path: String) : TaskDelegate<String, Computation<*>>() {
        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<String>): Computation<*> {
            Timber.d("doInBackgroundImportReplace")
            val res = AnkiDroidApp.getInstance().baseContext.resources
            val context = col.context

            // extract the deck from the zip file
            val colPath = CollectionHelper.getCollectionPath(context)
            val dir = File(File(colPath).parentFile, "tmpzip")
            if (dir.exists()) {
                BackupManager.removeDir(dir)
            }

            // from anki2.py
            var colname = "collection.anki21"
            val zip: ZipFile
            zip = try {
                ZipFile(File(path))
            } catch (e: IOException) {
                Timber.e(e, "doInBackgroundImportReplace - Error while unzipping")
                CrashReportService.sendExceptionReport(e, "doInBackgroundImportReplace0")
                return Computation.ERR
            }
            try {
                // v2 scheduler?
                if (zip.getEntry(colname) == null) {
                    colname = CollectionHelper.COLLECTION_FILENAME
                }
                Utils.unzipFiles(zip, dir.absolutePath, arrayOf(colname, "media"), null)
            } catch (e: IOException) {
                CrashReportService.sendExceptionReport(e, "doInBackgroundImportReplace - unzip")
                return Computation.ERR
            }
            val colFile = File(dir, colname).absolutePath
            if (!File(colFile).exists()) {
                return Computation.ERR
            }
            var tmpCol: Collection? = null
            try {
                tmpCol = Storage.Collection(context, colFile)
                if (!tmpCol.validCollection()) {
                    tmpCol.close()
                    return Computation.ERR
                }
            } catch (e: Exception) {
                Timber.e("Error opening new collection file... probably it's invalid")
                try {
                    tmpCol!!.close()
                } catch (e2: Exception) {
                    Timber.w(e2)
                    // do nothing
                }
                CrashReportService.sendExceptionReport(e, "doInBackgroundImportReplace - open col")
                return Computation.ERR
            } finally {
                tmpCol?.close()
            }
            collectionTask.doProgress(res.getString(R.string.importing_collection))
            try {
                CollectionHelper.getInstance().getCol(context)
                // unload collection
                CollectionHelper.getInstance().closeCollection(true, "Importing new collection")
                CollectionHelper.getInstance().lockCollection()
            } catch (e: Exception) {
                Timber.w(e)
            }
            // overwrite collection
            val f = File(colFile)
            if (!f.renameTo(File(colPath))) {
                // Exit early if this didn't work
                return Computation.ERR
            }
            return try {
                CollectionHelper.getInstance().unlockCollection()

                // because users don't have a backup of media, it's safer to import new
                // data and rely on them running a media db check to get rid of any
                // unwanted media. in the future we might also want to duplicate this step
                // import media
                val nameToNum = HashMap<String, String>()
                val numToName = HashMap<String, String>()
                val mediaMapFile = File(dir.absolutePath, "media")
                if (mediaMapFile.exists()) {
                    factory.createParser(mediaMapFile).use { jp ->
                        var name: String
                        var num: String
                        check(jp.nextToken() == JsonToken.START_OBJECT) { "Expected content to be an object" }
                        while (jp.nextToken() != JsonToken.END_OBJECT) {
                            num = jp.currentName()
                            name = jp.nextTextValue()
                            nameToNum[name] = num
                            numToName[num] = name
                        }
                    }
                }
                val mediaDir = Media.getCollectionMediaPath(colPath)
                val total = nameToNum.size
                var i = 0
                for ((file, c) in nameToNum) {
                    val of = File(mediaDir, file)
                    if (!of.exists()) {
                        Utils.unzipFiles(zip, mediaDir, arrayOf(c), numToName)
                    }
                    ++i
                    collectionTask.doProgress(res.getString(R.string.import_media_count, (i + 1) * 100 / total))
                }
                zip.close()
                // delete tmp dir
                BackupManager.removeDir(dir)
                Computation.OK
            } catch (e: RuntimeException) {
                Timber.e(e, "doInBackgroundImportReplace - RuntimeException")
                CrashReportService.sendExceptionReport(e, "doInBackgroundImportReplace1")
                Computation.ERR
            } catch (e: FileNotFoundException) {
                Timber.e(e, "doInBackgroundImportReplace - FileNotFoundException")
                CrashReportService.sendExceptionReport(e, "doInBackgroundImportReplace2")
                Computation.ERR
            } catch (e: IOException) {
                Timber.e(e, "doInBackgroundImportReplace - IOException")
                CrashReportService.sendExceptionReport(e, "doInBackgroundImportReplace3")
                Computation.ERR
            }
        }
    }

    class ExportApkg(private val apkgPath: String, private val did: Long?, private val includeSched: Boolean, private val includeMedia: Boolean) : TaskDelegate<Void, Pair<Boolean, String?>>() {
        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<Void>): Pair<Boolean, String?> {
            Timber.d("doInBackgroundExportApkg")
            try {
                val exporter = if (did == null) {
                    AnkiPackageExporter(col, includeSched, includeMedia)
                } else {
                    AnkiPackageExporter(col, did, includeSched, includeMedia)
                }
                exporter.exportInto(apkgPath, col.context)
            } catch (e: FileNotFoundException) {
                Timber.e(e, "FileNotFoundException in doInBackgroundExportApkg")
                return Pair(false, null)
            } catch (e: IOException) {
                Timber.e(e, "IOException in doInBackgroundExportApkg")
                return Pair(false, null)
            } catch (e: JSONException) {
                Timber.e(e, "JSOnException in doInBackgroundExportApkg")
                return Pair(false, null)
            } catch (e: ImportExportException) {
                Timber.e(e, "ImportExportException in doInBackgroundExportApkg")
                return Pair(true, e.message)
            }
            return Pair(false, apkgPath)
        }
    }

    class Reorder(private val conf: DeckConfig) : TaskDelegate<Void, Boolean>() {
        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<Void>): Boolean {
            Timber.d("doInBackgroundReorder")
            col.sched.resortConf(conf)
            return true
        }
    }

    class ConfChange(private val deck: Deck, private val conf: DeckConfig) : TaskDelegate<Void, Boolean>() {
        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<Void>): Boolean {
            Timber.d("doInBackgroundConfChange")
            return try {
                val newConfId = conf.getLong("id")
                // If new config has a different sorting order, reorder the cards
                val oldOrder = col.decks.getConf(deck.getLong("conf"))!!.getJSONObject("new").getInt("order")
                val newOrder = col.decks.getConf(newConfId)!!.getJSONObject("new").getInt("order")
                if (oldOrder != newOrder) {
                    when (newOrder) {
                        0 -> col.sched.randomizeCards(deck.getLong("id"))
                        1 -> col.sched.orderCards(deck.getLong("id"))
                    }
                }
                col.decks.setConf(deck, newConfId)
                col.save()
                true
            } catch (e: JSONException) {
                Timber.w(e)
                false
            }
        }
    }

    class ConfReset(private val conf: DeckConfig) : TaskDelegate<Void, Boolean?>() {
        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<Void>): Boolean? {
            Timber.d("doInBackgroundConfReset")
            col.decks.restoreToDefault(conf)
            col.save()
            return null
        }
    }

    class ConfRemove(private val conf: DeckConfig) : TaskDelegate<Void, Boolean>() {
        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<Void>): Boolean {
            Timber.d("doInBackgroundConfRemove")
            return try {
                // Note: We do the actual removing of the options group in the main thread so that we
                // can ask the user to confirm if they're happy to do a full sync, and just do the resorting here

                // When a conf is deleted, all decks using it revert to the default conf.
                // Cards must be reordered according to the default conf.
                val order = conf.getJSONObject("new").getInt("order")
                val defaultOrder = col.decks.getConf(1)!!.getJSONObject("new").getInt("order")
                if (order != defaultOrder) {
                    conf.getJSONObject("new").put("order", defaultOrder)
                    col.sched.resortConf(conf)
                }
                col.save()
                true
            } catch (e: JSONException) {
                Timber.w(e)
                false
            }
        }
    }

    @KotlinCleanup("fix `val changed = execTask()!!`")
    class ConfSetSubdecks(private val deck: Deck, private val conf: DeckConfig) : TaskDelegate<Void, Boolean>() {
        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<Void>): Boolean {
            Timber.d("doInBackgroundConfSetSubdecks")
            return try {
                val children = col.decks.children(deck.getLong("id"))
                for (childDid in children.values) {
                    val child = col.decks.get(childDid)
                    if (child.isDyn) {
                        continue
                    }
                    val changed = ConfChange(child, conf).execTask(col, collectionTask)
                    if (!changed) {
                        return false
                    }
                }
                true
            } catch (e: JSONException) {
                Timber.w(e)
                false
            }
        }
    }

    /**
     * @return The results list from the check, or false if any errors.
     */
    class CheckMedia : TaskDelegate<Void, Computation<List<List<String>>>>() {
        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<Void>): Computation<List<List<String>>> {
            Timber.d("doInBackgroundCheckMedia")
            // Ensure that the DB is valid - unknown why, but some users were missing the meta table.
            try {
                col.media.rebuildIfInvalid()
            } catch (e: IOException) {
                Timber.w(e)
                return Computation.err()
            }
            // A media check on AnkiDroid will also update the media db
            col.media.findChanges(true)
            // Then do the actual check
            return Computation.ok(col.media.check())
        }
    }

    class DeleteMedia(private val unused: List<String>) : TaskDelegate<Void, Int>() {
        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<Void>): Int {
            val m = col.media
            for (fname in unused) {
                m.removeFile(fname)
            }
            return unused.size
        }
    }

    /**
     * Handles everything for a model change at once - template add / deletes as well as content updates
     */
    class SaveModel(private val model: Model, private val templateChanges: ArrayList<Array<Any>>) : TaskDelegate<Void, Pair<Boolean, String?>?>() {
        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<Void>): Pair<Boolean, String?> {
            Timber.d("doInBackgroundSaveModel")
            val oldModel = col.models.get(model.getLong("id"))
            Objects.requireNonNull(oldModel)

            // TODO need to save all the cards that will go away, for undo
            //  (do I need to remove them from graves during undo also?)
            //    - undo (except for cards) could just be Models.update(model) / Models.flush() / Collection.reset() (that was prior "undo")
            val newTemplates = model.getJSONArray("tmpls")
            col.db.database.beginTransaction()
            try {
                for (change in templateChanges) {
                    val oldTemplates = oldModel!!.getJSONArray("tmpls")
                    when (change[1] as TemporaryModel.ChangeType) {
                        TemporaryModel.ChangeType.ADD -> {
                            Timber.d("doInBackgroundSaveModel() adding template %s", change[0])
                            try {
                                col.models.addTemplate(oldModel, newTemplates.getJSONObject(change[0] as Int))
                            } catch (e: Exception) {
                                Timber.e(e, "Unable to add template %s to model %s", change[0], model.getLong("id"))
                                return Pair(false, e.localizedMessage)
                            }
                        }
                        TemporaryModel.ChangeType.DELETE -> {
                            Timber.d("doInBackgroundSaveModel() deleting template currently at ordinal %s", change[0])
                            try {
                                col.models.remTemplate(oldModel, oldTemplates.getJSONObject(change[0] as Int))
                            } catch (e: Exception) {
                                Timber.e(e, "Unable to delete template %s from model %s", change[0], model.getLong("id"))
                                return Pair(false, e.localizedMessage)
                            }
                        }
                    }
                }

                // required for Rust: the modified time can't go backwards, and we updated the model by adding fields
                // This could be done better
                model.put("mod", oldModel!!.getLong("mod"))
                col.models.save(model, true)
                col.models.update(model)
                col.reset()
                col.save()
                if (col.db.database.inTransaction()) {
                    col.db.database.setTransactionSuccessful()
                } else {
                    Timber.i("CollectionTask::SaveModel was not in a transaction? Cannot mark transaction successful.")
                }
            } finally {
                DB.safeEndInTransaction(col.db)
            }
            return Pair(true, null)
        }
    }

    /*
     * Async task for the ModelBrowser Class
     * Returns an ArrayList of all models alphabetically ordered and the number of notes
     * associated with each model.
     *
     * @return {ArrayList<JSONObject> models, ArrayList<Integer> cardCount}
     */
    class CountModels : TaskDelegate<Void, Pair<List<Model>, ArrayList<Int>>?>() {
        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<Void>): Pair<List<Model>, ArrayList<Int>>? {
            Timber.d("doInBackgroundLoadModels")
            val models = col.models.all()
            val cardCount = ArrayList<Int>()
            Collections.sort(models, Comparator { a: JSONObject, b: JSONObject -> a.getString("name").compareTo(b.getString("name")) } as Comparator<JSONObject>)
            for (n in models) {
                if (collectionTask.isCancelled()) {
                    Timber.e("doInBackgroundLoadModels :: Cancelled")
                    // onPostExecute not executed if cancelled. Return value not used.
                    return null
                }
                cardCount.add(col.models.useCount(n))
            }
            return Pair(models, cardCount)
        }
    }

    /**
     * Deletes the given model
     * and all notes associated with it
     */
    class DeleteModel(private val modID: Long) : TaskDelegate<Void, Boolean?>() {
        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<Void>): Boolean {
            Timber.d("doInBackGroundDeleteModel")
            try {
                col.models.rem(col.models.get(modID)!!)
                col.save()
            } catch (e: ConfirmModSchemaException) {
                e.log()
                Timber.e("doInBackGroundDeleteModel :: ConfirmModSchemaException")
                return false
            }
            return true
        }
    }

    /**
     * Deletes the given field in the given model
     */
    class DeleteField(private val model: Model, private val field: JSONObject) : TaskDelegate<Void, Boolean>() {
        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<Void>): Boolean {
            Timber.d("doInBackGroundDeleteField")
            try {
                col.models.remField(model, field)
                col.save()
            } catch (e: ConfirmModSchemaException) {
                // Should never be reached
                e.log()
                return false
            }
            return true
        }
    }

    /**
     * Repositions the given field in the given model
     */
    class RepositionField(private val model: Model, private val field: JSONObject, private val index: Int) : TaskDelegate<Void, Boolean>() {
        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<Void>): Boolean {
            Timber.d("doInBackgroundRepositionField")
            try {
                col.models.moveField(model, field, index)
                col.save()
            } catch (e: ConfirmModSchemaException) {
                e.log()
                // Should never be reached
                return false
            }
            return true
        }
    }

    /**
     * Adds a field with name in given model
     */
    class AddField(private val model: Model, private val fieldName: String) : TaskDelegate<Void, Boolean?>() {
        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<Void>): Boolean {
            Timber.d("doInBackgroundRepositionField")
            col.models.addFieldModChanged(model, col.models.newField(fieldName))
            col.save()
            return true
        }
    }

    /**
     * Adds a field of with name in given model
     */
    class ChangeSortField(private val model: Model, private val idx: Int) : TaskDelegate<Void, Boolean>() {
        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<Void>): Boolean {
            try {
                Timber.d("doInBackgroundChangeSortField")
                col.models.setSortIdx(model, idx)
                col.save()
            } catch (e: Exception) {
                Timber.e(e, "Error changing sort field")
                return false
            }
            return true
        }
    }

    class FindEmptyCards : TaskDelegate<Int, List<Long?>?>() {
        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<Int>): List<Long> {
            return col.emptyCids(collectionTask)
        }
    }

    /**
     * Goes through selected cards and checks selected and marked attribute
     * @return If there are unselected cards, if there are unmarked cards
     */
    class CheckCardSelection(private val checkedCards: Set<CardCache>) : TaskDelegate<Void, Pair<Boolean, Boolean>?>() {
        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<Void>): Pair<Boolean, Boolean>? {
            var hasUnsuspended = false
            var hasUnmarked = false
            for (c in checkedCards) {
                if (collectionTask.isCancelled()) {
                    Timber.v("doInBackgroundCheckCardSelection: cancelled.")
                    return null
                }
                val card = c.card
                hasUnsuspended = hasUnsuspended || card.queue != Consts.QUEUE_TYPE_SUSPENDED
                hasUnmarked = hasUnmarked || !NoteService.isMarked(card.note())
                if (hasUnsuspended && hasUnmarked) break
            }
            return Pair(hasUnsuspended, hasUnmarked)
        }
    }

    class PreloadNextCard : TaskDelegate<Void, Void?>() {
        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<Void>): Void? {
            try {
                col.sched.counts() // Ensure counts are recomputed if necessary, to know queue to look for
                col.sched.preloadNextCard()
            } catch (e: RuntimeException) {
                Timber.e(e, "doInBackgroundPreloadNextCard - RuntimeException on preloading card")
            }
            return null
        }
    }

    class LoadCollectionComplete : TaskDelegate<Void, Void?>() {
        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<Void>): Void? {
            CollectionHelper.loadCollectionComplete(col)
            return null
        }
    }

    class Reset : TaskDelegate<Void, Void?>() {
        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<Void>): Void? {
            col.sched.reset()
            return null
        }
    }

    companion object {
        @JvmStatic
        @VisibleForTesting
        fun nonTaskUndo(col: Collection): Card? {
            val sched = col.sched
            val card = col.undo()
            if (card == null) {
                /* multi-card action undone, no action to take here */
                Timber.d("Multi-select undo succeeded")
            } else {
                // cid is actually a card id.
                // a review was undone,
                /* card review undone, set up to review that card again */
                Timber.d("Single card review undo succeeded")
                card.startTimer()
                col.reset()
                sched.deferReset(card)
            }
            return card
        }
    }
}
