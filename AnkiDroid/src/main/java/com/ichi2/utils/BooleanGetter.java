package com.ichi2.utils;

/**
 * A simple interface with a getter for a specific type
 */
public interface BooleanGetter {
    boolean getBoolean();

    BooleanGetter TRUE = () -> true;
    BooleanGetter FALSE = () -> false;
    static BooleanGetter fromBoolean(boolean b) {
        return (() -> b);
    }
}
