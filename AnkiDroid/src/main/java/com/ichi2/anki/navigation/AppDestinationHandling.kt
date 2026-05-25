/*
 *  Copyright (c) 2026 Tim Rae <perceptualchaos2@gmail.com>
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

package com.ichi2.anki.navigation

import android.content.Intent
import android.view.Menu
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.dialogs.help.HelpDialog
import com.ichi2.anki.pages.StatisticsDestination
import com.ichi2.anki.preferences.PreferencesActivity
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.utils.IntentUtil

/**
 * Default side-effect for selecting [dest] from an app-level navigation surface.
 *
 * Hosts that need to customise a specific destination (e.g. legacy
 * [com.ichi2.anki.NavigationDrawerActivity] passes a `currentCardId` extra to the
 * Card Browser and launches Settings via an `ActivityResultLauncher`) should
 * branch on that destination before delegating here.
 */
fun AnkiActivity.handleAppDestination(dest: AppDestination) {
    when (dest) {
        AppDestination.Decks ->
            startActivity(
                Intent(this, DeckPicker::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        AppDestination.Browser -> startActivity(Intent(this, CardBrowser::class.java))
        AppDestination.Stats -> startActivity(StatisticsDestination().toIntent(this))
        AppDestination.Settings -> startActivity(PreferencesActivity.getIntent(this))
        AppDestination.Help -> showDialogFragment(HelpDialog.newHelpInstance())
        AppDestination.Support -> {
            val canRate = IntentUtil.canOpenIntent(this, AnkiDroidApp.getMarketIntent(this))
            showDialogFragment(HelpDialog.newSupportInstance(canRate))
        }
    }
}

/**
 * Populates a [Menu] with one item per [AppDestination], grouped by [AppDestination.Group].
 * Items in the [AppDestination.Group.Primary] group are made checkable as a single-selection group.
 */
fun Menu.populateFromAppDestinations() {
    AppDestination.Group.entries.forEachIndexed { groupOrder, group ->
        AppDestination.entries
            .filter { it.group == group }
            .forEach { dest ->
                add(groupOrder, dest.menuItemId, Menu.NONE, dest.titleRes)
                    .setIcon(dest.iconRes)
            }
        if (group == AppDestination.Group.Primary) {
            setGroupCheckable(groupOrder, true, true)
        }
    }
}
