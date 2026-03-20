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

import android.content.Context
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import anki.i18n.GeneratedTranslations
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R

// Functions for handling a move from 'Title Case' in Anki Desktop to 'Sentence case' in AnkiDroid

/**
 * Converts a string to sentence case if it matches the provided resource in `sentence-case.xml`
 *
 * ```
 * "Toggle Suspend".toSentenceCase(R.string.sentence_toggle_suspend) // "Toggle suspend"
 * ```
 */
context(context: Context)
fun String.toSentenceCase(
    @StringRes resId: Int,
) = toSentenceCase(context, resId)

context(fragment: Fragment)
fun String.toSentenceCase(
    @StringRes resId: Int,
): String = toSentenceCase(fragment.requireContext(), resId)

fun String.toSentenceCase(
    context: Context,
    @StringRes resId: Int,
): String {
    val resString = context.getString(resId)
    // lowercase both for the comparison: sentence case doesn't mean all words are lowercase
    if (this.equals(resString, ignoreCase = true)) return resString
    return this
}

fun String.toSentenceCase(
    resources: Resources,
    @StringRes resId: Int,
): String {
    val resString = resources.getString(resId)
    // lowercase both for the comparison: sentence case doesn't mean all words are lowercase
    if (this.equals(resString, ignoreCase = true)) return resString
    return this
}

/**
 * Provides properties converting from Anki Desktop's 'Title Case' strings to AnkiDroid's
 * 'Sentence case' strings.
 *
 * Sentence case is a material design guideline
 */
// TODO: Expand for all past properties
object SentenceCase {
    context(_: Context)
    val checkDatabase get() = TR.databaseCheckTitle().toSentenceCase(R.string.sentence_check_db)

    context(_: Fragment)
    val checkDatabase get() = TR.databaseCheckTitle().toSentenceCase(R.string.sentence_check_db)

    context(_: Context)
    val checkMediaTitle get() = TR.mediaCheckWindowTitle().toSentenceCase(R.string.sentence_check_media)

    context(_: Fragment)
    val checkMediaTitle get() = TR.mediaCheckWindowTitle().toSentenceCase(R.string.sentence_check_media)

    context(_: Context)
    val checkMediaAction get() = TR.mediaCheckCheckMediaAction().toSentenceCase(R.string.sentence_check_media)
    context(_: Fragment)
    val checkMediaAction get() = TR.mediaCheckCheckMediaAction().toSentenceCase(R.string.sentence_check_media)
}

/**
 * Provides properties converting from Anki Desktop's 'Title Case' strings to AnkiDroid's
 * 'Sentence case' strings.
 *
 * Sentence case is a material design guideline
 */
@Suppress("UnusedReceiverParameter")
val GeneratedTranslations.sentenceCase get() = SentenceCase
