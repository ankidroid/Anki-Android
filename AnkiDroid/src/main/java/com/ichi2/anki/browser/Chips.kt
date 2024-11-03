/*
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
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.browser

import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.R

@Suppress("UnusedReceiverParameter")
fun CardBrowser.setupChips(
    @Suppress("UNUSED_PARAMETER") chips: ChipGroup,
) {
    // TODO add here code to initialize each of the filtering chips
}

@Suppress("RedundantSuspendModifier", "UnusedReceiverParameter")
suspend fun CardBrowser.updateChips(
    @Suppress("UNUSED_PARAMETER") chips: ChipGroup,
    oldSearchParameters: SearchParameters,
    newSearchParameters: SearchParameters,
) {
    if (oldSearchParameters.flags != newSearchParameters.flags) {
        // TODO add code for each chip to update its status
    }
}

@Suppress("unused")
private fun <T> Chip.update(
    activeItems: Collection<T>,
    inactiveText: String,
    activeTextGetter: (T) -> String,
) {
    if (activeItems.isEmpty()) {
        isChecked = false
        text = inactiveText
    } else {
        isChecked = true
        val firstSelectedItemName = activeTextGetter(activeItems.first())
        text =
            if (activeItems.size == 1) {
                firstSelectedItemName
            } else {
                // Display the first filter, along with the count of additional applied filters:
                // e.g. ["Tag1", "Tag2", "Tag3"] -> "Tag1 +2"
                context.getString(R.string.chip_filter_multiple_selections, firstSelectedItemName, (activeItems.size - 1))
            }
    }
}
