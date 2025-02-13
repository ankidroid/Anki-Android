/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki

import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.FilteredDeckOptions.Companion.LIMIT
import com.ichi2.anki.FilteredDeckOptions.Companion.LIMIT_2
import com.ichi2.anki.FilteredDeckOptions.Companion.ORDER
import com.ichi2.anki.FilteredDeckOptions.Companion.ORDER_2
import com.ichi2.anki.FilteredDeckOptions.Companion.SEARCH
import com.ichi2.anki.FilteredDeckOptions.Companion.SEARCH_2
import com.ichi2.anki.FilteredDeckOptions.Companion.STEPS
import com.ichi2.anki.FilteredDeckOptions.Companion.STEPS_ON
import com.ichi2.anki.FlashCardsContract.Note.MOD
import com.ichi2.anki.FlashCardsContract.Note.USN
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.FilteredDeck
import com.ichi2.testutils.isJsonHolderEqual
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.intellij.lang.annotations.Language
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FilteredDeckOptionsTest : RobolectricTest() {
    companion object {
        const val LRN_TODAY = "lrnToday"
        const val REV_TODAY = "revToday"
        const val NEW_TODAY = "newToday"
        const val TIME_TODAY = "timeToday"
        const val SEPARATE = "separate"
        const val PREVIEW_DELAY = "previewDelay"
        const val SEARCH = "search"
        const val LIMIT = "limit"
        const val ORDER = "order"
        const val SEARCH_2 = "search_2"
        const val LIMIT_2 = "limit_2"
        const val ORDER_2 = "order_2"
        const val STEPS = "steps"
        const val STEPS_ON = "stepsOn"
        const val PRESET = "preset"
        const val DYN = "dyn"
        const val NAME = "name"
        const val BROWSER_COLLAPSED = "browserCollapsed"
        const val COLLAPSED = "collapsed"
        const val ID = "id"
        const val DESCRIPTION = "desc"
        const val RESCHED = "resched"
        const val PREVIEW_AGAIN_SECS = "previewAgainSecs"
        const val PREVIEW_HARD_SECS = "previewHardSecs"
        const val PREVIEW_GOOD_SECS = "previewGoodSecs"
        const val DELAYS = "delays"
        const val TERMS = "terms"
        const val EMPTY = "empty"
    }

    @Test
    fun `integration test`() {
        @Language("JSON")
        val expected =
            FilteredDeck(
                """{
                    "$ID" : 1737164378146,
                    "$MOD" : 1737164378,
                    "$NAME" : "Filtered",
                    "$USN" : -1,
                    "$LRN_TODAY" : [0,0],
                    "$REV_TODAY" : [0,0],
                    "$NEW_TODAY" : [0,0],
                    "$TIME_TODAY" : [0,0],
                    "$COLLAPSED" : false,
                    "$BROWSER_COLLAPSED" : false,
                    "$DESCRIPTION" : "",
                    "$DYN" : 1,
                    "$RESCHED" : true,
                    "$TERMS" : [["", 100, 0]],
                    "$SEPARATE" : true,
                    "$DELAYS" : null,
                    "$PREVIEW_DELAY" : 0,
                    "$PREVIEW_AGAIN_SECS" : 60,
                    "$PREVIEW_HARD_SECS" : 600,
                    "$PREVIEW_GOOD_SECS" : 0
                   }""",
            )
        assertThat("should not be using default deck", filteredDeckConfig.id, not(equalTo(Consts.DEFAULT_DECK_ID)))
        assertThat("before", filteredDeckConfig.removeNonDeterministicValues(), isJsonHolderEqual(expected.removeNonDeterministicValues()))

        withFilteredDeckOptions(newFilteredDeckId) {
            @Suppress("DEPRECATION")
            secondFilterSign.onPreferenceChangeListener.onPreferenceChange(null, true)
            pref.edit(commit = true) {
                putString(SEARCH_2, "search_2")
                putString(LIMIT_2, "42")
                putString(ORDER_2, "43")
                putString(SEARCH, "search_1")
                putString(LIMIT, "44")
                putString(ORDER, "45")
                putBoolean(RESCHED, false)
                putInt(PREVIEW_AGAIN_SECS, 46)
                putInt(PREVIEW_HARD_SECS, 47)
                putInt(PREVIEW_GOOD_SECS, 48)
                putBoolean(STEPS_ON, true)
                putString(STEPS, "50 51 52")
                // TODO: Create a test for PRESET
            }
        }

        val updatedExpectation =
            expected
                .copyWith {
                    val firstFilter = JSONArray(listOf("search_1", 44, 45))
                    val secondFilter = JSONArray(listOf("search_2", 42, 43)) //
                    it.put(TERMS, JSONArray(listOf(firstFilter, secondFilter)))
                    it.put(RESCHED, false)
                    it.put(PREVIEW_AGAIN_SECS, 46)
                    it.put(PREVIEW_HARD_SECS, 47)
                    it.put(PREVIEW_GOOD_SECS, 48)
                    it.put(DELAYS, JSONArray(listOf(50, 51, 52)))
                }
        assertThat(
            "after",
            filteredDeckConfig.removeNonDeterministicValues(),
            isJsonHolderEqual(updatedExpectation.removeNonDeterministicValues()),
        )
    }

    /**
     * A copy of [this] without its "id" and "mod" keys.
     * Those two keys are the only non deterministic values, removing them allows to compare the returned value to some expected deck.
     */
    fun FilteredDeck.removeNonDeterministicValues() =
        this.copyWith { copy ->
            copy.remove("id")
            copy.remove("mod")
        }

    /**
     * The result of applying [block] to [this], leaving the input unchanged.
     */
    fun FilteredDeck.copyWith(block: (JSONObject) -> Unit) =
        FilteredDeck(this.toString()).apply {
            block(jsonObject)
        }

    private fun withFilteredDeckOptions(
        deckId: DeckId,
        block: FilteredDeckOptions.() -> Unit,
    ) {
        startRegularActivity<FilteredDeckOptions>(FilteredDeckOptions.createIntent(targetContext, deckId)).apply(block)
    }

    /**
     * A filtered deck named "Filtered" with default config, always the same deck during a test.
     */
    private val filteredDeckConfig
        get() =
            newFilteredDeckId.let { did ->
                col.decks.getLegacy(did) as FilteredDeck
            }

    /**
     * The deck id of a fresh filtered deck. The deck is created the first time this value is accessed, the id is then constant.*/
    private val newFilteredDeckId by lazy { col.decks.newFiltered("Filtered") }

    private val defaultDeckConfig
        get() = col.decks.configDictForDeckId(Consts.DEFAULT_DECK_ID)
}
