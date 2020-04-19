package com.ichi2.ui;

import android.annotation.SuppressLint;
import android.view.MenuItem;

import java.lang.reflect.Method;

import androidx.annotation.Nullable;
import timber.log.Timber;

/**
 * Detection of whether an item is in the ActionBar overflow
 * WARN: When making changes to this code, also test with Proguard
 * */
public class ActionBarOverflow {
    //Idea from: https://stackoverflow.com/a/29208483

    protected static final String SUPPORT_CLASS = "android.support.v7.internal.view.menu.MenuItemImpl";

    protected static final String NATIVE_CLASS = "com.android.internal.view.menu.MenuItemImpl";

    protected static final String ANDROIDX_CLASS = "androidx.appcompat.view.menu.MenuItemImpl";

    protected static Method sSupportIsActionButton;

    protected static Method sNativeIsActionButton;

    protected static Method sAndroidXIsActionButton;

    static {
        try {
            //We know this won't always work, we'll log if this isn't the case.
            @SuppressLint("PrivateApi") Class<?> MenuItemImpl = Class.forName(NATIVE_CLASS);
            sNativeIsActionButton = MenuItemImpl.getDeclaredMethod("isActionButton");
            sNativeIsActionButton.setAccessible(true);
        } catch (Exception ignoreAndLogEx) {
            Timber.d(ignoreAndLogEx, "Failed to obtain: sNativeIsActionButton");
        }
        try {
            Class<?> MenuItemImpl = Class.forName(SUPPORT_CLASS);
            sSupportIsActionButton = MenuItemImpl.getDeclaredMethod("isActionButton");
            sSupportIsActionButton.setAccessible(true);
        } catch (Exception ignoreAndLogEx) {
            Timber.d(ignoreAndLogEx, "Failed to obtain: sSupportIsActionButton");
        }
        try {
            Class<?> MenuItemImpl = Class.forName(ANDROIDX_CLASS);
            sAndroidXIsActionButton = MenuItemImpl.getDeclaredMethod("isActionButton");
            sAndroidXIsActionButton.setAccessible(true);
        } catch (Exception ignoreAndLogEx) {
            Timber.d(ignoreAndLogEx, "Failed to obtain: sAndroidXIsActionButton");
        }
    }

// --------------------------------------------------------------------------------------------

    /**
     * Check if an item is showing (not in the overflow menu).
     *
     * @param item
     *            the MenuItem.
     * @return {@code true} if the MenuItem is visible on the ActionBar. {@code false} if not. {@code null if unknown)
     */
    public static @Nullable Boolean isActionButton(MenuItem item) {
        //I don't think falling through is the right action here.
        String className = item.getClass().getName();
        switch (className) {
            case SUPPORT_CLASS:
                try {
                    return (boolean) sSupportIsActionButton.invoke(item, (Object[]) null);
                } catch (Exception e) {
                    // fall through
                }
            case NATIVE_CLASS:
                try {
                    return (boolean) sNativeIsActionButton.invoke(item, (Object[]) null);
                } catch (Exception e) {
                    // fall through
                }
            case ANDROIDX_CLASS:
                try {
                    return (boolean) sAndroidXIsActionButton.invoke(item, (Object[]) null);
                } catch (Exception e) {
                    // fall through
                }
            default:
                Timber.w("Unhandled ActionBar class: %s", className);
                return null;
        }
    }
}
