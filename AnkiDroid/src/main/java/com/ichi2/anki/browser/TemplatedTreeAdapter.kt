/*
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.browser

import android.content.Context
import androidx.annotation.DrawableRes
import java.lang.String.CASE_INSENSITIVE_ORDER
import java.util.TreeSet

class TemplatedTreeAdapter(context: Context) : TreeAdapter(context) {
    /**
     * A template for a [TreeAdapter.Item],
     * excluding user-modifiable properties ([TreeAdapter.Item.checked], etc...)
     */
    data class SourceItem(
        /** @see TreeAdapter.Item.id */
        val id: Long,
        /** @see TreeAdapter.Item.icon */
        @DrawableRes val icon: Int?,
        /** @see TreeAdapter.Item.text */
        val text: CharSequence,
        val subtitle: String? = null,
        /** @see TreeAdapter.Item.indent */
        val indent: Int,
        /** Defines the possible values of [TreeAdapter.Item.checked] */
        val checkable: Boolean
    )

    fun update(
        sourceItems: List<SourceItem>? = null,
        collapsedItemIds: Set<Long>? = null,
        checkedItemIds: Set<Long>? = null
    ) {
        val usedSourceItems = sourceItems ?: this.sourceItems
        val usedCollapsedItemIds = collapsedItemIds ?: this.collapsedItemIds
        val usedCheckedItemIds = checkedItemIds ?: this.checkedItemIds

        val items = mutableListOf<Item>()

        usedSourceItems.forEachWithNext { current, next ->
            items.lastOrNull()?.let { last ->
                if (current.indent > last.indent && last.collapsed == Collapsed.Yes) return@forEachWithNext
            }

            val collapsed = when {
                next == null || current.indent >= next.indent -> Collapsed.NotCollapsible
                current.id in usedCollapsedItemIds -> Collapsed.Yes
                else -> Collapsed.No
            }

            val checked = when {
                !current.checkable -> Checked.NotCheckable
                current.id in usedCheckedItemIds -> Checked.Yes
                else -> Checked.No
            }

            items.add(Item(current.id, current.icon, current.text, current.subtitle, current.indent, collapsed, checked))
        }

        this.sourceItems = usedSourceItems

        setItems(items, usedCollapsedItemIds, usedCheckedItemIds)
    }

    override fun updateCollapsedItemIds(ids: Set<Long>) = update(collapsedItemIds = ids)
    override fun updateCheckedItemIds(ids: Set<Long>) = update(checkedItemIds = ids)

    private var sourceItems: List<SourceItem> = listOf()
}

fun Iterable<String>.toSortedChainSetWithMissingParentsAdded() =
    TreeSet(CASE_INSENSITIVE_ORDER).also { out ->
        forEach { string ->
            val segments = string.split("::")
            (1..segments.size).forEach { end ->
                out.add(segments.subList(0, end).joinToString("::"))
            }
        }
    }

data class Row(val id: Long, val icon: Int, val chain: String)

fun Iterable<Row>.toSourceItems(filter: String):
    List<TemplatedTreeAdapter.SourceItem> {
    val flat = this.all { !it.chain.contains("::") }

    val matchingChainsAndTheirParents = this
        .map { it.chain }
        .filter { it.contains(filter, ignoreCase = true) }
        .toSortedChainSetWithMissingParentsAdded()

    return this.mapNotNull { (id, icon, chain) ->
        if (chain !in matchingChainsAndTheirParents) return@mapNotNull null

        TemplatedTreeAdapter.SourceItem(
            id = id,
            icon = icon,
            text = chain.substringAfterLast("::"),
            indent = if (flat) -1 else chain.split("::").size - 1, // TODO better method?
            checkable = true
        )
    }
}

fun List<TemplatedTreeAdapter.SourceItem>.withItemPrepended(sourceItem: TemplatedTreeAdapter.SourceItem) =
    listOf(sourceItem) + this

private inline fun <E> List<E>.forEachWithNext(block: (E, E?) -> Unit) {
    (0..lastIndex).forEach { idx ->
        block(this[idx], if (idx == lastIndex) null else this[idx + 1])
    }
}
