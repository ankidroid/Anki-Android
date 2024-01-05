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

enum class CardBrowserColumn {
    QUESTION, ANSWER, FLAGS, SUSPENDED, MARKED, SFLD, DECK, TAGS, ID, CARD, DUE, EASE, CHANGED, CREATED, EDITED, INTERVAL, LAPSES, NOTE_TYPE, REVIEWS;

    companion object {

        val COLUMN1_KEYS = arrayOf(QUESTION, SFLD)

        // list of available keys in mCards corresponding to the column names in R.array.browser_column2_headings.
        // Note: the last 6 are currently hidden
        val COLUMN2_KEYS = arrayOf(ANSWER, CARD, DECK, NOTE_TYPE, QUESTION, TAGS, LAPSES, REVIEWS, INTERVAL, EASE, DUE, CHANGED, CREATED, EDITED)
    }
}
