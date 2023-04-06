/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.anki

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.view.Gravity
import android.view.View
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.annotation.CheckResult
import androidx.annotation.IdRes
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.compat.CompatHelper.Companion.getParcelableExtraCompat
import com.ichi2.libanki.*
import com.ichi2.libanki.Collection
import com.ichi2.libanki.stats.Stats
import com.ichi2.ui.FixedTextView
import com.ichi2.utils.LanguageUtil
import com.ichi2.utils.UiUtil.makeColored
import net.ankiweb.rsdroid.RustCleanup
import timber.log.Timber
import java.text.DateFormat
import java.util.*
import java.util.function.Function

@RustCleanup("Remove this whole activity and use the new Anki page once the new backend is the default")
class CardInfo : AnkiActivity() {
    @get:VisibleForTesting(otherwise = VisibleForTesting.NONE)
    var model: CardInfoModel? = null
        private set
    private var mCardId: CardId = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        setTitle(R.string.card_info_title)
        setContentView(R.layout.card_info)
        mCardId = getCardId(savedInstanceState)
        if (!hasValidCardId()) {
            showSnackbar(getString(R.string.multimedia_editor_something_wrong))
            finishWithoutAnimation()
            return
        }
        enableToolbar()
        startLoadingCollection()
    }

    override fun onCollectionLoaded(col: Collection) {
        super.onCollectionLoaded(col)
        val c = getCard(col)
        if (c == null) {
            showSnackbar(getString(R.string.multimedia_editor_something_wrong))
            finishWithoutAnimation()
            return
        }

        // Candidate to move to background thread - can get hundreds of rows for bad cards.
        val model = CardInfoModel.create(c, col)
        setText(R.id.card_info_added, formatDate(model.cardId))
        setIfNotNull<Long?>(model.firstReviewDate, R.id.card_info_first_review, R.id.card_info_first_review_label) { date: Long? -> formatDate(date) }
        setIfNotNull<Long?>(model.latestReviewDate, R.id.card_info_latest_review, R.id.card_info_latest_review_label) { date: Long? -> formatDate(date) }
        setIfNotNull<String?>(model.dues, R.id.card_info_due, R.id.card_info_due_label) { s: String? -> s }
        setIfNotNull<Int?>(model.interval, R.id.card_info_interval, R.id.card_info_interval_label) { _: Int? -> resources.getQuantityString(R.plurals.time_span_days, model.interval!!, model.interval) }
        setIfNotNull<Double?>(model.easeInPercent, R.id.card_info_ease, R.id.card_info_ease_label) { easePercent: Double? -> formatDouble("%.0f%%", easePercent!! * 100) }
        setFormattedText(R.id.card_info_review_count, "%d", model.reviews.toLong())
        setFormattedText(R.id.card_info_lapse_count, "%d", model.lapses.toLong())
        setIfNotNull<Long?>(model.averageTimeMs, R.id.card_info_average_time, R.id.card_info_average_time_label) { timeInMs: Long? -> formatAsTimeSpan(timeInMs) }
        setIfNotNull<Long?>(model.totalTimeMs, R.id.card_info_total_time, R.id.card_info_total_time_label) { timeInMs: Long? -> formatAsTimeSpan(timeInMs) }
        setText(R.id.card_info_card_type, model.cardType)
        setText(R.id.card_info_note_type, model.noteType)
        setText(R.id.card_info_deck_name, model.deckName)
        setFormattedText(R.id.card_info_card_id, "%d", model.cardId)
        setFormattedText(R.id.card_info_note_id, "%d", model.noteId)
        val tl = findViewById<TableLayout>(R.id.card_info_revlog_entries)
        for (entry in model.entries) {
            val row = TableRow(this)
            addWithText(row, formatDateTime(entry.dateTime)).gravity = Gravity.START
            addWithText(row, entry.spannableType(this)).gravity = Gravity.CENTER_HORIZONTAL
            addWithText(row, entry.getRating(this)).gravity = Gravity.CENTER_HORIZONTAL
            addWithText(row, Utils.timeQuantityNextIvl(this, entry.intervalAsTimeSeconds())).gravity = Gravity.START
            addWithText(row, entry.getEase(this)).gravity = Gravity.CENTER_HORIZONTAL
            addWithText(row, entry.timeTaken).gravity = Gravity.END
            tl.addView(row)
        }
        this.model = model
    }

    override fun finish() {
        val animation = intent.getParcelableExtraCompat<ActivityTransitionAnimation.Direction>(FINISH_ANIMATION_EXTRA)
        if (animation != null) {
            finishWithAnimation(animation)
        } else {
            super.finish()
        }
    }

    override fun onActionBarBackPressed(): Boolean {
        finish()
        return true
    }

    private fun addWithText(row: TableRow, value: String): FixedTextView {
        return addWithText(row, SpannableString(value))
    }

    private fun addWithText(row: TableRow, value: Spannable): FixedTextView {
        val text = FixedTextView(this)
        text.text = value
        text.textSize = 12f
        row.addView(text)
        return text
    }

    private fun formatAsTimeSpan(timeInMs: Long?): String {
        // HACK: There is probably a bug here
        // It would be nice to use Utils.timeSpan, but the Android string formatting system does not support floats.
        // https://stackoverflow.com/questions/54882981/android-plurals-for-float-values
        // Mixing both float-based time processing and plural processing seems like a recipe for disaster until we have
        // a spec, so we ignore the problem for now

        // So, we use seconds
        return getString(R.string.time_span_decimal_seconds, String.format(locale, "%.2f", timeInMs!! / 1000.0))
    }

    private fun <T> setIfNotNull(nullableData: T?, @IdRes dataRes: Int, @IdRes labelRes: Int, asString: Function<T, String?>) {
        if (nullableData == null) {
            findViewById<View>(dataRes).visibility = View.GONE
            findViewById<View>(labelRes).visibility = View.GONE
        } else {
            setText(dataRes, asString.apply(nullableData))
        }
    }

    private fun setFormattedText(@IdRes resource: Int, formatSpecifier: String, number: Long) {
        val text = formatLong(formatSpecifier, number)
        setText(resource, text)
    }

    private fun formatLong(formatSpecifier: String, number: Long): String {
        return String.format(locale, formatSpecifier, number)
    }

    private fun formatDouble(formatSpecifier: String, number: Double): String {
        return String.format(locale, formatSpecifier, number)
    }

    private val locale: Locale?
        get() = LanguageUtil.getLocaleCompat(resources)

    private fun setText(@IdRes id: Int, text: String?) {
        val view = findViewById<TextView>(id)
        view.text = text
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong("cardId", mCardId)
    }

    @SuppressLint("DirectDateInstantiation")
    private fun formatDate(date: Long?): String {
        return sDateFormat.format(Date(date!!))
    }

    @SuppressLint("DirectDateInstantiation")
    private fun formatDateTime(dateTime: Long): String {
        return sDateTimeFormat.format(Date(dateTime))
    }

    private fun getCard(col: Collection): Card? {
        return col.getCard(mCardId)
    }

    private fun hasValidCardId(): Boolean {
        return mCardId > 0
    }

    private fun getCardId(savedInstanceState: Bundle?): Long {
        return savedInstanceState?.getLong("cardId")
            ?: try {
                intent.getLongExtra("cardId", INVALID_CARD_ID)
            } catch (e: Exception) {
                Timber.w(e, "Failed to get Card Id")
                INVALID_CARD_ID
            }
    }

    class CardInfoModel(
        val cardId: CardId,
        val firstReviewDate: Long?,
        val latestReviewDate: Long?,
        val dues: String,
        val interval: Int?,
        val easeInPercent: Double?,
        val reviews: Int,
        val lapses: Int,
        val averageTimeMs: Long?,
        val totalTimeMs: Long?,
        val cardType: String?,
        val noteType: String,
        val deckName: String,
        val noteId: NoteId,
        val entries: List<RevLogEntry>
    ) {
        val due: String
            get() = dues

        // date type rating interval ease time
        class RevLogEntry {
            var dateTime: Long = 0
            var type = 0
            var rating = 0
            var ivl: Long = 0
            var factor: Long = 0
            var timeTakenMs: Long = 0
            fun spannableType(context: Context): Spannable {
                val attrs = intArrayOf(
                    R.attr.newCountColor,
                    R.attr.learnCountColor,
                    R.attr.reviewCountColor
                )
                val ta = context.obtainStyledAttributes(attrs)
                val newCountColor = ta.getColor(0, ContextCompat.getColor(context, R.color.black))
                val learnCountColor = ta.getColor(1, ContextCompat.getColor(context, R.color.black))
                val reviewCountColor = ta.getColor(2, ContextCompat.getColor(context, R.color.black))
                val filteredColor = ContextCompat.getColor(context, R.color.material_orange_A700)
                ta.recycle()
                return when (type) {
                    Consts.REVLOG_LRN -> makeColored(context.getString(R.string.card_info_revlog_learn), newCountColor)
                    Consts.REVLOG_REV -> makeColored(context.getString(R.string.card_info_revlog_review), reviewCountColor)
                    Consts.REVLOG_RELRN -> makeColored(context.getString(R.string.card_info_revlog_relearn), learnCountColor)
                    Consts.REVLOG_CRAM -> makeColored(context.getString(R.string.card_info_revlog_filtered), filteredColor)
                    else -> SpannableString(Integer.toString(type))
                }
            }

            fun getEase(context: Context): Spannable {
                return if (factor == 0L) {
                    SpannableString(context.getString(R.string.card_info_ease_not_applicable))
                } else {
                    SpannableString(java.lang.Long.toString(factor / 10))
                }
            }

            fun intervalAsTimeSeconds(): Long {
                return if (ivl < 0) {
                    -ivl
                } else {
                    ivl * Stats.SECONDS_PER_DAY
                }
            }

            // saves space if we just use seconds rather than a "s" suffix
            // return Utils.timeQuantityNextIvl(context, timeTakenMs / 1000);
            val timeTaken: String
                get() = // saves space if we just use seconds rather than a "s" suffix
                    // return Utils.timeQuantityNextIvl(context, timeTakenMs / 1000);
                    java.lang.Long.toString(timeTakenMs / 1000)

            fun getRating(context: Context): Spannable {
                val source = java.lang.Long.toString(rating.toLong())
                return if (rating == 1) {
                    val attrs = intArrayOf(R.attr.learnCountColor)
                    val ta = context.obtainStyledAttributes(attrs)
                    val failColor = ta.getColor(0, ContextCompat.getColor(context, R.color.black))
                    ta.recycle()
                    makeColored(source, failColor)
                } else {
                    SpannableString(source)
                }
            }
        }

        companion object {
            @CheckResult
            fun create(c: Card, collection: Collection): CardInfoModel {
                val addedDate = c.id
                var firstReview: Long? = collection.db.queryLongScalar("select min(id) from revlog where cid = ?", c.id)
                if (firstReview == 0L) {
                    firstReview = null
                }
                var latestReview: Long? = collection.db.queryLongScalar("select max(id) from revlog where cid = ?", c.id)
                if (latestReview == 0L) {
                    latestReview = null
                }
                var averageTime: Long? = collection.db.queryLongScalar("select avg(time) from revlog where cid = ?", c.id)
                if (averageTime == 0L) {
                    averageTime = null
                }
                var totalTime: Long? = collection.db.queryLongScalar("select sum(time) from revlog where cid = ?", c.id)
                if (totalTime == 0L) {
                    totalTime = null
                }
                var easeInPercent: Double? = c.factor / 1000.0
                val lapses = c.lapses
                val reviews = c.reps
                val model = collection.models.get(c.note().mid)
                val cardType = getCardType(c, model)
                val noteType = model!!.getString("name")
                val deckName = collection.decks.get(c.did).getString("name")
                val noteId = c.nid
                var interval: Int? = c.ivl
                if (interval!! <= 0) {
                    interval = null
                }
                if (c.type < Consts.CARD_TYPE_REV) {
                    easeInPercent = null
                }
                val due = c.dueString
                val entries: MutableList<RevLogEntry> = ArrayList(collection.db.queryScalar("select count() from revlog where cid = ?", c.id))
                collection.db.query(
                    "select " +
                        "id as dateTime, " +
                        "ease as rating, " +
                        "ivl, " +
                        "factor as ease, " +
                        "time, " +
                        "type " +
                        "from revlog where cid = ?" +
                        "order by id desc",
                    c.id
                ).use { cur ->
                    while (cur.moveToNext()) {
                        val e = RevLogEntry()
                        e.dateTime = cur.getLong(0)
                        e.rating = cur.getInt(1)
                        e.ivl = cur.getLong(2)
                        e.factor = cur.getLong(3)
                        e.timeTakenMs = cur.getLong(4)
                        e.type = cur.getInt(5)
                        entries.add(e)
                    }
                }
                return CardInfoModel(addedDate, firstReview, latestReview, due, interval, easeInPercent, reviews, lapses, averageTime, totalTime, cardType, noteType, deckName, noteId, entries)
            }

            protected fun getCardType(c: Card, model: Model?): String? {
                return try {
                    val ord = if (c.model().isCloze) {
                        0
                    } else {
                        c.ord
                    }
                    model!!.getJSONArray("tmpls").getJSONObject(ord).getString("name")
                } catch (e: Exception) {
                    Timber.w(e)
                    null
                }
            }
        }
    }

    companion object {
        private const val INVALID_CARD_ID: Long = -1
        private val sDateFormat = DateFormat.getDateInstance()
        private val sDateTimeFormat = DateFormat.getDateTimeInstance()
    }
}
