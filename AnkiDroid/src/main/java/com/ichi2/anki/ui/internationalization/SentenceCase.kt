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

package com.ichi2.anki.ui.internationalization

import android.app.Activity
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment

// Functions for handling a move from 'Title Case' in Anki Desktop to 'Sentence case' in AnkiDroid

/**
 * Converts a string to sentence case if it matches the provided resource in `sentence-case.xml`
 *
 * ```
 * "Toggle Suspend".toSentenceCase(R.string.sentence_toggle_suspend) // "Toggle suspend"
 * ```
 */
fun String.toSentenceCase(activity: Activity, @StringRes resId: Int): String {
    val resString = activity.getString(resId)
    // lowercase both for the comparison: sentence case doesn't mean all words are lowercase
    if (this.lowercase() == resString.lowercase()) return resString
    return this
}

fun String.toSentenceCase(fragment: Fragment, @StringRes resId: Int): String {
    val resString = fragment.getString(resId)
    // lowercase both for the comparison: sentence case doesn't mean all words are lowercase
    if (this.lowercase() == resString.lowercase()) return resString
    return this
}
