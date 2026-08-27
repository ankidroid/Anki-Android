/*
 * Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>
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

package com.ichi2.anki.analytics

/**
 * A centralized repository for all constant values used in analytics tracking.
 */
object AnalyticsConstants {
    object Category {
        const val LINK_CLICKED = "LinkClicked"
        const val SETTING = "Setting"
        const val ACRA_CRASH_HANDLER = "ACRA Crash Handler"
        const val WIDGET = "Widget"
    }

    /**
     * These Strings must not be changed as they are used for analytic comparisons between AnkiDroid versions.
     * If a new string is added here then the respective changes must also be made in AnalyticsConstantsTest.kt
     * All the constant strings added here must be annotated with @AnalyticsConstant.
     */
    object Actions {
        // Analytics actions used in Help Dialog
        @AnalyticsConstant
        val OPENED_HELP_DIALOG = "Opened HelpDialogBox"

        @AnalyticsConstant
        val OPENED_USING_ANKIDROID = "Opened Using AnkiDroid"

        @AnalyticsConstant
        val OPENED_GET_HELP = "Opened Get Help"

        @AnalyticsConstant
        val OPENED_SUPPORT_ANKIDROID = "Opened Support AnkiDroid"

        @AnalyticsConstant
        val OPENED_COMMUNITY = "Opened Community"

        @AnalyticsConstant
        val OPENED_PRIVACY = "Opened Privacy"

        @AnalyticsConstant
        val OPENED_ANKIWEB_TERMS_AND_CONDITIONS = "Opened AnkiWeb Terms and Conditions"

        @AnalyticsConstant
        val OPENED_ANKIDROID_PRIVACY_POLICY = "Opened AnkiDroid Privacy Policy"

        @AnalyticsConstant
        val OPENED_ANKIWEB_PRIVACY_POLICY = "Opened AnkiWeb Privacy Policy"

        @AnalyticsConstant
        val OPENED_ANKIDROID_MANUAL = "Opened AnkiDroid Manual"

        @AnalyticsConstant
        val OPENED_ANKI_MANUAL = "Opened Anki Manual"

        @AnalyticsConstant
        val OPENED_ANKIDROID_FAQ = "Opened AnkiDroid FAQ"

        @AnalyticsConstant
        val OPENED_MAILING_LIST = "Opened Mailing List"

        @AnalyticsConstant
        val OPENED_REPORT_BUG = "Opened Report a Bug"

        @AnalyticsConstant
        val OPENED_DONATE = "Opened Donate"

        @AnalyticsConstant
        val OPENED_TRANSLATE = "Opened Translate"

        @AnalyticsConstant
        val OPENED_DEVELOP = "Opened Develop"

        @AnalyticsConstant
        val OPENED_RATE = "Opened Rate"

        @AnalyticsConstant
        val OPENED_OTHER = "Opened Other"

        @AnalyticsConstant
        val OPENED_SEND_FEEDBACK = "Opened Send Feedback"

        @AnalyticsConstant
        val OPENED_ANKI_FORUMS = "Opened Anki Forums"

        @AnalyticsConstant
        val OPENED_REDDIT = "Opened Reddit"

        @AnalyticsConstant
        val OPENED_DISCORD = "Opened Discord"

        @AnalyticsConstant
        val OPENED_FACEBOOK = "Opened Facebook"

        @AnalyticsConstant
        val OPENED_TWITTER = "Opened Twitter"

        @AnalyticsConstant
        val EXCEPTION_REPORT = "Exception Report"

        @AnalyticsConstant
        val IMPORT_APKG_FILE = "Import APKG"

        @AnalyticsConstant
        val IMPORT_COLPKG_FILE = "Import COLPKG"

        @AnalyticsConstant
        val IMPORT_CSV_FILE = "Import CSV"

        @AnalyticsConstant
        val TAPPED_SETTING = "Tapped setting"

        @AnalyticsConstant
        val CHANGED_SETTING = "Changed setting"

        @AnalyticsConstant
        val CRASH_REPORTED = "Crash reported"

        // Analytics actions used by home screen widgets
        @AnalyticsConstant
        val WIDGET_ENABLED = "enabled"

        @AnalyticsConstant
        val WIDGET_DISABLED = "disabled"
    }
}
