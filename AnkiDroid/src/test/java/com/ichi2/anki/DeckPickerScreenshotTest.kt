// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.anki

import android.content.Intent
import androidx.core.content.edit
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.testutils.BackupManagerTestUtilities
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
    fun tearDownBackup() {
        BackupManagerTestUtilities.reset()
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
    fun hierarchy_lines() {
        targetContext.sharedPrefs().edit { putBoolean("devBottomNav", true) }

        val root1 = addDeck("Math")
        addDeck("Math::Algebra")
        addDeck("Math::Algebra::Linear")
        addDeck("Math::Geometry")
        val root2 = addDeck("Science")
        addDeck("Science::Physics")
        addDeck("Science::Physics::Kinematics")
        addDeck("Science::Chemistry")

        val deckPicker =
            startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent()).also {
                RobolectricTest.advanceRobolectricLooper()
            }

        // Expand root nodes to see children
        deckPicker.viewModel.toggleDeckExpand(root1)
        deckPicker.viewModel.toggleDeckExpand(root2)
        RobolectricTest.advanceRobolectricLooper()

        // Also expand Math::Algebra and Science::Physics to see deep nesting
        deckPicker.viewModel.toggleDeckExpand(col.decks.id("Math::Algebra"))
        deckPicker.viewModel.toggleDeckExpand(col.decks.id("Science::Physics"))
        RobolectricTest.advanceRobolectricLooper()

        captureScreen("hierarchy_lines")
    }

    @Test
    fun hierarchy_lines_collapsed() {
        targetContext.sharedPrefs().edit { putBoolean("devBottomNav", true) }

        val root = addDeck("Math")
        addDeck("Math::Algebra")
        addDeck("Math::Algebra::Linear")
        addDeck("Math::Geometry")
        addDeck("Science::Physics::Kinematics")

        val deckPicker =
            startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent()).also {
                RobolectricTest.advanceRobolectricLooper()
            }

        // Expand Math to see Algebra and Geometry, but leave Algebra collapsed
        deckPicker.viewModel.toggleDeckExpand(root)
        RobolectricTest.advanceRobolectricLooper()

        captureScreen("hierarchy_lines_collapsed")
    }

    @Test
    fun hierarchy_lines_deep_nesting() {
        targetContext.sharedPrefs().edit { putBoolean("devBottomNav", true) }

        val root = addDeck("Level1")
        addDeck("Level1::Level2")
        addDeck("Level1::Level2::Level3")
        addDeck("Level1::Level2::Level3::Level4")
        addDeck("Level1::Level2::Level3::Level4::Level5")
        addDeck("Level1::Level2::Level3::Level4::Level5::Level6")
        addDeck("Level1::Level2::Level3::Level4::Level5::Level6::Level7")
        addDeck("Level1::Level2::Sibling")
        addDeck("Level1::Sibling")

        val deckPicker =
            startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent()).also {
                RobolectricTest.advanceRobolectricLooper()
            }

        // Expand all levels
        for (i in 1..6) {
            val name = (1..i).joinToString("::") { "Level$it" }
            deckPicker.viewModel.toggleDeckExpand(col.decks.id(name))
        }
        RobolectricTest.advanceRobolectricLooper()

        captureScreen("hierarchy_lines_deep_nesting")
    }

    @Test
    fun hierarchy_lines_many_siblings() {
        targetContext.sharedPrefs().edit { putBoolean("devBottomNav", true) }

        val root = addDeck("Parent")
        addDeck("Parent::Child1")
        addDeck("Parent::Child2")
        val child3 = addDeck("Parent::Child3")
        addDeck("Parent::Child3::Subchild")
        addDeck("Parent::Child4")
        addDeck("Parent::Child5")

        val deckPicker =
            startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent()).also {
                RobolectricTest.advanceRobolectricLooper()
            }

        deckPicker.viewModel.toggleDeckExpand(root)
        deckPicker.viewModel.toggleDeckExpand(child3)
        RobolectricTest.advanceRobolectricLooper()

        captureScreen("hierarchy_lines_many_siblings")
    }
}
