/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.browser

import androidx.lifecycle.ViewModel
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.model.SortType
import com.ichi2.libanki.CardId
import java.util.Collections
import java.util.LinkedHashSet

class CardBrowserViewModel : ViewModel() {

    val cards = CardBrowser.CardCollection<CardBrowser.CardCache>()

    var searchTerms: String = ""
    var restrictOnDeck: String = ""
    var currentFlag = 0

    var cardsOrNotes = CardsOrNotes.CARDS

    // card that was clicked (not marked)
    var currentCardId: CardId = 0
    var order = SortType.NO_SORTING
    var orderAsc = false
    var column1Index = 0
    var column2Index = 0

    /** The query which is currently in the search box, potentially null. Only set when search box was open  */
    var tempSearchQuery: String? = null

    var isInMultiSelectMode = false

    var isTruncated = false

    val checkedCards: MutableSet<CardBrowser.CardCache> = Collections.synchronizedSet(LinkedHashSet())
    var lastSelectedPosition = 0
}
