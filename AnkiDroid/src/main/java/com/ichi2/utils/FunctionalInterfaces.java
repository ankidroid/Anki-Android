package com.ichi2.utils;


import androidx.annotation.NonNull;

public final class FunctionalInterfaces {

    @FunctionalInterface
    public interface FunctionThrowable<TIn, TOut, TEx extends Throwable> {
        TOut apply(TIn item) throws TEx;
    }

    @FunctionalInterface
    public interface Filter<TIn> {
        boolean shouldInclude(TIn item);
    }

    public static class Filters {
        @NonNull
        public static <T> Filter<T> allowAll() {
            return (a) -> true;
        }
    }
}
