// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.destinations

import android.content.Intent
import com.ichi2.anki.libanki.DeckId

/**
 * Opens the reviewer for [deckId]: either the legacy or the new reviewer, based on
 * `Prefs.isNewStudyScreenEnabled`.
 */
data class ReviewDeckDestination(
    val deckId: DeckId,
    val navigationType: NavigationType,
) : Destination() {
    /** The task-stack behavior when opening the reviewer. */
    enum class NavigationType {
        /** @see Intent.FLAG_ACTIVITY_CLEAR_TOP */
        CLEAR_TOP,
    }
}
