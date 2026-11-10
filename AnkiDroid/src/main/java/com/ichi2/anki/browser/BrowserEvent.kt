// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

/** Events coordinated by [CardBrowserViewModel.onEvent]. */
sealed interface BrowserEvent {
    data object ToggleMarkRequested : BrowserEvent
}
