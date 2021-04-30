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
package com.ichi2.utils;

import com.ichi2.libanki.Collection;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentFactory;
import androidx.fragment.app.FragmentManager;

/**
 * A factory that enable extending another {@link FragmentFactory}.
 *
 * This should be useful if you want to add extra instantiations without overriding the instantiations in an old factory
 */
public abstract class ExtendedFragmentFactory extends FragmentFactory {

    @Nullable
    private FragmentFactory mBaseFactory;


    /**
     * Create an extended factory from a base factory
     */
    public ExtendedFragmentFactory(@NonNull FragmentFactory baseFactory) {
        mBaseFactory = baseFactory;
    }


    /**
     * Create a factory with no base, you can assign a base factory later using {@link #setBaseFactory(FragmentFactory)}
     */
    public ExtendedFragmentFactory() {}


    /**
     * Typically you want to return the result of a super call as the last result, so if the passed class couldn't be
     * instantiated by the extending factory, the base factory should instantiate it.
     */
    @NonNull
    @CallSuper
    @Override
    public Fragment instantiate(@NonNull ClassLoader classLoader, @NonNull String className) {
        return mBaseFactory != null ? mBaseFactory.instantiate(classLoader, className) : super.instantiate(classLoader, className);
    }


    /**
     * Sets a base factory to be used as a fallback
     */
    public void setBaseFactory(@Nullable FragmentFactory baseFactory) {
        this.mBaseFactory = baseFactory;
    }


    /**
     * Attaches the factory to an activity by setting the current activity fragment factory as the base factory
     * and updating the activity with the extended factory
     */
    public <F extends ExtendedFragmentFactory> F attachToActivity(@NonNull AppCompatActivity activity) {
        return (F) attachToFragmentManager(activity.getSupportFragmentManager());
    }

    /**
     * Attaches the factory to a fragment manager by setting the current fragment factory as the base factory
     * and updating the fragment manager with the extended factory
     */
    public <F extends ExtendedFragmentFactory> F attachToFragmentManager(@NonNull FragmentManager fragmentManager) {
        mBaseFactory = fragmentManager.getFragmentFactory();
        fragmentManager.setFragmentFactory(this);
        return (F) this;
    }
}
