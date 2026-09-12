// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 lukstbit <52494258+lukstbit@users.noreply.github.com>

package com.ichi2.anki.ui.internationalization

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalInspectionMode
import anki.i18n.GeneratedTranslations
import anki.i18n.TranslateArgMap
import com.ichi2.anki.CollectionManager

/**
 * Translations provided by the Rust backend/Anki desktop code in composable code.
 * Note: A different static implementation is used for Android Studio's preview mode.
 */
@Composable
fun tr(): GeneratedTranslations =
    if (LocalInspectionMode.current) {
        PreviewTranslations
    } else {
        BackendTranslations
    }

/** Provides the real translations from the backend. */
@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
object BackendTranslations : GeneratedTranslations by CollectionManager.TR

/**
 * This implementation of [GeneratedTranslations] will be used when composables are used in preview
 * mode to get around the backend not being initialized in that context.
 *
 * Override here the methods for the strings that will be shown in preview mode.
 * Recommended flow: Append a failing assertion for the desired string in ComposableTranslationsTest.kt
 * and then override the specific method here with the text from the failing test.
 */
@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
object PreviewTranslations : GeneratedTranslations {
    override fun translate(
        module: Int,
        translation: Int,
        args: TranslateArgMap,
    ): String = "Implement in PreviewTranslations!"

    override fun actionsAdd(): String = "Add"

    override fun actionsAddNotetype(): String = "Add Note Type"

    override fun decksStudy(): String = "Study"

    override fun qtMiscBrowse(): String = "Browse"

    override fun cardStatsCurrentCard(context: String): String = "Current Card (\u2068$context\u2069)"

    override fun cardStatsPreviousCard(context: String): String = "Previous Card (\u2068$context\u2069)"
}
