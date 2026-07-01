/*
 * Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>
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
package com.ichi2.anki.common.analytics

import androidx.annotation.VisibleForTesting

/**
 * Interface for analytics tracking.
 *
 * Implemented in the app module (e.g. by `AnkiDroidUsageAnalytics`).
 * Feature modules should use [Analytics] object to send events.
 */
interface UsageAnalytics {
    /**
     * Send a detailed arbitrary analytics event, with noun/verb pairs and extra data if needed.
     *
     * @param category the category of event, make your own but use a constant so reporting is good
     * @param action   the action the user performed
     * @param value    a value for the event
     * @param label    a label for the event
     */
    fun sendAnalyticsEvent(
        category: String,
        action: String,
        value: Int? = null,
        label: String? = null,
    )

    /**
     * Submit a screen for aggregation / analysis.
     * Intended for use to determine if / how features are being used.
     *
     * @param screen the result of [Class.getSimpleName] will be used as the screen tag
     */
    fun sendAnalyticsScreenView(screen: Any) {
        sendAnalyticsScreenView(screen.javaClass.simpleName)
    }

    /**
     * Submit a screen display with a synthetic name for aggregation / analysis.
     * Intended for use if your class handles multiple screens you want to track separately.
     *
     * @param screenName the name to show in analysis reports
     */
    fun sendAnalyticsScreenView(screenName: String)
}

/**
 * Global accessor for analytics. Delegates to the [UsageAnalytics] implementation
 * set during app initialization.
 *
 * Usage:
 * ```
 * Analytics.sendAnalyticsEvent("Widget", "enabled")
 * Analytics.sendAnalyticsScreenView("DeckPicker")
 * ```
 */
object Analytics {
    lateinit var instance: UsageAnalytics
        private set

    fun setAnalytics(analytics: UsageAnalytics) {
        instance = analytics
    }

    @VisibleForTesting
    fun getAnalytics(): UsageAnalytics = instance

    fun sendAnalyticsEvent(
        category: String,
        action: String,
        value: Int? = null,
        label: String? = null,
    ) = instance.sendAnalyticsEvent(category, action, value, label)

    fun sendAnalyticsScreenView(screen: Any) = instance.sendAnalyticsScreenView(screen)

    fun sendAnalyticsScreenView(screenName: String) = instance.sendAnalyticsScreenView(screenName)
}
