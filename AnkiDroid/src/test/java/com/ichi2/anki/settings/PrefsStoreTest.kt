// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Eric Li <ericli3690@gmail.com>

package com.ichi2.anki.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import kotlinx.serialization.Serializable
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.equalTo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrefsStoreTest : RobolectricTest() {
    private val store =
        PrefsStore(
            keyResId = R.string.pref_browser_search_history,
            entrySerializer = TestEntry.serializer(),
            maxEntries = 5,
        ).apply {
            this.clear()
        }

    @Test
    fun `entries is empty if no key is set`() {
        assertThat(store.entries, empty())
    }

    @Test
    fun `entries is empty if corrupt`() {
        Prefs.putString(R.string.pref_browser_search_history, "A")
        assertThat(store.entries, empty())
    }

    @Test
    fun `entries returns written value`() {
        store.addRecent(TestEntry("A"))
        assertEntriesEquals("A")
    }

    @Test
    fun `entries skips duplicate values`() {
        store.addRecent(TestEntry("A"))
        store.addRecent(TestEntry("A"))
        assertEntriesEquals("A")
    }

    @Test
    fun `entries returns latest values first`() {
        store.addRecent(TestEntry("A"))
        store.addRecent(TestEntry("B"))
        assertEntriesEquals("B", "A")
    }

    @Test
    fun `entries truncates least recently used`() {
        addNumberedEntries(6)
        assertEntriesEquals("6", "5", "4", "3", "2")

        // no more truncation occurs
        store.addRecent(TestEntry("2"))
        assertEntriesEquals("2", "6", "5", "4", "3")
    }

    @Test
    fun `clear on empty list does nothing`() {
        store.clear()
        assertThat(store.entries, empty())
    }

    @Test
    fun `clear on full list empties list`() {
        addNumberedEntries(5)
        store.clear()
        assertThat(store.entries, empty())
    }

    @Test
    fun `remove non-existing entry`() {
        assertFalse(store.removeEntry(TestEntry("AA")))
    }

    @Test
    fun `remove existing entry`() {
        addNumberedEntries(6)
        assertTrue(store.removeEntry(TestEntry("5")))
        assertEntriesEquals("6", "4", "3", "2")
    }

    /** Adds numbered entries from 1 to [count] inclusive */
    private fun addNumberedEntries(count: Int) =
        repeat(count) {
            store.addRecent(TestEntry((it + 1).toString()))
        }

    private fun assertEntriesEquals(vararg entries: String) {
        val listOfEntities = entries.map(::TestEntry)
        assertThat(store.entries, equalTo(listOfEntities))
    }
}

@Serializable
private data class TestEntry(
    val value: String,
)
