package com.ichi2.utils;


/** TODO: Move this to standard library in API 24 */
public final class FunctionalInterfaces {

    @FunctionalInterface
    public interface Supplier<T> {
        T get();
    }

    @FunctionalInterface
    public interface Consumer<T> {
        void consume(T item);
    }
}
