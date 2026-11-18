// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser.search

import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.Flag
import com.ichi2.anki.libanki.DeckNameId
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.utils.ext.hasCheckedBackground

// Functions that show one filter of [SearchFilters] on a chip.
//
// The browser shows filter chips in two places: above the card list and inside
// the search screen. Both places use these functions, so the chips always
// look and act the same.

/** Shows the deck filter on a chip: the deck name if one is picked, "All decks" if not */
fun Chip.setDeckFilter(decks: List<DeckNameId>) {
    text = decks.firstOrNull()?.name ?: with(context) { TR.sentenceCase.allDecks }
    hasCheckedBackground = decks.any()
}

/** Shows the tag filter on a chip */
fun Chip.setTagFilter(tags: List<String>) {
    text = formatChipDescription(tags, emptyValue = "Tags")
    hasCheckedBackground = tags.any()
}

/** Shows the card state filter on a chip */
fun Chip.setCardStateFilter(cardStates: List<CardState>) {
    text = formatChipDescription(cardStates.map { it.label }, emptyValue = "Card state")
    hasCheckedBackground = cardStates.any()
    chipIcon = cardStates.firstOrNull().getIcon(context)
}

/** Shows the flag filter on a chip */
fun Chip.setFlagFilter(flags: List<Flag>) {
    text =
        formatChipDescription(
            entries = flags,
            singleValue = if (flags.singleOrNull() == Flag.NONE) "No Flag" else TR.browsingFlag(),
            nonSingleValue = TR.browsingSidebarFlags(),
        )
    hasCheckedBackground = flags.any()
    chipIcon =
        ContextCompat.getDrawable(context, flags.firstOrNull().iconRes)?.also {
            if (flags.isEmpty()) {
                DrawableCompat.setTint(
                    it.mutate(),
                    MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, 0),
                )
            }
        }
}
