// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.internationalization

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalInspectionMode
import anki.i18n.GeneratedTranslations
import anki.i18n.PreviewTranslations
import com.ichi2.anki.CollectionManager.TR

/**
 * Provides translations from the Rust backend/Anki desktop code in composable code.
 */
@Composable
fun tr(): GeneratedTranslations = if (LocalInspectionMode.current) PreviewTranslations else BackendTranslations

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
object BackendTranslations : GeneratedTranslations by TR
