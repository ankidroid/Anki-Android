// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.anki

import android.content.Intent
import androidx.core.content.edit
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.widgets.DeckAdapter
import com.ichi2.testutils.BackupManagerTestUtilities
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Screenshot tests for [DeckPicker]
 *
 * `./gradlew :AnkiDroid:verifyRoborazziPlayDebug -Pscreenshot --tests "com.ichi2.anki.DeckPickerScreenshotTest"`
 */
class DeckPickerScreenshotTest : ScreenshotTest() {
    @Before
    override fun setUp() {
        super.setUp()
        setPhoneQualifiers()
        ensureCollectionLoadIsSynchronous()
        setIntroductionSlidesShown(true)
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        // suppress the periodic 'backup your collection' prompt so the screenshot is just the activity
        targetContext.sharedPrefs().edit { putBoolean("backupPromptDisabled", true) }
    }

    @After
    override fun tearDown() {
        super.tearDown()
        BackupManagerTestUtilities.reset()
        Prefs.sharedPrefs.edit { putBoolean(Prefs.key(R.string.dev_bottom_nav_key), false) }
    }

    @Test
    fun baseState_and_fabExpanded() =
        withDeckPicker(deckCount = 0) { deckPicker ->
            captureScreen("baseState")

            deckPicker.floatingActionMenu.showFloatingActionMenu()
            captureScreen("fabExpanded")
        }

    @Test
    fun edgeToEdge_30_decks() =
        withDeckPicker(deckCount = 30) { deckPicker ->
            deckPicker.simulateEdgeToEdge()
            captureScreen("edgeToEdge_30_decks")
        }

    @Test
    fun hierarchy_lines() =
        runTest {
            enableBottomNavigation()

            val root1 = addDeck("Math")
            val mathAlg = addDeck("Math::Algebra")
            addDeck("Math::Algebra::Linear")
            addDeck("Math::Geometry")
            val root2 = addDeck("Science")
            val sciPhys = addDeck("Science::Physics")
            addDeck("Science::Physics::Kinematics")
            addDeck("Science::Chemistry")

            val deckPicker = startDeckPicker()
            expandDecks(deckPicker, root1, root2, mathAlg, sciPhys)

            captureScreen("hierarchy_lines")
        }

    @Test
    fun hierarchy_lines_collapsed() =
        runTest {
            enableBottomNavigation()

            val root = addDeck("Math")
            addDeck("Math::Algebra")
            addDeck("Math::Algebra::Linear")
            addDeck("Math::Geometry")
            addDeck("Science::Physics::Kinematics")

            val deckPicker = startDeckPicker()
            // Expand Math to see Algebra and Geometry, but leave Algebra collapsed
            expandDecks(deckPicker, root)

            captureScreen("hierarchy_lines_collapsed")
        }

    @Test
    fun hierarchy_lines_deep_nesting() =
        runTest {
            enableBottomNavigation()

            addDeck("Level1")
            addDeck("Level1::Level2")
            addDeck("Level1::Level2::Level3")
            addDeck("Level1::Level2::Level3::Level4")
            addDeck("Level1::Level2::Level3::Level4::Level5")
            addDeck("Level1::Level2::Level3::Level4::Level5::Level6")
            addDeck("Level1::Level2::Level3::Level4::Level5::Level6::Level7")
            addDeck("Level1::Level2::Sibling")
            addDeck("Level1::Sibling")

            val deckPicker = startDeckPicker()
            val expandedDecks =
                (1..6).map { depth ->
                    val name = (1..depth).joinToString("::") { "Level$it" }
                    col.decks.id(name)
                }
            expandDecks(deckPicker, *expandedDecks.toLongArray())

            captureScreen("hierarchy_lines_deep_nesting")
        }

    @Test
    fun hierarchy_lines_many_siblings() =
        runTest {
            enableBottomNavigation()

            val root = addDeck("Parent")
            addDeck("Parent::Child1")
            addDeck("Parent::Child2")
            val child3 = addDeck("Parent::Child3")
            addDeck("Parent::Child3::Subchild")
            addDeck("Parent::Child4")
            addDeck("Parent::Child5")

            val deckPicker = startDeckPicker()
            expandDecks(deckPicker, root, child3)

            captureScreen("hierarchy_lines_many_siblings")
        }

    private fun enableBottomNavigation() {
        Prefs.sharedPrefs.edit { putBoolean(Prefs.key(R.string.dev_bottom_nav_key), true) }
    }

    private suspend fun startDeckPicker(): DeckPicker =
        startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent()).also {
            it.awaitDeckList()
        }

    private suspend fun DeckPicker.awaitDeckList() {
        viewModel.flowOfDeckList.first { it.data.isNotEmpty() }
        advanceRobolectricLooper()
    }

    private suspend fun expandDecks(
        deckPicker: DeckPicker,
        vararg deckIds: DeckId,
    ) {
        deckIds.forEach { deckPicker.viewModel.toggleDeckExpand(it).join() }
        deckPicker.awaitDecksRenderedExpanded(*deckIds)
    }

    /**
     * Waits until the deck list's [RecyclerView] has rendered [deckIds] as expanded.
     */
    private fun DeckPicker.awaitDecksRenderedExpanded(vararg deckIds: DeckId) {
        val adapter = this.deckPickerBinding.decks.adapter as DeckAdapter
        advanceRobolectricLooperUntil(
            lazyMessage = {
                "Decks ${deckIds.toList()} not rendered as expanded. Displayed: " +
                    adapter.currentList.map { "${it.did}(collapsed=${it.collapsed})" }
            },
        ) {
            deckIds.all { id -> adapter.currentList.any { it.did == id && !it.collapsed } }
        }
    }
}
