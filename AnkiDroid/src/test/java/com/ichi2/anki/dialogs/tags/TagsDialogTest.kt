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

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.RadioGroup
import androidx.fragment.app.testing.FragmentScenario
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.WhichButton
import com.afollestad.materialdialogs.actions.getActionButton
import com.afollestad.materialdialogs.customview.getCustomView
import com.ichi2.anki.R
import com.ichi2.testutils.ParametersUtils
import com.ichi2.testutils.RecyclerViewUtils
import com.ichi2.ui.CheckBoxTriStates
import com.ichi2.utils.ListUtil
import org.hamcrest.MatcherAssert
import org.hamcrest.core.IsNull
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.whenever
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class TagsDialogTest {
    @Test
    fun testTagsDialogCustomStudyOptionInterface() {
        val type = TagsDialog.DialogType.CUSTOM_STUDY_TAGS
        val allTags = listOf("1", "2", "3", "4")
        val args = TagsDialog(ParametersUtils.whatever())
            .withArguments(type, ArrayList(), allTags)
            .arguments
        val mockListener = Mockito.mock(TagsDialogListener::class.java)
        val factory = TagsDialogFactory(mockListener)
        val scenario = FragmentScenario.launch(TagsDialog::class.java, args, R.style.Theme_AppCompat, factory)
        scenario.moveToState(Lifecycle.State.STARTED)
        scenario.onFragment { f: TagsDialog ->
            val dialog = f.dialog as MaterialDialog?
            MatcherAssert.assertThat(dialog, IsNull.notNullValue())
            val body = dialog!!.getCustomView()
            val optionsGroup = body.findViewById<RadioGroup>(R.id.tags_dialog_options_radiogroup)
            Assert.assertEquals(optionsGroup.visibility.toLong(), View.VISIBLE.toLong())
            val expectedOption = 1
            optionsGroup.getChildAt(expectedOption).performClick()
            dialog.getActionButton(WhichButton.POSITIVE).callOnClick()
            Mockito.verify(mockListener, Mockito.times(1)).onSelectedTags(ArrayList(), ArrayList(), expectedOption)
        }
    }

    @Test
    fun testTagsDialogCustomStudyOptionFragmentAPI() {
        val type = TagsDialog.DialogType.CUSTOM_STUDY_TAGS
        val allTags = listOf("1", "2", "3", "4")
        val args = TagsDialog(ParametersUtils.whatever())
            .withArguments(type, ArrayList(), allTags)
            .arguments
        val scenario = FragmentScenario.launch(TagsDialog::class.java, args, R.style.Theme_AppCompat)
        scenario.moveToState(Lifecycle.State.STARTED)
        scenario.onFragment { f: TagsDialog ->
            val dialog = f.dialog as MaterialDialog?
            MatcherAssert.assertThat(dialog, IsNull.notNullValue())
            val returnedList = AtomicReference<List<String>?>()
            val returnedOption = AtomicInteger()
            f.parentFragmentManager.setFragmentResultListener(
                TagsDialogListener.ON_SELECTED_TAGS_KEY, mockLifecycleOwner(),
                { _: String?, bundle: Bundle ->
                    returnedList.set(bundle.getStringArrayList(TagsDialogListener.ON_SELECTED_TAGS__SELECTED_TAGS))
                    returnedOption.set(bundle.getInt(TagsDialogListener.ON_SELECTED_TAGS__OPTION))
                }
            )
            val body = dialog!!.getCustomView()
            val optionsGroup = body.findViewById<RadioGroup>(R.id.tags_dialog_options_radiogroup)
            Assert.assertEquals(optionsGroup.visibility.toLong(), View.VISIBLE.toLong())
            val expectedOption = 2
            optionsGroup.getChildAt(expectedOption).performClick()
            dialog.getActionButton(WhichButton.POSITIVE).callOnClick()
            ListUtil.assertListEquals(ArrayList(), returnedList.get())
            Assert.assertEquals(expectedOption.toLong(), returnedOption.get().toLong())
        }
    }

    // regression test #8762
    // test for #8763
    @Test
    fun test_AddNewTag_shouldBeVisibleInRecyclerView_andSortedCorrectly() {
        val type = TagsDialog.DialogType.EDIT_TAGS
        val allTags = listOf("a", "b", "d", "e")
        val checkedTags = listOf("a", "b")
        val args = TagsDialog(ParametersUtils.whatever())
            .withArguments(type, checkedTags, allTags)
            .arguments
        val mockListener = Mockito.mock(TagsDialogListener::class.java)
        val factory = TagsDialogFactory(mockListener)
        val scenario = FragmentScenario.launch(TagsDialog::class.java, args, R.style.Theme_AppCompat, factory)
        scenario.moveToState(Lifecycle.State.STARTED)
        scenario.onFragment { f: TagsDialog ->
            val dialog = f.dialog as MaterialDialog?
            MatcherAssert.assertThat(dialog, IsNull.notNullValue())
            val body = dialog!!.getCustomView()
            val recycler: RecyclerView = body.findViewById(R.id.tags_dialog_tags_list)
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
        val checkedTags = listOf("a", "b")
        val args = TagsDialog(ParametersUtils.whatever())
            .withArguments(type, checkedTags, allTags)
            .arguments
        val mockListener = Mockito.mock(TagsDialogListener::class.java)
        val factory = TagsDialogFactory(mockListener)
        val scenario = FragmentScenario.launch(TagsDialog::class.java, args, R.style.Theme_AppCompat, factory)
        scenario.moveToState(Lifecycle.State.STARTED)
        scenario.onFragment { f: TagsDialog ->
            val dialog = f.dialog as MaterialDialog?
            MatcherAssert.assertThat(dialog, IsNull.notNullValue())
            val body = dialog!!.getCustomView()
            val recycler: RecyclerView = body.findViewById(R.id.tags_dialog_tags_list)
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
    fun test_checked_unchecked_indeterminate() {
        val type = TagsDialog.DialogType.EDIT_TAGS
        val expectedAllTags = listOf("a", "b", "d", "e")
        val checkedTags = listOf("a", "b")
        val uncheckedTags = listOf("b", "d")
        val expectedCheckedTags = listOf("a")
        val expectedUncheckedTags = listOf("d", "e")
        val expectedIndeterminate = listOf("b")
        val args = TagsDialog(ParametersUtils.whatever())
            .withArguments(type, checkedTags, uncheckedTags, expectedAllTags)
            .arguments
        val mockListener = Mockito.mock(TagsDialogListener::class.java)
        val factory = TagsDialogFactory(mockListener)
        val scenario = FragmentScenario.launch(TagsDialog::class.java, args, R.style.Theme_AppCompat, factory)
        scenario.moveToState(Lifecycle.State.STARTED)
        scenario.onFragment { f: TagsDialog ->
            val dialog = f.dialog as MaterialDialog?
            MatcherAssert.assertThat(dialog, IsNull.notNullValue())
            val body = dialog!!.getCustomView()
            val recycler: RecyclerView = body.findViewById(R.id.tags_dialog_tags_list)

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
                    else -> Assert.fail("Unknown CheckBoxTriStates.State? " + vh.checkboxState)
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
        val allTags = listOf(
            "fruit::apple", "fruit::pear", "fruit::pear::big", "sport::football", "sport::tennis", "book"
        )
        val checkedTags = listOf(
            "fruit::pear::big", "sport::tennis"
        )
        val args = TagsDialog(ParametersUtils.whatever())
            .withArguments(type, checkedTags, allTags)
            .arguments
        val mockListener = Mockito.mock(TagsDialogListener::class.java)
        val factory = TagsDialogFactory(mockListener)
        val scenario = FragmentScenario.launch(TagsDialog::class.java, args, R.style.Theme_AppCompat, factory)
        scenario.moveToState(Lifecycle.State.STARTED)
        scenario.onFragment { f: TagsDialog ->
            val dialog = f.dialog as MaterialDialog?
            MatcherAssert.assertThat(dialog, IsNull.notNullValue())
            val body = dialog!!.getCustomView()
            val recycler: RecyclerView = body.findViewById(R.id.tags_dialog_tags_list)

            fun getItem(index: Int): TagsArrayAdapter.ViewHolder {
                return RecyclerViewUtils.viewHolderAt(recycler, index)
            }
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
        val checkedTags = listOf("common::speak::daily", "common::sport::tennis")
        val args = TagsDialog(ParametersUtils.whatever())
            .withArguments(type, checkedTags, allTags)
            .arguments
        val mockListener = Mockito.mock(TagsDialogListener::class.java)
        val factory = TagsDialogFactory(mockListener)
        val scenario = FragmentScenario.launch(TagsDialog::class.java, args, R.style.Theme_AppCompat, factory)
        scenario.moveToState(Lifecycle.State.STARTED)
        scenario.onFragment { f: TagsDialog ->
            val dialog = f.dialog as MaterialDialog?
            MatcherAssert.assertThat(dialog, IsNull.notNullValue())
            val body = dialog!!.getCustomView()
            val recycler: RecyclerView = body.findViewById(R.id.tags_dialog_tags_list)
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
            Assert.assertEquals(CheckBoxTriStates.State.INDETERMINATE, item0.mCheckBoxView.state)
            Assert.assertEquals(CheckBoxTriStates.State.INDETERMINATE, item1.mCheckBoxView.state)
            Assert.assertEquals(CheckBoxTriStates.State.CHECKED, item2.mCheckBoxView.state)
            Assert.assertEquals(CheckBoxTriStates.State.INDETERMINATE, item3.mCheckBoxView.state)
            Assert.assertEquals(CheckBoxTriStates.State.INDETERMINATE, item4.mCheckBoxView.state)
            Assert.assertTrue(item5.mCheckBoxView.isChecked)
            Assert.assertTrue(item6.mCheckBoxView.isChecked)
        }
    }

    @Test
    fun test_AddNewTag_newHierarchicalTag_willUniformHierarchicalTag() {
        val type = TagsDialog.DialogType.EDIT_TAGS
        val allTags = listOf("common")
        val checkedTags = listOf("common")
        val args = TagsDialog(ParametersUtils.whatever())
            .withArguments(type, checkedTags, allTags)
            .arguments
        val mockListener = Mockito.mock(TagsDialogListener::class.java)
        val factory = TagsDialogFactory(mockListener)
        val scenario = FragmentScenario.launch(TagsDialog::class.java, args, R.style.Theme_AppCompat, factory)
        scenario.moveToState(Lifecycle.State.STARTED)
        scenario.onFragment { f: TagsDialog ->
            val dialog = f.dialog as MaterialDialog?
            MatcherAssert.assertThat(dialog, IsNull.notNullValue())
            val body = dialog!!.getCustomView()
            val recycler: RecyclerView = body.findViewById(R.id.tags_dialog_tags_list)
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
            Assert.assertTrue(item0.mCheckBoxView.isChecked)
            Assert.assertTrue(item1.mCheckBoxView.state == CheckBoxTriStates.State.INDETERMINATE)
            Assert.assertTrue(item2.mCheckBoxView.isChecked)
        }
    }

    @Test
    fun test_SearchTag_showAllRelevantTags() {
        val type = TagsDialog.DialogType.FILTER_BY_TAG
        val allTags = listOf(
            "common::speak", "common::speak::tennis", "common::sport::tennis",
            "common::sport::football", "common::sport::football::small"
        )
        val checkedTags = listOf(
            "common::speak::tennis", "common::sport::tennis",
            "common::sport::football::small"
        )
        val args = TagsDialog(ParametersUtils.whatever())
            .withArguments(type, checkedTags, allTags)
            .arguments
        val mockListener = Mockito.mock(TagsDialogListener::class.java)
        val factory = TagsDialogFactory(mockListener)
        val scenario = FragmentScenario.launch(TagsDialog::class.java, args, R.style.Theme_AppCompat, factory)
        scenario.moveToState(Lifecycle.State.STARTED)
        scenario.onFragment { f: TagsDialog ->
            val dialog = f.dialog as MaterialDialog?
            MatcherAssert.assertThat(dialog, IsNull.notNullValue())
            val body = dialog!!.getCustomView()
            val recycler: RecyclerView = body.findViewById(R.id.tags_dialog_tags_list)
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
        val checkedTags = emptyList<String>()
        val args = TagsDialog(ParametersUtils.whatever())
            .withArguments(type, checkedTags, allTags)
            .arguments
        val mockListener = Mockito.mock(TagsDialogListener::class.java)
        val factory = TagsDialogFactory(mockListener)
        val scenario = FragmentScenario.launch(TagsDialog::class.java, args, R.style.Theme_AppCompat, factory)
        scenario.moveToState(Lifecycle.State.STARTED)
        scenario.onFragment { f: TagsDialog ->
            val dialog = f.dialog as MaterialDialog?
            MatcherAssert.assertThat(dialog, IsNull.notNullValue())
            val body = dialog!!.getCustomView()
            val recycler: RecyclerView = body.findViewById(R.id.tags_dialog_tags_list)

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
        val allTags = listOf(
            "common::speak", "common::speak::tennis", "common::sport::tennis",
            "common::sport::football", "common::sport::football::small"
        )
        val checkedTags = emptyList<String>()
        val args = TagsDialog(ParametersUtils.whatever())
            .withArguments(type, checkedTags, allTags)
            .arguments
        val mockListener = Mockito.mock(TagsDialogListener::class.java)
        val factory = TagsDialogFactory(mockListener)
        val scenario = FragmentScenario.launch(TagsDialog::class.java, args, R.style.Theme_AppCompat, factory)
        scenario.moveToState(Lifecycle.State.STARTED)
        scenario.onFragment { f: TagsDialog ->
            val dialog = f.dialog as MaterialDialog?
            MatcherAssert.assertThat(dialog, IsNull.notNullValue())
            val body = dialog!!.getCustomView()
            val recycler: RecyclerView = body.findViewById(R.id.tags_dialog_tags_list)

            fun getItem(index: Int): TagsArrayAdapter.ViewHolder {
                return RecyclerViewUtils.viewHolderAt(recycler, index)
            }
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

            getItem(2).mCheckBoxView.performClick()
            updateLayout()
            getItem(5).mCheckBoxView.performClick()
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

            getItem(2).mCheckBoxView.performClick()
            updateLayout()
            getItem(5).mCheckBoxView.performClick()
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

            getItem(5).mCheckBoxView.performClick()
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

            getItem(3).mCheckBoxView.performClick()
            updateLayout()
            getItem(5).mCheckBoxView.performClick()
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
        val checkedTags = emptyList<String>()
        val args = TagsDialog(ParametersUtils.whatever())
            .withArguments(type, checkedTags, allTags)
            .arguments
        val mockListener = Mockito.mock(TagsDialogListener::class.java)
        val factory = TagsDialogFactory(mockListener)
        val scenario = FragmentScenario.launch(TagsDialog::class.java, args, R.style.Theme_AppCompat, factory)
        scenario.moveToState(Lifecycle.State.STARTED)
        scenario.onFragment { f: TagsDialog ->
            val dialog = f.dialog as MaterialDialog?
            MatcherAssert.assertThat(dialog, IsNull.notNullValue())
            val editText = f.getSearchView()!!.findViewById<EditText>(R.id.search_src_text)!!

            editText.setText("hello ")
            Assert.assertEquals(
                "The space should be replaced by '::' without mistakenly clear everything.",
                "hello::", editText.text.toString()
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

    companion object {
        private fun mockLifecycleOwner(): LifecycleOwner {
            val owner = Mockito.mock(LifecycleOwner::class.java)
            val lifecycle = LifecycleRegistry(owner)
            lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            whenever(owner.lifecycle).thenReturn(lifecycle)
            return owner
        }
    }
}
