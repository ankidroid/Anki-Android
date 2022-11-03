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
package com.ichi2.ui

import android.view.MenuItem
import androidx.annotation.CheckResult
import androidx.annotation.VisibleForTesting
import timber.log.Timber
import java.lang.reflect.Method

/**
 * Detection of whether an item is in the ActionBar overflow
 * WARN: When making changes to this code, also test with Proguard
 */
object ActionBarOverflow {
    // Idea from: https://stackoverflow.com/a/29208483
    private const val NATIVE_CLASS = "com.android.internal.view.menu.MenuItemImpl"
    private const val ANDROIDX_CLASS = "androidx.appcompat.view.menu.MenuItemImpl"
    private var sNativeClassRef: Class<*>? = null
    private var sAndroidXClassRef: Class<*>? = null
    private var sNativeIsActionButton: Method? = null
    private var sAndroidXIsActionButton: Method? = null

    @VisibleForTesting
    fun setupMethods(accessor: PrivateMethodAccessor) {
        // Note: Multiple of these can succeed.
        val nativeImpl = accessor.getPrivateMethod(NATIVE_CLASS, "isActionButton")
        sNativeClassRef = nativeImpl.first
        sNativeIsActionButton = nativeImpl.second
        val androidXImpl = accessor.getPrivateMethod(ANDROIDX_CLASS, "isActionButton")
        sAndroidXClassRef = androidXImpl.first
        sAndroidXIsActionButton = androidXImpl.second
    }

    @CheckResult
    private fun getPrivateMethodHandleSystemErrors(className: String, methodName: String): Pair<Class<*>?, Method?> {
        var action: Method? = null
        var menuItemImpl: Class<*>? = null
        try {
            // We know this won't always work, we'll log if this isn't the case.
            menuItemImpl = Class.forName(className)
            action = menuItemImpl.getDeclaredMethod(methodName)
            action.isAccessible = true
            Timber.d("Setup ActionBarOverflow: %s", className)
        } catch (ignoreAndLogEx: Exception) {
            // See: #5806. MenuItemImpl;->isActionButton is on the light greylist
            // https://developer.android.com/distribute/best-practices/develop/restrictions-non-sdk-interfaces#results-of-keeping-non-sdk
            Timber.d(ignoreAndLogEx, "Failed to handle: %s", className)
        } catch (ignoreAndLogEx: NoSuchFieldError) {
            Timber.d(ignoreAndLogEx, "Failed to handle: %s", className)
        } catch (ignoreAndLogEx: NoSuchMethodError) {
            Timber.d(ignoreAndLogEx, "Failed to handle: %s", className)
        }
        return Pair(menuItemImpl, action)
    }

    /**
     * Check if an item is showing (not in the overflow menu).
     *
     * @param item
     * the MenuItem.
     * @return `true` if the MenuItem is visible on the ActionBar. `false` if not. `null if unknown`
     */
    fun isActionButton(item: MenuItem): Boolean? {
        return if (sNativeClassRef != null && sNativeClassRef!!.isInstance(item)) {
            tryInvokeMethod(item, sNativeIsActionButton)
        } else if (sAndroidXClassRef != null && sAndroidXClassRef!!.isInstance(item)) {
            tryInvokeMethod(item, sAndroidXIsActionButton)
        } else {
            Timber.w("Unhandled ActionBar class: %s", item.javaClass.name)
            null
        }
    }

    private fun tryInvokeMethod(item: MenuItem, method: Method?): Boolean? {
        return try {
            method!!.invoke(item) as Boolean
        } catch (ex: Exception) {
            Timber.w(ex, "Error handling ActionBar class: %s", item.javaClass.name)
            null
        } catch (ex: NoSuchFieldError) {
            Timber.w(ex, "Error handling ActionBar class: %s", item.javaClass.name)
            null
        } catch (ex: NoSuchMethodError) {
            Timber.w(ex, "Error handling ActionBar class: %s", item.javaClass.name)
            null
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun hasUsableMethod(): Boolean {
        return sNativeIsActionButton != null ||
            sAndroidXIsActionButton != null
    }

    @CheckResult
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun getPrivateMethodOnlyHandleExceptions(className: String?, methodName: String?): Pair<Class<*>?, Method?> {
        var action: Method? = null
        var menuItemImpl: Class<*>? = null
        try {
            menuItemImpl = Class.forName(className!!)
            action = menuItemImpl.getDeclaredMethod(methodName!!)
            action.isAccessible = true
            Timber.d("Setup ActionBarOverflow: %s", className)
        } catch (ignoreAndLogEx: Exception) {
            Timber.d(ignoreAndLogEx, "Failed to handle: %s", className)
        }
        return Pair(menuItemImpl, action)
    }

    @VisibleForTesting
    fun interface PrivateMethodAccessor {
        fun getPrivateMethod(className: String, methodName: String): Pair<Class<*>?, Method?>
    }

    init {
        setupMethods(::getPrivateMethodHandleSystemErrors)
    }
}
