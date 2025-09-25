package com.ichi2.anki.deckpicker

import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.sched.DeckNode

enum class DeckSelectionType {
    /** Show study options if fragmented, otherwise, review  */
    DEFAULT,

    /** Always show study options (if the deck counts are clicked)  */
    SHOW_STUDY_OPTIONS,

    /** Always open reviewer (keyboard shortcut)  */
    SKIP_STUDY_OPTIONS,
}

sealed class DeckSelectionResult {
    data class HasCardsToStudy(
        val selectionType: DeckSelectionType,
    ) : DeckSelectionResult()

    data class Empty(
        val deckId: DeckId,
    ) : DeckSelectionResult()

    object NoCardsToStudy : DeckSelectionResult()
}

fun DeckNode.onlyHasDefaultDeck() = children.singleOrNull()?.did == Consts.DEFAULT_DECK_ID
