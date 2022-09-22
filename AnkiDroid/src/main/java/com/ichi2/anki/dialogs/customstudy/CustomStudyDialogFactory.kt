/*
 Copyright (c) 2021 Tarek Mohamed Abdalla <tarekkma@gmail.com>

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
package com.ichi2.anki.dialogs.customstudy

import androidx.fragment.app.Fragment
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.CustomStudyListener
import com.ichi2.libanki.Collection
import com.ichi2.utils.ExtendedFragmentFactory
import java.util.function.Supplier

class CustomStudyDialogFactory(val collectionSupplier: Supplier<Collection>, private val customStudyListener: CustomStudyListener?) : ExtendedFragmentFactory() {
    override fun instantiate(classLoader: ClassLoader, className: String): Fragment {
        val cls = loadFragmentClass(classLoader, className)
        return if (cls == CustomStudyDialog::class.java) {
            newCustomStudyDialog()
        } else super.instantiate(classLoader, className)
    }

    fun newCustomStudyDialog(): CustomStudyDialog {
        return CustomStudyDialog(collectionSupplier.get(), customStudyListener)
    }
}
