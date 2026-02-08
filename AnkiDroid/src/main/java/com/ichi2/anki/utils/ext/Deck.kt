/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.utils.ext

import android.R.attr.name
import android.annotation.SuppressLint
import androidx.annotation.VisibleForTesting
import com.ichi2.anki.libanki.Deck
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.Decks
import com.ichi2.anki.libanki.sched.DeckNode
import java.util.Locale
import kotlin.collections.any

fun Decks.update(
    did: DeckId,
    block: Deck.() -> Unit,
) {
    val deck = getLegacy(did)!!
    block(deck)
    this.save(deck)
}
