// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import com.ichi2.anki.settings.PrefsRepository
import com.ichi2.anki.utils.CollectionPreferences

/**
 * Retrieves the current [TimeboxTimeLimit] configured in the collection.
 */
suspend fun CollectionPreferences.getTimeboxLimit(): TimeboxTimeLimit = getTimeboxTimeLimit().toTimeboxTimeLimit()

/**
 * Persists the given [TimeboxTimeLimit] to the collection.
 */
suspend fun CollectionPreferences.setTimeboxLimit(limit: TimeboxTimeLimit) {
    setTimeboxTimeLimit(limit.duration)
}

/**
 * Extension property on [PrefsRepository] providing access to the cached customized [TimeboxTimeLimit].
 */
var PrefsRepository.timeboxCustomLimit: TimeboxTimeLimit
    get() {
        val stored = customTimeboxLimit
        return if (stored > 0) {
            TimeboxTimeLimit(stored)
        } else {
            TimeboxTimeLimit.DEFAULT
        }
    }
    set(value) {
        if (value.isEnabled) {
            customTimeboxLimit = value.minutes
        }
    }
