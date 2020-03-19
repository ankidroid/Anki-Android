package com.ichi2.utils;

public final class FunctionalInterfaces {

    /** TODO: Move this to Supplier in API 24 */
    @FunctionalInterface
    public interface Supplier<T> {
        T get();
    }
}
