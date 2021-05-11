/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.ui;

import android.util.Pair;
import android.view.MenuItem;

import java.lang.reflect.Method;

import androidx.annotation.CheckResult;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import timber.log.Timber;

/**
 * Detection of whether an item is in the ActionBar overflow
 * WARN: When making changes to this code, also test with Proguard
 * */
public class ActionBarOverflow {
    //Idea from: https://stackoverflow.com/a/29208483

    protected static final String NATIVE_CLASS = "com.android.internal.view.menu.MenuItemImpl";

    protected static final String ANDROIDX_CLASS = "androidx.appcompat.view.menu.MenuItemImpl";

    @Nullable
    protected static Class<?> sNativeClassRef;

    @Nullable
    protected static Class<?> sAndroidXClassRef;

    @Nullable
    protected static Method sNativeIsActionButton;

    @Nullable
    protected static Method sAndroidXIsActionButton;

    static {
        setupMethods(ActionBarOverflow::getPrivateMethodHandleSystemErrors);
    }

    @VisibleForTesting
    static void setupMethods(PrivateMethodAccessor accessor) {
        //Note: Multiple of these can succeed.
        Pair<Class<?>, Method> nativeImpl = accessor.getPrivateMethod(NATIVE_CLASS, "isActionButton");
        sNativeClassRef = nativeImpl.first;
        sNativeIsActionButton = nativeImpl.second;

        Pair<Class<?>, Method> androidXImpl =  accessor.getPrivateMethod(ANDROIDX_CLASS, "isActionButton");
        sAndroidXClassRef = androidXImpl.first;
        sAndroidXIsActionButton = androidXImpl.second;
    }


    @CheckResult
    private static Pair<Class<?>, Method> getPrivateMethodHandleSystemErrors(String className, String methodName) {
        Method action = null;
        Class<?> menuItemImpl = null;
        try {
            //We know this won't always work, we'll log if this isn't the case.
            menuItemImpl = Class.forName(className);
            action = menuItemImpl.getDeclaredMethod(methodName);
            action.setAccessible(true);
            Timber.d("Setup ActionBarOverflow: %s", className);
        } catch (Exception | NoSuchFieldError | NoSuchMethodError ignoreAndLogEx) {
            //See: #5806. MenuItemImpl;->isActionButton is on the light greylist
            //https://developer.android.com/distribute/best-practices/develop/restrictions-non-sdk-interfaces#results-of-keeping-non-sdk
            Timber.d(ignoreAndLogEx, "Failed to handle: %s", className);
        }

        return new Pair<>(menuItemImpl, action);
    }

    /**
     * Check if an item is showing (not in the overflow menu).
     *
     * @param item
     *            the MenuItem.
     * @return {@code true} if the MenuItem is visible on the ActionBar. {@code false} if not. {@code null if unknown}
     */
    public static @Nullable Boolean isActionButton(MenuItem item) {
        if (sNativeClassRef != null && sNativeClassRef.isInstance(item)) {
            return tryInvokeMethod(item, sNativeIsActionButton);
        } else if (sAndroidXClassRef != null && sAndroidXClassRef.isInstance(item)) {
            return tryInvokeMethod(item, sAndroidXIsActionButton);
        } else {
            Timber.w("Unhandled ActionBar class: %s", item.getClass().getName());
            return null;
        }
    }


    private static Boolean tryInvokeMethod(MenuItem item, Method method) {
        try {
            return (boolean) method.invoke(item, (Object[]) null);
        } catch (Exception  | NoSuchFieldError | NoSuchMethodError ex) {
            Timber.w(ex, "Error handling ActionBar class: %s", item.getClass().getName());
            return null;
        }
    }


    @VisibleForTesting
    @FunctionalInterface
    interface PrivateMethodAccessor {
        Pair<Class<?>, Method> getPrivateMethod(String className, String methodName);
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    static boolean hasUsableMethod() {
        return  sNativeIsActionButton != null ||
                sAndroidXIsActionButton != null;

    }

    @CheckResult
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    static Pair<Class<?>, Method> getPrivateMethodOnlyHandleExceptions(String className, String methodName) {
        Method action = null;
        Class<?> menuItemImpl = null;
        try {
            menuItemImpl = Class.forName(className);
            action = menuItemImpl.getDeclaredMethod(methodName);
            action.setAccessible(true);
            Timber.d("Setup ActionBarOverflow: %s", className);
        } catch (Exception ignoreAndLogEx) {
            Timber.d(ignoreAndLogEx, "Failed to handle: %s", className);
        }

        return new Pair<>(menuItemImpl, action);
    }
}
