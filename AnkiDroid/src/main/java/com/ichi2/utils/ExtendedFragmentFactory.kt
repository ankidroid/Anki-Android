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
package com.ichi2.utils

import androidx.annotation.CallSuper
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.FragmentManager

/**
 * A factory that enable extending another [FragmentFactory].
 *
 * This should be useful if you want to add extra instantiations without overriding the instantiations in an old factory
 */
abstract class ExtendedFragmentFactory : FragmentFactory {
    private var mBaseFactory: FragmentFactory? = null

    /**
     * Create an extended factory from a base factory
     */
    constructor(baseFactory: FragmentFactory) {
        mBaseFactory = baseFactory
    }

    /**
     * Create a factory with no base, you can assign a base factory later using [.setBaseFactory]
     */
    constructor() {}

    /**
     * Typically you want to return the result of a super call as the last result, so if the passed class couldn't be
     * instantiated by the extending factory, the base factory should instantiate it.
     */
    @CallSuper
    override fun instantiate(classLoader: ClassLoader, className: String): Fragment {
        return mBaseFactory?.instantiate(classLoader, className)
            ?: super.instantiate(classLoader, className)
    }

    /**
     * Sets a base factory to be used as a fallback
     */
    fun setBaseFactory(baseFactory: FragmentFactory?) {
        mBaseFactory = baseFactory
    }

    /**
     * Attaches the factory to an activity by setting the current activity fragment factory as the base factory
     * and updating the activity with the extended factory
     */
    fun <F : ExtendedFragmentFactory?> attachToActivity(activity: AppCompatActivity): F {
        @Suppress("UNCHECKED_CAST")
        return attachToFragmentManager<ExtendedFragmentFactory>(activity.supportFragmentManager) as F
    }

    /**
     * Attaches the factory to a fragment manager by setting the current fragment factory as the base factory
     * and updating the fragment manager with the extended factory
     */
    fun <F : ExtendedFragmentFactory?> attachToFragmentManager(fragmentManager: FragmentManager): F {
        mBaseFactory = fragmentManager.fragmentFactory
        fragmentManager.fragmentFactory = this
        @Suppress("UNCHECKED_CAST")
        return this as F
    }
}
