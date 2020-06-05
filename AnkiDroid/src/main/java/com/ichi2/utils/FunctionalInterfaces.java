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

    @FunctionalInterface
    public interface Function<TIn, TOut> {
        TOut apply(TIn item);
    }

    @FunctionalInterface
    public interface FunctionThrowable<TIn, TOut, TEx extends Throwable> {
        TOut apply(TIn item) throws TEx;
    }
}
