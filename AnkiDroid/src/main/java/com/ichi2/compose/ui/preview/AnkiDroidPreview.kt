// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.preview

import android.view.ContextThemeWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.ichi2.anki.R
import com.ichi2.compose.theme.AnkiDroidTheme

/**
 * Applies the AnkiDroid theme matching the preview's light or dark UI mode.
 *
 * Use this wrapper with [ThemePreviews].
 */
@Composable
fun AnkiDroidPreview(content: @Composable () -> Unit) {
    // AnkiDroidTheme reads XML attributes, so changing only the preview's UI mode is insufficient.
    val context = LocalContext.current
    val theme = if (isSystemInDarkTheme()) R.style.Theme_Dark else R.style.Theme_Light
    val themedContext = remember(context, theme) { ContextThemeWrapper(context, theme) }
    CompositionLocalProvider(LocalContext provides themedContext) {
        AnkiDroidTheme(content = content)
    }
}
