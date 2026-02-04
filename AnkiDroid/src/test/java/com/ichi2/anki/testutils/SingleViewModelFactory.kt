/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.testutils

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Implementation of [androidx.lifecycle.ViewModelProvider.Factory] returning a single ViewModel
 *
 * ```kotlin
 * // FragmentUnderTest requires a SampleViewModel
 * val viewModelFactory = SingleViewModelFactory.create(SampleViewModel())
 *
 * val fragmentArgs = Bundle()
 * val scenario = launchFragmentInContainer(fragmentArgs) {
 *     FragmentUnderTest(viewModelFactory)
 * }
 * ```
 */
@Suppress("UNCHECKED_CAST")
class SingleViewModelFactory<T : ViewModel>(
    private val instance: T,
    private val clazz: Class<T>,
) : ViewModelProvider.Factory {
    override fun <R : ViewModel> create(modelClass: Class<R>): R {
        require(modelClass == clazz)
        return instance as R
    }

    companion object {
        inline fun <reified T : ViewModel> create(instance: T): SingleViewModelFactory<T> =
            SingleViewModelFactory(
                instance,
                T::class.java,
            )
    }
}
