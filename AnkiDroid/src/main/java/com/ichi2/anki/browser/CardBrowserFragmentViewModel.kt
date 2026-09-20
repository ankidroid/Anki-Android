// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.common.annotations.NeedsTest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class CardBrowserFragmentViewModel : ViewModel() {
    val flowOfSearchForDecks = MutableSharedFlow<Unit>()

    @NeedsTest("default usage")
    fun openDeckSelectionDialog() =
        viewModelScope.launch {
            flowOfSearchForDecks.emit(Unit)
        }
}
