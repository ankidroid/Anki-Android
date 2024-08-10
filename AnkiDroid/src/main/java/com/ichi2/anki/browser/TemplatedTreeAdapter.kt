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

class TemplatedTreeAdapter(context: Context) : TreeAdapter(context) {
    /**
     * A template for a [TreeAdapter.Item],
     * excluding user-modifiable properties ([TreeAdapter.Item.checked], etc...)
     */
    data class SourceItem(
        /** @see TreeAdapter.Item.id */
        val id: Long,
        /** @see TreeAdapter.Item.icon */
        val icon: Int,
        /** @see TreeAdapter.Item.text */
        val text: CharSequence,
        /** Defines the possible values of [TreeAdapter.Item.checked] */
        val checkable: Boolean
    )

    fun update(
        sourceItems: List<SourceItem>? = null,
        checkedItemIds: Set<Long>? = null
    ) {
        val usedSourceItems = sourceItems ?: this.sourceItems
        val usedCheckedItemIds = checkedItemIds ?: this.checkedItemIds

        val items = mutableListOf<Item>()

        for (item in usedSourceItems) {
            val checked = when {
                !item.checkable -> Checked.NotCheckable
                item.id in usedCheckedItemIds -> Checked.Yes
                else -> Checked.No
            }

            items.add(Item(item.id, item.icon, item.text, checked))
        }

        this.sourceItems = usedSourceItems

        setItems(items, usedCheckedItemIds)
    }

    override fun updateCheckedItemIds(ids: Set<Long>) = update(checkedItemIds = ids)

    private var sourceItems: List<SourceItem> = listOf()
}

fun List<TemplatedTreeAdapter.SourceItem>.withItemPrepended(sourceItem: TemplatedTreeAdapter.SourceItem) =
    listOf(sourceItem) + this
