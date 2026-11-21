// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.model

import anki.search.SearchNode
import anki.search.searchNode
import com.ichi2.anki.libanki.Deck

/** A deck's full name, including any parent decks. */
@JvmInline
value class FullDeckName(
    val name: String,
) {
    /**
     * Builds a deck search node.
     *
     * @see toStoredNameSearchNode
     */
    fun toSearchNode(): SearchNode =
        searchNode {
            // Anki treats `deck:filtered` and `deck:"filtered"` as searches for cards in filtered decks.
            // `deck:Filtered` finds the named deck because Anki matches deck names without regard to case.
            deck = if (name == "filtered") "Filtered" else name
        }

    /**
     * Mirrors the search Anki builds from the stored deck name for Custom Study.
     * Returns null for `filtered` because Anki would search for cards in filtered decks.
     *
     * Workaround for [Anki Issue 6004](https://github.com/ankitects/anki/issues/6004).
     */
    fun toStoredNameSearchNode(): SearchNode? = if (name == "filtered") null else searchNode { deck = name }
}

val Deck.fullName: FullDeckName
    get() = FullDeckName(name)
