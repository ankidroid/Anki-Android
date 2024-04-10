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
package com.ichi2.anki.dialogs.help

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.addCallback
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.analytics.UsageAnalytics.Actions
import com.ichi2.anki.analytics.UsageAnalytics.Category
import com.ichi2.anki.convertDpToPixel
import com.ichi2.anki.dialogs.help.HelpItem.Action.OpenUrl
import com.ichi2.anki.dialogs.help.HelpItem.Action.OpenUrlResource
import com.ichi2.anki.dialogs.help.HelpItem.Action.Rate
import com.ichi2.anki.dialogs.help.HelpItem.Action.SendReport
import com.ichi2.utils.createAndApply
import com.ichi2.utils.customView
import com.ichi2.utils.title

/**
 * [DialogFragment] responsible for showing the help/support menus.
 */
class HelpDialog : DialogFragment() {
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    lateinit var actionsDispatcher: HelpItemActionsDispatcher

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val customView = requireActivity().layoutInflater.inflate(R.layout.dialog_help, null)
        val ankiActivity = requireActivity() as? AnkiActivity
        if (ankiActivity != null) {
            actionsDispatcher = AnkiActivityHelpActionsDispatcher(ankiActivity)
        }
        childFragmentManager.setFragmentResultListener(
            REQUEST_HELP_PAGE,
            this
        ) { _, arguments -> handleItemSelection(arguments) }
        return AlertDialog.Builder(requireContext())
            .title(requireArguments().getInt(ARG_MENU_TITLE))
            .customView(customView)
            .createAndApply {
                // the dialog captures the BACK call so we manually pop the inner FragmentManager
                // if there's a second page
                onBackPressedDispatcher.addCallback(this@HelpDialog, true) {
                    if (childFragmentManager.backStackEntryCount > 1) {
                        childFragmentManager.popBackStack()
                    } else {
                        dismiss()
                    }
                }
                setOnShowListener {
                    // if there's no fragment added this is a fresh start so add the initial page from arguments
                    if (childFragmentManager.findFragmentByTag(PAGE_TAG) == null) {
                        newHelpPage(requireArgsHelpEntries())
                    }
                }
            }
    }

    private fun handleItemSelection(from: Bundle) {
        from.classLoader = javaClass.classLoader
        val selectedItem = BundleCompat.getParcelable(
            from,
            ARG_SELECTED_MENU_ITEM,
            HelpItem::class.java
        ) ?: return
        when (selectedItem.action) {
            is OpenUrl -> actionsDispatcher.onOpenUrl(selectedItem.action.url)
            is OpenUrlResource -> actionsDispatcher.onOpenUrlResource(selectedItem.action.urlResourceId)
            Rate -> actionsDispatcher.onRate()
            SendReport -> actionsDispatcher.onSendReport()
            null -> {
                // there's no action so check if the selected item has children to show
                val children = childHelpMenuItems.filter { it.parentId == selectedItem.id }
                if (children.isNotEmpty()) {
                    newHelpPage(children.toTypedArray())
                }
            }
        }
    }

    private fun newHelpPage(items: Array<HelpItem>) {
        val menuPage = HelpPageFragment().apply {
            arguments = bundleOf(ARG_MENU_ITEMS to items)
        }
        childFragmentManager.commit {
            replace(R.id.fragment_container, menuPage, PAGE_TAG).addToBackStack(null)
        }
    }

    companion object {
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        const val ARG_MENU_TITLE = "arg_menu_title"
        private const val PAGE_TAG = "HelpMenuPage"

        fun newHelpInstance(): HelpDialog {
            UsageAnalytics.sendAnalyticsEvent(Category.LINK_CLICKED, Actions.OPENED_HELPDIALOG)
            return HelpDialog().apply {
                arguments = bundleOf(
                    ARG_MENU_TITLE to R.string.help,
                    ARG_MENU_ITEMS to mainHelpMenuItems
                )
            }
        }

        /**
         * @param canRateApp a boolean that indicates if the system has an app to open to rate AnkiDroid
         */
        fun newSupportInstance(canRateApp: Boolean): HelpDialog {
            UsageAnalytics.sendAnalyticsEvent(
                Category.LINK_CLICKED,
                Actions.OPENED_SUPPORT_ANKIDROID
            )
            val actualMenuItems = supportMenuItems.filterNot { it.action is Rate && !canRateApp }
            return HelpDialog().apply {
                arguments = bundleOf(
                    ARG_MENU_TITLE to R.string.help_title_support_ankidroid,
                    ARG_MENU_ITEMS to actualMenuItems.toTypedArray()
                )
            }
        }
    }
}

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
internal fun Fragment.requireArgsHelpEntries(): Array<HelpItem> {
    requireArguments().classLoader = javaClass.classLoader
    val retrievedItems = BundleCompat.getParcelableArray(
        requireArguments(),
        ARG_MENU_ITEMS,
        HelpItem::class.java
    ) ?: error("Unable to retrieve current help menu items")
    return retrievedItems.map { it as HelpItem }.toTypedArray()
}

internal const val ARG_MENU_ITEMS = "arg_menu_items"
internal const val REQUEST_HELP_PAGE = "request_help_page"
internal const val ARG_SELECTED_MENU_ITEM = " selected_menu_item"

/**
 * This fragment is responsible for showing a list of menu items in the application's [HelpDialog].
 */
class HelpPageFragment : Fragment(R.layout.fragment_help_page) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val drawablePadding = convertDpToPixel(16F, requireContext()).toInt()
        val pageContentLayout = view.findViewById<LinearLayout>(R.id.page_content)
        requireArgsHelpEntries().forEach { menuItem ->
            val contentRow = requireActivity().layoutInflater.inflate(
                R.layout.item_help_entry,
                pageContentLayout,
                false
            ) as TextView
            contentRow.apply {
                setText(menuItem.titleResId)
                setCompoundDrawablesRelativeWithIntrinsicBounds(
                    menuItem.iconResId,
                    0,
                    0,
                    0
                )
                compoundDrawablePadding = drawablePadding
                setOnClickListener {
                    UsageAnalytics.sendAnalyticsEvent(Category.LINK_CLICKED, menuItem.analyticsId)
                    parentFragmentManager.setFragmentResult(
                        REQUEST_HELP_PAGE,
                        bundleOf(ARG_SELECTED_MENU_ITEM to menuItem)
                    )
                }
                pageContentLayout.addView(this)
            }
        }
    }
}

/**
 * The top level menu items being shown in the help menu.
 * The help menu is a two level menu and for simplicity and ease of handling the corresponding menu
 * items are split in two arrays: this one which shows the main items and [childHelpMenuItems] which
 * groups all the children of the menu items from this array.
 */
@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
internal val mainHelpMenuItems = arrayOf(
    HelpItem(
        titleResId = R.string.help_title_using_ankidroid,
        iconResId = R.drawable.ic_manual_black_24dp,
        analyticsId = Actions.OPENED_USING_ANKIDROID,
        id = 1
    ),
    HelpItem(
        titleResId = R.string.help_title_get_help,
        iconResId = R.drawable.ic_help_black_24dp,
        analyticsId = Actions.OPENED_GET_HELP,
        id = 2
    ),
    HelpItem(
        titleResId = R.string.help_title_community,
        iconResId = R.drawable.ic_people_black_24dp,
        analyticsId = Actions.OPENED_COMMUNITY,
        id = 3
    ),
    HelpItem(
        titleResId = R.string.help_title_privacy,
        iconResId = R.drawable.ic_baseline_privacy_tip_24,
        analyticsId = Actions.OPENED_PRIVACY,
        id = 4
    )
)

/** The menu items that are shown in the support menu. */
@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
internal val supportMenuItems = arrayOf(
    HelpItem(
        titleResId = R.string.help_item_support_opencollective_donate,
        iconResId = R.drawable.ic_donate_black_24dp,
        analyticsId = Actions.OPENED_DONATE,
        id = 5,
        action = OpenUrlResource(R.string.link_opencollective_donate)
    ),
    HelpItem(
        titleResId = R.string.multimedia_editor_trans_translate,
        iconResId = R.drawable.ic_language_black_24dp,
        analyticsId = Actions.OPENED_TRANSLATE,
        id = 6,
        action = OpenUrlResource(R.string.link_translation)
    ),
    HelpItem(
        titleResId = R.string.help_item_support_develop_ankidroid,
        iconResId = R.drawable.ic_build_black_24,
        analyticsId = Actions.OPENED_DEVELOP,
        id = 7,
        action = OpenUrlResource(R.string.link_ankidroid_development_guide)
    ),
    HelpItem(
        titleResId = R.string.help_item_support_rate_ankidroid,
        iconResId = R.drawable.ic_star_black_24,
        analyticsId = Actions.OPENED_RATE,
        id = 8,
        action = Rate
    ),
    HelpItem(
        titleResId = R.string.help_item_support_other_ankidroid,
        iconResId = R.drawable.ic_help_black_24dp,
        analyticsId = Actions.OPENED_OTHER,
        id = 9,
        action = OpenUrlResource(R.string.link_contribution)
    ),
    HelpItem(
        titleResId = R.string.send_feedback,
        iconResId = R.drawable.ic_email_black_24dp,
        analyticsId = Actions.OPENED_SEND_FEEDBACK,
        id = 10,
        action = OpenUrl(AnkiDroidApp.feedbackUrl)
    )
)

/** This array contains all the children of the top level menu items from the help menu. */
@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
internal val childHelpMenuItems = arrayOf(
    HelpItem(
        titleResId = R.string.help_item_ankidroid_manual,
        iconResId = R.drawable.ic_manual_black_24dp,
        analyticsId = Actions.OPENED_ANKIDROID_MANUAL,
        id = 100,
        parentId = 1,
        action = OpenUrl(AnkiDroidApp.manualUrl)
    ),
    HelpItem(
        titleResId = R.string.help_item_anki_manual,
        iconResId = R.drawable.ic_manual_black_24dp,
        analyticsId = Actions.OPENED_ANKI_MANUAL,
        id = 101,
        parentId = 1,
        action = OpenUrlResource(R.string.link_anki_manual)
    ),
    HelpItem(
        titleResId = R.string.help_item_ankidroid_faq,
        iconResId = R.drawable.ic_help_black_24dp,
        analyticsId = Actions.OPENED_ANKIDROID_FAQ,
        id = 102,
        parentId = 1,
        action = OpenUrlResource(R.string.link_ankidroid_faq)
    ),
    HelpItem(
        titleResId = R.string.help_item_mailing_list,
        iconResId = R.drawable.ic_email_black_24dp,
        analyticsId = Actions.OPENED_MAILING_LIST,
        id = 200,
        parentId = 2,
        action = OpenUrlResource(R.string.link_forum)
    ),
    HelpItem(
        titleResId = R.string.help_item_report_bug,
        iconResId = R.drawable.ic_bug_report_black_24dp,
        analyticsId = Actions.OPENED_REPORT_BUG,
        id = 201,
        parentId = 2,
        action = OpenUrl(AnkiDroidApp.feedbackUrl)
    ),
    HelpItem(
        titleResId = R.string.help_title_send_exception,
        iconResId = R.drawable.ic_round_assignment_24,
        analyticsId = Actions.EXCEPTION_REPORT,
        id = 202,
        parentId = 2,
        action = SendReport
    ),
    HelpItem(
        titleResId = R.string.help_item_anki_forums,
        iconResId = R.drawable.ic_forum_black_24dp,
        analyticsId = Actions.OPENED_ANKI_FORUMS,
        id = 300,
        parentId = 3,
        action = OpenUrlResource(R.string.link_anki_forum)
    ),
    HelpItem(
        titleResId = R.string.help_item_mailing_list,
        iconResId = R.drawable.ic_email_black_24dp,
        analyticsId = Actions.OPENED_MAILING_LIST,
        id = 301,
        parentId = 3,
        action = OpenUrlResource(R.string.link_forum)
    ),
    HelpItem(
        titleResId = R.string.help_item_reddit,
        iconResId = R.drawable.ic_link,
        analyticsId = Actions.OPENED_REDDIT,
        id = 302,
        parentId = 3,
        action = OpenUrlResource(R.string.link_reddit)
    ),
    HelpItem(
        titleResId = R.string.help_item_discord,
        iconResId = R.drawable.ic_link,
        analyticsId = Actions.OPENED_DISCORD,
        id = 303,
        parentId = 3,
        action = OpenUrlResource(R.string.link_discord)
    ),
    HelpItem(
        titleResId = R.string.help_item_facebook,
        iconResId = R.drawable.ic_link,
        analyticsId = Actions.OPENED_FACEBOOK,
        id = 304,
        parentId = 3,
        action = OpenUrlResource(R.string.link_facebook)
    ),
    HelpItem(
        titleResId = R.string.help_item_twitter,
        iconResId = R.drawable.ic_link,
        analyticsId = Actions.OPENED_TWITTER,
        id = 305,
        parentId = 3,
        action = OpenUrlResource(R.string.link_twitter)
    ),
    HelpItem(
        titleResId = R.string.help_item_ankidroid_privacy_policy,
        iconResId = R.drawable.ic_baseline_policy_24,
        analyticsId = Actions.OPENED_ANKIDROID_PRIVACY_POLICY,
        id = 400,
        parentId = 4,
        action = OpenUrlResource(R.string.link_ankidroid_privacy_policy)
    ),
    HelpItem(
        titleResId = R.string.help_item_ankiweb_privacy_policy,
        iconResId = R.drawable.ic_baseline_policy_24,
        analyticsId = Actions.OPENED_ANKIWEB_PRIVACY_POLICY,
        id = 401,
        parentId = 4,
        action = OpenUrlResource(R.string.link_ankiweb_privacy_policy)
    ),
    HelpItem(
        titleResId = R.string.help_item_ankiweb_terms_and_conditions,
        iconResId = R.drawable.ic_baseline_description_24,
        analyticsId = Actions.OPENED_ANKIWEB_TERMS_AND_CONDITIONS,
        id = 402,
        parentId = 4,
        action = OpenUrlResource(R.string.link_ankiweb_terms_and_conditions)
    )
)
