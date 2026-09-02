// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.FragmentScenario.Companion.launch
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.IntroductionActivity
import com.ichi2.anki.R
import com.ichi2.anki.S
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.testutils.BackupManagerTestUtilities.setupSpaceForBackup
import com.ichi2.testutils.JvmTest
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// JvmTest loads the rust backend. Without it this class only passes when it happens to run
// after a test which loads it, which is filesystem order dependent (#14796)
// TODO: PERF: JvmTest also opens a collection, which these tests never use. They only
// need the backend for the menu's translated labels
@RunWith(AndroidJUnit4::class)
class DeckPickerContextMenuTest : JvmTest() {
    private val scenariosForCleanup = ArrayList<FragmentScenario<*>>()

    @Before
    fun before() {
        val context = ApplicationProvider.getApplicationContext<AnkiDroidApp>()
        context.sharedPrefs().edit { putBoolean(IntroductionActivity.INTRODUCTION_SLIDES_SHOWN, true) }
        setupSpaceForBackup(context)
    }

    @After
    fun after() {
        for (scenario in scenariosForCleanup) {
            try {
                scenario.close()
            } catch (e: Exception) {
                Timber.e(e, "ignored")
            }
        }
    }

    @Test
    fun ensure_cannot_be_instantiated_without_expected_arguments() {
        // fails on deck id missing from arguments
        assertFailsWith<IllegalArgumentException> { startContextMenuWithMissingArgument("id") }
        // fails on deck name missing from arguments
        assertFailsWith<IllegalArgumentException> { startContextMenuWithMissingArgument("name") }
        // fails on deck dynamic status missing from arguments
        assertFailsWith<IllegalArgumentException> { startContextMenuWithMissingArgument("dynamic") }
        // fails on deck having buried status missing from arguments
        assertFailsWith<IllegalArgumentException> { startContextMenuWithMissingArgument("hasBuried") }
    }

    /**
     * Create a [Bundle] with static data to be passed as arguments for [DeckPickerContextMenu].
     * One option is excluded to simulate the argument missing.
     *
     * @param excluded a value from [id, name, dynamic, hasBuried] to be removed from the returned
     * bundle. See source code for [DeckPickerContextMenu] for options meaning.
     */
    private fun startContextMenuWithMissingArgument(excluded: String) {
        val arguments =
            Bundle().apply {
                if (excluded != "id") {
                    DeckPickerContextMenu.ARG_DECK_ID to 1000L
                }
                if (excluded != "name") {
                    DeckPickerContextMenu.ARG_DECK_NAME to "Deck"
                }
                if (excluded != "dynamic") {
                    DeckPickerContextMenu.ARG_DECK_IS_DYN to false
                }
                if (excluded != "hasBuried") {
                    DeckPickerContextMenu.ARG_DECK_HAS_BURIED_IN_DECK to false
                }
            }
        launch(arguments)
    }

    @Test
    fun `Shows standard options`() {
        launch(withArguments()).onFragment { fragment ->
            with(fragment.requireContext()) {
                fragment.assertOptionPresent(S.menu_add)
                fragment.assertOptionPresent(S.browse_cards)
                fragment.assertOptionPresent(TR.sentenceCase.renameDeck)
                fragment.assertOptionPresent(TR.sentenceCase.deckOptions)
                fragment.assertOptionPresent(S.export_deck)
                fragment.assertOptionPresent(S.create_shortcut)
                fragment.assertOptionPresent(TR.sentenceCase.deleteDeck)
            }
        }
    }

    private fun DeckPickerContextMenu.assertOptionPresent(optionStringRes: Int) {
        assertOptionPresent(getString(optionStringRes))
    }

    private fun DeckPickerContextMenu.assertOptionPresent(optionTitle: String) {
        assertTrue(
            foundOptions().contains(optionTitle),
            "'$optionTitle' should be present",
        )
    }

    @Test
    fun `DELETE_DECK is the last option in the menu(issue 10283)`() {
        // "Delete deck" was previously close to "Custom study" which caused misclicks.
        // This is less likely at the bottom of the list
        launch(withArguments()).onFragment { fragment ->
            MatcherAssert.assertThat(
                "'Delete deck' should be last item in the menu",
                fragment.foundOptions().last(),
                equalTo(with(fragment.requireContext()) { TR.sentenceCase.deleteDeck }),
            )
        }
    }

    @Test
    fun `Shows options to empty and rebuild when deck is dynamic`() {
        launch(withArguments(isDynamic = true)).onFragment { fragment ->
            assertTrue(
                fragment.foundOptions().contains(fragment.getString(S.empty_cram_label)),
                "'Empty' should be present when deck is dynamic",
            )
            assertTrue(
                fragment.foundOptions().contains(TR.actionsRebuild()),
                "'Rebuild' should be present when deck is dynamic",
            )
        }
    }

    @Test
    fun `Shows option to create subdeck when deck is not dynamic`() {
        launch(withArguments()).onFragment { fragment ->
            assertTrue(
                fragment.foundOptions().contains(fragment.getString(S.create_subdeck)),
                "'Create subdeck' should be present when deck is not dynamic",
            )
        }
    }

    @Test
    fun `Shows option to unbury if deck has buried cards`() {
        launch(withArguments(hasBuriedCards = true)).onFragment { fragment ->
            assertTrue(
                fragment.foundOptions().contains(TR.studyingUnbury()),
                "'Unbury' should be present when deck has buried cards",
            )
        }
    }

    private fun launch(arguments: Bundle) =
        launch(DeckPickerContextMenu::class.java, arguments, R.style.Theme_Light).also {
            scenariosForCleanup.add(it)
        }

    private fun DeckPickerContextMenu.foundOptions(): List<String> {
        val foundOptions = mutableListOf<String>()
        val menuAdapter = (dialog as AlertDialog).listView.adapter
        for (index in 0 until menuAdapter.count) {
            foundOptions.add(menuAdapter.getItem(index).toString())
        }
        return foundOptions
    }

    private fun withArguments(
        deckId: DeckId = 1000L,
        deckName: String = "Deck 1",
        isDynamic: Boolean = false,
        hasBuriedCards: Boolean = false,
    ) = Bundle().apply {
        putLong(DeckPickerContextMenu.ARG_DECK_ID, deckId)
        putString(DeckPickerContextMenu.ARG_DECK_NAME, deckName)
        putBoolean(DeckPickerContextMenu.ARG_DECK_IS_DYN, isDynamic)
        putBoolean(DeckPickerContextMenu.ARG_DECK_HAS_BURIED_IN_DECK, hasBuriedCards)
    }
}
