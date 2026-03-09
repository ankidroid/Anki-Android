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

/**
 * A map of Title Case action names to their sentence case resource IDs.
 * Used for undo/redo label conversion.
 */
private val actionToSentenceCaseMap = mapOf(
    "Empty Cards" to R.string.sentence_empty_cards,
    "Custom Study" to R.string.sentence_custom_study,
    "Set Due Date" to R.string.sentence_set_due_date,
    "Suspend Card" to R.string.sentence_suspend_card,
    "Answer Card" to R.string.sentence_answer_card,
    "Add Deck" to R.string.sentence_add_deck,
    "Add Note" to R.string.sentence_add_note,
    "Update Tag" to R.string.sentence_update_tag,
    "Update Note" to R.string.sentence_update_note,
    "Update Card" to R.string.sentence_update_card,
    "Update Deck" to R.string.sentence_update_deck,
    "Reset Card" to R.string.sentence_reset_card,
    "Build Deck" to R.string.sentence_build_deck,
    "Add Note Type" to R.string.sentence_add_notetype,
    "Remove Note Type" to R.string.sentence_remove_notetype,
    "Update Note Type" to R.string.sentence_update_notetype,
    "Update Config" to R.string.sentence_update_config,
    "Card Info" to R.string.sentence_card_info,
    "Previous Card Info" to R.string.sentence_previous_card_info,
    "Set Flag" to R.string.sentence_set_flag,
    "Auto Advance" to R.string.sentence_auto_advance,
    "Bury Card" to R.string.sentence_bury_card,
    "Bury Note" to R.string.sentence_bury_note,
    "Unbury/Unsuspend" to R.string.sentence_unbury_unsuspend,
    "Rename" to R.string.sentence_rename,
    "Reposition" to R.string.sentence_reposition,
    "Forget Card" to R.string.sentence_forget_card,
    "Toggle Load Balancer" to R.string.sentence_toggle_load_balancer,
)

/**
 * Converts an action name from Title Case to sentence case.
 *
 * @param context The context to access resources
 * @param action The action name in Title Case (e.g., "Empty Cards")
 * @return The action name in sentence case (e.g., "empty cards")
 */
fun actionToSentenceCase(
    context: Context,
    action: String,
): String {
    val resId = actionToSentenceCaseMap[action]
    return if (resId != null) {
        context.getString(resId)
    } else {
        // Fallback: convert to lowercase except first letter
        action.replaceFirstChar { it.uppercaseChar() }.let { titleCase ->
            titleCase.split(" ").joinToString(" ") { word ->
                if (word == titleCase.split(" ").first()) {
                    word.replaceFirstChar { it.uppercaseChar() }
                } else {
                    word.lowercase()
                }
            }
        }
    }
}

/**
 * Converts undo label to sentence case.
 * Handles patterns like "Undo Empty Cards" -> "Undo empty cards"
 *
 * @param context The context to access resources
 * @param action The action name (e.g., "Empty Cards")
 * @return The sentence case version (e.g., "Undo empty cards")
 */
fun undoLabelToSentenceCase(
    context: Context,
    action: String,
): String {
    val actionSentenceCase = actionToSentenceCase(context, action)
    return "Undo $actionSentenceCase"
}

/**
 * Converts redo label to sentence case.
 * Handles patterns like "Redo Empty Cards" -> "Redo empty cards"
 *
 * @param context The context to access resources
 * @param action The action name (e.g., "Empty Cards")
 * @return The sentence case version (e.g., "Redo empty cards")
 */
fun redoLabelToSentenceCase(
    context: Context,
    action: String,
): String {
    val actionSentenceCase = actionToSentenceCase(context, action)
    return "Redo $actionSentenceCase"
}

/**
 * Converts "undone" message to sentence case.
 * Handles patterns like "Empty Cards undone" -> "Empty cards undone"
 *
 * @param context The context to access resources
 * @param action The action name (e.g., "Empty Cards")
 * @return The sentence case version (e.g., "Empty cards undone")
 */
fun undoneMessageToSentenceCase(
    context: Context,
    action: String,
): String {
    val actionSentenceCase = actionToSentenceCase(context, action)
    return "$actionSentenceCase undone"
}

/**
 * Converts "redone" message to sentence case.
 * Handles patterns like "Empty Cards redone" -> "Empty cards redone"
 *
 * @param context The context to access resources
 * @param action The action name (e.g., "Empty Cards")
 * @return The sentence case version (e.g., "Empty cards redone")
 */
fun redoneMessageToSentenceCase(
    context: Context,
    action: String,
): String {
    val actionSentenceCase = actionToSentenceCase(context, action)
    return "$actionSentenceCase redone"
}
