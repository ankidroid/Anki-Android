/****************************************************************************************
 * Copyright (c) 2022 Shai Guelman <shaiguelman@gmail.com>                        *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/
package com.ichi2.anki

import com.ichi2.anki.widgets.DeckAdapter
import com.ichi2.libanki.DeckId
import com.ichi2.libanki.sched.AbstractDeckTreeNode
import com.ichi2.libanki.sched.DeckTreeNode
import com.ichi2.libanki.sched.TreeNode
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations

class DeckAdapterFilterTest {

    @Mock
    private lateinit var adapter: DeckAdapter

    private lateinit var filter: DeckAdapter.DeckFilter

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        filter = adapter.DeckFilter(deckList)
    }

    @Test
    fun verifyFilterResultsReturnsCorrectList() {
        val pattern = "Math"

        val actual = filter.filterResults(pattern, deckList)
        val expected = deckList.getByDids(0, 4, 5, 6, 8)

        Assert.assertEquals(expected, actual)
    }

    @Test
    fun verifyFilterResultsReturnsEmptyForNoMatches() {
        val deckList = deckList
        val pattern = "geometry"

        val actual = filter.filterResults(pattern, deckList)

        Assert.assertTrue(actual.isEmpty())
    }

    private val deckList: MutableList<TreeNode<AbstractDeckTreeNode>>
        get() {
            val deckList: MutableList<TreeNode<AbstractDeckTreeNode>> = mutableListOf(
                TreeNode(DeckTreeNode("Chanson", 0)),
                TreeNode(DeckTreeNode("Chanson::A Vers", 1)),
                TreeNode(DeckTreeNode("Chanson::A Vers::1", 2)),
                TreeNode(DeckTreeNode("Chanson::A Vers::Other", 3)),
                TreeNode(DeckTreeNode("Chanson::Math HW", 4)),
                TreeNode(DeckTreeNode("Chanson::Math HW::Theory", 5)),
                TreeNode(DeckTreeNode("Chanson::Important", 6)),
                TreeNode(DeckTreeNode("Chanson::Important::Stuff", 7)),
                TreeNode(DeckTreeNode("Chanson::Important::Math", 8)),
                TreeNode(DeckTreeNode("Chanson::Important::Stuff::Other Stuff", 9)),
            )

            deckList.getByDid(0).children.addAll(deckList.getByDids(1, 4, 6))
            deckList.getByDid(1).children.addAll(deckList.getByDids(2, 3))
            deckList.getByDid(4).children.addAll(deckList.getByDids(5))
            deckList.getByDid(6).children.addAll(deckList.getByDids(7, 8))
            deckList.getByDid(7).children.addAll(deckList.getByDids(9))

            return deckList
        }

    private fun List<TreeNode<AbstractDeckTreeNode>>.getByDid(did: DeckId): TreeNode<AbstractDeckTreeNode> {
        return this.first { it.value.did == did }
    }

    private fun List<TreeNode<AbstractDeckTreeNode>>.getByDids(vararg dids: Long): List<TreeNode<AbstractDeckTreeNode>> {
        return this.filter { it.value.did in dids }
    }
}
