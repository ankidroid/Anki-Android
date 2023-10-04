/*
 * Copyright (c) 2023 Ashish Yadav <mailtoashish693@gmail.com>
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
package com.ichi2.anki.bottomdialogsheet

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CrashReportService
import com.ichi2.anki.R
import com.ichi2.anki.analytics.UsageAnalytics

class BottomDialogSheetFragment : BottomSheetDialogFragmentFix() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.help_bottom_sheet, container, false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        val adapter = HelpBottomSheetAdapter(helpDialogModelList())
        recyclerView?.adapter = adapter
        UsageAnalytics.sendAnalyticsEvent(
            UsageAnalytics.Category.LINK_CLICKED,
            UsageAnalytics.Actions.OPENED_HELPDIALOG
        )
        return view
    }

    private fun openManual(ankiActivity: AnkiActivity) {
        ankiActivity.openUrl(Uri.parse(AnkiDroidApp.manualUrl))
    }

    private fun openFeedback(ankiActivity: AnkiActivity) {
        ankiActivity.openUrl(Uri.parse(AnkiDroidApp.feedbackUrl))
    }

    private fun helpDialogModelList(): List<HelpDialogModel> {
        val helpList = ArrayList<HelpDialogModel>()
        helpList.add(
            HelpDialogModel(
                resources.getString(R.string.help_title_using_ankidroid),
                true,
                R.drawable.ic_manual_black_24dp,
                listOf(
                    HelpDialogModel(
                        resources.getString(R.string.help_item_ankidroid_manual),
                        false,
                        R.drawable.ic_manual_black_24dp,
                        null,
                        action = {
                            UsageAnalytics.Actions.OPENED_USING_ANKIDROID
                            val context = requireContext() as AnkiActivity
                            openManual(context)
                        }
                    ),
                    HelpDialogModel(
                        resources.getString(R.string.help_item_anki_manual),
                        false,
                        R.drawable.ic_manual_black_24dp,
                        null,
                        action = {
                            UsageAnalytics.Actions.OPENED_ANKIDROID_MANUAL
                            (requireContext() as AnkiActivity).openUrl(Uri.parse(resources.getString(R.string.link_anki_manual)))
                        }
                    ),
                    HelpDialogModel(
                        resources.getString(R.string.help_item_ankidroid_faq),
                        false,
                        R.drawable.ic_help_black_24dp,
                        null,
                        action = {
                            (requireContext() as AnkiActivity).openUrl(Uri.parse(resources.getString(R.string.link_ankidroid_faq)))
                        }
                    )
                ),
                action = {
                    UsageAnalytics.Actions.OPENED_USING_ANKIDROID
                }
            )
        )

        helpList.add(
            HelpDialogModel(
                resources.getString(R.string.help_title_get_help),
                true,
                R.drawable.ic_help_black_24dp,
                listOf(
                    HelpDialogModel(
                        resources.getString(R.string.help_item_mailing_list),
                        false,
                        R.drawable.ic_email_black_24dp,
                        null,
                        action = {
                            UsageAnalytics.Actions.OPENED_MAILING_LIST
                            (requireContext() as AnkiActivity).openUrl(Uri.parse(resources.getString(R.string.link_forum)))
                        }
                    ),
                    HelpDialogModel(
                        resources.getString(R.string.help_item_report_bug),
                        false,
                        R.drawable.ic_bug_report_black_24dp,
                        null,
                        action = {
                            UsageAnalytics.Actions.OPENED_REPORT_BUG
                            openFeedback((requireContext() as AnkiActivity))
                        }
                    ),
                    HelpDialogModel(
                        resources.getString(R.string.help_title_send_exception),
                        false,
                        R.drawable.ic_round_assignment_24,
                        null,
                        action = {
                            // TODO : fix this
                            CrashReportService.sendReport(requireContext() as AnkiActivity)
                            UsageAnalytics.sendAnalyticsEvent(
                                UsageAnalytics.Category.LINK_CLICKED,
                                UsageAnalytics.Actions.OPENED_HELPDIALOG
                            )
                        }
                    )
                ),
                action = {
                    UsageAnalytics.Actions.OPENED_GET_HELP
                }
            )
        )

        helpList.add(
            HelpDialogModel(
                resources.getString(R.string.help_title_community),
                true,
                R.drawable.ic_people_black_24dp,
                listOf(
                    HelpDialogModel(
                        resources.getString(R.string.help_item_anki_forums),
                        false,
                        R.drawable.ic_forum_black_24dp,
                        null,
                        action = {
                            UsageAnalytics.Actions.OPENED_ANKI_FORUMS
                            (requireContext() as AnkiActivity).openUrl(Uri.parse(resources.getString(R.string.link_anki_forum)))
                        }
                    ),
                    HelpDialogModel(
                        resources.getString(R.string.help_item_reddit),
                        false,
                        R.drawable.reddit,
                        null,
                        action = {
                            UsageAnalytics.Actions.OPENED_REDDIT
                            (requireContext() as AnkiActivity).openUrl(Uri.parse(resources.getString(R.string.link_reddit)))
                        }
                    ),
                    HelpDialogModel(
                        resources.getString(R.string.help_item_mailing_list),
                        false,
                        R.drawable.ic_email_black_24dp,
                        null,
                        action = {
                            UsageAnalytics.Actions.OPENED_MAILING_LIST
                            (requireContext() as AnkiActivity).openUrl(Uri.parse(resources.getString(R.string.link_forum)))
                        }
                    ),
                    HelpDialogModel(
                        resources.getString(R.string.help_item_discord),
                        false,
                        R.drawable.discord,
                        null,
                        action = {
                            UsageAnalytics.Actions.OPENED_DISCORD
                            (requireContext() as AnkiActivity).openUrl(Uri.parse(resources.getString(R.string.link_discord)))
                        }
                    ),
                    HelpDialogModel(
                        resources.getString(R.string.help_item_facebook),
                        false,
                        R.drawable.facebook,
                        null,
                        action = {
                            UsageAnalytics.Actions.OPENED_FACEBOOK
                            (requireContext() as AnkiActivity).openUrl(Uri.parse(resources.getString(R.string.link_facebook)))
                        }
                    ),
                    HelpDialogModel(
                        resources.getString(R.string.help_item_twitter),
                        false,
                        R.drawable.twitter,
                        null,
                        action = {
                            UsageAnalytics.Actions.OPENED_TWITTER
                            (requireContext() as AnkiActivity).openUrl(Uri.parse(resources.getString(R.string.link_twitter)))
                        }
                    )
                ),
                action = {
                    UsageAnalytics.Actions.OPENED_COMMUNITY
                }
            )
        )

        helpList.add(
            HelpDialogModel(
                resources.getString(R.string.help_title_privacy),
                true,
                R.drawable.ic_baseline_privacy_tip_24,
                listOf(
                    HelpDialogModel(
                        resources.getString(R.string.help_item_ankidroid_privacy_policy),
                        false,
                        R.drawable.ic_baseline_policy_24,
                        null,
                        action = {
                            UsageAnalytics.Actions.OPENED_ANKIDROID_PRIVACY_POLICY
                            (requireContext() as AnkiActivity).openUrl(Uri.parse(resources.getString(R.string.link_ankidroid_privacy_policy)))
                        }
                    ),
                    HelpDialogModel(
                        resources.getString(R.string.help_item_ankiweb_privacy_policy),
                        false,
                        R.drawable.ic_baseline_policy_24,
                        null,
                        action = {
                            UsageAnalytics.Actions.OPENED_ANKIWEB_PRIVACY_POLICY
                            (requireContext() as AnkiActivity).openUrl(Uri.parse(resources.getString(R.string.link_ankiweb_privacy_policy)))
                        }
                    ),
                    HelpDialogModel(
                        resources.getString(R.string.help_item_ankiweb_terms_and_conditions),
                        false,
                        R.drawable.ic_baseline_description_24,
                        null,
                        action = {
                            UsageAnalytics.Actions.OPENED_ANKIWEB_TERMS_AND_CONDITIONS
                            (requireContext() as AnkiActivity).openUrl(Uri.parse(resources.getString(R.string.link_ankiweb_terms_and_conditions)))
                        }
                    )
                ),
                action = {
                    UsageAnalytics.Actions.OPENED_PRIVACY
                }
            )
        )
        return helpList
    }
}
