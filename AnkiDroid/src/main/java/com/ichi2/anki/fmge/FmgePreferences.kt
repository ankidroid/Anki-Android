package com.ichi2.anki.fmge

import android.content.SharedPreferences
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object FmgePreferences {
    const val EXAM_DATE_KEY = "fmge_exam_date"
    const val DAILY_NEW_CARD_GOAL_KEY = "fmge_daily_new_card_goal"
    const val ENABLE_DASHBOARD_KEY = "fmge_enable_dashboard"
    const val ENABLE_COUNTDOWN_KEY = "fmge_enable_countdown"
    const val ENABLE_HIGH_YIELD_KEY = "fmge_enable_high_yield"
    const val ENABLE_WEAK_CARDS_KEY = "fmge_enable_weak_cards"

    val defaultExamDate: LocalDate = LocalDate.of(2027, 1, 9)

    fun examDate(preferences: SharedPreferences): LocalDate =
        runCatching { LocalDate.parse(preferences.getString(EXAM_DATE_KEY, null) ?: defaultExamDate.toString()) }
            .getOrDefault(defaultExamDate)

    fun daysUntilExam(preferences: SharedPreferences, today: LocalDate = LocalDate.now()): Long =
        ChronoUnit.DAYS.between(today, examDate(preferences)).coerceAtLeast(0)
}
