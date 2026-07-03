package com.ichi2.anki.fmge

import android.os.Bundle
import android.widget.TextView
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.common.preferences.sharedPrefs
import java.time.format.DateTimeFormatter

class FmgeDashboardActivity : AnkiActivity(R.layout.activity_fmge_dashboard) {
    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        enableToolbar().apply { title = getString(R.string.fmge_dashboard_title) }
        refreshDashboard()
    }

    override fun onResume() {
        super.onResume()
        refreshDashboard()
    }

    private fun refreshDashboard() {
        launchCatchingTask {
            val data = withCol { FmgeAnalyticsRepository.dashboard(this, this@FmgeDashboardActivity.sharedPrefs()) }
            val subjects = withCol { FmgeAnalyticsRepository.subjectAnalytics(this) }
            findViewById<TextView>(R.id.fmge_today_reviews).text = getString(R.string.fmge_today_reviews_value, data.reviewsToday)
            findViewById<TextView>(R.id.fmge_today_new).text = getString(R.string.fmge_today_new_value, data.newToday)
            findViewById<TextView>(R.id.fmge_study_time).text = getString(R.string.fmge_study_time_value, data.studyTimeMinutes)
            findViewById<TextView>(R.id.fmge_remaining_cards).text = getString(R.string.fmge_remaining_cards_value, data.remainingCards)
            findViewById<TextView>(R.id.fmge_retention).text = getString(R.string.fmge_retention_value, data.retentionPercent)
            findViewById<TextView>(R.id.fmge_exam_countdown).text = getString(R.string.fmge_exam_countdown_value, data.daysUntilExam)
            findViewById<TextView>(R.id.fmge_estimated_completion).text =
                getString(R.string.fmge_estimated_completion_value, data.estimatedCompletionDate.format(DateTimeFormatter.ISO_DATE))
            findViewById<TextView>(R.id.fmge_high_yield_count).text = getString(R.string.fmge_high_yield_value, data.highYieldCount)
            findViewById<TextView>(R.id.fmge_weak_cards_count).text = getString(R.string.fmge_weak_cards_value, data.weakCardsCount)
            findViewById<TextView>(R.id.fmge_subject_analytics).text = subjects.joinToString("\n\n") { subject ->
                getString(
                    R.string.fmge_subject_analytics_value,
                    subject.deckName,
                    subject.learning,
                    subject.review,
                    subject.mature,
                    subject.retentionPercent,
                    subject.totalCards,
                    subject.averageReviewSeconds,
                    subject.todayReviews,
                )
            }
        }
    }
}
