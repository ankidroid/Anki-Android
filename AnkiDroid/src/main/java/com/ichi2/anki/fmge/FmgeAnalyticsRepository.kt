package com.ichi2.anki.fmge

import android.content.SharedPreferences
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.Consts.QueueType
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.ceil

object FmgeAnalyticsRepository {
    const val HIGH_YIELD_TAG = "HighYield"
    const val WEAK_CARDS_FILTER_NAME = "Weak Cards"

    fun dashboard(
        col: Collection,
        preferences: SharedPreferences,
        today: LocalDate = LocalDate.now(),
    ): FmgeDashboardData {
        val dayStartMs = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val reviewsToday = col.db.queryScalar("SELECT count() FROM revlog WHERE id >= ?", dayStartMs)
        val newToday = col.db.queryScalar("SELECT count() FROM revlog WHERE id >= ? AND type = 0", dayStartMs)
        val studyTimeMs = col.db.queryLongScalar("SELECT coalesce(sum(time), 0) FROM revlog WHERE id >= ?", dayStartMs)
        val remainingNew = col.db.queryScalar("SELECT count() FROM cards WHERE queue = ?", QueueType.New.code)
        val remainingReview = col.db.queryScalar(
            "SELECT count() FROM cards WHERE queue IN (?, ?, ?) AND due <= ?",
            QueueType.Lrn.code,
            QueueType.Rev.code,
            QueueType.DayLearnRelearn.code,
            col.sched.today,
        )
        val retention = retentionPercent(col, dayStartMs)
        val daysUntilExam = FmgePreferences.daysUntilExam(preferences, today)
        val dailyGoal = preferences.getString(FmgePreferences.DAILY_NEW_CARD_GOAL_KEY, "100")?.toIntOrNull()?.coerceAtLeast(1) ?: 100
        val totalRemaining = remainingNew + remainingReview
        val completionDate = today.plusDays(ceil(totalRemaining.toDouble() / dailyGoal.toDouble()).toLong())
        return FmgeDashboardData(
            reviewsToday = reviewsToday,
            newToday = newToday,
            studyTimeMinutes = (studyTimeMs / 60000).toInt(),
            remainingCards = totalRemaining,
            retentionPercent = retention,
            daysUntilExam = daysUntilExam,
            estimatedCompletionDate = completionDate,
            dailyGoal = dailyGoal,
            progressPercent = ((reviewsToday + newToday) * 100 / dailyGoal).coerceIn(0, 100),
            highYieldCount = highYieldCount(col),
            weakCardsCount = weakCardsCount(col),
        )
    }

    fun subjectAnalytics(col: Collection): List<FmgeSubjectAnalytics> =
        col.decks.allNamesAndIds().map { deck ->
            val did = deck.id
            val learning = col.db.queryScalar("SELECT count() FROM cards WHERE did = ? AND queue IN (?, ?)", did, QueueType.Lrn.code, QueueType.DayLearnRelearn.code)
            val review = col.db.queryScalar("SELECT count() FROM cards WHERE did = ? AND queue = ?", did, QueueType.Rev.code)
            val mature = col.db.queryScalar("SELECT count() FROM cards WHERE did = ? AND queue = ? AND ivl >= 21", did, QueueType.Rev.code)
            val total = col.db.queryScalar("SELECT count() FROM cards WHERE did = ?", did)
            val todayReviews = col.db.queryScalar(
                "SELECT count() FROM revlog WHERE cid IN (SELECT id FROM cards WHERE did = ?) AND id >= ?",
                did,
                LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            )
            val avgReviewTimeMs = col.db.queryLongScalar("SELECT coalesce(avg(time), 0) FROM revlog WHERE cid IN (SELECT id FROM cards WHERE did = ?)", did)
            FmgeSubjectAnalytics(
                deckName = deck.name,
                learning = learning,
                review = review,
                mature = mature,
                retentionPercent = retentionPercent(col, 0, did),
                totalCards = total,
                averageReviewSeconds = (avgReviewTimeMs / 1000).toInt(),
                todayReviews = todayReviews,
            )
        }.sortedBy { it.deckName.lowercase(Locale.ROOT) }

    fun weakCardsCount(col: Collection): Int = col.db.queryScalar("SELECT count() FROM cards WHERE lapses >= 3")

    fun highYieldCount(col: Collection): Int = col.findCards("tag:$HIGH_YIELD_TAG").size

    fun toggleHighYield(
        col: Collection,
        cardId: Long,
    ): Boolean {
        val note = col.getCard(cardId).note(col)
        val enabled = !note.hasTag(col, HIGH_YIELD_TAG)
        if (enabled) {
            note.addTag(HIGH_YIELD_TAG)
        } else {
            note.removeTag(HIGH_YIELD_TAG)
        }
        col.updateNote(note)
        return enabled
    }

    private fun retentionPercent(
        col: Collection,
        sinceMs: Long,
        did: Long? = null,
    ): Int {
        val deckClause = did?.let { " AND cid IN (SELECT id FROM cards WHERE did = ?)" }.orEmpty()
        val args = if (did == null) arrayOf<Any>(sinceMs) else arrayOf<Any>(sinceMs, did)
        val total = col.db.queryScalar("SELECT count() FROM revlog WHERE id >= ?$deckClause", *args)
        if (total == 0) return 0
        val correct = col.db.queryScalar("SELECT count() FROM revlog WHERE id >= ? AND ease > 1$deckClause", *args)
        return (correct * 100 / total).coerceIn(0, 100)
    }
}

data class FmgeDashboardData(
    val reviewsToday: Int,
    val newToday: Int,
    val studyTimeMinutes: Int,
    val remainingCards: Int,
    val retentionPercent: Int,
    val daysUntilExam: Long,
    val estimatedCompletionDate: LocalDate,
    val dailyGoal: Int,
    val progressPercent: Int,
    val highYieldCount: Int,
    val weakCardsCount: Int,
)

data class FmgeSubjectAnalytics(
    val deckName: String,
    val learning: Int,
    val review: Int,
    val mature: Int,
    val retentionPercent: Int,
    val totalCards: Int,
    val averageReviewSeconds: Int,
    val todayReviews: Int,
)
