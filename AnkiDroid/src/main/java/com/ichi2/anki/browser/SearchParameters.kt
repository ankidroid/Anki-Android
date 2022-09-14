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

import android.os.Parcelable
import com.ichi2.anki.Flag
import com.ichi2.libanki.DeckId
import kotlinx.parcelize.Parcelize

@Parcelize
data class SearchParameters(
    val userInput: String,
    val deckIds: Set<DeckId>,
    val tags: Set<String>,
    val flags: Set<Flag>
) : Parcelable {
    val isEmpty get() = userInput.isEmpty() && deckIds.isEmpty() && tags.isEmpty() && flags.isEmpty()
    val isNotEmpty get() = !isEmpty

    companion object {
        val EMPTY = SearchParameters("", emptySet(), emptySet(), emptySet())
    }
}

fun SearchParameters.toQuery() =
    listOf(
        userInput,
        deckIds.joinToString(" OR ") { "did:$it" },
        tags.joinToString(" OR ") { """"tag:$it"""" },
        flags.joinToString(" OR ") { "flag:${it.code}" }
    )
        .filter { it.isNotEmpty() }
        .joinToString(" ") { "($it)" }
