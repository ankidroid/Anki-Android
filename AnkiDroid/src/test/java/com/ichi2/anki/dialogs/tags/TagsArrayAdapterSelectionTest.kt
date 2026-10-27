// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.dialogs.tags

import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.testutils.RecyclerViewUtils
import com.ichi2.ui.CheckBoxTriStates.State.CHECKED
import com.ichi2.ui.CheckBoxTriStates.State.INDETERMINATE
import com.ichi2.ui.CheckBoxTriStates.State.UNCHECKED
import com.ichi2.utils.TagsUtil.getUpdatedTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

@RunWith(AndroidJUnit4::class)
class TagsArrayAdapterSelectionTest : RobolectricTest() {
    private inner class TagSelection(
        val tags: TagsList,
        val recycler: RecyclerView,
        val adapter: TagsArrayAdapter,
    ) {
        fun holder(tag: String): TagsArrayAdapter.ViewHolder {
            recycler.measure(0, 0)
            recycler.layout(0, 0, 800, 2000)
            return (0 until adapter.itemCount)
                .map { RecyclerViewUtils.viewHolderAt<TagsArrayAdapter.ViewHolder>(recycler, it) }
                .first { it.text == tag }
        }

        fun click(tag: String) = holder(tag).checkBoxView.performClick()

        fun filter(text: String) {
            adapter.filter.filter(text)
            advanceRobolectricLooper()
        }

        fun bulkToggle() {
            tags.toggleAllCheckedStatuses()
            adapter.notifyCheckedStatusesChanged()
        }

        fun saved(previous: List<String>) =
            getUpdatedTags(previous, tags.copyOfCheckedTagList(), tags.copyOfPartiallySelectedTagList()).sorted()
    }

    private fun withTags(
        tags: TagsList,
        block: TagSelection.() -> Unit,
    ) {
        Robolectric.buildActivity(FragmentActivity::class.java).use { controller ->
            controller.get().setTheme(R.style.Theme_Light)
            val activity = controller.setup().get()
            val recycler = RecyclerView(activity)
            recycler.layoutManager = LinearLayoutManager(activity)
            val adapter = TagsArrayAdapter(tags) {}
            recycler.adapter = adapter
            activity.setContentView(recycler)
            TagSelection(tags, recycler, adapter).block()
        }
    }

    @Test
    fun `filtering does not hide partially selected siblings from parent state`() {
        withTags(TagsList(listOf("B::one", "B::two"), listOf("B::one", "B::two"), listOf("B::one", "B::two"))) {
            filter("one")
            click("B::one")
            assertEquals(INDETERMINATE, holder("B").checkboxState)
            filter("")
            assertEquals(INDETERMINATE, holder("B").checkboxState)
            assertEquals(INDETERMINATE, holder("B::two").checkboxState)
        }
    }

    @Test
    fun `filtering does not hide checked siblings from parent state`() {
        withTags(TagsList(listOf("B::one", "B::two"), listOf("B::one", "B::two"))) {
            filter("one")
            click("B::one")
            assertEquals(INDETERMINATE, holder("B").checkboxState)
        }
    }

    @Test
    fun `clearing a child preserves partially selected parent tags when saving`() {
        withTags(TagsList(listOf("B", "B::child"), listOf("B", "B::child"), listOf("B", "B::child"))) {
            click("B::child")
            assertEquals(listOf("B"), saved(listOf("B", "B::child")))
            assertEquals(emptyList<String>(), saved(emptyList()))
        }
    }

    @Test
    fun `synthetic parent states do not add tags when saving`() {
        withTags(TagsList(listOf("B::child"), listOf("B::child"), listOf("B::child"))) {
            assertEquals(listOf("B::child"), saved(listOf("B::child")))
            assertEquals(emptyList<String>(), saved(emptyList()))
            click("B::child")
            assertEquals(emptyList<String>(), saved(listOf("B::child")))
        }
    }

    @Test
    fun `bulk selection replaces original partial parent selection`() {
        withTags(TagsList(listOf("B", "B::child"), listOf("B", "B::child"), listOf("B", "B::child"))) {
            bulkToggle()
            bulkToggle()
            click("B::child")
            assertEquals(INDETERMINATE, holder("B").checkboxState)
            click("B::child")
            assertEquals(UNCHECKED, holder("B").checkboxState)
            assertEquals(emptyList<String>(), saved(listOf("B", "B::child")))
        }
    }

    @Test
    fun `unbound parents update their model state`() {
        withTags(TagsList(listOf("B::child"), listOf("B::child"), listOf("B::child"))) {
            val parent = holder("B")
            adapter.onViewRecycled(parent)
            click("B::child")
            assertFalse(tags.isIndeterminate("B"))
            adapter.onBindViewHolder(parent, 0)
            assertEquals(UNCHECKED, parent.checkboxState)
        }
    }

    @Test
    fun `clearing a partially selected child preserves checked parents`() {
        withTags(TagsList(listOf("B", "B::child"), listOf("B", "B::child"), listOf("B::child"))) {
            click("B::child")
            assertEquals(CHECKED, holder("B").checkboxState)
            assertEquals(listOf("B"), saved(listOf("B", "B::child")))
        }
    }

    @Test
    fun `clearing a child updates parents ignoring case`() {
        withTags(TagsList(listOf("b", "B::child"), listOf("B::CHILD"), listOf("b::child"))) {
            click("B::child")
            assertEquals(UNCHECKED, holder("b").checkboxState)
        }
    }

    @Test
    fun `parent tags are case-insensitive`() {
        val firstTag = "B::first"
        val secondTag = "b::second"
        val tagList = listOf(firstTag, secondTag)
        withTags(TagsList(tagList, tagList)) {
            val firstParentTag = holder(firstTag).node.parent?.tag
            val secondParentTag = holder(secondTag).node.parent?.tag
            assertEquals(firstParentTag, secondParentTag)
            assertEquals(1, holder(firstTag).node.level)
            assertEquals(1, holder(secondTag).node.level)
        }
    }
}
