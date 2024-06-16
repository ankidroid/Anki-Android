/*
 * Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.instantnoteeditor

import android.content.Context
import android.view.View
import androidx.annotation.OptIn
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.BadgeUtils
import com.google.android.material.badge.ExperimentalBadgeUtils
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import timber.log.Timber

/**
 * Updates the clozeChipGroup by removing all views and re-adding chips based on the current text.
 *
 * This method retrieves the words from the ViewModel's field text, creates ChipData objects for each word,
 * and sets up the chips with appropriate badge drawables. It also sets click listeners on each chip to
 * handle the creation and updating of cloze deletions.
 */
// TODO: single chars have more margin in chip
fun Context.setupChipGroup(viewModel: InstantEditorViewModel, clozeChipGroup: ChipGroup) {
    Timber.d("Setting up chip group view")
    clozeChipGroup.removeAllViews()

    val words = viewModel.getWordsFromFieldText()

    val chipsData = words.map { word ->
        val clozeNumber = viewModel.getWordClozeNumber(word)

        ChipData(
            word,
            Chip(clozeChipGroup.context),
            createBadgeDrawable(this),
            clozeNumber
        )
    }

    chipsData.forEach { data ->
        val chip = data.chip

        chip.isCheckable = true
        updateChipAndBadge(data)
        chip.text = viewModel.getCleanClozeWords(data.word)

        chip.setOnClickListener {
            val newWord = viewModel.buildClozeText(data.word)
            data.word = newWord
            val newClozeNumber = viewModel.getWordClozeNumber(newWord)

            updateChipAndBadge(data.copy(clozeNumber = newClozeNumber))

            val modifiedSentence = chipsData.joinToString(separator = " ") { it.word }
            viewModel.setClozeFieldText(modifiedSentence)
        }
        clozeChipGroup.addView(chip)
    }
}

private fun updateChipAndBadge(data: ChipData) {
    val chip = data.chip
    val clozeNumber = data.clozeNumber

    if (clozeNumber == null) {
        chip.isChecked = false
        updateBadgeVisibilityOnView(data.chip, data.badgeDrawable, false)
    } else {
        data.badgeDrawable.number = clozeNumber
        data.clozeNumber = clozeNumber
        chip.isChecked = true
        updateBadgeVisibilityOnView(data.chip, data.badgeDrawable, true)
    }
}

/**
 * Creates and configures a BadgeDrawable instance.
 *
 * This method creates a BadgeDrawable with a specified gravity and vertical offset.
 *
 * @param context The context used to create the BadgeDrawable.
 * @return A configured BadgeDrawable instance.
 */
private fun createBadgeDrawable(context: Context): BadgeDrawable {
    return BadgeDrawable.create(context).apply {
        badgeGravity = BadgeDrawable.TOP_END
        verticalPadding = 0
        horizontalPadding = 0
        verticalOffset = 50
    }
}

/**
 * Attaches or detaches a BadgeDrawable to/from a view based on visibility.
 *
 * This method posts a runnable to the view's message queue to either attach or detach
 * the BadgeDrawable, ensuring the operation is performed on the UI thread.
 *
 * @param view The view to which the BadgeDrawable will be attached or from which it will be detached.
 * @param badgeDrawable The BadgeDrawable to be shown or hidden.
 * @param visible A boolean indicating whether the BadgeDrawable should be attached (true) or detached (false).
 */
@OptIn(ExperimentalBadgeUtils::class)
private fun updateBadgeVisibilityOnView(view: View, badgeDrawable: BadgeDrawable, visible: Boolean) {
    view.post {
        if (visible) {
            BadgeUtils.attachBadgeDrawable(badgeDrawable, view, null)
        } else {
            BadgeUtils.detachBadgeDrawable(badgeDrawable, view)
        }
    }
}

/**
 * Data class representing a chip with associated data for a cloze-deleted text.
 *
 * @param word The original word that was deleted from the cloze-deleted text passage.
 * @param chip The chip UI element associated with this data.
 * @param badgeDrawable A drawable to be displayed as a badge on the chip.
 * @param clozeNumber (Optional) The number associated with the cloze deletion where this chip is used.
 * If null then it means that the word is not cloze i.e. no cloze deletion
 */
private data class ChipData(
    var word: String,
    val chip: Chip,
    val badgeDrawable: BadgeDrawable,
    var clozeNumber: Int?
)
