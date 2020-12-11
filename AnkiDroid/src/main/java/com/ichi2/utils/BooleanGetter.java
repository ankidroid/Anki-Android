package com.ichi2.utils;

/**
 * A simple interface with a getter for a specific type
 */
public interface BooleanGetter {
    boolean getBoolean();

    BooleanGetter True = () -> true;
    BooleanGetter False = () -> false;
    static BooleanGetter fromBoolean(boolean b) {
        return (() -> b);
    }
}
