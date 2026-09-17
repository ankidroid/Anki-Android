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

import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.ichi2.anki.libanki.NoteId
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.utils.ExtendedFragmentFactory

class TagsDialogFactory(
    val listener: TagsDialogListener,
) : ExtendedFragmentFactory() {
    override fun instantiate(
        classLoader: ClassLoader,
        className: String,
    ): Fragment {
        val cls = loadFragmentClass(classLoader, className)
        return if (cls == TagsDialog::class.java) {
            newTagsDialog()
        } else {
            super.instantiate(classLoader, className)
        }
    }

    fun newTagsDialog(): TagsDialog = TagsDialog(listener)

    @MainThread
    fun showEditTags(
        activity: FragmentActivity,
        noteIds: List<NoteId> = emptyList(),
        checkedTags: ArrayList<String> = arrayListOf(),
    ) {
        val dialog = newTagsDialog().withArguments(activity, TagsDialog.DialogType.EDIT_TAGS, noteIds, checkedTags)
        activity.showDialogFragment(dialog)
    }
}
