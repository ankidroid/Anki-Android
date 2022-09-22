/*
 *  Copyright (c) 2022 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.testutils

import androidx.fragment.app.Fragment

/** HACK: Mockito had issues with mocking a class loader: https://github.com/ankidroid/Anki-Android/pull/10048 */
class MockFragmentClassLoader : ClassLoader() {

    override fun loadClass(name: String?): Class<*> {
        if (name == FAKE_CLASS_NAME) return Fragment::class.java
        throw IllegalStateException("Only intended for class: '$name'")
    }

    companion object {
        const val FAKE_CLASS_NAME: String = "androidx.fragment.app.Fragment"
    }
}
