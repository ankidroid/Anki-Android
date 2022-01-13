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

import androidx.fragment.app.Fragment
import com.ichi2.utils.ExtendedFragmentFactory

class TagsDialogFactory(val listener: TagsDialogListener) : ExtendedFragmentFactory() {
    override fun instantiate(classLoader: ClassLoader, className: String): Fragment {
        val cls = loadFragmentClass(classLoader, className)
        return if (cls == TagsDialog::class.java) {
            newTagsDialog()
        } else super.instantiate(classLoader, className)
    }

    fun newTagsDialog(): TagsDialog {
        return TagsDialog(listener)
    }
}
