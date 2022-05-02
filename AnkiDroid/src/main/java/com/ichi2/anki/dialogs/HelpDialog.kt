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
package com.ichi2.anki.dialogs

import android.content.Context
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.dialogs.RecursiveMenuDialog.Companion.createInstance
import com.ichi2.anki.dialogs.RecursiveMenuItemAction.*
import com.ichi2.utils.IntentUtil.canOpenIntent

object HelpDialog {

    @JvmStatic
    fun createInstance(): RecursiveMenuDialog {
        UsageAnalytics.sendAnalyticsEvent(UsageAnalytics.Category.LINK_CLICKED, UsageAnalytics.Actions.OPENED_HELPDIALOG)
        val menuItems = arrayOf(
            // how to use AnkiDroid
            RecursiveMenuItem(R.string.help_title_using_ankidroid, R.drawable.ic_manual_black_24dp, UsageAnalytics.Actions.OPENED_USING_ANKIDROID, 1, null, true, Header),
            RecursiveMenuItem(R.string.help_item_ankidroid_manual, R.drawable.ic_manual_black_24dp, UsageAnalytics.Actions.OPENED_ANKIDROID_MANUAL, 100, 1, false, OpenUrl(AnkiDroidApp.getManualUrl())),
            RecursiveMenuItem(R.string.help_item_anki_manual, R.drawable.ic_manual_black_24dp, UsageAnalytics.Actions.OPENED_ANKI_MANUAL, 101, 1, false, OpenUrlResource(R.string.link_anki_manual)),
            RecursiveMenuItem(R.string.help_item_ankidroid_faq, R.drawable.ic_help_black_24dp, UsageAnalytics.Actions.OPENED_ANKIDROID_FAQ, 102, 1, false, OpenUrlResource(R.string.link_ankidroid_faq)),
            // options for getting help
            RecursiveMenuItem(R.string.help_title_get_help, R.drawable.ic_help_black_24dp, UsageAnalytics.Actions.OPENED_GET_HELP, 2, null, true, Header),
            RecursiveMenuItem(R.string.help_item_mailing_list, R.drawable.ic_email_black_24dp, UsageAnalytics.Actions.OPENED_MAILING_LIST, 200, 2, false, OpenUrlResource(R.string.link_forum)),
            RecursiveMenuItem(R.string.help_item_report_bug, R.drawable.ic_bug_report_black_24dp, UsageAnalytics.Actions.OPENED_REPORT_BUG, 201, 2, false, OpenUrl(AnkiDroidApp.getFeedbackUrl())),
            RecursiveMenuItem(R.string.help_title_send_exception, R.drawable.ic_round_assignment_24, UsageAnalytics.Actions.EXCEPTION_REPORT, 202, 2, false, ReportError),
            // links to social media
            RecursiveMenuItem(R.string.help_title_community, R.drawable.ic_people_black_24dp, UsageAnalytics.Actions.OPENED_COMMUNITY, 3, null, true, Header),
            RecursiveMenuItem(R.string.help_item_anki_forums, R.drawable.ic_forum_black_24dp, UsageAnalytics.Actions.OPENED_ANKI_FORUMS, 300, 3, false, OpenUrlResource(R.string.link_anki_forum)),
            RecursiveMenuItem(R.string.help_item_reddit, R.drawable.reddit, UsageAnalytics.Actions.OPENED_REDDIT, 301, 3, false, OpenUrlResource(R.string.link_reddit)),
            RecursiveMenuItem(R.string.help_item_mailing_list, R.drawable.ic_email_black_24dp, UsageAnalytics.Actions.OPENED_MAILING_LIST, 302, 3, false, OpenUrlResource(R.string.link_forum)),
            RecursiveMenuItem(R.string.help_item_discord, R.drawable.discord, UsageAnalytics.Actions.OPENED_DISCORD, 303, 3, false, OpenUrlResource(R.string.link_discord)),
            RecursiveMenuItem(R.string.help_item_facebook, R.drawable.facebook, UsageAnalytics.Actions.OPENED_FACEBOOK, 304, 3, false, OpenUrlResource(R.string.link_facebook)),
            RecursiveMenuItem(R.string.help_item_twitter, R.drawable.twitter, UsageAnalytics.Actions.OPENED_TWITTER, 305, 3, false, OpenUrlResource(R.string.link_twitter)),
            // privacy related help menu items
            RecursiveMenuItem(R.string.help_title_privacy, R.drawable.ic_baseline_privacy_tip_24, UsageAnalytics.Actions.OPENED_PRIVACY, 4, null, true, Header),
            RecursiveMenuItem(R.string.help_item_ankidroid_privacy_policy, R.drawable.ic_baseline_policy_24, UsageAnalytics.Actions.OPENED_ANKIDROID_PRIVACY_POLICY, 400, 4, false, OpenUrlResource(R.string.link_ankidroid_privacy_policy)),
            RecursiveMenuItem(R.string.help_item_ankiweb_privacy_policy, R.drawable.ic_baseline_policy_24, UsageAnalytics.Actions.OPENED_ANKIWEB_PRIVACY_POLICY, 401, 4, false, OpenUrlResource(R.string.link_ankiweb_privacy_policy)),
            RecursiveMenuItem(R.string.help_item_ankiweb_terms_and_conditions, R.drawable.ic_baseline_description_24, UsageAnalytics.Actions.OPENED_ANKIWEB_TERMS_AND_CONDITIONS, 402, 4, false, OpenUrlResource(R.string.link_ankiweb_terms_and_conditions))
        )
        return createInstance(menuItems, R.string.help)
    }

    @JvmStatic
    fun createInstanceForSupportAnkiDroid(context: Context): RecursiveMenuDialog {
        UsageAnalytics.sendAnalyticsEvent(UsageAnalytics.Category.LINK_CLICKED, UsageAnalytics.Actions.OPENED_SUPPORT_ANKIDROID)
        var menuItems = arrayOf(
            RecursiveMenuItem(R.string.help_item_support_opencollective_donate, R.drawable.ic_donate_black_24dp, UsageAnalytics.Actions.OPENED_DONATE, 1, null, true, OpenUrlResource(R.string.link_opencollective_donate)),
            RecursiveMenuItem(R.string.multimedia_editor_trans_translate, R.drawable.ic_language_black_24dp, UsageAnalytics.Actions.OPENED_TRANSLATE, 2, null, true, OpenUrlResource(R.string.link_translation)),
            RecursiveMenuItem(R.string.help_item_support_develop_ankidroid, R.drawable.ic_build_black_24, UsageAnalytics.Actions.OPENED_DEVELOP, 3, null, true, OpenUrlResource(R.string.link_ankidroid_development_guide)),
            RecursiveMenuItem(R.string.help_item_support_rate_ankidroid, R.drawable.ic_star_black_24, UsageAnalytics.Actions.OPENED_RATE, 4, null, true, Rate),
            RecursiveMenuItem(R.string.help_item_support_other_ankidroid, R.drawable.ic_help_black_24dp, UsageAnalytics.Actions.OPENED_OTHER, 5, null, true, OpenUrlResource(R.string.link_contribution)),
            RecursiveMenuItem(R.string.send_feedback, R.drawable.ic_email_black_24dp, UsageAnalytics.Actions.OPENED_SEND_FEEDBACK, 6, null, true, OpenUrl(AnkiDroidApp.getFeedbackUrl()))
        )
        if (!canOpenIntent(context, AnkiDroidApp.getMarketIntent(context))) {
            menuItems = menuItems.filterNot { it.action is Rate }.toTypedArray()
        }
        return createInstance(menuItems, R.string.help_title_support_ankidroid)
    }
}
