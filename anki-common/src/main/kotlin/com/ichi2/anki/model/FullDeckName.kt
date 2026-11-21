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
    /** Builds a deck search node. */
    fun toSearchNode(): SearchNode = searchNode { deck = name }
}

val Deck.fullName: FullDeckName
    get() = FullDeckName(name)
