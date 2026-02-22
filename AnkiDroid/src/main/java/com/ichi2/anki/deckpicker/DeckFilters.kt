/*
 *  Copyright (c) 2026 Arthur Milchior <arthur@milchior.fr>
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

package com.ichi2.anki.deckpicker

import android.annotation.SuppressLint
import androidx.annotation.VisibleForTesting
import java.util.Locale
import kotlin.collections.filter
import kotlin.sequences.filter
import kotlin.text.filter

/**
 * Represents a deck search typed by the user, used to filter a list of decks.
 */
class DeckFilters
    @VisibleForTesting
    constructor(
        private val filters: List<DeckFilter>,
    ) {
        /**
         * Whether the user is searching something
         */
        fun searching() = filters.isNotEmpty()

        /**
         * Whether all filter of [filters] appear in [name]
         */
        @VisibleForTesting
        fun deckNamesMatchFilters(name: String) = filters.all { filter -> filter.deckNameMatchesFilter(name) }

        /**
         * Whether at least one of the filter matches the last name.
         * @See [deckLastNameMatchesFilter] to understand the exact meaning
         */
        @VisibleForTesting
        fun deckLastNameMatchesAFilter(name: String) = filters.any { filter -> filter.deckLastNameMatchesFilter(name) }

        /**
         * Whether the deck with this full name must be kept for the current filter.
         */
        @VisibleForTesting
        fun accept(name: String) =
            !searching() ||
                (
                    deckLastNameMatchesAFilter(name) &&
                        deckNamesMatchFilters(name)
                )

        /**
         * Represents a single filter
         * @param filter: a trimmed lower case string
         */
        class DeckFilter(
            private val filter: String,
        ) {
            /**
             * Whether there is a single : at the end. This case must be treated specially in order not to remove result from the deck list
             * while the user starts tapping "::subdeckName"
             */
            val endsWithSingleColumn = filter.endsWith(":") && !filter.endsWith("::")

            /**
             * The filter without its last ":" if the deck name ends with exactly one ":"
             */
            val trimmedFilter = if (endsWithSingleColumn) filter.trimEnd(':') else filter

            /**
             * Whether [filter] appears in [name].
             */
            @SuppressLint("LocaleRootUsage")
            fun deckNameMatchesFilter(name: String) =
                // If the filter is "foo:", the user may wants "foo: we can match against "foo" at the end of the deck name or with "foo:" anywhere in the deck name
                name.lowercase(Locale.getDefault()).contains(filter) ||
                    name.lowercase(Locale.ROOT).contains(filter) ||
                    name.lowercase(Locale.getDefault()).endsWith(trimmedFilter) ||
                    name.lowercase(Locale.ROOT).endsWith(trimmedFilter)

            /**
             * Whether [filter] matches against the last part of [name] specifically.
             * That is, if [filter] contains :: then the last suffix of the form "::foo", the name of the deck starts with "foo".
             * Otherwise, the name contains "foo".
             */
            @VisibleForTesting
            fun deckLastNameMatchesFilter(name: String): Boolean {
                val indexOfSeparatorInFilter = filter.lastIndexOf("::")
                if (indexOfSeparatorInFilter == -1) {
                    // "::" does not appear in the filter. Then the filter can be anywhere in
                    // the last part of the name
                    return deckNameMatchesFilter(name.split("::").last())
                }
                // "::" appears in the filter. Then it must be the same as the last "::" in the name.
                val indexOfSeparatorInName = name.lastIndexOf("::")
                if (indexOfSeparatorInName == -1) {
                    // This name does not correspond to a subdeck
                    return false
                }

                // We use trimmed filter. This way:
                // * if the filter does not ends with a :, this is similar to the filter
                // * if the filter ends with a single :, we're actually considering the parent name
                // the deck list will contains the parent.
                // * If the filter ends with ::, the last deck of the filter is empty, so this last deck is not considered, instead we just check against second to last deck name
                return containsAtPosition(
                    trimmedFilter,
                    indexOfSeparatorInFilter,
                    name,
                    indexOfSeparatorInName,
                )
            }

            companion object {
                /**
                 * Whether [containing] contains [contained] where the positions matches.
                 * Position must be less than the length of the string.
                 */
                @VisibleForTesting
                fun containsAtPosition(
                    contained: String,
                    positionContained: Int,
                    containing: String,
                    positionContaining: Int,
                ): Boolean {
                    val startOfContainingInContained = positionContaining - positionContained
                    val endOfContainingInContained = startOfContainingInContained + contained.length
                    val substringInContaining: String
                    try {
                        substringInContaining =
                            containing.substring(startOfContainingInContained, endOfContainingInContained)
                    } catch (e: IndexOutOfBoundsException) {
                        return false
                    }
                    return substringInContaining.lowercase(Locale.getDefault()) ==
                        contained.lowercase(
                            Locale.getDefault(),
                        ) ||
                        substringInContaining.equals(
                            contained,
                            ignoreCase = true,
                        )
                }
            }
        }

        companion object {
            /**
             * Returns a DeckFilters for the user input [filters]
             */
            fun create(filters: CharSequence) =
                DeckFilters(
                    filters
                        .toString()
                        .lowercase()
                        .split("\\s+".toRegex())
                        .map { it.trim() }
                        .filter {
                            it.isNotEmpty()
                        }.map { DeckFilter(it) },
                )
        }
    }
