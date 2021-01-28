package com.ichi2.utils;

public class PairWithBoolean<U> implements BooleanGetter {
    public final boolean bool;
    public final U other;

    public boolean getBoolean() {
        return bool;
    }

    public PairWithBoolean(boolean bool, U other) {
        this.bool = bool;
        this.other = other;
    }
}
