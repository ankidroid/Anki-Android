// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.ichi2.anki.noteeditor.data.ToolbarPreferences
import com.ichi2.anki.noteeditor.presentation.ToolbarLayoutMode
import com.ichi2.anki.settings.enums.DayTheme
import com.ichi2.anki.settings.enums.Theme
import com.ichi2.compose.theme.AnkiDroidTheme
import com.ichi2.compose.ui.preview.AnkiDroidPreview
import com.ichi2.compose.ui.preview.AnkiDroidThemeProvider
import com.ichi2.themes.Themes

private const val STATE_TOOLBAR_LAYOUT_DIALOG = "toolbarLayoutDialog"

/**
 * Hosts the 'Toolbar layout' dialog and restores its visibility after recreation.
 *
 * @see ToolbarLayoutDialog
 */
internal class ToolbarLayoutDialogHost {
    private var isShowing by mutableStateOf(false)

    fun bind(
        view: ComposeView,
        preferences: ToolbarPreferences,
        savedInstanceState: Bundle?,
        onChanged: () -> Unit,
    ) {
        isShowing = savedInstanceState?.getBoolean(STATE_TOOLBAR_LAYOUT_DIALOG) ?: false
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent {
            AnkiDroidTheme {
                if (isShowing) {
                    ToolbarLayoutDialog(
                        selected = preferences.layout,
                        onSelect = { layout ->
                            preferences.layout = layout
                            isShowing = false
                            onChanged()
                        },
                        onDismiss = { isShowing = false },
                        showSelectionBackground = Themes.currentTheme != DayTheme.EINK,
                    )
                }
            }
        }
    }

    fun show() {
        isShowing = true
    }

    fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_TOOLBAR_LAYOUT_DIALOG, isShowing)
    }
}

// TODO: Move these previews to ToolbarLayoutDialog.kt once theming is available in :common:android.
// https://github.com/ankidroid/Anki-Android/issues/22694
@Preview(name = "Scroll")
@Composable
private fun ToolbarLayoutDialogPreview(
    @PreviewParameter(AnkiDroidThemeProvider::class) theme: Theme,
) {
    AnkiDroidPreview(theme = theme) {
        ToolbarLayoutDialog(
            selected = ToolbarLayoutMode.SCROLL,
            onSelect = {},
            onDismiss = {},
            showSelectionBackground = theme != DayTheme.EINK,
        )
    }
}

@Preview(name = "Stack")
@Composable
private fun ToolbarLayoutDialogStackPreview(
    @PreviewParameter(AnkiDroidThemeProvider::class) theme: Theme,
) {
    AnkiDroidPreview(theme = theme) {
        ToolbarLayoutDialog(
            selected = ToolbarLayoutMode.STACK,
            onSelect = {},
            onDismiss = {},
            showSelectionBackground = theme != DayTheme.EINK,
        )
    }
}

@Preview(name = "Hidden")
@Composable
private fun ToolbarLayoutDialogHiddenPreview(
    @PreviewParameter(AnkiDroidThemeProvider::class) theme: Theme,
) {
    AnkiDroidPreview(theme = theme) {
        ToolbarLayoutDialog(
            selected = ToolbarLayoutMode.HIDDEN,
            onSelect = {},
            onDismiss = {},
            showSelectionBackground = theme != DayTheme.EINK,
        )
    }
}

@Preview(name = "Large text - short window", widthDp = 320, heightDp = 240, fontScale = 2f)
@Composable
private fun ToolbarLayoutDialogLargeTextPreview(
    @PreviewParameter(AnkiDroidThemeProvider::class) theme: Theme,
) {
    AnkiDroidPreview(theme = theme) {
        ToolbarLayoutDialog(
            selected = ToolbarLayoutMode.HIDDEN,
            onSelect = {},
            onDismiss = {},
            showSelectionBackground = theme != DayTheme.EINK,
        )
    }
}
