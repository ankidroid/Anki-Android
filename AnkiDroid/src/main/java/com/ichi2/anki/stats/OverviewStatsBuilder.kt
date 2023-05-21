/****************************************************************************************
 * Copyright (c) 2014 Michael Goldbach <michael@m-goldbach.net>                         *
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
package com.ichi2.anki.stats

import android.R
import android.webkit.WebView
import com.ichi2.anki.R.string.stats_added
import com.ichi2.anki.R.string.stats_answer_buttons
import com.ichi2.anki.R.string.stats_forecast
import com.ichi2.anki.R.string.stats_overview_answer_buttons_learn
import com.ichi2.anki.R.string.stats_overview_answer_buttons_mature
import com.ichi2.anki.R.string.stats_overview_answer_buttons_young
import com.ichi2.anki.R.string.stats_overview_average_answer_time
import com.ichi2.anki.R.string.stats_overview_average_interval
import com.ichi2.anki.R.string.stats_overview_card_types_average_ease
import com.ichi2.anki.R.string.stats_overview_card_types_highest_ease
import com.ichi2.anki.R.string.stats_overview_card_types_lowest_ease
import com.ichi2.anki.R.string.stats_overview_card_types_total_cards
import com.ichi2.anki.R.string.stats_overview_card_types_total_notes
import com.ichi2.anki.R.string.stats_overview_days_studied
import com.ichi2.anki.R.string.stats_overview_forecast_average
import com.ichi2.anki.R.string.stats_overview_forecast_due_tomorrow
import com.ichi2.anki.R.string.stats_overview_forecast_total
import com.ichi2.anki.R.string.stats_overview_longest_interval
import com.ichi2.anki.R.string.stats_overview_new_cards_per_day
import com.ichi2.anki.R.string.stats_overview_reviews_per_day_all
import com.ichi2.anki.R.string.stats_overview_reviews_per_day_studydays
import com.ichi2.anki.R.string.stats_overview_time_per_day_all
import com.ichi2.anki.R.string.stats_overview_time_per_day_studydays
import com.ichi2.anki.R.string.stats_overview_total_new_cards
import com.ichi2.anki.R.string.stats_overview_total_time_in_period
import com.ichi2.anki.R.string.stats_review_count
import com.ichi2.anki.R.string.stats_review_intervals
import com.ichi2.anki.R.string.stats_review_time
import com.ichi2.anki.R.string.stats_today
import com.ichi2.anki.R.string.stats_today_again_count
import com.ichi2.anki.R.string.stats_today_correct_count
import com.ichi2.anki.R.string.stats_today_mature_cards
import com.ichi2.anki.R.string.stats_today_no_mature_cards
import com.ichi2.anki.R.string.stats_today_type_breakdown
import com.ichi2.anki.R.string.title_activity_template_editor
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Consts
import com.ichi2.libanki.DeckId
import com.ichi2.libanki.Utils
import com.ichi2.libanki.stats.Stats
import com.ichi2.libanki.stats.Stats.AxisType
import com.ichi2.themes.Themes.getColorFromAttr
import com.ichi2.utils.toRGBHex
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * @param webView for resources access
 */
class OverviewStatsBuilder(private val webView: WebView, private val col: Collection, private val deckId: DeckId, private val type: AxisType) {
    class OverviewStats {
        var forecastTotalReviews = 0
        var forecastAverageReviews = 0.0
        var forecastDueTomorrow = 0
        var reviewsPerDayOnAll = 0.0
        var reviewsPerDayOnStudyDays = 0.0
        var allDays = 0
        var daysStudied = 0
        var timePerDayOnAll = 0.0
        var timePerDayOnStudyDays = 0.0
        var totalTime = 0.0
        var totalReviews = 0
        var newCardsPerDay = 0.0
        var totalNewCards = 0
        var averageInterval = 0.0
        var longestInterval = 0.0
        lateinit var newCardsOverview: AnswerButtonsOverview
        lateinit var youngCardsOverview: AnswerButtonsOverview
        lateinit var matureCardsOverview: AnswerButtonsOverview

        var totalCards: Long = 0
        var totalNotes: Long = 0
        var lowestEase = 0.0
        var averageEase = 0.0
        var highestEase = 0.0

        class AnswerButtonsOverview {
            var total = 0
            var correct = 0
            val percentage: Double
                get() = if (correct == 0) {
                    0.0
                } else {
                    correct.toDouble() / total.toDouble() * 100.0
                }
        }
    }

    fun createInfoHtmlString(): String {
        val textColor = getColorFromAttr(webView.context, R.attr.textColor).toRGBHex()
        val css = """
               <style>
               h1, h3 { margin-bottom: 0; margin-top: 1em; text-transform: capitalize; }
               .pielabel { text-align:center; padding:0px; color:white; }
               body {color:$textColor;}
               </style>
        """.trimIndent()
        val stringBuilder = StringBuilder()
        stringBuilder.append("<center>")
        stringBuilder.append(css)
        appendTodaysStats(stringBuilder)
        appendOverViewStats(stringBuilder)
        stringBuilder.append("</center>")
        return stringBuilder.toString()
    }

    private fun appendOverViewStats(stringBuilder: StringBuilder) {
        val stats = Stats(col, deckId)
        val oStats = OverviewStats()
        stats.calculateOverviewStatistics(type, oStats)
        val res = webView.resources
        stringBuilder.append(_title(res.getString(type.descriptionId)))
        val allDaysStudied = oStats.daysStudied == oStats.allDays
        val daysStudied = res.getString(
            stats_overview_days_studied,
            (oStats.daysStudied.toFloat() / oStats.allDays.toFloat() * 100).toInt(),
            oStats.daysStudied,
            oStats.allDays
        )

        // FORECAST
        // Fill in the forecast summaries first
        calculateForecastOverview(type, oStats)
        val l = Locale.getDefault()
        stringBuilder.append(_subtitle(res.getString(stats_forecast).uppercase(l)))
        stringBuilder.append(res.getString(stats_overview_forecast_total, oStats.forecastTotalReviews))
        stringBuilder.append("<br>")
        stringBuilder.append(res.getString(stats_overview_forecast_average, oStats.forecastAverageReviews))
        stringBuilder.append("<br>")
        stringBuilder.append(res.getString(stats_overview_forecast_due_tomorrow, oStats.forecastDueTomorrow))
        stringBuilder.append("<br>")

        // REVIEW COUNT
        stringBuilder.append(_subtitle(res.getString(stats_review_count).uppercase(l)))
        stringBuilder.append(daysStudied)
        stringBuilder.append("<br>")
        stringBuilder.append(res.getString(stats_overview_forecast_total, oStats.totalReviews))
        stringBuilder.append("<br>")
        stringBuilder.append(res.getString(stats_overview_reviews_per_day_studydays, oStats.reviewsPerDayOnStudyDays))
        if (!allDaysStudied) {
            stringBuilder.append("<br>")
            stringBuilder.append(res.getString(stats_overview_reviews_per_day_all, oStats.reviewsPerDayOnAll))
        }
        stringBuilder.append("<br>")

        // TODO: AnkiDroid uses 30 days on 2020-06-09, whereas Anki Desktop used 31

        // REVIEW TIME
        stringBuilder.append(_subtitle(res.getString(stats_review_time).uppercase(l)))
        stringBuilder.append(daysStudied)
        stringBuilder.append("<br>")
        // TODO: Anki Desktop allows changing to hours / days here.
        stringBuilder.append(res.getString(stats_overview_total_time_in_period, oStats.totalTime.roundToInt()))
        stringBuilder.append("<br>")
        stringBuilder.append(res.getString(stats_overview_time_per_day_studydays, oStats.timePerDayOnStudyDays))
        if (!allDaysStudied) {
            stringBuilder.append("<br>")
            stringBuilder.append(res.getString(stats_overview_time_per_day_all, oStats.timePerDayOnAll))
        }
        val cardsPerMinute: Double = if (oStats.totalTime == 0.0) 0.0 else oStats.totalReviews.toDouble() / oStats.totalTime
        val averageAnswerTime: Double = if (oStats.totalReviews == 0) 0.0 else oStats.totalTime * 60 / oStats.totalReviews.toDouble()
        stringBuilder.append("<br>")
        stringBuilder.append(res.getString(stats_overview_average_answer_time, averageAnswerTime, cardsPerMinute))
        stringBuilder.append("<br>")

        // ADDED
        stringBuilder.append(_subtitle(res.getString(stats_added).uppercase(l)))
        stringBuilder.append(res.getString(stats_overview_total_new_cards, oStats.totalNewCards))
        stringBuilder.append("<br>")
        stringBuilder.append(res.getString(stats_overview_new_cards_per_day, oStats.newCardsPerDay))
        stringBuilder.append("<br>")

        // INTERVALS
        stringBuilder.append(_subtitle(res.getString(stats_review_intervals).uppercase(l)))
        stringBuilder.append(res.getString(stats_overview_average_interval))
        stringBuilder.append(Utils.roundedTimeSpan(webView.context, (oStats.averageInterval * Stats.SECONDS_PER_DAY).roundToLong()))
        stringBuilder.append("<br>")
        stringBuilder.append(res.getString(stats_overview_longest_interval))
        stringBuilder.append(Utils.roundedTimeSpan(webView.context, (oStats.longestInterval * Stats.SECONDS_PER_DAY).roundToLong()))

        // ANSWER BUTTONS
        stringBuilder.append(_subtitle(res.getString(stats_answer_buttons).uppercase(l)))
        stringBuilder.append(res.getString(stats_overview_answer_buttons_learn, oStats.newCardsOverview.percentage, oStats.newCardsOverview.correct, oStats.newCardsOverview.total))
        stringBuilder.append("<br>")
        stringBuilder.append(res.getString(stats_overview_answer_buttons_young, oStats.youngCardsOverview.percentage, oStats.youngCardsOverview.correct, oStats.youngCardsOverview.total))
        stringBuilder.append("<br>")
        stringBuilder.append(res.getString(stats_overview_answer_buttons_mature, oStats.matureCardsOverview.percentage, oStats.matureCardsOverview.correct, oStats.matureCardsOverview.total))

        // CARD TYPES
        stringBuilder.append(_subtitle(res.getString(title_activity_template_editor).uppercase(l)))
        stringBuilder.append(res.getString(stats_overview_card_types_total_cards, oStats.totalCards))
        stringBuilder.append("<br>")
        stringBuilder.append(res.getString(stats_overview_card_types_total_notes, oStats.totalNotes))
        stringBuilder.append("<br>")
        stringBuilder.append(res.getString(stats_overview_card_types_lowest_ease, oStats.lowestEase))
        stringBuilder.append("<br>")
        stringBuilder.append(res.getString(stats_overview_card_types_average_ease, oStats.averageEase))
        stringBuilder.append("<br>")
        stringBuilder.append(res.getString(stats_overview_card_types_highest_ease, oStats.highestEase))
    }

    private fun appendTodaysStats(stringBuilder: StringBuilder) {
        val stats = Stats(col, deckId)
        val todayStats = stats.calculateTodayStats()
        stringBuilder.append(_title(webView.resources.getString(stats_today)))
        val res = webView.resources
        val minutes = (todayStats[THETIME_INDEX] / 60.0).roundToInt()
        val span = res.getQuantityString(com.ichi2.anki.R.plurals.time_span_minutes, minutes, minutes)
        stringBuilder.append(
            res.getQuantityString(
                com.ichi2.anki.R.plurals.stats_today_cards,
                todayStats[CARDS_INDEX],
                todayStats[CARDS_INDEX],
                span
            )
        )
        stringBuilder.append("<br>")
        stringBuilder.append(res.getString(stats_today_again_count, todayStats[FAILED_INDEX]))
        if (todayStats[CARDS_INDEX] > 0) {
            stringBuilder.append(" ")
            stringBuilder.append(res.getString(stats_today_correct_count, (1 - todayStats[FAILED_INDEX] / todayStats[CARDS_INDEX].toFloat()) * 100.0))
        }
        stringBuilder.append("<br>")
        stringBuilder.append(res.getString(stats_today_type_breakdown, todayStats[LRN_INDEX], todayStats[REV_INDEX], todayStats[RELRN_INDEX], todayStats[FILT_INDEX]))
        stringBuilder.append("<br>")
        if (todayStats[MCNT_INDEX] != 0) {
            stringBuilder.append(res.getString(stats_today_mature_cards, todayStats[MSUM_INDEX], todayStats[MCNT_INDEX], todayStats[MSUM_INDEX] / todayStats[MCNT_INDEX].toFloat() * 100.0))
        } else {
            stringBuilder.append(res.getString(stats_today_no_mature_cards))
        }
    }

    private fun _title(title: String): String {
        return "<h1>$title</h1>"
    }

    private fun _subtitle(title: String): String {
        return "<h3>$title</h3>"
    }

    // This is a copy of Stats#calculateDue that is more similar to the original desktop version which
    // allows us to easily fetch the values required for the summary. In the future, this version
    // should replace the one in Stats.java.
    private fun calculateForecastOverview(type: AxisType, oStats: OverviewStats) {
        var start: Int? = null
        var end: Int? = null
        var chunk = 0
        when (type) {
            AxisType.TYPE_MONTH -> {
                start = 0
                end = 31
                chunk = 1
            }
            AxisType.TYPE_YEAR -> {
                start = 0
                end = 52
                chunk = 7
            }
            AxisType.TYPE_LIFE -> {
                start = 0
                end = null
                chunk = 30
            }
        }
        val d = _due(start, end, chunk)
        var tot = 0
        val totd: MutableList<IntArray> = ArrayList(d.size)
        for (day in d) {
            tot += day[1] + day[2]
            totd.add(intArrayOf(day[0], tot))
        }

        // Fill in the overview stats
        oStats.forecastTotalReviews = tot
        oStats.forecastAverageReviews = if (totd.isEmpty()) 0.0 else tot.toDouble() / (totd.size * chunk)
        oStats.forecastDueTomorrow = col.db.queryScalar(
            "select count() from cards where did in " + _limit() + " and queue in (" + Consts.QUEUE_TYPE_REV + "," + Consts.QUEUE_TYPE_DAY_LEARN_RELEARN + ") " +
                "and due = ?",
            col.sched.today + 1
        )
    }

    private fun _due(start: Int?, end: Int?, chunk: Int): List<IntArray> {
        var lim = ""
        if (start != null) {
            lim += String.format(Locale.US, " and due-%d >= %d", col.sched.today, start)
        }
        if (end != null) {
            lim += String.format(Locale.US, " and day < %d", end)
        }
        val d: MutableList<IntArray> = ArrayList()
        val query = "select (due-" + col.sched.today + ")/" + chunk + " as day,\n" +
            "sum(case when ivl < 21 then 1 else 0 end), -- yng\n" +
            "sum(case when ivl >= 21 then 1 else 0 end) -- mtr\n" +
            "from cards\n" +
            "where did in " + _limit() + " and queue in (" + Consts.QUEUE_TYPE_REV + "," + Consts.QUEUE_TYPE_DAY_LEARN_RELEARN + ")\n" +
            lim + "\n" +
            "group by day order by day"
        col.db.query(query).use { cur ->
            while (cur.moveToNext()) {
                d.add(intArrayOf(cur.getInt(0), cur.getInt(1), cur.getInt(2)))
            }
        }
        return d
    }

    private fun _limit(): String {
        return Stats.deckLimit(col, deckId)
    }

    companion object {
        private const val CARDS_INDEX = 0
        private const val THETIME_INDEX = 1
        private const val FAILED_INDEX = 2
        private const val LRN_INDEX = 3
        private const val REV_INDEX = 4
        private const val RELRN_INDEX = 5
        private const val FILT_INDEX = 6
        private const val MCNT_INDEX = 7
        private const val MSUM_INDEX = 8
    }
}
