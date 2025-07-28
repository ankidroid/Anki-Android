/*
 *  Copyright (c) 2025 Hari Srinivasan <harisrini21@gmail.com>
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

package com.ichi2.anki.utils

import timber.log.Timber

/**
 * Utility functions for working with cloze deletions in Anki notes
 */
object ClozeUtils {
    /**
     * Regular expression pattern to find cloze deletions in text.
     * Matches patterns like {{c1::text}} or {{c2::text::hint}}
     */
    private val CLOZE_PATTERN = Regex("\\{\\{c(\\d+)::(.+?)(?:::(.+?))?\\}\\}")

    /**
     * Extracts all cloze numbers from a list of field values.
     *
     * @param fields The list of field values to search for cloze deletions
     * @return A sorted set of cloze numbers found in the fields
     */
    fun extractClozeNumbers(fields: List<String>): Set<Int> {
        val clozeNumbers = mutableSetOf<Int>()

        fields.forEach { fieldContent ->
            val matches = CLOZE_PATTERN.findAll(fieldContent)
            matches.forEach { match ->
                try {
                    val clozeNumber = match.groupValues[1].toInt()
                    clozeNumbers.add(clozeNumber)
                } catch (e: NumberFormatException) {
                    Timber.w(e, "Failed to parse cloze number from: ${match.groupValues[1]}")
                }
            }
        }

        return clozeNumbers.toSortedSet()
    }
}
