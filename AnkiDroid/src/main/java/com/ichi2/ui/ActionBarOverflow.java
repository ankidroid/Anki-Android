package com.ichi2.ui;

import android.annotation.SuppressLint;
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

    protected static final String SUPPORT_CLASS = "android.support.v7.internal.view.menu.MenuItemImpl";

    protected static final String NATIVE_CLASS = "com.android.internal.view.menu.MenuItemImpl";

    protected static final String ANDROIDX_CLASS = "androidx.appcompat.view.menu.MenuItemImpl";

    protected static Method sSupportIsActionButton;

    protected static Method sNativeIsActionButton;

    protected static Method sAndroidXIsActionButton;

    static {
        setupMethods(ActionBarOverflow::getPrivateMethodHandleSystemErrors);
    }

    @VisibleForTesting
    static void setupMethods(PrivateMethodAccessor accessor) {
        //Note: Multiple of these can succeed.
        sNativeIsActionButton = accessor.getPrivateMethod(NATIVE_CLASS, "isActionButton");
        sSupportIsActionButton = accessor.getPrivateMethod(SUPPORT_CLASS, "isActionButton");
        sAndroidXIsActionButton = accessor.getPrivateMethod(ANDROIDX_CLASS, "isActionButton");
    }


    @CheckResult
    private static Method getPrivateMethodHandleSystemErrors(String className, String methodName) {
        Method action = null;
        try {
            //We know this won't always work, we'll log if this isn't the case.
            @SuppressLint("PrivateApi") Class<?> MenuItemImpl = Class.forName(className);
            action = MenuItemImpl.getDeclaredMethod(methodName);
            action.setAccessible(true);
            Timber.d("Setup ActionBarOverflow: %s", className);
        } catch (Exception | NoSuchFieldError | NoSuchMethodError ignoreAndLogEx) {
            //See: #5806. MenuItemImpl;->isActionButton is on the light greylist
            //https://developer.android.com/distribute/best-practices/develop/restrictions-non-sdk-interfaces#results-of-keeping-non-sdk
            Timber.d(ignoreAndLogEx, "Failed to handle: %s", className);
        }

        return action;
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

    @VisibleForTesting
    @FunctionalInterface
    interface PrivateMethodAccessor {
        Method getPrivateMethod(String className, String methodName);
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    static boolean hasUsableMethod() {
        return  sSupportIsActionButton != null ||
                sNativeIsActionButton != null ||
                sAndroidXIsActionButton != null;

    }

    @CheckResult
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    static Method getPrivateMethodOnlyHandleExceptions(String className, String methodName) {
        Method action = null;
        try {
            @SuppressLint("PrivateApi") Class<?> MenuItemImpl = Class.forName(className);
            action = MenuItemImpl.getDeclaredMethod(methodName);
            action.setAccessible(true);
            Timber.d("Setup ActionBarOverflow: %s", className);
        } catch (Exception ignoreAndLogEx) {
            Timber.d(ignoreAndLogEx, "Failed to handle: %s", className);
        }

        return action;
    }
}
