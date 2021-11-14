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
import android.widget.RadioGroup
import androidx.fragment.app.testing.FragmentScenario
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
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
            val body = dialog!!.customView
            val optionsGroup = body!!.findViewById<RadioGroup>(R.id.tags_dialog_options_radiogroup)
            Assert.assertEquals(optionsGroup.visibility.toLong(), View.VISIBLE.toLong())
            val expectedOption = 1
            optionsGroup.getChildAt(expectedOption).performClick()
            dialog.getActionButton(DialogAction.POSITIVE).callOnClick()
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
            val body = dialog!!.customView
            val optionsGroup = body!!.findViewById<RadioGroup>(R.id.tags_dialog_options_radiogroup)
            Assert.assertEquals(optionsGroup.visibility.toLong(), View.VISIBLE.toLong())
            val expectedOption = 2
            optionsGroup.getChildAt(expectedOption).performClick()
            dialog.getActionButton(DialogAction.POSITIVE).callOnClick()
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
            val body = dialog!!.customView
            val recycler: RecyclerView = body!!.findViewById(R.id.tags_dialog_tags_list)
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
            val body = dialog!!.customView
            val recycler: RecyclerView = body!!.findViewById(R.id.tags_dialog_tags_list)
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
            val body = dialog!!.customView
            val recycler: RecyclerView = body!!.findViewById(R.id.tags_dialog_tags_list)

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
                when (vh?.checkboxState) {
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

    companion object {
        private fun mockLifecycleOwner(): LifecycleOwner {
            val owner = Mockito.mock(LifecycleOwner::class.java)
            val lifecycle = LifecycleRegistry(owner)
            lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            Mockito.`when`(owner.lifecycle).thenReturn(lifecycle)
            return owner
        }
    }
}
