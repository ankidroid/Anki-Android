// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 lukstbit <52494258+lukstbit@users.noreply.github.com>

package com.ichi2.anki.ui.internationalization

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.ichi2.anki.ankicommon.R

/** Translations provided by the Rust backend/Anki desktop code in composable code. */
@Composable
fun trCase(): SentenceCaseExtension = SentenceCaseExtension

/**
 * Provides properties converting from Anki Desktop's 'Title Case' strings to AnkiDroid's
 * 'Sentence case' strings.
 *
 * Sentence case is a material design guideline.
 */
// TODO: Expand for all past properties
object SentenceCaseExtension {
    /** A thin composable wrapper around [toSentenceCase] */
    @Composable
    private fun resourceCase(
        backendString: String,
        @StringRes resourceId: Int,
    ): String {
        val context = LocalContext.current
        return with(context) { backendString.toSentenceCase(resourceId) }
    }

    @Composable
    fun addNoteType(): String = resourceCase(tr().actionsAddNotetype(), R.string.sentence_add_note_type)

    @Composable
    fun cardStatsCurrentCardStudy(): String {
        val backendString = tr().cardStatsCurrentCard(tr().decksStudy())
        return resourceCase(backendString, R.string.sentence_card_stats_current_card_study)
    }

    @Composable
    fun cardStatsCurrentCardBrowse(): String {
        val backendString = tr().cardStatsCurrentCard(tr().qtMiscBrowse())
        return resourceCase(backendString, R.string.sentence_card_stats_current_card_browse)
    }

    @Composable
    fun cardStatsPreviousCardStudy(): String {
        val backendString = tr().cardStatsPreviousCard(tr().decksStudy())
        return resourceCase(backendString, R.string.sentence_card_stats_previous_card_study)
    }
}
