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

import androidx.annotation.VisibleForTesting
import anki.cards.FsrsMemoryState
import com.ichi2.anki.Flag
import com.ichi2.anki.utils.ext.ifZero
import com.ichi2.libanki.Consts.CardQueue
import com.ichi2.libanki.Consts.CardType
import com.ichi2.libanki.TemplateManager.TemplateRenderContext.TemplateRenderOutput
import com.ichi2.libanki.utils.LibAnkiAlias
import com.ichi2.libanki.utils.NotInLibAnki
import com.ichi2.libanki.utils.TimeManager
import com.ichi2.utils.Assert
import net.ankiweb.rsdroid.RustCleanup
import org.json.JSONObject

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
 * - Generate new cards (see [Collection])
 * - Store the templates or the style sheet (see [NotetypeJson])
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

    /**
     * Time in MS when timer was started
     */
    var timerStarted: Long = 0L

    // Record time spent reviewing in MS in order to restore when resuming.
    @NotInLibAnki
    private var elapsedTime: Long = 0

    @set:VisibleForTesting
    var id: CardId = 0
    var nid: NoteId = 0
    var did: DeckId = 0
    var ord = 0
    var mod: Long = 0
    private var usn = 0

    @get:CardType
    @CardType
    var type = 0

    @get:CardQueue
    @CardQueue
    var queue = 0
    var due: Int = 0
    var ivl = 0
    var factor = 0

    @set:VisibleForTesting
    var reps = 0
    var lapses = 0
    var left = 0
    var oDue: Int = 0
    var oDid: DeckId = 0
    var originalPosition: Int? = null
    private var customData: String = ""
    private var flags = 0
    private var memoryState: FsrsMemoryState? = null
    private var desiredRetention: Float? = null

    var renderOutput: TemplateRenderOutput? = null
    var note: Note? = null

    constructor(card: anki.cards.Card) {
        loadFromBackendCard(card)
    }

    constructor(col: Collection, id: Long? = null) {
        if (id != null) {
            this.id = id
            load(col)
        } else {
            loadFromBackendCard(anki.cards.Card.getDefaultInstance())
        }
    }

    @LibAnkiAlias("load")
    fun load(col: Collection) {
        val card = col.backend.getCard(id)
        loadFromBackendCard(card)
    }

    @LibAnkiAlias("_load_from_backend_card")
    private fun loadFromBackendCard(card: anki.cards.Card) {
        renderOutput = null
        note = null
        id = card.id
        nid = card.noteId
        did = card.deckId
        ord = card.templateIdx
        mod = card.mtimeSecs
        usn = card.usn
        type = card.ctype
        queue = card.queue
        due = card.due
        ivl = card.interval
        factor = card.easeFactor
        reps = card.reps
        lapses = card.lapses
        left = card.remainingSteps
        oDue = card.originalDue
        oDid = card.originalDeckId
        flags = card.flags
        originalPosition = if (card.hasOriginalPosition()) card.originalPosition else null
        customData = card.customData
        memoryState = if (card.hasMemoryState()) card.memoryState else null
        desiredRetention = if (card.hasDesiredRetention()) card.desiredRetention else null
    }

    @LibAnkiAlias("_to_backend_card")
    fun toBackendCard(): anki.cards.Card {
        val builder = anki.cards.Card.newBuilder()
            .setId(id)
            .setNoteId(nid)
            .setDeckId(did)
            .setTemplateIdx(ord)
            .setCtype(type)
            .setQueue(queue)
            .setDue(due)
            .setInterval(ivl)
            .setEaseFactor(factor)
            .setReps(reps)
            .setLapses(lapses)
            .setRemainingSteps(left)
            .setOriginalDue(oDue)
            .setOriginalDeckId(oDid)
            .setFlags(flags)
            .setCustomData(customData)
        originalPosition?.let { builder.setOriginalPosition(it) }
        memoryState?.let { builder.setMemoryState(it) }
        desiredRetention?.let { builder.setDesiredRetention(it) }
        return builder.build()
    }

    @LibAnkiAlias("question")
    fun question(col: Collection, reload: Boolean = false, browser: Boolean = false): String {
        return renderOutput(col, reload, browser).questionAndStyle()
    }

    @LibAnkiAlias("answer")
    fun answer(col: Collection): String {
        return renderOutput(col).answerAndStyle()
    }

    @LibAnkiAlias("question_av_tags")
    fun questionAvTags(col: Collection): List<AvTag> {
        return renderOutput(col).questionAvTags
    }

    @LibAnkiAlias("answer_av_tags")
    fun answerAvTags(col: Collection): List<AvTag> {
        return renderOutput(col).answerAvTags
    }

    /**
     * @throws net.ankiweb.rsdroid.exceptions.BackendInvalidInputException: If the card does not exist
     */
    @LibAnkiAlias("render_output")
    open fun renderOutput(col: Collection, reload: Boolean = false, browser: Boolean = false): TemplateRenderOutput {
        if (renderOutput == null || reload) {
            renderOutput = TemplateManager.TemplateRenderContext.fromExistingCard(col, this, browser).render(col)
        }
        return renderOutput!!
    }

    @LibAnkiAlias("note")
    open fun note(col: Collection, reload: Boolean = false): Note {
        if (note == null || reload) {
            note = col.getNote(nid)
        }
        return note!!
    }

    @LibAnkiAlias("note_type")
    open fun noteType(col: Collection): NotetypeJson {
        return note(col).notetype
    }

    @LibAnkiAlias("template")
    fun template(col: Collection): JSONObject {
        val m = noteType(col)
        return if (m.isStd) {
            m.getJSONArray("tmpls").getJSONObject(ord)
        } else {
            noteType(col).getJSONArray("tmpls").getJSONObject(0)
        }
    }

    @LibAnkiAlias("start_timer")
    fun startTimer() {
        timerStarted = TimeManager.time.intTimeMS()
    }

    @LibAnkiAlias("current_deck_id")
    fun currentDeckId(): anki.decks.DeckId {
        return anki.decks.DeckId.newBuilder()
            .setDid(oDid.ifZero { did })
            .build()
    }

    /**
     * Time limit for answering in milliseconds.
     */
    @LibAnkiAlias("time_limit")
    fun timeLimit(col: Collection): Int {
        val conf = col.decks.configDictForDeckId(currentDeckId().did)
        return conf.getInt("maxTaken") * 1000
    }

    /*
     * Time taken to answer card, in integer MS.
     */
    @LibAnkiAlias("time_taken")
    fun timeTaken(col: Collection): Int {
        // Indeed an int. Difference between two big numbers is still small.
        val total = (TimeManager.time.intTimeMS() - timerStarted).toInt()
        return kotlin.math.min(total, timeLimit(col))
    }

    /**
     * Save the currently elapsed reviewing time so it can be restored on resume.
     *
     * Use this method whenever a review session (activity) has been paused. Use the resumeTimer()
     * method when the session resumes to start counting review time again.
     */
    @NotInLibAnki
    fun stopTimer() {
        elapsedTime = TimeManager.time.intTimeMS() - timerStarted
    }

    /**
     * Resume the timer that counts the time spent reviewing this card.
     *
     * Unlike the desktop client, AnkiDroid must pause and resume the process in the middle of
     * reviewing. This method is required to keep track of the actual amount of time spent in
     * the reviewer and *must* be called on resume before any calls to timeTaken(col) take place
     * or the result of timeTaken(col) will be wrong.
     */
    @NotInLibAnki
    fun resumeTimer() {
        timerStarted = TimeManager.time.intTimeMS() - elapsedTime
    }

    @LibAnkiAlias("should_show_timer")
    fun shouldShowTimer(col: Collection): Boolean {
        val options = col.decks.configDictForDeckId(currentDeckId().did)
        return DeckConfig.parseTimerOpt(options, true)
    }

    @LibAnkiAlias("replay_question_audio_on_answer_side")
    fun replayQuestionAudioOnAnswerSide(col: Collection): Boolean {
        val conf = col.decks.configDictForDeckId(currentDeckId().did)
        return conf.optBoolean("replayq", true)
    }

    @LibAnkiAlias("autoplay")
    fun autoplay(col: Collection): Boolean {
        return col.decks.configDictForDeckId(currentDeckId().did).getBoolean("autoplay")
    }

    @NotInLibAnki
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

    // upstream's function returns an int between 0 and 7 (included).
    // We return an enum entry for the sake of improving the typing.
    @LibAnkiAlias("user_flag")
    fun userFlag() = Flag.fromCode(flags and 0b111)

    /**
     * Set [flags] to [flag].
     * Should only be used for testing.
     * Use [setUserFlag] instead.
     */
    @VisibleForTesting
    fun setFlag(flag: Int) {
        flags = flag
    }

    /**
     * Set the first three bits of [flags] to [flag]. Don't change the other ones.
     */
    // Upstream's function take an int and raise if it's not between 0 and 7 included.
    // We take a flag for the sake of typing clarity.
    @RustCleanup("deprecated in Anki: use col.set_user_flag_for_cards() instead")
    @LibAnkiAlias("set_user_flag")
    fun setUserFlag(flag: Flag) {
        flags = setFlagInInt(flags, flag.code)
    }

    @NotInLibAnki
    val isInDynamicDeck: Boolean
        get() = // In Anki Desktop, a card with oDue <> 0 && oDid == 0 is not marked as dynamic.
            oDid != 0L

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
    @NotInLibAnki
    open class Cache : Cloneable {
        val col: Collection
        val id: CardId
        private var _card: Card? = null

        constructor(col: Collection, id: CardId) {
            this.col = col
            this.id = id
        }

        /** Copy of cache. Useful to create a copy of a subclass without loosing card if it is loaded.  */
        protected constructor(cache: Cache) {
            col = cache.col
            this.id = cache.id
            _card = cache._card
        }

        /**
         * The card with id given at creation. Note that it has content of the time at which the card was loaded, which
         * may have changed in database. So it is not equivalent to getCol().getCard(getId()). If you need fresh data, reload
         * first. */
        @get:Synchronized
        val card: Card
            get() {
                if (_card == null) {
                    _card = col.getCard(this.id)
                }
                return _card!!
            }

        /** Next access to card will reload the card from the database.  */
        @Synchronized
        open fun reload() {
            _card = null
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

        /**
         * Returns [flags] with the 3 first bits set as in [flag]
         */
        fun setFlagInInt(flags: Int, flag: Int): Int {
            Assert.that(0 <= flag, "flag to set is negative")
            Assert.that(flag <= 7, "flag to set is greater than 7.")
            // Setting the 3 firsts bits to 0, keeping the remaining.
            val extraData = flags and 7.inv()
            // flag in 3 fist bits, same data as in mFlags everywhere else
            return extraData or flag
        }
    }
}
