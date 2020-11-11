package com.ichi2.utils;

/**
 * A simple interface with a getter for a specific type
 */
public interface BooleanGetter {
    boolean getBoolean();

    public static BooleanGetter fromBoolean(boolean b) {
        return (() -> b);
    }
}
