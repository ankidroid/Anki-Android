// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Tim Farrelly <timf34@gmail.com>

package com.ichi2.anki.blocker

import androidx.annotation.StringRes
import androidx.core.content.edit
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.anki.libanki.DeckId

/**
 * Blocker settings, kept separate from [com.ichi2.anki.settings.Prefs] so the
 * feature stays self-contained.
 */
object BlockerPrefs {
    const val DEFAULT_CARDS_REQUIRED = 1
    const val MAX_CARDS_REQUIRED = 3
    const val DEFAULT_UNLOCK_MINUTES = 5

    /** [DeckId] value meaning "no deck chosen yet": fall back to the currently selected deck. */
    const val NO_GATE_DECK: DeckId = 0

    private val prefs get() = AnkiDroidApp.sharedPrefs()

    private fun key(
        @StringRes resId: Int,
    ): String = AnkiDroidApp.appResources.getString(resId)

    /** Whether the blocker is on at all. */
    var isEnabled: Boolean
        get() = prefs.getBoolean(key(R.string.blocker_enabled_key), false)
        set(value) = prefs.edit { putBoolean(key(R.string.blocker_enabled_key), value) }

    /** Package names of the apps the blocker gates. */
    var blockedApps: Set<String>
        get() = prefs.getStringSet(key(R.string.blocker_blocked_apps_key), null) ?: emptySet()
        set(value) = prefs.edit { putStringSet(key(R.string.blocker_blocked_apps_key), value) }

    /** Website domains the blocker gates (bare hosts like `x.com`; subdomains match). */
    var blockedDomains: Set<String>
        get() = prefs.getStringSet(key(R.string.blocker_blocked_domains_key), null) ?: emptySet()
        set(value) = prefs.edit { putStringSet(key(R.string.blocker_blocked_domains_key), value) }

    /** How many unique cards must be rated Good/Easy to open a gate. */
    var cardsRequired: Int
        get() = prefs.getInt(key(R.string.blocker_cards_required_key), DEFAULT_CARDS_REQUIRED).coerceIn(1, MAX_CARDS_REQUIRED)
        set(value) = prefs.edit { putInt(key(R.string.blocker_cards_required_key), value.coerceIn(1, MAX_CARDS_REQUIRED)) }

    /** How long an unlock lasts before the gate triggers again. */
    var unlockMinutes: Int
        get() = prefs.getInt(key(R.string.blocker_unlock_minutes_key), DEFAULT_UNLOCK_MINUTES).coerceAtLeast(1)
        set(value) = prefs.edit { putInt(key(R.string.blocker_unlock_minutes_key), value.coerceAtLeast(1)) }

    /** The deck gate reviews come from, or [NO_GATE_DECK] for the currently selected deck. */
    var gateDeckId: DeckId
        get() = prefs.getLong(key(R.string.blocker_gate_deck_key), NO_GATE_DECK)
        set(value) = prefs.edit { putLong(key(R.string.blocker_gate_deck_key), value) }

    /** Serialized unlock sessions; owned by [UnlockStore]. */
    var unlockSessionsJson: String?
        get() = prefs.getString(key(R.string.blocker_unlock_sessions_key), null)
        set(value) = prefs.edit { putString(key(R.string.blocker_unlock_sessions_key), value) }
}
