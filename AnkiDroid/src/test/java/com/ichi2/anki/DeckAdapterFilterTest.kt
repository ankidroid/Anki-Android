// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Shai Guelman <shaiguelman@gmail.com>

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.deckpicker.addVisibleToList
import com.ichi2.anki.libanki.filterAndFlatten
import com.ichi2.anki.libanki.sched.DeckNode
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeckAdapterFilterTest : RobolectricTest() {
    @Test
    fun verifyFilterResultsReturnsCorrectList() {
        val pattern = "Math"
        val actual = deckTree.filterAndFlatten(pattern)
        val expected =
            deckTree.getByNames(
                "Chanson",
                "Chanson::Math HW",
                "Chanson::Math HW::Theory",
                "Chanson::Important",
                "Chanson::Important::Math",
            )
        Assert.assertEquals(expected.map { it.fullDeckName }, actual.map { it.fullDeckName })
    }

    @Test
    fun verifyFilterResultsReturnsEmptyForNoMatches() {
        val pattern = "geometry"
        val actual = deckTree.filterAndFlatten(pattern)
        Assert.assertTrue(actual.isEmpty())
    }

    private val deckTree: DeckNode by lazy {
        val names =
            listOf(
                "Chanson",
                "Chanson::A Vers",
                "Chanson::A Vers::1",
                "Chanson::A Vers::Other",
                "Chanson::Math HW",
                "Chanson::Math HW::Theory",
                "Chanson::Important",
                "Chanson::Important::Stuff",
                "Chanson::Important::Math",
                "Chanson::Important::Stuff::Other Stuff",
            )
        names.forEach {
            val did = col.decks.id(it)
            col.decks.collapse(did)
        }
        col.sched.deckDueTree()
    }

    private fun DeckNode.getByNames(vararg names: String): List<DeckNode> {
        val all = mutableListOf<DeckNode>()
        this.addVisibleToList(all)
        return all.filter { it.fullDeckName in names }
    }
}
