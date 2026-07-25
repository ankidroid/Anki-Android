// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Eric Li <ericli3690@gmail.com>

package com.ichi2.anki.settings

import androidx.annotation.StringRes
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * A bounded list of [Entry], stored as JSON under [keyResId] in Shared Preferences.
 * Entries are kept in most recently used order: [addRecent] moves an entry to the head, and no more
 * than [maxEntries] are retained.
 *
 * @param entrySerializer [Entry] is erased, so its serializer must be supplied by the subclass
 */
open class PrefsStore<Entry>(
    @StringRes private val keyResId: Int,
    entrySerializer: KSerializer<Entry>,
    private val maxEntries: Int,
    private val prefs: PrefsRepository = Prefs,
) {
    private val listSerializer = ListSerializer(entrySerializer)

    /**
     * The stored entries, in most recently used order.
     */
    var entries: List<Entry>
        @Synchronized
        get() {
            val jsonString = prefs.getString(keyResId, "[]") ?: "[]"
            return runCatching {
                Json.decodeFromString(listSerializer, jsonString)
            }.getOrElse { emptyList() }
        }

        @Synchronized
        private set(value) {
            Timber.i("updating entries: %d values", value.size)
            val json = Json.encodeToString(listSerializer, value)
            prefs.putString(keyResId, json)
        }

    /**
     * Adds the provided entry to the head of the list. Returns the updated list.
     * If the entry already exists, it will be moved to the head.
     */
    @Synchronized
    fun addRecent(entry: Entry): List<Entry> {
        val updatedEntries = entries.toMutableList()
        updatedEntries.remove(entry)
        updatedEntries.add(0, entry)
        return updatedEntries.take(maxEntries).also {
            this.entries = it.toMutableList()
            Timber.d("updated entries with '%s'", entry)
        }
    }

    /**
     * Removes [entry] from [entries]. Returns whether the element was contained in the collection.
     */
    @Synchronized
    fun removeEntry(entry: Entry): Boolean {
        val newEntries = entries.toMutableList()
        Timber.d("removing entry '%s'", entry)
        return newEntries.remove(entry).also {
            this.entries = newEntries
        }
    }

    @Synchronized
    fun clear() {
        Timber.i("clearing all entries")
        this.entries = listOf()
    }
}
