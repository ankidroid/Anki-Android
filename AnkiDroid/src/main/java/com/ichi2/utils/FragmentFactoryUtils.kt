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
package com.ichi2.utils

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentFactory

object FragmentFactoryUtils {
    /**
     * A convenience util method that instantiate a fragment using the passed activity [FragmentFactory]
     */
    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun <F : Fragment?> instantiate(activity: FragmentActivity, cls: Class<F>): F {
        val factory = activity.supportFragmentManager.fragmentFactory
        return factory.instantiate(activity.classLoader, cls.name) as F
    }
}
