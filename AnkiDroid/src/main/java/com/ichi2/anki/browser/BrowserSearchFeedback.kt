// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

/** The feedback to display after a browser search completes. */
internal enum class BrowserSearchFeedback(
    val offersSearchAllDecks: Boolean = false,
) {
    NONE,
    COUNT,
    COUNT_WITH_SEARCH_ALL_DECKS(offersSearchAllDecks = true),
    NO_CARDS_IN_SELECTED_DECK(offersSearchAllDecks = true),
}

internal fun browserSearchFeedback(
    fromUserSearch: Boolean,
    rowCount: Int,
    allDecksSelected: Boolean,
    isHeaderCountVisible: Boolean,
): BrowserSearchFeedback =
    // The search facts come from the completed search; header visibility is checked when displaying it.
    when {
        // Ordinary opening, deck changes and sorting stay quiet (Issue 21242).
        !fromUserSearch -> BrowserSearchFeedback.NONE
        // A search within a deck also offers "Search all decks", even with a visible header (Issue 5159).
        !allDecksSelected && rowCount == 0 -> BrowserSearchFeedback.NO_CARDS_IN_SELECTED_DECK
        !allDecksSelected -> BrowserSearchFeedback.COUNT_WITH_SEARCH_ALL_DECKS
        // Submitted searches show the count when the header hides it (Issue 3592).
        isHeaderCountVisible -> BrowserSearchFeedback.NONE
        else -> BrowserSearchFeedback.COUNT
    }
