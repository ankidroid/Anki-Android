// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser.search

import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.Flag
import com.ichi2.anki.R
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
    setFilterIcon(isSelected = decks.any(), outlined = R.drawable.ic_cards_star, filled = R.drawable.ic_cards_star_filled)
}

/** Shows the tag filter on a chip */
fun Chip.setTagFilter(tags: List<String>) {
    text = formatChipDescription(tags, emptyValue = "Tags")
    hasCheckedBackground = tags.any()
    setFilterIcon(isSelected = tags.any(), outlined = R.drawable.ic_label_outline, filled = R.drawable.ic_tag)
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
    // Flag icons include their own tint (or an explicit tint below). Do not let
    // ChipDrawable's stateful tint replace it while drawing.
    chipIconTint = null
    chipIcon =
        ContextCompat.getDrawable(context, flags.firstOrNull().iconRes)?.also {
            val tint =
                when {
                    flags.isEmpty() -> androidx.appcompat.R.attr.colorPrimary
                    flags.singleOrNull() == Flag.NONE -> android.R.attr.colorControlNormal
                    else -> null
                }
            tint?.let { colorAttribute ->
                DrawableCompat.setTint(
                    it.mutate(),
                    MaterialColors.getColor(context, colorAttribute, 0),
                )
            }
        }
}

/** Sets the chip icon: filled when the filter is on, outlined when it is not */
private fun Chip.setFilterIcon(
    isSelected: Boolean,
    outlined: Int,
    filled: Int,
) {
    // Let each vector drawable provide its own tint. The default Chip tint is
    // stateful and would override the outline/filled icon colors.
    chipIconTint = null
    chipIcon = ContextCompat.getDrawable(context, if (isSelected) filled else outlined)
}
