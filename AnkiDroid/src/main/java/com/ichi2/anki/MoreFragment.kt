// SPDX-FileCopyrightText: 2026 Shaan Narendran <shaannaren06@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import com.google.android.material.color.MaterialColors
import com.ichi2.anki.common.analytics.Analytics
import com.ichi2.anki.common.analytics.AnalyticsEvent.LinkClicked
import com.ichi2.anki.common.analytics.LinkAction
import com.ichi2.anki.common.destinations.PreferencesDestination
import com.ichi2.anki.common.destinations.navigate
import com.ichi2.anki.dialogs.help.ARG_MENU_ITEMS
import com.ichi2.anki.dialogs.help.HelpDialog
import com.ichi2.anki.dialogs.help.HelpItem
import com.ichi2.anki.dialogs.help.HelpItem.Action.OpenUrl
import com.ichi2.anki.dialogs.help.HelpItem.Action.OpenUrlResource
import com.ichi2.anki.dialogs.help.HelpItem.Action.Rate
import com.ichi2.anki.dialogs.help.childHelpMenuItems
import com.ichi2.anki.dialogs.help.mainHelpMenuItems
import com.ichi2.anki.dialogs.help.supportMenuItems
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.compose.theme.AnkiDroidTheme
import com.ichi2.utils.IntentUtil
import com.ichi2.anki.common.android.R as CommonR

/**
 * Full-screen "More" destination in the bottom navigation bar.
 * Shows Settings, Help items, and Support items in a sectioned list.
 *
 * Help items open the existing [HelpDialog] focused on their subsection.
 * Support items directly open their respective URLs.
 */
class MoreFragment : Fragment() {
    @VisibleForTesting
    internal val scrollState = ScrollState(0)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val marketIntent = AnkiDroidApp.getMarketIntent(requireContext())
        val canRate = IntentUtil.canOpenIntent(requireContext(), marketIntent)
        val supportItems =
            supportMenuItems
                .filterNot { it.action is Rate && !canRate }
                .filterNot { it.analyticsId == LinkAction.OPENED_DONATE && !BuildConfig.SHOW_DONATE_LINKS }
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setBackgroundColor(MaterialColors.getColor(this, CommonR.attr.appBarColor))
            setContent {
                AnkiDroidTheme {
                    MoreScreen(
                        helpItems = mainHelpMenuItems.toList(),
                        supportItems = supportItems,
                        onSettingsClick = { navigate(PreferencesDestination.Root) },
                        onHelpItemClick = { section ->
                            Analytics.send(LinkClicked(section.analyticsId))
                            openHelpSection(section)
                        },
                        onSupportItemClick = { item ->
                            Analytics.send(LinkClicked(item.analyticsId))
                            when (val action = item.action) {
                                is OpenUrl -> openUrl(action.url)
                                is OpenUrlResource -> openUrl(getString(action.urlResourceId))
                                Rate -> startActivity(marketIntent)
                                else -> {}
                            }
                        },
                        scrollState = scrollState,
                    )
                }
            }
        }
    }

    /**
     * Opens [HelpDialog] showing the children of the help [section].
     */
    private fun openHelpSection(section: HelpItem) {
        val children = childHelpMenuItems.filter { it.parentId == section.id }
        val dialog =
            HelpDialog().apply {
                arguments =
                    Bundle().apply {
                        putInt(HelpDialog.ARG_MENU_TITLE, section.titleResId)
                        putParcelableArray(ARG_MENU_ITEMS, children.toTypedArray())
                    }
            }
        requireActivity().showDialogFragment(dialog)
    }

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }
}
