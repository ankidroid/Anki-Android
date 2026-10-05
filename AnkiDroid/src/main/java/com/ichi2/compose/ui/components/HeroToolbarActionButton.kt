// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.components

import android.view.ContextThemeWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.compose.theme.AnkiDroidTheme
import com.ichi2.compose.ui.preview.ThemePreviews

/** A prominent primary action for a toolbar. */
@Composable
fun HeroToolbarActionButton(
    modifier: Modifier = Modifier,
    label: String = stringResource(CommonString.multimedia_editor_field_editing_done),
    onClick: () -> Unit = {},
) {
    Box(modifier.padding(horizontal = 8.dp)) {
        Button(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(text = label)
        }
    }
}

@ThemePreviews
@Composable
private fun HeroToolbarActionButtonPreview() {
    // AnkiDroidTheme reads XML attributes, so changing only the preview's uiMode is insufficient.
    val context = LocalContext.current
    val theme = if (isSystemInDarkTheme()) R.style.Theme_Dark else R.style.Theme_Light
    val themedContext = remember(context, theme) { ContextThemeWrapper(context, theme) }
    CompositionLocalProvider(LocalContext provides themedContext) {
        AnkiDroidTheme {
            HeroToolbarActionButton()
        }
    }
}
