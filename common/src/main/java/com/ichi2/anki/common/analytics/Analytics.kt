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

interface Analytics {
    /**
     * Submit a screen display for aggregation / analysis.
     * @param screenName The name of the screen to track.
     */
    fun sendAnalyticsScreenView(screenName: String)

    /**
     * Send an arbitrary analytics event.
     * @param category The category of the event.
     * @param action The action performed.
     * @param value Optional numeric value for the event.
     * @param label Optional label for the event.
     */
    fun sendAnalyticsEvent(
        category: String,
        action: String,
        value: Int? = null,
        label: String? = null,
    )

    /**
     * Send an exception for aggregation / analysis.
     * @param description A description of the exception.
     * @param fatal Whether the exception was fatal.
     */
    fun sendAnalyticsException(
        description: String,
        fatal: Boolean,
    )
}

object AnalyticsService {
    lateinit var instance: Analytics
        private set

    fun setAnalytics(analytics: Analytics) {
        instance = analytics
    }
}
