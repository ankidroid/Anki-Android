/*
 * Copyright (c) 2009 Daniel Svärd daniel.svard@gmail.com>
 * Copyright (c) 2011 Norbert Nagold norbert.nagold@gmail.com>
 * Copyright (c) 2014 Houssam Salem <houssam.salem.au@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.libanki

import android.content.ContentValues
import androidx.annotation.VisibleForTesting
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.R
import com.ichi2.anki.servicelayer.NoteService.avgEase
import com.ichi2.async.CancelListener
import com.ichi2.libanki.Consts.CARD_QUEUE
import com.ichi2.libanki.Consts.CARD_TYPE
import com.ichi2.libanki.TemplateManager.TemplateRenderContext.TemplateRenderOutput
import com.ichi2.libanki.stats.Stats
import com.ichi2.libanki.template.TemplateError
import com.ichi2.libanki.utils.TimeManager
import com.ichi2.utils.Assert
import com.ichi2.utils.LanguageUtil
import net.ankiweb.rsdroid.RustCleanup
import org.json.JSONObject
import timber.log.Timber
import java.util.*
import java.util.concurrent.CancellationException

/**
 * A Card is the ultimate entity subject to review; it encapsulates the scheduling parameters (from which to derive
 * the next interval), the note it is derived from (from which field data is retrieved), its own ownership (which deck it
 * currently belongs to), and the retrieval of presentation elements (filled-in templates).
 *
 * Card presentation has two components: the question (front) side and the answer (back) side. The presentation of the
 * card is derived from the template of the card's Card Type. The Card Type is a component of the Note Type (see Models)
 * that this card is derived from.
 *
 * This class is responsible for:
 * - Storing and retrieving database entries that map to Cards in the Collection
 * - Providing the HTML representation of the Card's question and answer
 * - Recording the results of review (answer chosen, time taken, etc)
 *
 * It does not:
 * - Generate new cards (see Collection)
 * - Store the templates or the style sheet (see Models)
 *
 * Type: 0=new, 1=learning, 2=due
 * Queue: same as above, and:
 * -1=suspended, -2=user buried, -3=sched buried
 * Due is used differently for different queues.
 * - new queue: note id or random int
 * - rev queue: integer day
 * - lrn queue: integer timestamp
 */
open class Card : Cloneable {
    // Needed for tests
    var col: Collection

    /**
     * Time in MS when timer was started
     */
    var timerStarted: Long

    // Not in LibAnki. Record time spent reviewing in MS in order to restore when resuming.
    private var elapsedTime: Long = 0

    // BEGIN SQL table entries
    @set:VisibleForTesting
    var id: Long
    var nid: NoteId = 0
    var did: DeckId = 0
    var ord = 0
    var mod: Long = 0
    var usn = 0

    @get:CARD_TYPE
    @CARD_TYPE
    var type = 0

    @get:CARD_QUEUE
    @CARD_QUEUE
    var queue = 0
    var due: Long = 0
    var ivl = 0
    var factor = 0
    var reps = 0
        private set
    var lapses = 0
    var left = 0
    var oDue: Long = 0
    var oDid: DeckId = 0
    private var flags = 0
    private lateinit var data: String

    // END SQL table entries
    @set:JvmName("setRenderOutput")
    @get:JvmName("getRenderOutput")
    protected var render_output: TemplateRenderOutput?
    private var note: Note?

    /** Used by Sched to determine which queue to move the card to after answering. */
    var wasNew = false

    /** Used by Sched to record the original interval in the revlog after answering. */
    var lastIvl = 0

    constructor(col: Collection) {
        this.col = col
        timerStarted = 0L
        render_output = null
        note = null
        // to flush, set nid, ord, and due
        this.id = TimeManager.time.timestampID(this.col.db, "cards")
        did = 1
        this.type = Consts.CARD_TYPE_NEW
        queue = Consts.QUEUE_TYPE_NEW
        ivl = 0
        factor = 0
        reps = 0
        lapses = 0
        left = 0
        oDue = 0
        oDid = 0
        flags = 0
        data = ""
    }

    constructor(col: Collection, id: Long) {
        this.col = col
        timerStarted = 0L
        render_output = null
        note = null
        this.id = id
        load()
    }

    fun load() {
        col.db.query("SELECT * FROM cards WHERE id = ?", this.id).use { cursor ->
            if (!cursor.moveToFirst()) {
                throw WrongId(this.id, "card")
            }
            this.id = cursor.getLong(0)
            nid = cursor.getLong(1)
            did = cursor.getLong(2)
            ord = cursor.getInt(3)
            mod = cursor.getLong(4)
            usn = cursor.getInt(5)
            this.type = cursor.getInt(6)
            queue = cursor.getInt(7)
            due = cursor.getInt(8).toLong()
            ivl = cursor.getInt(9)
            factor = cursor.getInt(10)
            reps = cursor.getInt(11)
            lapses = cursor.getInt(12)
            left = cursor.getInt(13)
            oDue = cursor.getLong(14)
            oDid = cursor.getLong(15)
            flags = cursor.getInt(16)
            data = cursor.getString(17)
        }
        render_output = null
        note = null
    }

    fun flush(changeModUsn: Boolean = true) {
        if (changeModUsn) {
            mod = TimeManager.time.intTime()
            usn = col.usn()
        }
        assert(due < "4294967296".toLong())
        col.db.execute(
            "insert or replace into cards values " +
                "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            this.id,
            nid,
            did,
            ord,
            mod,
            usn,
            this.type,
            queue,
            due,
            ivl,
            factor,
            reps,
            lapses,
            left,
            oDue,
            oDid,
            flags,
            data
        )
        col.log(this)
    }

    fun flushSched() {
        mod = TimeManager.time.intTime()
        usn = col.usn()
        assert(due < "4294967296".toLong())
        val values = ContentValues()
        values.put("mod", mod)
        values.put("usn", usn)
        values.put("type", this.type)
        values.put("queue", queue)
        values.put("due", due)
        values.put("ivl", ivl)
        values.put("factor", factor)
        values.put("reps", reps)
        values.put("lapses", lapses)
        values.put("left", left)
        values.put("odue", oDue)
        values.put("odid", oDid)
        values.put("did", did)
        // TODO: The update DB call sets mod=true. Verify if this is intended.
        col.db.update("cards", values, "id = ?", arrayOf(java.lang.Long.toString(this.id)))
        col.log(this)
    }

    fun q(reload: Boolean = false, browser: Boolean = false): String {
        return render_output(reload, browser).question_and_style()
    }

    fun a(): String {
        return render_output().answer_and_style()
    }

    @RustCleanup("legacy")
    fun css(): String {
        return "<style>${render_output().css}</style>"
    }

    fun questionAvTags(): List<AvTag> {
        return render_output().question_av_tags
    }

    fun answerAvTags(): List<AvTag> {
        return render_output().answer_av_tags
    }

    /**
     * @throws net.ankiweb.rsdroid.exceptions.BackendInvalidInputException: If the card does not exist
     */
    @RustCleanup("move col.render_output back to Card once the java collection is removed")
    open fun render_output(reload: Boolean = false, browser: Boolean = false): TemplateRenderOutput {
        if (render_output == null || reload) {
            render_output = col.render_output(this, reload, browser)
        }
        return render_output!!
    }

    open fun note(): Note {
        return note(false)
    }

    open fun note(reload: Boolean): Note {
        if (note == null || reload) {
            note = col.getNote(nid)
        }
        return note!!
    }

    // not in upstream
    open fun model(): Model {
        return note().model()
    }

    fun template(): JSONObject {
        val m = model()
        return if (m.isStd) {
            m.getJSONArray("tmpls").getJSONObject(ord)
        } else {
            model().getJSONArray("tmpls").getJSONObject(0)
        }
    }

    fun startTimer() {
        timerStarted = TimeManager.time.intTimeMS()
    }

    /**
     * Time limit for answering in milliseconds.
     */
    fun timeLimit(): Int {
        val conf = col.decks.confForDid(if (!isInDynamicDeck) did else oDid)
        return conf.getInt("maxTaken") * 1000
    }

    /*
     * Time taken to answer card, in integer MS.
     */
    fun timeTaken(): Int {
        // Indeed an int. Difference between two big numbers is still small.
        val total = (TimeManager.time.intTimeMS() - timerStarted).toInt()
        return Math.min(total, timeLimit())
    }

    open val isEmpty: Boolean
        get() = try {
            Models.emptyCard(model(), ord, note().fields)
        } catch (er: TemplateError) {
            Timber.w("Card is empty because the card's template has an error: %s.", er.message(col.context))
            true
        }

    /*
     * ***********************************************************
     * The methods below are not in LibAnki.
     * ***********************************************************
     */
    fun qSimple(): String {
        return render_output(false).question_text
    }

    /*
     * Returns the answer with anything before the <hr id=answer> tag removed
     */
    val pureAnswer: String
        get() {
            val s = render_output(false).answer_text
            for (target in arrayOf("<hr id=answer>", "<hr id=\"answer\">")) {
                val pos = s.indexOf(target)
                if (pos == -1) continue
                return s.substring(pos + target.length).trim { it <= ' ' }
            }
            // neither found
            return s
        }

    /**
     * Save the currently elapsed reviewing time so it can be restored on resume.
     *
     * Use this method whenever a review session (activity) has been paused. Use the resumeTimer()
     * method when the session resumes to start counting review time again.
     */
    fun stopTimer() {
        elapsedTime = TimeManager.time.intTimeMS() - timerStarted
    }

    /**
     * Resume the timer that counts the time spent reviewing this card.
     *
     * Unlike the desktop client, AnkiDroid must pause and resume the process in the middle of
     * reviewing. This method is required to keep track of the actual amount of time spent in
     * the reviewer and *must* be called on resume before any calls to timeTaken() take place
     * or the result of timeTaken() will be wrong.
     */
    fun resumeTimer() {
        timerStarted = TimeManager.time.intTimeMS() - elapsedTime
    }

    @VisibleForTesting
    fun setReps(reps: Int): Int {
        return reps.also { this.reps = it }
    }

    fun incrReps(): Int {
        return ++reps
    }

    fun showTimer(): Boolean {
        val options = col.decks.confForDid(if (!isInDynamicDeck) did else oDid)
        return DeckConfig.parseTimerOpt(options, true)
    }

    public override fun clone(): Card {
        return try {
            super.clone() as Card
        } catch (e: CloneNotSupportedException) {
            throw RuntimeException(e)
        }
    }

    override fun toString(): String {
        val declaredFields = this.javaClass.declaredFields
        val members: MutableList<String?> = ArrayList(declaredFields.size)
        for (f in declaredFields) {
            try {
                // skip non-useful elements
                if (SKIP_PRINT.contains(f.name)) {
                    continue
                }
                members.add("'${f.name}': ${f[this]}")
            } catch (e: IllegalAccessException) {
                members.add("'${f.name}': N/A")
            } catch (e: IllegalArgumentException) {
                members.add("'${f.name}': N/A")
            }
        }
        return members.joinToString(",  ")
    }

    override fun equals(other: Any?): Boolean {
        return if (other is Card) {
            this.id == other.id
        } else {
            super.equals(other)
        }
    }

    override fun hashCode(): Int {
        // Map a long to an int. For API>=24 you would just do `Long.hashCode(this.getId())`
        return (this.id xor (this.id ushr 32)).toInt()
    }

    fun userFlag(): Int {
        return intToFlag(flags)
    }

    @VisibleForTesting
    fun setFlag(flag: Int) {
        flags = flag
    }

    /** Should use [userFlag] */
    fun internalGetFlags() = flags

    fun setUserFlag(flag: Int) {
        flags = setFlagInInt(flags, flag)
    }

    // not in Anki.
    val dueString: String
        get() {
            var t = nextDue()
            if (queue < 0) {
                t = "($t)"
            }
            return t
        }

    // as in Anki aqt/browser.py
    @VisibleForTesting
    fun nextDue(): String {
        val date: Long
        val due = due
        date = if (isInDynamicDeck) {
            return AnkiDroidApp.appResources.getString(R.string.card_browser_due_filtered_card)
        } else if (queue == Consts.QUEUE_TYPE_LRN) {
            due
        } else if (queue == Consts.QUEUE_TYPE_NEW || type == Consts.CARD_TYPE_NEW) {
            return java.lang.Long.valueOf(due).toString()
        } else if (queue == Consts.QUEUE_TYPE_REV || queue == Consts.QUEUE_TYPE_DAY_LEARN_RELEARN || type == Consts.CARD_TYPE_REV && queue < 0) {
            val time = TimeManager.time.intTime()
            val nbDaySinceCreation = due - col.sched.today
            time + nbDaySinceCreation * Stats.SECONDS_PER_DAY
        } else {
            return ""
        }
        return LanguageUtil.getShortDateFormatFromS(date)
    } // In Anki Desktop, a card with oDue <> 0 && oDid == 0 is not marked as dynamic.

    fun avgEaseOfNote() = avgEase(col, note())

    /** Non libAnki  */
    val isInDynamicDeck: Boolean
        get() = // In Anki Desktop, a card with oDue <> 0 && oDid == 0 is not marked as dynamic.
            oDid != 0L
    val isReview: Boolean
        get() = this.type == Consts.CARD_TYPE_REV && queue == Consts.QUEUE_TYPE_REV
    val isNew: Boolean
        get() = this.type == Consts.CARD_TYPE_NEW

    /** A cache represents an intermediary step between a card id and a card object. Creating a Card has some fixed cost
     * in term of database access. Using an id has an unknown cost: none if the card is never accessed, heavy if the
     * card is accessed a lot of time. CardCache ensure that the cost is paid at most once, by waiting for first access
     * to load the data, and then saving them. Since CPU and RAM is usually less of a bottleneck than database access,
     * it may often be worth using this cache.
     *
     * Beware that the card is loaded only once. Change in the database are not reflected, so use it only if you can
     * safely assume that the card has not changed. That is
     * long id;
     * Card card = col.getCard(id);
     * ....
     * Card card2 = col.getCard(id);
     * is not equivalent to
     * long id;
     * Card.Cache cache = new Cache(col, id);
     * Card card = cache.getCard();
     * ....
     * Card card2 = cache.getCard();
     *
     * It is equivalent to:
     * long id;
     * Card.Cache cache = new Cache(col, id);
     * Card card = cache.getCard();
     * ....
     * cache.reload();
     * Card card2 = cache.getCard();
     */
    open class Cache : Cloneable {
        val col: Collection
        val id: Long
        private var mCard: Card? = null

        constructor(col: Collection, id: Long) {
            this.col = col
            this.id = id
        }

        /** Copy of cache. Useful to create a copy of a subclass without loosing card if it is loaded.  */
        protected constructor(cache: Cache) {
            col = cache.col
            this.id = cache.id
            mCard = cache.mCard
        }

        /** Copy of cache. Useful to create a copy of a subclass without loosing card if it is loaded.  */
        constructor(card: Card) {
            col = card.col
            this.id = card.id
            mCard = card
        }

        /**
         * The card with id given at creation. Note that it has content of the time at which the card was loaded, which
         * may have changed in database. So it is not equivalent to getCol().getCard(getId()). If you need fresh data, reload
         * first. */
        @get:Synchronized
        val card: Card
            get() {
                if (mCard == null) {
                    mCard = col.getCard(this.id)
                }
                return mCard!!
            }

        /** Next access to card will reload the card from the database.  */
        @Synchronized
        open fun reload() {
            mCard = null
        }

        override fun hashCode(): Int {
            return java.lang.Long.valueOf(this.id).hashCode()
        }

        /** The cloned version represents the same card but data are not loaded.  */
        public override fun clone(): Cache {
            return Cache(col, this.id)
        }

        override fun equals(other: Any?): Boolean {
            return if (other !is Cache) {
                false
            } else {
                this.id == other.id
            }
        }

        fun loadQA(reload: Boolean, browser: Boolean) {
            card.render_output(reload, browser)
        }
    }

    companion object {
        const val TYPE_REV = 2

        // A list of class members to skip in the toString() representation
        val SKIP_PRINT: Set<String> = HashSet(
            listOf(
                "SKIP_PRINT", "\$assertionsDisabled", "TYPE_LRN",
                "TYPE_NEW", "TYPE_REV", "mNote", "mQA", "mCol", "mTimerStarted", "mTimerStopped"
            )
        )

        fun intToFlag(flags: Int): Int {
            // setting all bits to 0, except the three first one.
            // equivalent to `mFlags % 8`. Used this way to copy Anki.
            return flags and 7
        }

        fun setFlagInInt(flags: Int, flag: Int): Int {
            Assert.that(0 <= flag, "flag to set is negative")
            Assert.that(flag <= 7, "flag to set is greater than 7.")
            // Setting the 3 firsts bits to 0, keeping the remaining.
            val extraData = flags and 7.inv()
            // flag in 3 fist bits, same data as in mFlags everywhere else
            return extraData or flag
        }

        @Throws(CancellationException::class)
        fun deepCopyCardArray(originals: Array<Card>, cancelListener: CancelListener): Array<Card> {
            val col = CollectionHelper.instance.getCol(AnkiDroidApp.instance)!!
            val copies = mutableListOf<Card>()
            for (i in originals.indices) {
                if (cancelListener.isCancelled()) {
                    Timber.i("Cancelled during deep copy, probably memory pressure?")
                    throw CancellationException("Cancelled during deep copy")
                }

                // TODO: the performance-naive implementation loads from database instead of working in memory
                // the high performance version would implement .clone() on Card and test it well
                copies.add(Card(col, originals[i].id))
            }
            return copies.toTypedArray()
        }
    }
}
