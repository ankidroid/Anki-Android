/*
 Copyright (c) 2021 Tarek Mohamed <tarekkma@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.dialogs.tags

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.testing.FragmentScenario
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.browser.IdsFile
import com.ichi2.anki.libanki.testutils.ext.newNote
import com.ichi2.anki.model.CardStateFilter
import com.ichi2.anki.utils.ext.requireParcelable
import com.ichi2.testutils.ParametersUtils
import com.ichi2.testutils.RecyclerViewUtils
import com.ichi2.testutils.parcelledCopy
import com.ichi2.testutils.saveState
import com.ichi2.ui.CheckBoxTriStates
import com.ichi2.ui.CheckBoxTriStates.State.CHECKED
import com.ichi2.ui.CheckBoxTriStates.State.INDETERMINATE
import com.ichi2.ui.CheckBoxTriStates.State.UNCHECKED
import com.ichi2.utils.ListUtil
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.Matchers.lessThan
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import timber.log.Timber
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class TagsDialogTest : RobolectricTest() {
    @Test
    fun `confirming removal of a parent does not preserve its display state`() {
        confirmTags(listOf("B B::child"), parentClicks = 1, selected = listOf("B::child"), preserved = emptyList())
    }

    @Test
    fun `confirming unchanged partial selection preserves the original parent tag`() {
        confirmTags(listOf("B B::child", "B::child"), parentClicks = 0, selected = listOf("B::child"), preserved = listOf("B"))
    }

    @Test
    fun `confirming an overridden partial parent does not restore its original selection`() {
        confirmTags(listOf("B B::child", "B::child"), parentClicks = 2, selected = listOf("B::child"), preserved = emptyList())
    }

    @Test
    fun `confirming a partial child does not export a synthetic parent`() {
        confirmTags(listOf("B::child", ""), parentClicks = 0, selected = emptyList(), preserved = listOf("B::child"))
    }

    /** Opens the dialog for the supplied notes, clicks the parent tag, and verifies the confirmed selection. */
    private fun confirmTags(
        noteTags: List<String>,
        parentClicks: Int,
        selected: List<String>,
        preserved: List<String>,
    ) {
        val ids =
            noteTags.mapIndexed { index, tags ->
                addBasicNote("note $index").id.also { col.tags.bulkAdd(listOf(it), tags) }
            }
        val listener = Mockito.mock(TagsDialogListener::class.java)
        val args = TagsDialog().withArguments(targetContext, TagsDialog.DialogType.EDIT_TAGS, ids).requireArguments()
        FragmentScenario.launch(TagsDialog::class.java, args, R.style.Theme_Light, TagsDialogFactory(listener)).use { scenario ->
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.onFragment { fragment ->
                advanceRobolectricLooperUntil { fragment.binding.tagsList.adapter != null }
                val recycler = fragment.binding.tagsList
                recycler.measure(0, 0)
                recycler.layout(0, 0, 100, 1000)
                val parent = RecyclerViewUtils.viewHolderAt<TagsArrayAdapter.ViewHolder>(recycler, 0)
                repeat(parentClicks) { parent.checkBoxView.performClick() }
                (fragment.requireDialog() as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                advanceRobolectricLooper()
                Mockito.verify(listener).onSelectedTags(selected, preserved, CardStateFilter.ALL_CARDS)
            }
        }
    }

    @Test
    fun `missing selection dismisses tags dialog without submitting`() = assertUnavailableSelection { assertTrue(delete()) }

    @Test
    fun `truncated selection dismisses tags dialog without submitting`() =
        assertUnavailableSelection {
            writeBytes(readBytes().dropLast(1).toByteArray())
        }

    private fun assertUnavailableSelection(changeFile: IdsFile.() -> Unit) {
        val listener = Mockito.mock(TagsDialogListener::class.java)
        val fragment = TagsDialog(listener).withArguments(targetContext, TagsDialog.DialogType.EDIT_TAGS, listOf(addBasicNote().id))
        fragment.requireArguments().requireParcelable<IdsFile>(TagsDialog.ARG_TAGS_FILE).changeFile()
        Robolectric.buildActivity(FragmentActivity::class.java).use { controller ->
            controller.get().setTheme(R.style.Theme_Light)
            val activity = controller.setup().get()
            fragment.show(activity.supportFragmentManager, "tags")
            advanceRobolectricLooper()
            assertNull(activity.supportFragmentManager.findFragmentByTag("tags"))
            assertEquals(targetContext.getString(CommonString.something_wrong), ShadowToast.getTextOfLatestToast())
            Mockito.verifyNoInteractions(listener)
        }
    }

    @Test
    fun `restoring after selection loss dismisses tags dialog`() {
        val fragment = TagsDialog().withArguments(targetContext, TagsDialog.DialogType.EDIT_TAGS, listOf(addBasicNote().id))
        val file = fragment.requireArguments().requireParcelable<IdsFile>(TagsDialog.ARG_TAGS_FILE)
        val savedState =
            Robolectric.buildActivity(FragmentActivity::class.java).use { controller ->
                controller.get().setTheme(R.style.Theme_Light)
                val activity = controller.setup().get()
                fragment.show(activity.supportFragmentManager, "tags")
                advanceRobolectricLooper()
                controller.saveState().parcelledCopy(FragmentActivity::class.java.classLoader)
            }
        assertTrue(file.delete())
        ShadowToast.reset()
        Robolectric.buildActivity(FragmentActivity::class.java).use { controller ->
            controller.get().setTheme(R.style.Theme_Light)
            val activity = controller.setup(savedState).get()
            advanceRobolectricLooper()
            assertNull(activity.supportFragmentManager.findFragmentByTag("tags"))
            assertEquals(targetContext.getString(CommonString.something_wrong), ShadowToast.getTextOfLatestToast())
        }
    }

    // regression test #8762
    // test for #8763
    @Test
    fun test_AddNewTag_shouldBeVisibleInRecyclerView_andSortedCorrectly() {
        val type = TagsDialog.DialogType.EDIT_TAGS
        val allTags = listOf("a", "b", "d", "e")
        val checkedTags = arrayListOf("a", "b")
        val args =
            TagsDialog(ParametersUtils.whatever())
                .withTestArguments(type, checkedTags, allTags)
                .requireArguments()
        val mockListener = Mockito.mock(TagsDialogListener::class.java)
        val factory = TagsDialogFactory(mockListener)
        runTagsDialogScenario(args, factory) { f: TagsDialog ->
            val recycler = f.binding.tagsList
            val tag = "zzzz"
            f.addTag(tag)

            // workaround robolectric recyclerView issue
            // update recycler
            recycler.measure(0, 0)
            recycler.layout(0, 0, 100, 1000)
            val lastItem = RecyclerViewUtils.viewHolderAt<TagsArrayAdapter.ViewHolder>(recycler, 4)
            val newTagItemItem = RecyclerViewUtils.viewHolderAt<TagsArrayAdapter.ViewHolder>(recycler, 2)
            Assert.assertEquals(5, recycler.adapter!!.itemCount.toLong())
            Assert.assertEquals(tag, newTagItemItem.text)
            Assert.assertTrue(newTagItemItem.isChecked)
            Assert.assertNotEquals(tag, lastItem.text)
            Assert.assertFalse(lastItem.isChecked)
        }
    }

    // test for #8763
    @Test
    fun test_AddNewTag_existingTag_shouldBeSelectedAndSorted() {
        val type = TagsDialog.DialogType.EDIT_TAGS
        val allTags = listOf("a", "b", "d", "e")
        val checkedTags = arrayListOf("a", "b")
        val args =
            TagsDialog(ParametersUtils.whatever())
                .withTestArguments(type, checkedTags, allTags)
                .requireArguments()
        val mockListener = Mockito.mock(TagsDialogListener::class.java)
        val factory = TagsDialogFactory(mockListener)
        runTagsDialogScenario(args, factory) { f: TagsDialog ->
            val recycler = f.binding.tagsList
            val tag = "e"
            f.addTag(tag)

            // workaround robolectric recyclerView issue
            // update recycler
            recycler.measure(0, 0)
            recycler.layout(0, 0, 100, 1000)
            val lastItem = RecyclerViewUtils.viewHolderAt<TagsArrayAdapter.ViewHolder>(recycler, 3)
            val newTagItemItem = RecyclerViewUtils.viewHolderAt<TagsArrayAdapter.ViewHolder>(recycler, 2)
            Assert.assertEquals(4, recycler.adapter!!.itemCount.toLong())
            Assert.assertEquals(tag, newTagItemItem.text)
            Assert.assertTrue(newTagItemItem.isChecked)
            Assert.assertNotEquals(tag, lastItem.text)
            Assert.assertFalse(lastItem.isChecked)
        }
    }

    @Test
    fun `unchecking parent after check all keeps it indeterminate`() {
        withTagsAfterTogglingAll(arrayListOf("1")) { parent, child ->
            parent.performClick()
            assertThat(parent.state, equalTo(INDETERMINATE))
            assertThat(child.state, equalTo(CHECKED))

            child.performClick()
            assertThat(parent.state, equalTo(UNCHECKED))
        }
    }

    @Test
    fun `uncheck all clears the indeterminate cycle for parents`() {
        withTagsAfterTogglingAll(arrayListOf("1", "1::2")) { parent, child ->
            parent.performClick()
            parent.performClick()
            assertThat(parent.state, equalTo(UNCHECKED))
            assertThat(child.state, equalTo(UNCHECKED))
        }
    }

    @Test
    fun `partially selected hierarchical tags are visible at the top`() {
        val first = addBasicNote("first")
        val second = addBasicNote("second")
        val unrelated = addBasicNote("unrelated")
        col.tags.bulkAdd(listOf(first.id), "B::B")
        col.tags.bulkAdd(listOf(second.id), "C::child::leaf")
        col.tags.bulkAdd(listOf(unrelated.id), "aaa")
        val args =
            TagsDialog()
                .withArguments(targetContext, TagsDialog.DialogType.EDIT_TAGS, listOf(first.id, second.id))
                .requireArguments()

        runTagsDialogScenario(args) { fragment ->
            val recycler = fragment.binding.tagsList
            recycler.measure(0, 0)
            recycler.layout(0, 0, 100, 1000)
            val expected = listOf("B", "B::B", "C", "C::child", "C::child::leaf", "aaa")
            assertEquals(expected.size, recycler.adapter!!.itemCount)
            expected.forEachIndexed { index, tag ->
                val holder = RecyclerViewUtils.viewHolderAt<TagsArrayAdapter.ViewHolder>(recycler, index)
                assertEquals(tag, holder.text)
                assertEquals(if (tag == "aaa") UNCHECKED else INDETERMINATE, holder.checkboxState)
            }
        }
    }

    @Test
    fun test_checked_unchecked_indeterminate() {
        val type = TagsDialog.DialogType.EDIT_TAGS
        val expectedAllTags = listOf("a", "b", "d", "e")
        val checkedTags = arrayListOf("a", "b")
        val expectedCheckedTags = listOf("a", "b")
        val expectedUncheckedTags = listOf("d", "e")
        val expectedIndeterminate = emptyList<String>()
        val args =
            TagsDialog(ParametersUtils.whatever())
                .withTestArguments(type, checkedTags, expectedAllTags)
                .requireArguments()
        val mockListener = Mockito.mock(TagsDialogListener::class.java)
        val factory = TagsDialogFactory(mockListener)
        runTagsDialogScenario(args, factory) { f: TagsDialog ->
            val recycler = f.binding.tagsList

            // workaround robolectric recyclerView issue
            // update recycler
            recycler.measure(0, 0)
            recycler.layout(0, 0, 100, 1000)
            val itemCount = recycler.adapter!!.itemCount
            val foundAllTags: MutableList<String> = ArrayList()
            val foundCheckedTags: MutableList<String> = ArrayList()
            val foundUncheckedTags: MutableList<String> = ArrayList()
            val foundIndeterminate: MutableList<String> = ArrayList()
            for (i in 0 until itemCount) {
                val vh = RecyclerViewUtils.viewHolderAt<TagsArrayAdapter.ViewHolder>(recycler, i)
                val tag = vh.text
                foundAllTags.add(tag)
                when (vh.checkboxState) {
                    CheckBoxTriStates.State.INDETERMINATE -> foundIndeterminate.add(tag)
                    CheckBoxTriStates.State.UNCHECKED -> foundUncheckedTags.add(tag)
                    CheckBoxTriStates.State.CHECKED -> foundCheckedTags.add(tag)
                }
            }
            ListUtil.assertListEquals(expectedAllTags, foundAllTags)
            ListUtil.assertListEquals(expectedCheckedTags, foundCheckedTags)
            ListUtil.assertListEquals(expectedUncheckedTags, foundUncheckedTags)
            ListUtil.assertListEquals(expectedIndeterminate, foundIndeterminate)
        }
    }

    @Test
    fun test_TagsDialog_expandPathToCheckedTagsUponOpening() {
        val type = TagsDialog.DialogType.FILTER_BY_TAG
        val allTags =
            listOf(
                "fruit::apple",
                "fruit::pear",
                "fruit::pear::big",
                "sport::football",
                "sport::tennis",
                "book",
            )
        val checkedTags =
            arrayListOf(
                "fruit::pear::big",
                "sport::tennis",
            )
        val args =
            TagsDialog(ParametersUtils.whatever())
                .withTestArguments(type, checkedTags, allTags)
                .requireArguments()
        val mockListener = Mockito.mock(TagsDialogListener::class.java)
        val factory = TagsDialogFactory(mockListener)
        runTagsDialogScenario(args, factory) { f: TagsDialog ->
            val recycler = f.binding.tagsList

            fun getItem(index: Int): TagsArrayAdapter.ViewHolder = RecyclerViewUtils.viewHolderAt(recycler, index)

            fun updateLayout() {
                recycler.measure(0, 0)
                recycler.layout(0, 0, 100, 2000)
            }
            updateLayout()

            // v fruit         [-]
            //   - apple       [ ]
            //   v pear        [-]
            //     - big       [x]
            // v sport         [-]
            //   - football    [ ]
            //   - tennis      [x]
            // - book          [ ]
            Assert.assertEquals(8, recycler.adapter!!.itemCount.toLong())
            Assert.assertEquals("fruit", getItem(0).text)
            Assert.assertEquals("fruit::apple", getItem(1).text)
            Assert.assertEquals("fruit::pear", getItem(2).text)
            Assert.assertEquals("fruit::pear::big", getItem(3).text)
            Assert.assertEquals("sport", getItem(4).text)
            Assert.assertEquals("sport::football", getItem(5).text)
            Assert.assertEquals("sport::tennis", getItem(6).text)
            Assert.assertEquals("book", getItem(7).text)
        }
    }

    @Test
    fun test_AddNewTag_newHierarchicalTag_pathToItShouldBeExpanded() {
        val type = TagsDialog.DialogType.EDIT_TAGS
        val allTags = listOf("common::speak", "common::speak::daily", "common::sport::tennis", "common::sport::football")
        val checkedTags = arrayListOf("common::speak::daily", "common::sport::tennis")
        val args =
            TagsDialog(ParametersUtils.whatever())
                .withTestArguments(type, checkedTags, allTags)
                .requireArguments()
        val mockListener = Mockito.mock(TagsDialogListener::class.java)
        val factory = TagsDialogFactory(mockListener)
        runTagsDialogScenario(args, factory) { f: TagsDialog ->
            val recycler = f.binding.tagsList
            val tag = "common::sport::football::small"
            f.addTag(tag)

            // v common        [-]
            //   v speak       [-]
            //     - daily     [x]
            //   v sport       [-]
            //     v football  [-]
            //       > small   [x]
            //     - tennis    [x]
            recycler.measure(0, 0)
            recycler.layout(0, 0, 100, 1000)
            Assert.assertEquals(7, recycler.adapter!!.itemCount.toLong())
            val item0 = RecyclerViewUtils.viewHolderAt<TagsArrayAdapter.ViewHolder>(recycler, 0)
            val item1 = RecyclerViewUtils.viewHolderAt<TagsArrayAdapter.ViewHolder>(recycler, 1)
            val item2 = RecyclerViewUtils.viewHolderAt<TagsArrayAdapter.ViewHolder>(recycler, 2)
            val item3 = RecyclerViewUtils.viewHolderAt<TagsArrayAdapter.ViewHolder>(recycler, 3)
            val item4 = RecyclerViewUtils.viewHolderAt<TagsArrayAdapter.ViewHolder>(recycler, 4)
            val item5 = RecyclerViewUtils.viewHolderAt<TagsArrayAdapter.ViewHolder>(recycler, 5)
            val item6 = RecyclerViewUtils.viewHolderAt<TagsArrayAdapter.ViewHolder>(recycler, 6)
            Assert.assertEquals("common", item0.text)
            Assert.assertEquals("common::speak", item1.text)
            Assert.assertEquals("common::speak::daily", item2.text)
            Assert.assertEquals("common::sport", item3.text)
            Assert.assertEquals("common::sport::football", item4.text)
            Assert.assertEquals("common::sport::football::small", item5.text)
            Assert.assertEquals("common::sport::tennis", item6.text)
            Assert.assertEquals(CheckBoxTriStates.State.INDETERMINATE, item0.checkBoxView.state)
            Assert.assertEquals(CheckBoxTriStates.State.INDETERMINATE, item1.checkBoxView.state)
            Assert.assertEquals(CheckBoxTriStates.State.CHECKED, item2.checkBoxView.state)
            Assert.assertEquals(CheckBoxTriStates.State.INDETERMINATE, item3.checkBoxView.state)
            Assert.assertEquals(CheckBoxTriStates.State.INDETERMINATE, item4.checkBoxView.state)
            Assert.assertTrue(item5.checkBoxView.isChecked)
            Assert.assertTrue(item6.checkBoxView.isChecked)
        }
    }

    @Test
    fun test_AddNewTag_newHierarchicalTag_willUniformHierarchicalTag() {
        val type = TagsDialog.DialogType.EDIT_TAGS
        val allTags = listOf("common")
        val checkedTags = arrayListOf("common")
        val args =
            TagsDialog(ParametersUtils.whatever())
                .withTestArguments(type, checkedTags, allTags)
                .requireArguments()
        val mockListener = Mockito.mock(TagsDialogListener::class.java)
        val factory = TagsDialogFactory(mockListener)
        runTagsDialogScenario(args, factory) { f: TagsDialog ->
            val recycler = f.binding.tagsList
            val tag = "common::::careless"
            f.addTag(tag)

            // v common        [x]
            //   > blank       [-]
            //     - careless  [x]
            recycler.measure(0, 0)
            recycler.layout(0, 0, 100, 1000)
            Assert.assertEquals(3, recycler.adapter!!.itemCount.toLong())
            val item0 = RecyclerViewUtils.viewHolderAt<TagsArrayAdapter.ViewHolder>(recycler, 0)
            val item1 = RecyclerViewUtils.viewHolderAt<TagsArrayAdapter.ViewHolder>(recycler, 1)
            val item2 = RecyclerViewUtils.viewHolderAt<TagsArrayAdapter.ViewHolder>(recycler, 2)
            Assert.assertEquals("common", item0.text)
            Assert.assertEquals("common::blank", item1.text)
            Assert.assertEquals("common::blank::careless", item2.text)
            Assert.assertTrue(item0.checkBoxView.isChecked)
            Assert.assertTrue(item1.checkBoxView.state == CheckBoxTriStates.State.INDETERMINATE)
            Assert.assertTrue(item2.checkBoxView.isChecked)
        }
    }

    @Test
    fun test_SearchTag_showAllRelevantTags() {
        val type = TagsDialog.DialogType.FILTER_BY_TAG
        val allTags =
            listOf(
                "common::speak",
                "common::speak::tennis",
                "common::sport::tennis",
                "common::sport::football",
                "common::sport::football::small",
            )
        val checkedTags =
            arrayListOf(
                "common::speak::tennis",
                "common::sport::tennis",
                "common::sport::football::small",
            )
        val args =
            TagsDialog(ParametersUtils.whatever())
                .withTestArguments(type, checkedTags, allTags)
                .requireArguments()
        val mockListener = Mockito.mock(TagsDialogListener::class.java)
        val factory = TagsDialogFactory(mockListener)
        runTagsDialogScenario(args, factory) { f: TagsDialog ->
            val recycler = f.binding.tagsList
            val adapter = recycler.adapter!! as TagsArrayAdapter
            adapter.filter.filter("tennis")

            // v common        [-]
            //   v speak       [-]
            //     - tennis    [x]
            //   v sport       [-]
            //     - tennis    [x]
            recycler.measure(0, 0)
            recycler.layout(0, 0, 100, 1000)
            Assert.assertEquals(5, recycler.adapter!!.itemCount.toLong())
            val item0 = RecyclerViewUtils.viewHolderAt<TagsArrayAdapter.ViewHolder>(recycler, 0)
            val item1 = RecyclerViewUtils.viewHolderAt<TagsArrayAdapter.ViewHolder>(recycler, 1)
            val item2 = RecyclerViewUtils.viewHolderAt<TagsArrayAdapter.ViewHolder>(recycler, 2)
            val item3 = RecyclerViewUtils.viewHolderAt<TagsArrayAdapter.ViewHolder>(recycler, 3)
            val item4 = RecyclerViewUtils.viewHolderAt<TagsArrayAdapter.ViewHolder>(recycler, 4)
            Assert.assertEquals("common", item0.text)
            Assert.assertEquals("common::speak", item1.text)
            Assert.assertEquals("common::speak::tennis", item2.text)
            Assert.assertEquals("common::sport", item3.text)
            Assert.assertEquals("common::sport::tennis", item4.text)
        }
    }

    @Test
    fun test_SearchTag_willInheritExpandState() {
        val type = TagsDialog.DialogType.FILTER_BY_TAG
        val allTags = listOf("common::speak", "common::sport::tennis")
        val checkedTags = arrayListOf<String>()
        val args =
            TagsDialog(ParametersUtils.whatever())
                .withTestArguments(type, checkedTags, allTags)
                .requireArguments()
        val mockListener = Mockito.mock(TagsDialogListener::class.java)
        val factory = TagsDialogFactory(mockListener)
        runTagsDialogScenario(args, factory) { f: TagsDialog ->
            val recycler = f.binding.tagsList

            fun updateLayout() {
                recycler.measure(0, 0)
                recycler.layout(0, 0, 100, 2000)
            }

            val adapter = recycler.adapter!! as TagsArrayAdapter
            adapter.filter.filter("sport")
            updateLayout()
            // v common     [ ]
            //   v sport    [ ]
            //     - tennis [ ]
            Assert.assertEquals(3, recycler.adapter!!.itemCount.toLong())

            adapter.filter.filter("")
            updateLayout()
            // v common     [ ]
            //   - speak    [ ]
            //   v sport    [ ]
            //     - tennis [ ]
            Assert.assertEquals(4, recycler.adapter!!.itemCount.toLong())
        }
    }

    @Test
    fun test_CheckTags_intermediateTagsShouldToggleDynamically() {
        val type = TagsDialog.DialogType.FILTER_BY_TAG
        val allTags =
            listOf(
                "common::speak",
                "common::speak::tennis",
                "common::sport::tennis",
                "common::sport::football",
                "common::sport::football::small",
            )
        val checkedTags = arrayListOf<String>()
        val args =
            TagsDialog(ParametersUtils.whatever())
                .withTestArguments(type, checkedTags, allTags)
                .requireArguments()
        val mockListener = Mockito.mock(TagsDialogListener::class.java)
        val factory = TagsDialogFactory(mockListener)
        runTagsDialogScenario(args, factory) { f: TagsDialog ->
            val recycler = f.binding.tagsList

            fun getItem(index: Int): TagsArrayAdapter.ViewHolder = RecyclerViewUtils.viewHolderAt(recycler, index)

            fun updateLayout() {
                recycler.measure(0, 0)
                recycler.layout(0, 0, 100, 2000)
            }
            updateLayout()
            getItem(0).itemView.performClick()
            updateLayout()
            getItem(1).itemView.performClick()
            updateLayout()
            getItem(3).itemView.performClick()
            updateLayout()
            getItem(4).itemView.performClick()
            updateLayout()
            // v common        [ ]
            //   v speak       [ ]
            //     - tennis    [ ]
            //   v sport       [ ]
            //     v football  [ ]
            //       - small   [ ]
            //     - tennis    [ ]
            Assert.assertEquals(7, recycler.adapter!!.itemCount.toLong())

            getItem(2).checkBoxView.performClick()
            updateLayout()
            getItem(5).checkBoxView.performClick()
            updateLayout()
            // v common        [-]
            //   v speak       [-]
            //     - tennis    [x]
            //   v sport       [-]
            //     v football  [-]
            //       - small   [x]
            //     - tennis    [ ]
            Assert.assertEquals(CheckBoxTriStates.State.INDETERMINATE, getItem(0).checkboxState)
            Assert.assertEquals(CheckBoxTriStates.State.INDETERMINATE, getItem(1).checkboxState)
            Assert.assertEquals(CheckBoxTriStates.State.CHECKED, getItem(2).checkboxState)
            Assert.assertEquals(CheckBoxTriStates.State.INDETERMINATE, getItem(3).checkboxState)
            Assert.assertEquals(CheckBoxTriStates.State.INDETERMINATE, getItem(4).checkboxState)
            Assert.assertEquals(CheckBoxTriStates.State.CHECKED, getItem(5).checkboxState)
            Assert.assertEquals(CheckBoxTriStates.State.UNCHECKED, getItem(6).checkboxState)

            getItem(2).checkBoxView.performClick()
            updateLayout()
            getItem(5).checkBoxView.performClick()
            updateLayout()
            // v common        [ ]
            //   v speak       [ ]
            //     - tennis    [ ]
            //   v sport       [ ]
            //     v football  [ ]
            //       - small   [ ]
            //     - tennis    [ ]
            Assert.assertEquals(CheckBoxTriStates.State.UNCHECKED, getItem(0).checkboxState)
            Assert.assertEquals(CheckBoxTriStates.State.UNCHECKED, getItem(1).checkboxState)
            Assert.assertEquals(CheckBoxTriStates.State.UNCHECKED, getItem(2).checkboxState)
            Assert.assertEquals(CheckBoxTriStates.State.UNCHECKED, getItem(3).checkboxState)
            Assert.assertEquals(CheckBoxTriStates.State.UNCHECKED, getItem(4).checkboxState)
            Assert.assertEquals(CheckBoxTriStates.State.UNCHECKED, getItem(5).checkboxState)
            Assert.assertEquals(CheckBoxTriStates.State.UNCHECKED, getItem(6).checkboxState)

            getItem(5).checkBoxView.performClick()
            updateLayout()
            // v common        [-]
            //   v speak       [ ]
            //     - tennis    [ ]
            //   v sport       [-]
            //     v football  [-]
            //       - small   [x]
            //     - tennis    [ ]
            Assert.assertEquals(CheckBoxTriStates.State.INDETERMINATE, getItem(0).checkboxState)
            Assert.assertEquals(CheckBoxTriStates.State.UNCHECKED, getItem(1).checkboxState)
            Assert.assertEquals(CheckBoxTriStates.State.UNCHECKED, getItem(2).checkboxState)
            Assert.assertEquals(CheckBoxTriStates.State.INDETERMINATE, getItem(3).checkboxState)
            Assert.assertEquals(CheckBoxTriStates.State.INDETERMINATE, getItem(4).checkboxState)
            Assert.assertEquals(CheckBoxTriStates.State.CHECKED, getItem(5).checkboxState)
            Assert.assertEquals(CheckBoxTriStates.State.UNCHECKED, getItem(6).checkboxState)

            getItem(3).checkBoxView.performClick()
            updateLayout()
            getItem(5).checkBoxView.performClick()
            updateLayout()
            // v common        [-]
            //   v speak       [ ]
            //     - tennis    [ ]
            //   v sport       [x]
            //     v football  [ ]
            //       - small   [ ]
            //     - tennis    [ ]
            Assert.assertEquals(CheckBoxTriStates.State.INDETERMINATE, getItem(0).checkboxState)
            Assert.assertEquals(CheckBoxTriStates.State.UNCHECKED, getItem(1).checkboxState)
            Assert.assertEquals(CheckBoxTriStates.State.UNCHECKED, getItem(2).checkboxState)
            Assert.assertEquals(CheckBoxTriStates.State.CHECKED, getItem(3).checkboxState)
            Assert.assertEquals(CheckBoxTriStates.State.UNCHECKED, getItem(4).checkboxState)
            Assert.assertEquals(CheckBoxTriStates.State.UNCHECKED, getItem(5).checkboxState)
            Assert.assertEquals(CheckBoxTriStates.State.UNCHECKED, getItem(6).checkboxState)
        }
    }

    @Test // #11089
    fun test_SearchTag_spaceWillBeFilteredCorrectly() {
        val type = TagsDialog.DialogType.FILTER_BY_TAG
        val allTags = listOf("hello::world")
        val checkedTags = arrayListOf<String>()
        val args =
            TagsDialog(ParametersUtils.whatever())
                .withTestArguments(type, checkedTags, allTags)
                .requireArguments()
        val mockListener = Mockito.mock(TagsDialogListener::class.java)
        val factory = TagsDialogFactory(mockListener)
        runTagsDialogScenario(args, factory) { f: TagsDialog ->
            val toolbar = f.binding.toolbar.root
            val searchView = toolbar.menu.findItem(R.id.tags_dialog_action_filter).actionView!!
            val editText = searchView.findViewById<EditText>(androidx.appcompat.R.id.search_src_text)!!

            editText.setText("hello ")
            Assert.assertEquals(
                "The space should be replaced by '::' without mistakenly clear everything.",
                "hello::",
                editText.text.toString(),
            )

            editText.setText("hello")
            editText.text.insert(5, " ")
            Assert.assertEquals("Should complete 2 colons.", "hello::", editText.text.toString())

            editText.setText("hello:")
            editText.text.insert(6, " ")
            Assert.assertEquals("Should complete 1 colon.", "hello::", editText.text.toString())

            editText.setText("hello::")
            editText.text.insert(7, " ")
            Assert.assertEquals("Should do nothing.", "hello::", editText.text.toString())

            editText.setText("")
            editText.text.insert(0, " ")
            Assert.assertEquals("Should not crash.", "::", editText.text.toString())
        }
    }

    @Test
    fun `unicode tags can be serialized 16576`() {
        val type = TagsDialog.DialogType.FILTER_BY_TAG
        val allTags = listOf("02动作状态")

        val args =
            TagsDialog(ParametersUtils.whatever())
                .withTestArguments(type, arrayListOf(), allTags)
                .arguments
        val mockListener = Mockito.mock(TagsDialogListener::class.java)
        val factory = TagsDialogFactory(mockListener)
        FragmentScenario.launch(TagsDialog::class.java, args, R.style.Theme_Light, factory).use { scenario ->
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.onFragment { Timber.d("Dialog successfully opened") }
        }
    }

    @Test
    fun `squeezed dialog does not inflate every tag 22082`() {
        val allTags = (1..50).map { "tag$it" }
        val args =
            TagsDialog(ParametersUtils.whatever())
                .withTestArguments(TagsDialog.DialogType.FILTER_BY_TAG, arrayListOf(), allTags)
                .requireArguments()
        runTagsDialogScenario(args) { f: TagsDialog ->
            val recycler = f.binding.tagsList
            val content = f.binding.root

            content.measure(
                View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(50, View.MeasureSpec.AT_MOST),
            )
            content.layout(0, 0, content.measuredWidth, content.measuredHeight)

            assertThat(recycler.height, greaterThanOrEqualTo(0))
            assertThat(recycler.childCount, lessThan(allTags.size))
        }
    }

    @Test
    @Config(qualifiers = "w731dp-h411dp")
    fun `keyboard pans the dialog on a short screen 22082`() {
        runTagsDialogScenario(editTagsArguments()) { f: TagsDialog ->
            assertThat(f.softInputAdjustment, equalTo(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN))
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h914dp")
    @Suppress("DEPRECATION")
    fun `keyboard resizes the dialog on a tall screen`() {
        runTagsDialogScenario(editTagsArguments()) { f: TagsDialog ->
            assertThat(f.softInputAdjustment, equalTo(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE))
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h914dp")
    fun `rotating to a short screen pans the dialog 22082`() {
        runTagsDialogScenario(editTagsArguments()) { f: TagsDialog ->
            val landscape =
                Configuration(f.resources.configuration).apply {
                    screenWidthDp = 914
                    screenHeightDp = 411
                    orientation = Configuration.ORIENTATION_LANDSCAPE
                }
            f.onConfigurationChanged(landscape)

            assertThat(f.softInputAdjustment, equalTo(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN))
        }
    }

    private val TagsDialog.softInputAdjustment: Int
        get() = requireDialog().window!!.attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST

    private fun editTagsArguments() =
        TagsDialog(ParametersUtils.whatever())
            .withTestArguments(TagsDialog.DialogType.EDIT_TAGS, arrayListOf(), listOf("a"))
            .requireArguments()

    private fun withTagsAfterTogglingAll(
        checkedTags: ArrayList<String>,
        block: (parent: CheckBoxTriStates, child: CheckBoxTriStates) -> Unit,
    ) {
        val args =
            TagsDialog()
                .withTestArguments(TagsDialog.DialogType.EDIT_TAGS, checkedTags, listOf("1", "1::2"))
                .requireArguments()
        runTagsDialogScenario(args) { fragment ->
            val toolbar = fragment.binding.toolbar.root
            toolbar.menu.performIdentifierAction(R.id.tags_dialog_action_select_all, 0)

            val recycler = fragment.binding.tagsList
            recycler.measure(0, 0)
            recycler.layout(0, 0, 100, 1000)
            val parent = RecyclerViewUtils.viewHolderAt<TagsArrayAdapter.ViewHolder>(recycler, 0).checkBoxView
            val child = RecyclerViewUtils.viewHolderAt<TagsArrayAdapter.ViewHolder>(recycler, 1).checkBoxView
            block(parent, child)
        }
    }

    // these are called 'withTestArguments' due to "extension is shadowed by a member" warnings
    // this is needed so we can pass in 'targetContext' for context.cacheDir
    private fun TagsDialog.withTestArguments(
        type: TagsDialog.DialogType,
        checkedTags: ArrayList<String>,
        allTags: Collection<String>,
    ): TagsDialog {
        val note = col.newNote()
        col.tags.bulkAdd(listOf(note.id), allTags.joinToString(separator = " "))
        col.addNote(note, 0L)
        return withArguments(
            context = targetContext,
            type = type,
            checkedTags = checkedTags,
        )
    }

    private fun runTagsDialogScenario(
        args: Bundle,
        factory: TagsDialogFactory? = null,
        block: (TagsDialog) -> Unit,
    ) {
        FragmentScenario.launch(TagsDialog::class.java, args, R.style.Theme_Light, factory).use { scenario ->
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.onFragment { tagsDialog: TagsDialog ->
                advanceRobolectricLooperUntil { tagsDialog.binding.tagsList.adapter != null }
                block(tagsDialog)
            }
        }
    }
}

val TagsArrayAdapter.ViewHolder.checkBoxView: CheckBoxTriStates
    get() = this.binding.checkBoxView
